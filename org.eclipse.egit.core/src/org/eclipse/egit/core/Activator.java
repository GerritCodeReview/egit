/*******************************************************************************
 * Copyright (C) 2008, 2015 Shawn O. Pearce <spearce@spearce.org> and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.egit.core;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.Authenticator;
import java.net.ProxySelector;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.eclipse.core.net.proxy.IProxyService;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IRegistryEventListener;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.egit.core.internal.CoreText;
import org.eclipse.egit.core.internal.EGitSshdSessionFactory;
import org.eclipse.egit.core.internal.ReportingTypedConfigGetter;
import org.eclipse.egit.core.internal.SshPreferencesMirror;
import org.eclipse.egit.core.internal.indexdiff.IndexDiffCache;
import org.eclipse.egit.core.internal.job.JobUtil;
import org.eclipse.egit.core.internal.job.RuleUtil;
import org.eclipse.egit.core.internal.trace.GitTraceLocation;
import org.eclipse.egit.core.internal.util.ResourceUtil;
import org.eclipse.egit.core.op.ConnectProviderOperation;
import org.eclipse.egit.core.op.IgnoreOperation;
import org.eclipse.egit.core.project.GitProjectData;
import org.eclipse.egit.core.project.RepositoryFinder;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.core.securestorage.EGitSecureStore;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.events.ListenerHandle;
import org.eclipse.jgit.events.WorkingTreeModifiedEvent;
import org.eclipse.jgit.events.WorkingTreeModifiedListener;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.eclipse.jsch.core.IJSchService;
import org.eclipse.osgi.service.debug.DebugOptions;
import org.eclipse.osgi.service.debug.DebugOptionsListener;
import org.eclipse.osgi.util.NLS;
import org.eclipse.team.core.RepositoryProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;

/**
 * The plugin class for the org.eclipse.egit.core plugin. This
 * is a singleton class.
 */
public class Activator extends Plugin implements DebugOptionsListener {

	private enum SshClientType {
		JSCH, APACHE
	}

	private static Activator plugin;
	private static String pluginId;
	private RepositoryCache repositoryCache;
	private IndexDiffCache indexDiffCache;
	private RepositoryUtil repositoryUtil;
	private EGitSecureStore secureStore;
	private AutoShareProjects shareGitProjectsJob;
	private IResourceChangeListener preDeleteProjectListener;
	private IgnoreDerivedResources ignoreDerivedResourcesListener;
	private MergeStrategyRegistryListener mergeStrategyRegistryListener;
	private IPreferenceChangeListener sshClientChangeListener;
	private ServiceTracker<IProxyService, IProxyService> proxyServiceTracker;
	private ResourceRefreshJob refreshJob;
	private ListenerHandle refreshHandle;

	/**
	 * @return the singleton {@link Activator}
	 */
	public static Activator getDefault() {
		return plugin;
	}

	/**
	 * @return the name of this plugin
	 */
	public static String getPluginId() {
		return pluginId;
	}

	/**
	 * Utility to create an error status for this plug-in.
	 *
	 * @param message User comprehensible message
	 * @param thr cause
	 * @return an initialized error status
	 */
	public static IStatus error(final String message, final Throwable thr) {
		return new Status(IStatus.ERROR, getPluginId(), 0,	message, thr);
	}

	/**
	 * Utility to create a cancel status for this plug-in.
	 *
	 * @param message
	 *            User comprehensible message
	 * @param thr
	 *            cause
	 * @return an initialized cancel status
	 */
	public static IStatus cancel(final String message, final Throwable thr) {
		return new Status(IStatus.CANCEL, getPluginId(), 0, message, thr);
	}

	/**
	 * Utility method to log errors in the Egit plugin.
	 *
	 * @param message
	 *            User comprehensible message
	 * @param thr
	 *            The exception through which we noticed the error
	 */
	public static void logError(final String message, final Throwable thr) {
		getDefault().getLog().log(error(message, thr));
	}

	/**
	 * Log an info message for this plug-in
	 *
	 * @param message
	 */
	public static void logInfo(final String message) {
		getDefault().getLog().log(
				new Status(IStatus.INFO, getPluginId(), 0, message, null));
	}

	/**
	 * Utility to create a warning status for this plug-in.
	 *
	 * @param message
	 *            User comprehensible message
	 * @param thr
	 *            cause
	 * @return an initialized warning status
	 */
	public static IStatus warning(final String message, final Throwable thr) {
		return new Status(IStatus.WARNING, getPluginId(), 0, message, thr);
	}

	/**
	 * Utility method to log warnings for this plug-in.
	 *
	 * @param message
	 *            User comprehensible message
	 * @param thr
	 *            The exception through which we noticed the warning
	 */
	public static void logWarning(final String message, final Throwable thr) {
		getDefault().getLog().log(warning(message, thr));
	}

	/**
	 * Construct the {@link Activator} singleton instance
	 */
	public Activator() {
		Activator.setActivator(this);
	}

	private static void setActivator(Activator a) {
		plugin = a;
	}

	@Override
	public void start(final BundleContext context) throws Exception {

		super.start(context);
		pluginId = context.getBundle().getSymbolicName();

		FS.setAsyncFileStoreAttributes(true);

		SystemReader.setInstance(
				new EclipseSystemReader(SystemReader.getInstance()));

		Config.setTypedConfigGetter(new ReportingTypedConfigGetter());
		// we want to be notified about debug options changes
		Dictionary<String, String> props = new Hashtable<>(4);
		props.put(DebugOptions.LISTENER_SYMBOLICNAME, pluginId);
		context.registerService(DebugOptionsListener.class.getName(), this,
				props);

		SshPreferencesMirror.INSTANCE.start();
		proxyServiceTracker = new ServiceTracker<>(context,
				IProxyService.class.getName(), null);
		proxyServiceTracker.open();
		setupSSH(context);
		sshClientChangeListener = event -> {
			if (GitCorePreferences.core_sshClient.equals(event.getKey())) {
				setupSSH(getBundle().getBundleContext());
			}
		};
		InstanceScope.INSTANCE.getNode(pluginId)
				.addPreferenceChangeListener(sshClientChangeListener);
		setupProxy();

		repositoryCache = new RepositoryCache();
		indexDiffCache = new IndexDiffCache();
		try {
			GitProjectData.reconfigureWindowCache();
		} catch (RuntimeException e) {
			logError(CoreText.Activator_ReconfigureWindowCacheError, e);
		}
		GitProjectData.attachToWorkspace();

		setupRepoResourceRefresh();

		repositoryUtil = new RepositoryUtil();

		secureStore = new EGitSecureStore(SecurePreferencesFactory.getDefault());

		registerAutoShareProjects();
		registerAutoIgnoreDerivedResources();
		registerPreDeleteResourceChangeListener();
		registerMergeStrategyRegistryListener();
		registerBuiltinLFS();
	}

