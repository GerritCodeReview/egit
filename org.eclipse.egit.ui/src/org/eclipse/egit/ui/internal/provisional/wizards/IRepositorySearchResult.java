/*******************************************************************************
 * Copyright (c) 2012 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Stefan Lay (SAP AG) - initial implementation
 *******************************************************************************/
package org.eclipse.egit.ui.internal.provisional.wizards;

/**
 * <p>
 * <strong>EXPERIMENTAL</strong>. This class or interface has been added as part
 * of a work in progress. There is no guarantee that this API will work or that
 * it will remain the same. Please do not use this API without consulting with
 * the egit team.
 * </p>
 *
 * Provides information of a Git repository
 */
public interface IRepositorySearchResult {

	/**
	 * @return an object encapsulating info about a git repository
	 * @throws NoRepositoryInfoException
	 *             if an error occurred when constructing a
	 *             {@code GitRepositoryInfo} object
	 */
	public GitRepositoryInfo getGitRepositoryInfo()
			throws NoRepositoryInfoException;

}
