/*******************************************************************************
 * Copyright (C) 2009, Tor Arne Vestbø <torarnv@gmail.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.egit.core;

import java.io.File;
import java.io.IOException;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.util.FS;

/**
 * Java IO file tree iterator that can adapt to a {@link ContainerTreeIterator}
 * <p>
 * The iterator automatically adapts to a {@link ContainerTreeIterator} when
 * recursing into directories that are accessible from the given workspace root.
 *
 * @see org.eclipse.jgit.treewalk.FileTreeIterator
 * @see org.eclipse.egit.core.ContainerTreeIterator
 */
public class AdaptableFileTreeIterator extends FileTreeIterator {

	IWorkspaceRoot root;

	/**
	 * Create a new iterator to traverse the work tree of the given repository
	 * <p>
	 * The iterator will automatically adapt to a {@link ContainerTreeIterator}
	 * when encountering directories what can be mapped into the given workspace
	 * root.
	 *
	 * @param repository
	 *            the repository this iterator should traverse the working tree
	 *            of
	 * @param workspaceRoot
	 *            the workspace root to check resource mapping against.
	 */
	public AdaptableFileTreeIterator(final Repository repository,
			final IWorkspaceRoot workspaceRoot) {
		super(repository);
		root = workspaceRoot;
	}

	/**
	 * Create a new iterator to traverse a subdirectory.
	 * <p>
	 * The iterator will automatically adapt to a {@link ContainerTreeIterator}
	 * when encountering directories what can be mapped into the given workspace
	 * root.
	 *
	 * @param path
	 *            the subdirectory. This should be a directory contained within
	 *            the parent directory.
	 * @param parent
	 *            the parent iterator we were created from.
	 * @param workspaceRoot
	 *            the workspace root to check resource mapping against.
	 */
	protected AdaptableFileTreeIterator(final AdaptableFileTreeIterator parent,
			File path, final IWorkspaceRoot workspaceRoot) {
		super(parent, path, FS.DETECTED);
		root = workspaceRoot;
	}

	@Override
	public AbstractTreeIterator createSubtreeIterator(ObjectReader repo)
			throws IncorrectObjectTypeException, IOException {
		final File currentFile = ((FileEntry) current()).getFile();

		// findContainer checks whether the container is a project anyway.
		// Limiting the lookup to directories containing a .project skips the
		// expensive checks if not a project anyway. Sub-directories of GIT
		// managed projects will not need the check, as the iterator will
		// be converted to a ContainerIterator if a project is found.
		if (findProjectFast(currentFile, root.getLocation().toFile())) {
			IContainer container = IteratorService.findContainer(root,
					currentFile);
			if (container != null)
				return new ContainerTreeIterator(this, container);
		}
		return new AdaptableFileTreeIterator(this, currentFile, root);
	}

	private boolean findProjectFast(File currentFile, File ws) {
		File check = currentFile;

		if(currentFile.isFile()) {
			check = currentFile.getParentFile();
		}

		while(check != null && !ws.equals(check)) {
			if(new File(check, ".project").exists()) //$NON-NLS-1$
				return true;

			check = check.getParentFile();
		}

		return false;
	}

}
