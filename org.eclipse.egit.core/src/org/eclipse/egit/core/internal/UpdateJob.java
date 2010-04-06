/*******************************************************************************
 * Copyright (C) 2007, Robin Rosenberg <me@lathund.dewire.com>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.internal;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.core.Activator;
import org.eclipse.egit.core.CoreText;
import org.eclipse.egit.core.internal.trace.GitTraceLocation;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.lib.GitIndex;
import org.eclipse.jgit.lib.GitIndex.Entry;
import org.eclipse.osgi.util.NLS;

/**
 * This job updates the index with the content of all specified
 * and tracked resources. If a project is selected all tracked
 * resources withing that container are updated.
 */
public class UpdateJob extends Job {

	private final Collection rsrcList;

	/**
	 * Construct an UpdateJob for the specified resources.
	 *
	 * @param rsrcList
	 */
	public UpdateJob(Collection rsrcList) {
		super(CoreText.UpdateJob_updatingIndex);
		this.rsrcList = rsrcList;
		setPriority(Job.LONG);
	}

	protected IStatus run(IProgressMonitor m) {
		if (m == null) {
			m = new NullProgressMonitor();
		}

		trace("running"); //$NON-NLS-1$
		try {
			final IdentityHashMap<RepositoryMapping, Boolean> tomerge = new IdentityHashMap<RepositoryMapping, Boolean>();
			try {
				final int[] count=new int[1];
				long t0=System.currentTimeMillis();
				for (Object obj : rsrcList) {
					obj = ((IAdaptable)obj).getAdapter(IResource.class);
					if (obj instanceof IContainer) {
						((IContainer)obj).accept(new IResourceProxyVisitor() {
							public boolean visit(IResourceProxy rp) throws CoreException {
								if (rp.getType() == IResource.FILE) {
									count[0]++;
								}
								return true;
							}
						}, IContainer.EXCLUDE_DERIVED);
					} else if (obj instanceof IResource) {
							count[0]++;
					}
				}
				long t1=System.currentTimeMillis();
				// TODO is this the right location?
				if (GitTraceLocation.CORE.isActive())
					GitTraceLocation.getTrace().trace(
							GitTraceLocation.CORE.getLocation(),
							"Counted " + count[0] //$NON-NLS-1$
									+ " items to update in " //$NON-NLS-1$
									+ (t1 - t0) / 1000.0 + "s"); //$NON-NLS-1$
				m.beginTask(CoreText.UpdateOperation_updating, count[0]);
				final IProgressMonitor fm = m;
				for (Object obj : rsrcList) {
					if (obj instanceof IResource) {
						final IResource r = (IResource)obj;
						final RepositoryMapping rm = RepositoryMapping.getMapping(r);
						final GitIndex index = rm.getRepository().getIndex();
						tomerge.put(rm, Boolean.TRUE);
						if (r instanceof IContainer) {
							((IContainer)r).accept(new IResourceVisitor() {
								public boolean visit(IResource resource) throws CoreException {
									try {
										if (resource.getType() == IResource.FILE) {
											String path = rm.getRepoRelativePath(resource);
											Entry entry = index.getEntry(path);
											if (entry != null) {
												entry.update(new File(rm.getWorkDir(),path));
											}
											fm.worked(1);
										}
									} catch (IOException e) {
										if (GitTraceLocation.CORE.isActive())
											GitTraceLocation.getTrace().trace(GitTraceLocation.CORE.getLocation(), e.getMessage(), e);
										throw new CoreException(Activator.error(CoreText.UpdateOperation_failed, e));
									}
									return true;
								}
							},IResource.DEPTH_INFINITE, IContainer.EXCLUDE_DERIVED);
						} else {
							String path = rm.getRepoRelativePath(r);
							Entry entry = index.getEntry(path);
							if (entry != null) {
								entry.update(new File(rm.getWorkDir(),path));
							}
							m.worked(1);
						}
					}
				}
				for (RepositoryMapping rm : tomerge.keySet()) {
					m.setTaskName(NLS.bind(CoreText.UpdateJob_writingIndex, rm
							.getRepository().getDirectory()));
					rm.getRepository().getIndex().write();
				}
			} catch (NotSupportedException e) {
				return Activator.error(e.getMessage(),e);
			} catch (RuntimeException e) {
				if (GitTraceLocation.CORE.isActive())
					GitTraceLocation.getTrace().trace(GitTraceLocation.CORE.getLocation(), e.getMessage(), e);
				return Activator.error(CoreText.UpdateOperation_failed, e);
			} catch (IOException e) {
				if (GitTraceLocation.CORE.isActive())
					GitTraceLocation.getTrace().trace(GitTraceLocation.CORE.getLocation(), e.getMessage(), e);
				return Activator.error(CoreText.UpdateOperation_failed, e);
			} catch (CoreException e) {
				if (GitTraceLocation.CORE.isActive())
					GitTraceLocation.getTrace().trace(GitTraceLocation.CORE.getLocation(), e.getMessage(), e);
				return Activator.error(CoreText.UpdateOperation_failed, e);
			} finally {
				try {
					final Iterator i = tomerge.keySet().iterator();
					while (i.hasNext()) {
						final RepositoryMapping r = (RepositoryMapping) i.next();
						r.getRepository().getIndex().read();
						r.fireRepositoryChanged();
					}
				} catch (IOException e) {
					if (GitTraceLocation.CORE.isActive())
						GitTraceLocation.getTrace().trace(GitTraceLocation.CORE.getLocation(), e.getMessage(), e);
				} finally {
					m.done();
				}
			}
		} finally {
			trace("done");  //$NON-NLS-1$
			m.done();
		}

		return Status.OK_STATUS;
	}

	private void trace(final String m) {
		// TODO is this the right location?
		if (GitTraceLocation.CORE.isActive())
			GitTraceLocation.getTrace().trace(
					GitTraceLocation.CORE.getLocation(), "(UpdateJob)" + m);
	}

}
