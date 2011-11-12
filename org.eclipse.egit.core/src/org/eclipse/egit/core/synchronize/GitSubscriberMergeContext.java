/*******************************************************************************
 * Copyright (C) 2010,2011 Dariusz Luksza <dariusz@luksza.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.synchronize;

import java.io.IOException;
import java.util.Collection;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.mapping.ResourceTraversal;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.egit.core.Activator;
import org.eclipse.egit.core.CoreText;
import org.eclipse.egit.core.internal.indexdiff.GitResourceDeltaVisitor;
import org.eclipse.egit.core.op.AddToIndexOperation;
import org.eclipse.egit.core.project.GitProjectData;
import org.eclipse.egit.core.project.RepositoryChangeListener;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.core.synchronize.dto.GitSynchronizeData;
import org.eclipse.egit.core.synchronize.dto.GitSynchronizeDataSet;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.diff.IDiff;
import org.eclipse.team.core.mapping.ISynchronizationScopeManager;
import org.eclipse.team.core.subscribers.SubscriberMergeContext;

/**
 *
 */
public class GitSubscriberMergeContext extends SubscriberMergeContext {

	private final GitSynchronizeDataSet gsds;

	private final RepositoryChangeListener repoChangeListener;

	private final IResourceChangeListener resourceChangeListener;

	/**
	 * @param subscriber
	 * @param manager
	 * @param gsds
	 */
	public GitSubscriberMergeContext(final GitResourceVariantTreeSubscriber subscriber,
			ISynchronizationScopeManager manager, GitSynchronizeDataSet gsds) {
		super(subscriber, manager);
		this.gsds = gsds;


		repoChangeListener = new RepositoryChangeListener() {
			public void repositoryChanged(RepositoryMapping which) {
				handleRepositoryChange(subscriber, which);
			}
		};
		resourceChangeListener = new IResourceChangeListener() {

			public void resourceChanged(IResourceChangeEvent event) {
				IResourceDelta delta = event.getDelta();
				if (delta == null)
					return;

				handleResourceChange(subscriber, delta);
			}
		};
		GitProjectData.addRepositoryChangeListener(repoChangeListener);
		ResourcesPlugin.getWorkspace().addResourceChangeListener(resourceChangeListener);

		initialize();
	}

	public void markAsMerged(IDiff node, boolean inSyncHint,
			IProgressMonitor monitor) throws CoreException {
		IResource resource = getDiffTree().getResource(node);
		AddToIndexOperation operation = new AddToIndexOperation(
				new IResource[] { resource });
		operation.execute(monitor);
	}

	public void reject(IDiff diff, IProgressMonitor monitor)
			throws CoreException {
		// TODO Auto-generated method stub

	}

	/**
	 * @return git synchronization data
	 */
	public GitSynchronizeDataSet getSyncData() {
		return gsds;
	}

	@Override
	protected void makeInSync(IDiff diff, IProgressMonitor monitor)
			throws CoreException {
		// TODO Auto-generated method stub

	}

	@Override
	public void dispose() {
		GitProjectData.removeRepositoryChangeListener(repoChangeListener);
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(resourceChangeListener);
		super.dispose();
	}

	private void handleRepositoryChange(
			GitResourceVariantTreeSubscriber subscriber, RepositoryMapping which) {
		for (GitSynchronizeData gsd : gsds)
			if (which.getRepository().equals(gsd.getRepository()))
				updateRefs(gsd);

		subscriber.reset(this.gsds);
		ResourceTraversal[] traversals = getScopeManager().getScope()
				.getTraversals();
		try {
			subscriber.refresh(traversals, new NullProgressMonitor());
		} catch (TeamException e) {
			logRefreshException(e);
		}
	}

	private void handleResourceChange(
			GitResourceVariantTreeSubscriber subscriber, IResourceDelta delta) {
		IResourceDelta[] children = delta.getAffectedChildren();
		for (IResourceDelta resourceDelta : children) {
			IResource resource = resourceDelta.getResource();
			RepositoryMapping mapping = RepositoryMapping.getMapping(resource);
			if (mapping == null)
				continue;

			scanDeltaAndRefresh(subscriber, mapping, resourceDelta);
		}
	}

	private void scanDeltaAndRefresh(
			GitResourceVariantTreeSubscriber subscriber,
			RepositoryMapping mapping, IResourceDelta delta) {
		Repository repo = mapping.getRepository();
		GitResourceDeltaVisitor visitor = new GitResourceDeltaVisitor(repo);
		try {
			delta.accept(visitor);
			Collection<IFile> files = visitor.getFileResourcesToUpdate();
			if (files != null && files.isEmpty())
				return;

			for (GitSynchronizeData gsd : gsds) {
				if (repo.equals(gsd.getRepository()))
					refreshResources(subscriber, files);
			}
		} catch (CoreException e) {
			Activator.logError(e.getMessage(), e);
		}
	}

	private void refreshResources(GitResourceVariantTreeSubscriber subscriber,
			Collection<IFile> resources) {
		IResource[] files = resources.toArray(new IResource[resources
				.size()]);
		try {
			subscriber.refresh(files, IResource.DEPTH_ONE,
					new NullProgressMonitor());
		} catch (final CoreException e) {
			logRefreshException(e);
		}
	}

	private void updateRefs(GitSynchronizeData gsd) {
		try {
			gsd.updateRevs();
		} catch (IOException e) {
			Activator.logError(
					CoreText.GitSubscriberMergeContext_FailedUpdateRevs, e);

			return;
		}
	}

	private void logRefreshException(Exception e) {
		Activator.logError(
				CoreText.GitSubscriberMergeContext_FailedRefreshSyncView, e);
	}

}