	@SuppressWarnings("unchecked")
	private void setupSSH(final BundleContext context) {
		String sshClient = Platform.getPreferencesService().getString(pluginId,
				GitCorePreferences.core_sshClient, "apache", null); //$NON-NLS-1$
		SshSessionFactory previous = SshSessionFactory.getInstance();
		if (SshClientType.JSCH.name().equalsIgnoreCase(sshClient)) {
			if (previous instanceof EclipseSshSessionFactory) {
				return;
			}
			ServiceReference ssh = context
					.getServiceReference(IJSchService.class.getName());
			if (ssh != null) {
				SshSessionFactory.setInstance(new EclipseSshSessionFactory(
						(IJSchService) context.getService(ssh)));
			} else {
				// Should never happen
				logWarning(CoreText.Activator_SshClientNoJsch, null);
				if (previous instanceof EGitSshdSessionFactory) {
					return;
				}
				SshSessionFactory.setInstance(new EGitSshdSessionFactory());
			}
		} else {
			if (!SshClientType.APACHE.name().equalsIgnoreCase(sshClient)) {
				logWarning(
						MessageFormat.format(
								CoreText.Activator_SshClientUnknown, sshClient),
						null);
			}
			if (previous instanceof EGitSshdSessionFactory) {
				return;
			}
			SshSessionFactory.setInstance(new EGitSshdSessionFactory());
		}
		if (previous instanceof SshdSessionFactory) {
			((SshdSessionFactory) previous).close();
		}
	}

	private void setupProxy() {
		IProxyService proxy = getProxyService();
		if (proxy != null) {
			ProxySelector.setDefault(new EclipseProxySelector(proxy));
			Authenticator.setDefault(new EclipseAuthenticator(proxy));
		}
	}

	private void setupRepoResourceRefresh() {
		refreshJob = new ResourceRefreshJob();
		refreshHandle = Repository.getGlobalListenerList()
				.addWorkingTreeModifiedListener(refreshJob);
	}

	private void registerPreDeleteResourceChangeListener() {
		if (preDeleteProjectListener == null) {
			preDeleteProjectListener = new IResourceChangeListener() {

				@Override
				public void resourceChanged(IResourceChangeEvent event) {
					IResource resource = event.getResource();
					if (resource instanceof IProject) {
						IProject project = (IProject) resource;
						if (project.isAccessible()) {
							if (ResourceUtil.isSharedWithGit(project)) {
								IResource dotGit = project
										.findMember(Constants.DOT_GIT);
								if (dotGit != null && dotGit
										.getType() == IResource.FOLDER) {
									GitProjectData.reconfigureWindowCache();
								}
							}
						} else {
							// bug 419706: project is closed - use java.io API
							IPath locationPath = project.getLocation();
							if (locationPath != null) {
								File locationDir = locationPath.toFile();
								File dotGit = new File(locationDir,
										Constants.DOT_GIT);
								if (dotGit.exists() && dotGit.isDirectory()) {
									GitProjectData.reconfigureWindowCache();
								}
							}
						}
					}
				}
			};
			ResourcesPlugin.getWorkspace().addResourceChangeListener(preDeleteProjectListener, IResourceChangeEvent.PRE_DELETE);
		}
	}

	private void registerBuiltinLFS() {
		if (Platform.getBundle("org.eclipse.jgit.lfs") != null) { //$NON-NLS-1$
			Class<?> lfs;
			try {
				lfs = Class.forName("org.eclipse.jgit.lfs.BuiltinLFS"); //$NON-NLS-1$
				if (lfs != null) {
					lfs.getMethod("register").invoke(null); //$NON-NLS-1$
				}
			} catch (ClassNotFoundException | IllegalAccessException
					| IllegalArgumentException | InvocationTargetException
					| NoSuchMethodException | SecurityException e1) {
				logWarning(CoreText.Activator_noBuiltinLfsSupportDetected, e1);
			}
		}
	}

	@Override
	public void optionsChanged(DebugOptions options) {
		// initialize the trace stuff
		GitTraceLocation.initializeFromOptions(options, isDebugging());
	}

	/**
	 * Provides the 3-way merge strategy to use according to the user's
	 * preferences. The preferred merge strategy is JGit's default merge
	 * strategy unless the user has explicitly chosen a different strategy among
	 * the registered strategies.
	 *
	 * @return The MergeStrategy to use, can be {@code null}, in which case the
	 *         default merge strategy should be used as defined by JGit.
	 * @since 4.1
	 */
	public MergeStrategy getPreferredMergeStrategy() {
		// Get preferences set by user in the UI
		final IEclipsePreferences prefs = InstanceScope.INSTANCE
				.getNode(Activator.getPluginId());
		String preferredMergeStrategyKey = prefs.get(
				GitCorePreferences.core_preferredMergeStrategy, null);

		// Get default preferences, wherever they are defined
		if (preferredMergeStrategyKey == null
				|| preferredMergeStrategyKey.isEmpty()) {
			final IEclipsePreferences defaultPrefs = DefaultScope.INSTANCE
					.getNode(Activator.getPluginId());
			preferredMergeStrategyKey = defaultPrefs.get(
					GitCorePreferences.core_preferredMergeStrategy, null);
		}
		if (preferredMergeStrategyKey != null
				&& !preferredMergeStrategyKey.isEmpty()
				&& !GitCorePreferences.core_preferredMergeStrategy_Default
						.equals(preferredMergeStrategyKey)) {
			MergeStrategy result = MergeStrategy.get(preferredMergeStrategyKey);
			if (result != null) {
				return result;
			}
			logError(NLS.bind(CoreText.Activator_invalidPreferredMergeStrategy,
					preferredMergeStrategyKey), null);
		}
		return null;
	}

