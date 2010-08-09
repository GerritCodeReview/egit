/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Dariusz Luksza <dariusz@luksza.org>
 *******************************************************************************/
package org.eclipse.egit.core.synchronize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.egit.core.CoreText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.osgi.util.NLS;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.variants.IResourceVariant;

/**
 *
 */
public class GitFolderResourceVariant extends GitResourceVariant {

	private IResourceVariant members[];

	GitFolderResourceVariant(Repository repo, RevCommit revCommit, ObjectId objectId, String path)
			throws IOException {
		super(repo, revCommit, objectId, path);
	}

	public boolean isContainer() {
		return true;
	}

	public IStorage getStorage(IProgressMonitor monitor) throws TeamException {
		return null;
	}

	public byte[] asBytes() {
		return getName().getBytes();
	}

	/**
	 * @param progress
	 * @return members
	 * @throws IOException
	 */
	public IResourceVariant[] getMembers(IProgressMonitor progress)
			throws IOException {
		if (members != null)
			try {
				return members;
			} finally {
				progress.done();
			}

		TreeWalk tw = new TreeWalk(getRepository());
		tw.reset();
		int nth = tw.addTree(getObjectId());

		IProgressMonitor monitor = SubMonitor.convert(progress);
		monitor.beginTask(
				NLS.bind(CoreText.GitFolderResourceVariant_fetchingMembers, this),
				tw.getTreeCount());

		Repository repo = getRepository();
		List<IResourceVariant> result = new ArrayList<IResourceVariant>();

		try {
			while (tw.next()) {
				ObjectId newObjectId = tw.getObjectId(nth);
				String path = getPath() + "/" + new String(tw.getRawPath()); //$NON-NLS-1$
				if (tw.isSubtree())
					result.add(new GitFolderResourceVariant(repo,
							getRevCommit(), newObjectId, path));
				else
					result.add(new GitBlobResourceVariant(repo, getRevCommit(),
							newObjectId, path));
				monitor.worked(1);
			}

			members = result.toArray(new IResourceVariant[result.size()]);
			return members;
		} finally {
			monitor.done();
		}
	}

}
