/*******************************************************************************
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch>
 * Copyright (C) 2019, Matthias Sohn <matthias.sohn@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.egit.core.internal;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.egit.core.Activator;
import org.eclipse.egit.core.internal.job.RuleUtil;
import org.eclipse.egit.core.internal.trace.GitTraceLocation;
import org.eclipse.egit.core.internal.util.ResourceUtil;
import org.eclipse.jgit.events.WorkingTreeModifiedEvent;
import org.eclipse.jgit.events.WorkingTreeModifiedListener;
import org.eclipse.jgit.lib.Repository;

/**
 * Refreshes parts of the workspace changed by JGit operations. This will
 * not refresh any git-ignored resources since those are not reported in the
 * {@link WorkingTreeModifiedEvent}.
 */
public class ResourceRefreshHandler implements WorkingTreeModifiedListener {

	/**
	 * Default constructor
	 */
	public ResourceRefreshHandler() {
		// empty
	}

	/**
	 * Internal helper class to record batched accumulated results from
	 * several {@link WorkingTreeModifiedEvent}s.
	 */
	private static class WorkingTreeChanges {

		private final File workTree;

		private final Set<String> modified;

		private final Set<String> deleted;

		public WorkingTreeChanges(WorkingTreeModifiedEvent event) {
			workTree = event.getRepository().getWorkTree()
					.getAbsoluteFile();
			modified = new HashSet<>(event.getModified());
			deleted = new HashSet<>(event.getDeleted());
		}

		public File getWorkTree() {
			return workTree;
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

		public ResourceRefreshHandler.WorkingTreeChanges merge(WorkingTreeModifiedEvent event) {
			modified.removeAll(event.getDeleted());
			deleted.removeAll(event.getModified());
			modified.addAll(event.getModified());
			deleted.addAll(event.getDeleted());
			return this;
		}
	}

	private Map<File, ResourceRefreshHandler.WorkingTreeChanges> repositoriesChanged = new LinkedHashMap<>();

	/**
	 * run the refresh
	 *
	 * @param monitor
	 */
	public void run(IProgressMonitor monitor) {
		try {
			List<ResourceRefreshHandler.WorkingTreeChanges> changes;
			synchronized (repositoriesChanged) {
				if (repositoriesChanged.isEmpty()) {
					return;
				}
				changes = new ArrayList<>(repositoriesChanged.values());
				repositoriesChanged.clear();
			}
			SubMonitor progress = SubMonitor.convert(monitor, changes.size());
			try {
				for (ResourceRefreshHandler.WorkingTreeChanges change : changes) {
					refreshRepository(change, progress.newChild(1));
				}
			} catch (OperationCanceledException oe) {
				return;
			} catch (CoreException e) {
				Activator.error(CoreText.Activator_refreshFailed, e);
			}
		} finally {
			monitor.done();
		}
	}

	private void refreshRepository(
			ResourceRefreshHandler.WorkingTreeChanges changes,
			IProgressMonitor monitor)
			throws CoreException {
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
		if (GitTraceLocation.REFRESH.isActive()) {
			GitTraceLocation.getTrace().trace(
					GitTraceLocation.REFRESH.getLocation(),
					"Refreshing repository " + workTree + ' ' //$NON-NLS-1$
							+ toRefresh.size());
		}
		for (Map.Entry<IResource, Boolean> entry : toRefresh.entrySet()) {
			entry.getKey()
					.refreshLocal(entry.getValue().booleanValue()
							? IResource.DEPTH_INFINITE
							: IResource.DEPTH_ONE, progress.newChild(1));
		}
		if (GitTraceLocation.REFRESH.isActive()) {
			GitTraceLocation.getTrace().trace(
					GitTraceLocation.REFRESH.getLocation(),
					"Refreshed repository " + workTree + ' ' //$NON-NLS-1$
							+ toRefresh.size());
		}
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

	private Map<IResource, Boolean> computeResources(
			Set<String> modified, Set<String> deleted, IPath workTree,
			Map<IPath, IProject> roots, IProgressMonitor monitor) {
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
		if (event.isEmpty()) {
			return;
		}
		Repository repo = event.getRepository();
		if (repo == null || repo.isBare()) {
			return; // Should never occur
		}
		File gitDir = repo.getDirectory();
		synchronized (repositoriesChanged) {
			ResourceRefreshHandler.WorkingTreeChanges changes = repositoriesChanged.get(gitDir);
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
		run(new NullProgressMonitor());
	}
}