	/**
	 * @return Provides a read-only view of the registered MergeStrategies
	 *         available.
	 * @since 4.1
	 */
	public Collection<MergeStrategyDescriptor> getRegisteredMergeStrategies() {
		if (mergeStrategyRegistryListener == null) {
			return Collections.emptyList();
		}
		return mergeStrategyRegistryListener.getStrategies();
	}

	private void registerMergeStrategyRegistryListener() {
		mergeStrategyRegistryListener = new MergeStrategyRegistryListener(
				Platform.getExtensionRegistry());
		Platform.getExtensionRegistry().addListener(
				mergeStrategyRegistryListener,
				"org.eclipse.egit.core.mergeStrategy"); //$NON-NLS-1$
	}

	/**
	 * @return cache for Repository objects
	 */
	public RepositoryCache getRepositoryCache() {
		return repositoryCache;
	}

	/**
	 *  @return cache for index diffs
	 */
	public IndexDiffCache getIndexDiffCache() {
		return indexDiffCache;
	}

	/**
	 * @return the {@link RepositoryUtil} instance
	 */
	public RepositoryUtil getRepositoryUtil() {
		return repositoryUtil;
	}

	/**
	 * @return the secure store
	 */
	public EGitSecureStore getSecureStore() {
		return secureStore;
	}

	/**
	 * Obtains the {@link IProxyService}.
	 *
	 * @return the {@link IProxyService} or {@code null} if none is available.
	 */
	public IProxyService getProxyService() {
		return proxyServiceTracker.getService();
	}

