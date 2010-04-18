/*******************************************************************************
 * Copyright (C) 2010, Dariusz Luksza <dariusz@luksza.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.synchronize.dto;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

/**
 *
 */
public class GitSynchronizeDataSet implements Iterable<GitSynchronizeData> {

	private final Set<GitSynchronizeData> gsd;

	private final Map<IProject, GitSynchronizeData> projectMapping;

	/**
	 * Constructs GitSynchronizeDataSet.
	 */
	public GitSynchronizeDataSet() {
		gsd = new HashSet<GitSynchronizeData>();
		projectMapping = new HashMap<IProject, GitSynchronizeData>();
	}

	/**
	 * Constructs GitSynchronizeDataSet and adds given element to set.
	 *
	 * @param data
	 */
	public GitSynchronizeDataSet(GitSynchronizeData data) {
		this();
		add(data);
	}

	/**
	 * @param data
	 */
	public void add(GitSynchronizeData data) {
		gsd.add(data);
		for (IProject proj : data.getProjects()) {
			projectMapping.put(proj, data);
		}
	}

	/**
	 * @param project
	 * @return <code>true</code> if project has corresponding data
	 */
	public boolean contains(IProject project) {
		return projectMapping.containsKey(project);
	}

	/**
	 * @param project
	 * @return <code>null</code> if project does not have corresponding data
	 */
	public GitSynchronizeData getData(IProject project) {
		return projectMapping.get(project);
	}

	public Iterator<GitSynchronizeData> iterator() {
		return gsd.iterator();
	}

	/**
	 * @return list of all resources
	 */
	public IResource[] getAllResources() {
		Set<IResource> resource = new HashSet<IResource>();
		for (GitSynchronizeData data : gsd) {
			resource.addAll(data.getProjects());
		}
		return resource.toArray(new IResource[resource.size()]);
	}

}
