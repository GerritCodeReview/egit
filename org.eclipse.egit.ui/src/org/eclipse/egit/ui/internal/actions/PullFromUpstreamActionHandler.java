/*******************************************************************************
 * Copyright (c) 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Mathias Kinzler (SAP AG) - initial implementation
 *******************************************************************************/
package org.eclipse.egit.ui.internal.actions;

import java.io.IOException;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.egit.ui.internal.pull.PullOperationUI;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

/**
 * Action for pulling into the currently checked-out branch.
 */
public class PullFromUpstreamActionHandler extends RepositoryActionHandler {
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Repository[] repos = getRepositories(event);
		if (repos.length == 0)
			return null;

		for (Repository repo : repos)
			new PullOperationUI(repo).start();
		return null;
	}

	@Override
	public boolean isEnabled() {
		// we don't do the full canMerge check here, but
		// ensure that a branch is checked out
		Repository[] repos = getRepositories();
		for (Repository repo : repos) {
			try {
				String fullBranch = repo.getFullBranch();
				if (!fullBranch.startsWith(Constants.R_REFS)
						|| repo.getAllRefs().isEmpty())
					return false;
			} catch (IOException e) {
				return false;
			}
		}
		return true;
	}
}