	@Override
	public void stop(final BundleContext context) throws Exception {
		if (refreshHandle != null) {
			refreshHandle.remove();
			refreshHandle = null;
		}
		if (GitTraceLocation.REFRESH.isActive()) {
			GitTraceLocation.getTrace().trace(
					GitTraceLocation.REFRESH.getLocation(),
					"Trying to cancel " + refreshJob.getName() + " job"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		refreshJob.cancel();
		refreshJob.join();

		SshPreferencesMirror.INSTANCE.stop();
		if (sshClientChangeListener != null) {
			InstanceScope.INSTANCE.getNode(pluginId)
					.removePreferenceChangeListener(sshClientChangeListener);
			sshClientChangeListener = null;
		}
		SshSessionFactory current = SshSessionFactory.getInstance();
		if (current instanceof SshdSessionFactory) {
			((SshdSessionFactory) current).close();
		}
		if (proxyServiceTracker != null) {
			proxyServiceTracker.close();
			proxyServiceTracker = null;
		}
		if (mergeStrategyRegistryListener != null) {
			Platform.getExtensionRegistry()
					.removeListener(mergeStrategyRegistryListener);
			mergeStrategyRegistryListener = null;
		}
		if (preDeleteProjectListener != null) {
			ResourcesPlugin.getWorkspace().removeResourceChangeListener(preDeleteProjectListener);
			preDeleteProjectListener = null;
		}
		if (ignoreDerivedResourcesListener != null) {
			ResourcesPlugin.getWorkspace().removeResourceChangeListener(
					ignoreDerivedResourcesListener);
			ignoreDerivedResourcesListener.stop();
			ignoreDerivedResourcesListener = null;
		}
		if (shareGitProjectsJob != null) {
			ResourcesPlugin.getWorkspace().removeResourceChangeListener(
					shareGitProjectsJob);
			shareGitProjectsJob.stop();
			shareGitProjectsJob = null;
		}
		GitProjectData.detachFromWorkspace();
		indexDiffCache.dispose();
		indexDiffCache = null;
		repositoryCache.clear();
		repositoryCache = null;
		repositoryUtil.dispose();
		repositoryUtil = null;
		secureStore = null;
		Config.setTypedConfigGetter(null);
		super.stop(context);
		plugin = null;
	}

	/**
	 * Wait for the resource refresh job to be finished refreshing the event
	 * with the given id
	 *
	 * @param repo
	 *            repository the event was fired for
	 * @since 5.6
	 */
	public void waitForRefresh(Repository repo) {
		try {
			while (refreshJob.waitsForRefresh(repo)) {
				refreshJob.join();
			}
		} catch (InterruptedException e) {
			// Re-set interrupted flag
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Wait for the resource refresh job to be finished refreshing the event
	 * with the given id
	 *
	 * @param repos
	 *            repositories an event was fired for
	 * @since 5.6
	 */
	public void waitForRefresh(Collection<Repository> repos) {
		for (Repository r : repos) {
			waitForRefresh(r);
		}
	}

	private void registerAutoShareProjects() {
		shareGitProjectsJob = new AutoShareProjects();
		ResourcesPlugin.getWorkspace().addResourceChangeListener(
				shareGitProjectsJob, IResourceChangeEvent.POST_CHANGE);
	}

	private static class AutoShareProjects implements IResourceChangeListener {

		private static int INTERESTING_CHANGES = IResourceDelta.ADDED
				| IResourceDelta.OPEN;

		private final CheckProjectsToShare checkProjectsJob;

		public AutoShareProjects() {
			checkProjectsJob = new CheckProjectsToShare();
		}

		private boolean doAutoShare() {
			IEclipsePreferences d = DefaultScope.INSTANCE.getNode(Activator
					.getPluginId());
			IEclipsePreferences p = InstanceScope.INSTANCE.getNode(Activator
					.getPluginId());
			return p.getBoolean(GitCorePreferences.core_autoShareProjects, d
					.getBoolean(GitCorePreferences.core_autoShareProjects,
							true));
		}

		public void stop() {
			boolean isRunning = !checkProjectsJob.cancel();
			Job.getJobManager().cancel(JobFamilies.AUTO_SHARE);
			try {
				if (isRunning) {
					checkProjectsJob.join();
				}
				Job.getJobManager().join(JobFamilies.AUTO_SHARE,
						new NullProgressMonitor());
			} catch (OperationCanceledException e) {
				// Ignore
			} catch (InterruptedException e) {
				logError(e.getLocalizedMessage(), e);
			}
		}

		@Override
		public void resourceChanged(IResourceChangeEvent event) {
			if (!doAutoShare()) {
				return;
			}
			try {
				final Set<IProject> projectCandidates = new LinkedHashSet<>();
				event.getDelta().accept(new IResourceDeltaVisitor() {
					@Override
					public boolean visit(IResourceDelta delta)
							throws CoreException {
						return collectOpenedProjects(delta,
								projectCandidates);
					}
				});
				if(!projectCandidates.isEmpty()){
					checkProjectsJob.addProjectsToCheck(projectCandidates);
				}
			} catch (CoreException e) {
				Activator.logError(e.getMessage(), e);
				return;
			}
		}

		/*
		 * This method should not use RepositoryMapping.getMapping(project) or
		 * RepositoryProvider.getProvider(project) which can trigger
		 * RepositoryProvider.map(project) and deadlock current thread. See
		 * https://bugs.eclipse.org/bugs/show_bug.cgi?id=468270
		 */
		private boolean collectOpenedProjects(IResourceDelta delta,
				Set<IProject> projects) {
			if (delta.getKind() == IResourceDelta.CHANGED
					&& (delta.getFlags() & INTERESTING_CHANGES) == 0) {
				return true;
			}
			final IResource resource = delta.getResource();
			if (resource.getType() == IResource.ROOT) {
				return true;
			}
			if (resource.getType() != IResource.PROJECT) {
				return false;
			}
			if (!resource.isAccessible() || resource.getLocation() == null) {
				return false;
			}
			projects.add((IProject) resource);
			return false;
		}

	}

	private static class CheckProjectsToShare extends Job {
		private Object lock = new Object();

		private Set<IProject> projectCandidates;

		public CheckProjectsToShare() {
			super(CoreText.Activator_AutoShareJobName);
			this.projectCandidates = new LinkedHashSet<>();
			setUser(false);
			setSystem(true);
		}

		public void addProjectsToCheck(Set<IProject> projects) {
			synchronized (lock) {
				this.projectCandidates.addAll(projects);
				if (!projectCandidates.isEmpty()) {
					schedule(100);
				}
			}
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			Set<IProject> projectsToCheck;
			synchronized (lock) {
				projectsToCheck = projectCandidates;
				projectCandidates = new LinkedHashSet<>();
			}
			if (projectsToCheck.isEmpty()) {
				return Status.OK_STATUS;
			}

			final Map<IProject, File> projects = new HashMap<>();
			for (IProject project : projectsToCheck) {
				if (monitor.isCanceled()) {
					return Status.CANCEL_STATUS;
				}
				if (project.isAccessible()) {
					try {
						visitConnect(project, projects);
					} catch (CoreException e) {
						logError(e.getMessage(), e);
					}
				}
			}
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
			if (projects.size() > 0) {
				ConnectProviderOperation op = new ConnectProviderOperation(
						projects);
				op.setRefreshResources(false);
				JobUtil.scheduleUserJob(op,
						CoreText.Activator_AutoShareJobName,
						JobFamilies.AUTO_SHARE);
			}
			return Status.OK_STATUS;
		}

		private void visitConnect(IProject project,
				final Map<IProject, File> projects) throws CoreException {

			if (RepositoryMapping.getMapping(project) != null) {
				return;
			}
			RepositoryProvider provider = RepositoryProvider
					.getProvider(project);
			// respect if project is already shared with another
			// team provider
			if (provider != null) {
				return;
			}
			RepositoryFinder f = new RepositoryFinder(project);
			f.setFindInChildren(false);
			List<RepositoryMapping> mappings = f
					.find(new NullProgressMonitor());
			if (mappings.isEmpty()) {
				return;
			}
			RepositoryMapping m = mappings.get(0);
			IPath gitDirPath = m.getGitDirAbsolutePath();
			if (gitDirPath == null || gitDirPath.segmentCount() == 0) {
				return;
			}

			IPath workingDir = gitDirPath.removeLastSegments(1);
			// Don't connect "/" or "C:\"
			if (workingDir.isRoot()) {
				return;
			}

			File userHome = FS.DETECTED.userHome();
			if (userHome != null) {
				Path userHomePath = new Path(userHome.getAbsolutePath());
				// Don't connect "/home" or "/home/username"
				if (workingDir.isPrefixOf(userHomePath)) {
					return;
				}
			}

			// connect
			File repositoryDir = gitDirPath.toFile();
			projects.put(project, repositoryDir);

			// If we had more than one mapping: add the last one as
			// 'configured' repository. We don't want to add submodules,
			// that would only lead to problems when a configured repository
			// is deleted.
			int nofMappings = mappings.size();
			if (nofMappings > 1) {
				IPath lastPath = mappings.get(nofMappings - 1)
						.getGitDirAbsolutePath();
				if (lastPath != null) {
					repositoryDir = lastPath.toFile();
				}
			}
			try {
				Activator.getDefault().getRepositoryUtil()
						.addConfiguredRepository(repositoryDir);
			} catch (IllegalArgumentException e) {
				logError(CoreText.Activator_AutoSharingFailed, e);
			}
		}
	}

	private void registerAutoIgnoreDerivedResources() {
		ignoreDerivedResourcesListener = new IgnoreDerivedResources();
		ResourcesPlugin.getWorkspace().addResourceChangeListener(
				ignoreDerivedResourcesListener,
				IResourceChangeEvent.POST_CHANGE);
	}

	/**
	 * @return true if the derived resources should be automatically added to
	 *         the .gitignore files
	 */
	public static boolean autoIgnoreDerived() {
		IEclipsePreferences d = DefaultScope.INSTANCE
				.getNode(Activator.getPluginId());
		IEclipsePreferences p = InstanceScope.INSTANCE
				.getNode(Activator.getPluginId());
		return p.getBoolean(GitCorePreferences.core_autoIgnoreDerivedResources,
				d.getBoolean(GitCorePreferences.core_autoIgnoreDerivedResources,
						true));
	}

	/**
	 * @return {@code true} if files that get deleted should be automatically
	 *         staged
	 * @since 4.6
	 */
	public static boolean autoStageDeletion() {
		IEclipsePreferences d = DefaultScope.INSTANCE
				.getNode(Activator.getPluginId());
		IEclipsePreferences p = InstanceScope.INSTANCE
				.getNode(Activator.getPluginId());
		boolean autoStageDeletion = p.getBoolean(
				GitCorePreferences.core_autoStageDeletion,
				d.getBoolean(GitCorePreferences.core_autoStageDeletion, false));
		return autoStageDeletion;
	}

	/**
	 * @return {@code true} if files that are moved should be automatically
	 *         staged
	 * @since 4.6
	 */
	public static boolean autoStageMoves() {
		IEclipsePreferences d = DefaultScope.INSTANCE
				.getNode(Activator.getPluginId());
		IEclipsePreferences p = InstanceScope.INSTANCE
				.getNode(Activator.getPluginId());
		boolean autoStageMoves = p.getBoolean(
				GitCorePreferences.core_autoStageMoves,
				d.getBoolean(GitCorePreferences.core_autoStageMoves, false));
		return autoStageMoves;
	}
	private static class IgnoreDerivedResources implements
			IResourceChangeListener {

		public void stop() {
			Job.getJobManager().cancel(JobFamilies.AUTO_IGNORE);
			try {
				Job.getJobManager().join(JobFamilies.AUTO_IGNORE,
						new NullProgressMonitor());
			} catch (OperationCanceledException e) {
				// Ignore
			} catch (InterruptedException e) {
				logError(e.getLocalizedMessage(), e);
			}
		}

		@Override
		public void resourceChanged(IResourceChangeEvent event) {
			try {
				IResourceDelta d = event.getDelta();
				if (d == null || !autoIgnoreDerived()) {
					return;
				}

				final Set<IPath> toBeIgnored = new LinkedHashSet<>();

				d.accept(new IResourceDeltaVisitor() {

					@Override
					public boolean visit(IResourceDelta delta)
							throws CoreException {
						if ((delta.getKind() & (IResourceDelta.ADDED | IResourceDelta.CHANGED)) == 0)
							return false;
						int flags = delta.getFlags();
						if ((flags != 0)
								&& ((flags & IResourceDelta.DERIVED_CHANGED) == 0))
							return false;

						final IResource r = delta.getResource();
						// don't consider resources contained in a project not
						// shared with Git team provider
						if ((r.getProject() != null)
								&& (RepositoryMapping.getMapping(r) == null))
							return false;
						if (r.isTeamPrivateMember())
							return false;

						if (r.isDerived()) {
							try {
								IPath location = r.getLocation();
								if (RepositoryUtil.canBeAutoIgnored(location)) {
									toBeIgnored.add(location);
								}
							} catch (IOException e) {
								logError(
										MessageFormat.format(
												CoreText.Activator_ignoreResourceFailed,
												r.getFullPath()), e);
							}
							return false;
						}
						return true;
					}
				});
				if (toBeIgnored.size() > 0)
					JobUtil.scheduleUserJob(new IgnoreOperation(toBeIgnored),
							CoreText.Activator_autoIgnoreDerivedResources,
							JobFamilies.AUTO_IGNORE);
			} catch (CoreException e) {
				Activator.logError(e.getMessage(), e);
				return;
			}
		}
	}

	/**
	 * Describes a MergeStrategy which can be registered with the mergeStrategy
	 * extension point.
	 *
	 * @since 4.1
	 */
	public static class MergeStrategyDescriptor {
		private final String name;

		private final String label;

		private final Class<?> implementedBy;

		/**
		 * @param name
		 *            The referred strategy's name, to use for retrieving the
		 *            strategy from MergeRegistry via
		 *            {@link MergeStrategy#get(String)}
		 * @param label
		 *            The label to display to users so they can select the
		 *            strategy they need
		 * @param implementedBy
		 *            The class of the MergeStrategy registered through the
		 *            mergeStrategy extension point
		 */
		public MergeStrategyDescriptor(String name, String label,
				Class<?> implementedBy) {
			this.name = name;
			this.label = label;
			this.implementedBy = implementedBy;
		}

		/**
		 * @return The actual strategy's name, which can be used to retrieve
		 *         that actual strategy via {@link MergeStrategy#get(String)}.
		 */
		public String getName() {
			return name;
		}

		/**
		 * @return The strategy label, for display purposes.
		 */
		public String getLabel() {
			return label;
		}

		/**
		 * @return The class of the MergeStrategy registered through the
		 *         mergeStrategy extension point.
		 */
		public Class<?> getImplementedBy() {
			return implementedBy;
		}
	}

	private static class MergeStrategyRegistryListener implements
			IRegistryEventListener {

		private Map<String, MergeStrategyDescriptor> strategies;

		private MergeStrategyRegistryListener(IExtensionRegistry registry) {
			strategies = new LinkedHashMap<>();
			IConfigurationElement[] elements = registry
					.getConfigurationElementsFor("org.eclipse.egit.core.mergeStrategy"); //$NON-NLS-1$
			loadMergeStrategies(elements);
		}

		private Collection<MergeStrategyDescriptor> getStrategies() {
			return Collections.unmodifiableCollection(strategies.values());
		}

		@Override
		public void added(IExtension[] extensions) {
			for (IExtension extension : extensions) {
				loadMergeStrategies(extension.getConfigurationElements());
			}
		}

		@Override
		public void added(IExtensionPoint[] extensionPoints) {
			// Nothing to do here
		}

		@Override
		public void removed(IExtension[] extensions) {
			for (IExtension extension : extensions) {
				for (IConfigurationElement element : extension
						.getConfigurationElements()) {
					try {
						Object ext = element.createExecutableExtension("class"); //$NON-NLS-1$
						if (ext instanceof MergeStrategy) {
							MergeStrategy strategy = (MergeStrategy) ext;
							strategies.remove(strategy.getName());
						}
					} catch (CoreException e) {
						Activator.logError(CoreText.MergeStrategy_UnloadError,
								e);
					}
				}
			}
		}

		@Override
		public void removed(IExtensionPoint[] extensionPoints) {
			// Nothing to do here
		}

		private void loadMergeStrategies(IConfigurationElement[] elements) {
			for (IConfigurationElement element : elements) {
				try {
					Object ext = element.createExecutableExtension("class"); //$NON-NLS-1$
					if (ext instanceof MergeStrategy) {
						MergeStrategy strategy = (MergeStrategy) ext;
						String name = element.getAttribute("name"); //$NON-NLS-1$
						if (name == null || name.isEmpty()) {
							name = strategy.getName();
						}
						if (canRegister(name, strategy)) {
							if (MergeStrategy.get(name) == null) {
								MergeStrategy.register(name, strategy);
							}
							strategies
									.put(name,
											new MergeStrategyDescriptor(
													name,
													element.getAttribute("label"), //$NON-NLS-1$
													strategy.getClass()));
						}
					}
				} catch (CoreException e) {
					Activator.logError(CoreText.MergeStrategy_LoadError, e);
				}
			}
		}

		/**
		 * Checks whether it's possible to register the provided strategy with
		 * the given name
		 *
		 * @param name
		 *            Name to use to register the strategy
		 * @param strategy
		 *            Strategy to register
		 * @return <code>true</code> if the name is neither null nor empty, no
		 *         other strategy is already register for the same name, and the
		 *         name is not one of the core JGit strategies. If the given
		 *         name is that of a core JGit strategy, the method will return
		 *         <code>true</code> only if the strategy is the matching JGit
		 *         strategy for that name.
		 */
		private boolean canRegister(String name, MergeStrategy strategy) {
			boolean result = true;
			if (name == null || name.isEmpty()) {
				// name is mandatory
				Activator.logError(
						NLS.bind(CoreText.MergeStrategy_MissingName,
								strategy.getClass()), null);
				result = false;
			} else if (strategies.containsKey(name)) {
				// Other strategy already registered for this name
				Activator.logError(NLS.bind(
						CoreText.MergeStrategy_DuplicateName, new Object[] {
								name, strategies.get(name).getImplementedBy(),
								strategy.getClass() }), null);
				result = false;
			} else if (MergeStrategy.get(name) != null
					&& MergeStrategy.get(name) != strategy) {
				// The name is reserved by a core JGit strategy, and the
				// provided instance is not that of JGit
				Activator.logError(NLS.bind(
						CoreText.MergeStrategy_ReservedName, new Object[] {
								name, MergeStrategy.get(name).getClass(),
								strategy.getClass() }), null);
				result = false;
			}
			return result;
		}
	}

	/**
	 * A system reader that hides certain global git environment variables from
	 * JGit.
	 */
	private static class EclipseSystemReader extends SystemReader {

		/**
		 * Hide these variables lest JGit tries to use them for different
		 * repositories.
		 */
		private static final String[] HIDDEN_VARIABLES = {
				Constants.GIT_DIR_KEY, Constants.GIT_WORK_TREE_KEY,
				Constants.GIT_OBJECT_DIRECTORY_KEY,
				Constants.GIT_INDEX_FILE_KEY,
				Constants.GIT_ALTERNATE_OBJECT_DIRECTORIES_KEY };

		private final SystemReader delegate;

		public EclipseSystemReader(SystemReader delegate) {
			this.delegate = delegate;
		}

		@Override
		public String getenv(String variable) {
			String result = delegate.getenv(variable);
			if (result == null) {
				return result;
			}
			boolean isWin = isWindows();
			for (String gitvar : HIDDEN_VARIABLES) {
				if (isWin && gitvar.equalsIgnoreCase(variable)
						|| !isWin && gitvar.equals(variable)) {
					return null;
				}
			}
			return result;
		}

		@Override
		public String getHostname() {
			return delegate.getHostname();
		}

		@Override
		public String getProperty(String key) {
			return delegate.getProperty(key);
		}

		@Override
		public FileBasedConfig openUserConfig(Config parent, FS fs) {
			return delegate.openUserConfig(parent, fs);
		}

		@Override
		public FileBasedConfig openSystemConfig(Config parent, FS fs) {
			return delegate.openSystemConfig(parent, fs);
		}

		@Override
		public long getCurrentTime() {
			return delegate.getCurrentTime();
		}

		@Override
		public int getTimezone(long when) {
			return delegate.getTimezone(when);
		}

		@Override
		public StoredConfig getUserConfig()
				throws IOException, ConfigInvalidException {
			return delegate.getUserConfig();
		}

		@Override
		public StoredConfig getSystemConfig()
				throws IOException, ConfigInvalidException {
			return delegate.getSystemConfig();
		}
	}

	/**
	 * Refreshes resources in the workspace changed by JGit operations. This
	 * will not refresh any git-ignored resources since those are not reported
	 * in the {@link WorkingTreeModifiedEvent}.
	 */
	static class ResourceRefreshJob extends Job
			implements WorkingTreeModifiedListener {

		ResourceRefreshJob() {
			super(CoreText.Activator_refreshJobName);
			setUser(false);
			setSystem(true);
		}

		/**
		 * Internal helper class to record batched accumulated results from
		 * several {@link WorkingTreeModifiedEvent}s.
		 */
		private static class WorkingTreeChanges {

			private final Repository repo;

			private final Set<String> modified;

			private final Set<String> deleted;

			private final AtomicLong eventId;

			public WorkingTreeChanges(WorkingTreeModifiedEvent event) {
				repo = event.getRepository();
				modified = new HashSet<>(event.getModified());
				deleted = new HashSet<>(event.getDeleted());
				eventId = new AtomicLong(event.getId());
			}

			public Repository getRepo() {
				return repo;
			}

			public File getWorkTree() {
				return repo.getWorkTree().getAbsoluteFile();
			}

			public Set<String> getModified() {
				return modified;
			}

			public Set<String> getDeleted() {
				return deleted;
			}

			public boolean isEmpty() {
				return modified.isEmpty() && deleted.isEmpty();
			}

			/**
			 * @return the id of the event which notified about the changes, if
			 *         multiple events have been merged this is the maximum id
			 *         of the merged events
			 */
			public long getEventId() {
				return eventId.get();
			}

			public WorkingTreeChanges merge(WorkingTreeModifiedEvent event) {
				modified.removeAll(event.getDeleted());
				deleted.removeAll(event.getModified());
				modified.addAll(event.getModified());
				deleted.addAll(event.getDeleted());
				eventId.accumulateAndGet(event.getId(), Math::max);
				return this;
			}
		}

		private Map<File, WorkingTreeChanges> repositoriesChanged = new LinkedHashMap<>();

		private static class EventStatus {

			private final AtomicLong lastEvent = new AtomicLong(0);

			private final AtomicLong lastRefreshed = new AtomicLong(0);

			public EventStatus() {
				// default
			}

			public long recordEvent(long eventId) {
				return lastEvent.accumulateAndGet(eventId, Math::max);
			}

			public long refreshed(long eventId) {
				return lastRefreshed.accumulateAndGet(eventId, Math::max);
			}

			public synchronized boolean waitsForRefresh() {
				return lastRefreshed.get() < lastEvent.get();
			}

			@Override
			public String toString() {
				return "EventStatus [lastEvent=" + lastEvent //$NON-NLS-1$
						+ ", lastRefreshed=" + lastRefreshed + "]"; //$NON-NLS-1$ //$NON-NLS-2$
			}
		}

		private Map<Repository, EventStatus> refreshStatus = new ConcurrentHashMap<>();

		public boolean waitsForRefresh(Repository r) {
			EventStatus s = refreshStatus.computeIfAbsent(r, k -> new EventStatus());
			boolean waits = s.waitsForRefresh();
			if (GitTraceLocation.REFRESH.isActive()) {
				GitTraceLocation.getTrace().trace(
						GitTraceLocation.REFRESH.getLocation(),
						String.format("waits for refresh %s: %b", //$NON-NLS-1$
								r, Boolean.valueOf(waits)));
			}
			return waits;
		}

		public long recordEvent(WorkingTreeModifiedEvent e) {
			Repository r = e.getRepository();
			EventStatus s = refreshStatus.computeIfAbsent(r,
					k -> new EventStatus());
			long id = s.recordEvent(e.getId());
			if (GitTraceLocation.REFRESH.isActive()) {
				GitTraceLocation.getTrace().trace(
						GitTraceLocation.REFRESH.getLocation(),
						String.format("received refresh event %s: %d", //$NON-NLS-1$
								r, Long.valueOf(id)));
			}
			return id;
		}

		public long refreshed(Repository r, long eventId) {
			EventStatus s = refreshStatus.computeIfAbsent(r, (k -> new EventStatus()));
			long id = s.refreshed(eventId);
			if (GitTraceLocation.REFRESH.isActive()) {
				GitTraceLocation.getTrace().trace(
						GitTraceLocation.REFRESH.getLocation(),
						String.format("refreshed %s up to event %d", //$NON-NLS-1$
								r, Long.valueOf(id)));
			}
			return id;
		}

		@Override
		public IStatus run(IProgressMonitor monitor) {
			try {
				List<WorkingTreeChanges> changes;
				synchronized (repositoriesChanged) {
					if (repositoriesChanged.isEmpty()) {
						return Status.OK_STATUS;
					}
					changes = new ArrayList<>(repositoriesChanged.values());
					repositoriesChanged.clear();
				}

				SubMonitor progress = SubMonitor.convert(monitor,
						changes.size());
				try {
					for (WorkingTreeChanges change : changes) {
						refreshRepository(change, progress.newChild(1));
					}
				} catch (OperationCanceledException oe) {
					return Status.CANCEL_STATUS;
				} catch (CoreException e) {
					error(CoreText.Activator_refreshFailed, e);
					return new Status(IStatus.ERROR, getPluginId(),
							e.getMessage());
				}

				if (!monitor.isCanceled()) {
					// re-schedule if we got some changes in the meantime
					synchronized (repositoriesChanged) {
						if (!repositoriesChanged.isEmpty()) {
							schedule(100);
						}
					}
				}
			} finally {
				monitor.done();
			}
			return Status.OK_STATUS;
		}

		private void refreshRepository(WorkingTreeChanges changes,
				IProgressMonitor monitor) throws CoreException {
			if (monitor.isCanceled()) {
				throw new OperationCanceledException();
			}
			if (changes.isEmpty()) {
				return; // Should actually not occur
			}
			Map<IPath, IProject> roots = getProjectLocations(
					changes.getWorkTree());
			if (roots.isEmpty()) {
				// No open projects from this repository in the workspace
				return;
			}
			SubMonitor progress = SubMonitor.convert(monitor, 2);
			IPath workTree = new Path(changes.getWorkTree().getPath());
			Map<IResource, Boolean> toRefresh = computeResources(
					changes.getModified(), changes.getDeleted(), workTree,
					roots, progress.newChild(1));
			if (toRefresh.isEmpty()) {
				return;
			}
			IWorkspace workspace = ResourcesPlugin.getWorkspace();
			IWorkspaceRunnable operation = innerMonitor -> {
				SubMonitor innerProgress = SubMonitor.convert(innerMonitor,
						toRefresh.size());
				if (GitTraceLocation.REFRESH.isActive()) {
					GitTraceLocation.getTrace()
							.trace(GitTraceLocation.REFRESH
									.getLocation(),
									"Refreshing repository " + workTree + ' ' //$NON-NLS-1$
											+ toRefresh.size());
				}
				for (Map.Entry<IResource, Boolean> entry : toRefresh
						.entrySet()) {
					entry.getKey().refreshLocal(
							entry.getValue().booleanValue()
									? IResource.DEPTH_INFINITE
									: IResource.DEPTH_ONE,
							innerProgress.newChild(1));
				}
				refreshed(changes.getRepo(), changes.getEventId());
				if (GitTraceLocation.REFRESH.isActive()) {
					GitTraceLocation.getTrace()
							.trace(GitTraceLocation.REFRESH
									.getLocation(),
									"Refreshed repository " + workTree + ' ' //$NON-NLS-1$
											+ toRefresh.size());
				}
			};
			// No scheduling rule needed; IResource.refreshLocal() gets its own
			// rule. This workspace operation serves only to batch resource
			// update notifications.
			workspace.run(operation, null, IWorkspace.AVOID_UPDATE,
					progress.newChild(1));
		}

		private Map<IPath, IProject> getProjectLocations(File workTree) {
			IProject[] projects = RuleUtil.getProjects(workTree);
			if (projects == null) {
				return Collections.emptyMap();
			}
			Map<IPath, IProject> result = new HashMap<>();
			for (IProject project : projects) {
				if (project.isAccessible()) {
					IPath path = project.getLocation();
					if (path != null) {
						IPath projectFilePath = path.append(
								IProjectDescription.DESCRIPTION_FILE_NAME);
						if (projectFilePath.toFile().exists()) {
							result.put(path, project);
						}
					}
				}
			}
			return result;
		}

		private Map<IResource, Boolean> computeResources(Set<String> modified,
				Set<String> deleted, IPath workTree, Map<IPath, IProject> roots,
				IProgressMonitor monitor) {
			// Attempt to minimize the refreshes by returning IContainers if
			// more than one file in a container has changed.
			if (GitTraceLocation.REFRESH.isActive()) {
				GitTraceLocation.getTrace().trace(
						GitTraceLocation.REFRESH.getLocation(),
						"Calculating refresh for repository " + workTree + ' ' //$NON-NLS-1$
								+ modified.size() + ' ' + deleted.size());
			}
			SubMonitor progress = SubMonitor.convert(monitor,
					modified.size() + deleted.size());
			Set<IPath> fullRefreshes = new HashSet<>();
			Map<IPath, IFile> handled = new HashMap<>();
			Map<IResource, Boolean> result = new HashMap<>();
			Stream.concat(modified.stream(), deleted.stream()).forEach(path -> {
				if (progress.isCanceled()) {
					throw new OperationCanceledException();
				}
				IPath filePath = "/".equals(path) ? workTree //$NON-NLS-1$
						: workTree.append(path);
				IProject project = roots.get(filePath);
				if (project != null) {
					// Eclipse knows this as a project. Make sure it gets
					// refreshed as such. One can refresh a folder via an IFile,
					// but not an IProject.
					handled.put(filePath, null);
					result.put(project, Boolean.FALSE);
					progress.worked(1);
					return;
				}
				if (fullRefreshes.stream()
						.anyMatch(full -> full.isPrefixOf(filePath))
						|| !roots.keySet().stream()
								.anyMatch(root -> root.isPrefixOf(filePath))) {
					// Not in workspace or covered by a full container refresh
					progress.worked(1);
					return;
				}
				IPath containerPath;
				boolean isFile;
				if (path.endsWith("/")) { //$NON-NLS-1$
					// It's already a directory
					isFile = false;
					containerPath = filePath.removeTrailingSeparator();
				} else {
					isFile = true;
					containerPath = filePath.removeLastSegments(1);
				}
				if (!handled.containsKey(containerPath)) {
					if (!isFile && containerPath != null) {
						IContainer container = ResourceUtil
								.getContainerForLocation(containerPath, false);
						if (container != null) {
							IFile file = handled.get(containerPath);
							handled.put(containerPath, null);
							if (file != null) {
								result.remove(file);
							}
							result.put(container, Boolean.FALSE);
						}
					} else if (isFile) {
						// First file in this container. Find the deepest
						// existing container and record its non-existing child.
						String lastPart = filePath.lastSegment();
						while (containerPath != null
								&& workTree.isPrefixOf(containerPath)) {
							IContainer container = ResourceUtil
									.getContainerForLocation(containerPath,
											false);
							if (container == null) {
								lastPart = containerPath.lastSegment();
								containerPath = containerPath
										.removeLastSegments(1);
								isFile = false;
								continue;
							}
							if (container.getType() == IResource.ROOT) {
								// Missing project... ignore it and anything
								// beneath. The user or our own branch project
								// tracker will have to properly add/import the
								// project.
								containerPath = containerPath.append(lastPart);
								fullRefreshes.add(containerPath);
								handled.put(containerPath, null);
							} else if (isFile) {
								IFile file = container
										.getFile(new Path(lastPart));
								handled.put(containerPath, file);
								result.put(file, Boolean.FALSE);
							} else {
								// New or deleted folder.
								container = container
										.getFolder(new Path(lastPart));
								containerPath = containerPath.append(lastPart);
								fullRefreshes.add(containerPath);
								handled.put(containerPath, null);
								result.put(container, Boolean.TRUE);
							}
							break;
						}
					}
				} else {
					IFile file = handled.get(containerPath);
					if (file != null) {
						// Second file in this container: replace file by
						// its container.
						handled.put(containerPath, null);
						result.remove(file);
						result.put(file.getParent(), Boolean.FALSE);
					}
					// Otherwise we already have this container.
				}
				progress.worked(1);
			});

			if (GitTraceLocation.REFRESH.isActive()) {
				GitTraceLocation.getTrace().trace(
						GitTraceLocation.REFRESH.getLocation(),
						"Calculated refresh for repository " + workTree); //$NON-NLS-1$
			}
			return result;
		}

		@Override
		public void onWorkingTreeModified(WorkingTreeModifiedEvent event) {
			// Record which projects have changes and initiate a resource
			// refresh job.
			if (event.isEmpty()) {
				return;
			}
			Repository repo = event.getRepository();
			if (repo == null || repo.isBare()) {
				return; // Should never occur
			}
			recordEvent(event);
			File gitDir = repo.getDirectory();
			synchronized (repositoriesChanged) {
				WorkingTreeChanges changes = repositoriesChanged.get(gitDir);
				if (changes == null) {
					repositoriesChanged.put(gitDir,
							new WorkingTreeChanges(event));
				} else {
					changes.merge(event);
					if (changes.isEmpty()) {
						// Actually, this cannot happen.
						repositoriesChanged.remove(gitDir);
					}
				}
			}
			if (GitTraceLocation.REFRESH.isActive()) {
				GitTraceLocation.getTrace().trace(
						GitTraceLocation.REFRESH.getLocation(),
						"Triggered refresh"); //$NON-NLS-1$
			}
			schedule();
		}
	}
}
