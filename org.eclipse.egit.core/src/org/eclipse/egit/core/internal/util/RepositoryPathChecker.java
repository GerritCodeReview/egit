/*******************************************************************************
 * Copyright (c) 2016, Matthias Sohn and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.internal.util;

import java.io.File;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.egit.core.internal.CoreText;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.osgi.util.NLS;

/**
 * Checker for path suitable for storing a git repository
 */
public class RepositoryPathChecker {

	private boolean hasContent;

	private String errorMessage;

	/**
	 * Check if the given directory path is a valid local absolute repository
	 * path
	 *
	 * @param dir
	 *            the directory path to check
	 * @return {@code true} if the repository path is valid
	 */
	public boolean check(String dir) {
		hasContent = false;
		errorMessage = null;

		if (dir.length() == 0) {
			errorMessage = CoreText.RepositoryPathChecker_errAbsoluteRepoPath;
			return false;
		}

		if (dir.startsWith("git clone")) { //$NON-NLS-1$
			errorMessage = CoreText.RepositoryPathChecker_errNoCloneCommand;
			return false;
		}
		if (dir.startsWith("https://") || dir.startsWith("ssh://") //$NON-NLS-1$//$NON-NLS-2$
				|| dir.startsWith("git://")) { //$NON-NLS-1$
			errorMessage = CoreText.RepositoryPathChecker_errNoURL;
			return false;
		}
		File testFile = new File(dir);
		IPath path = Path.fromOSString(dir);
		for (String segment : path.segments()) {
			IStatus status = ResourcesPlugin.getWorkspace()
					.validateName(segment, IResource.FOLDER);
			if (!status.isOK()) {
				errorMessage = status.getMessage();
				return false;
			}
		}
		if (!path.isAbsolute()) {
			errorMessage = NLS.bind(CoreText.RepositoryPathChecker_errNotAbsoluteRepoPath,
					dir);
			return false;
		}
		if (testFile.exists() && !testFile.isDirectory()) {
			errorMessage = NLS.bind(CoreText.RepositoryPathChecker_errNoDirectory, dir);
			return false;
		}
		hasContent = testFile.exists() && testFile.list().length > 0;
		return true;
	}

	/**
	 * @return {@code true} if the directory exists and already has content
	 */
	public boolean hasContent() {
		return hasContent;
	}

	/**
	 * @return the error message if the path is invalid
	 */
	public @Nullable String getErrorMessage() {
		return errorMessage;
	}
}
