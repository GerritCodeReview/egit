/*******************************************************************************
 * Copyright (C) 2009, Mykola Nikishov <mn@mn.com.ua>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core;

import org.eclipse.team.core.ProjectSetCapability;
import org.eclipse.team.core.RepositoryProviderType;

/**
 * A Git repository provider type.
 */
public final class GitProviderType extends RepositoryProviderType {
	@Override
	public ProjectSetCapability getProjectSetCapability() {
		return new GitProjectSetCapability();
	}
}
