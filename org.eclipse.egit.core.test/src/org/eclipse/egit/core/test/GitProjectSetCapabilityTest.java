/*******************************************************************************
 * Copyright (C) 2011, Robin Stocker <robin@nibor.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.egit.core.Activator;
import org.eclipse.egit.core.GitProjectSetCapability;
import org.eclipse.egit.core.op.ConnectProviderOperation;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.team.core.ProjectSetSerializationContext;
import org.eclipse.team.core.TeamException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GitProjectSetCapabilityTest {

	private GitProjectSetCapability capability;

	private List<IProject> createdProjects = new ArrayList<IProject>();
	private List<File> pathsToClean = new ArrayList<File>();

	@Before
	public void setUp() {
		Activator.getDefault().getRepositoryCache().clear();
		capability = new GitProjectSetCapability();
	}

	@After
	public void tearDown() throws Exception {
		Activator.getDefault().getRepositoryCache().clear();
		for (IProject project : createdProjects)
			if (project.exists())
				project.delete(true, true, null);
		for (File pathToClean : pathsToClean)
			if (pathToClean.exists())
				FileUtils.delete(pathToClean, FileUtils.RECURSIVE | FileUtils.RETRY);
	}

	@Test
	public void testExport() throws Exception {
		IProject aProject = createProject("a");
		File aRepo = createRepository(aProject.getLocation(), "http://example.org/repo-a", "master");
		connectProject(aProject, aRepo);

		IPath bPath = ResourcesPlugin.getWorkspace().getRoot().getLocation().append("b");
		File bRepo = createRepository(bPath, "http://example.org/repo-b", "master");
		IProject baProject = createProject(bPath, "ba");
		IProject bbProject = createProject(bPath, "bb");
		connectProject(baProject, bRepo);
		connectProject(bbProject, bRepo);
		pathsToClean.add(bPath.toFile());

		IProject cProject = createProject("c");
		File cRepo = createRepository(cProject.getLocation(), "http://example.org/repo-c", "stable");
		connectProject(cProject, cRepo);

		IProject[] projects = new IProject[] { aProject, baProject, bbProject, cProject };
		String[] references = capability.asReference(
				projects, new ProjectSetSerializationContext(), new NullProgressMonitor());
		assertEquals(4, references.length);
		assertEquals("1.0,http://example.org/repo-a,master,.", references[0]);
		assertEquals("1.0,http://example.org/repo-b,master,ba", references[1]);
		assertEquals("1.0,http://example.org/repo-b,master,bb", references[2]);
		assertEquals("1.0,http://example.org/repo-c,stable,.", references[3]);
	}

	@Test
	public void testImport() throws Exception {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IPath reposPath = root.getLocation().append("repos");
		pathsToClean.add(reposPath.toFile());

		IPath aPath = reposPath.append("a");
		IProject aProject = createProject(reposPath, "a");
		createRepository(aPath, "notused", "master");
		aProject.delete(false, true, null);

		IPath bPath = reposPath.append("b");
		IProject baProject = createProject(bPath, "ba");
		IProject bbProject = createProject(bPath, "bb");
		createRepository(bPath, "notused", "master");
		baProject.delete(false, true, null);
		bbProject.delete(false, true, null);

		IPath cPath = reposPath.append("c");
		IProject cProject = createProject(reposPath, "c");
		createRepository(cPath, "notused", "stable");
		cProject.delete(false, true, null);

		String aReference = createProjectReference(aPath, "master", ".");
		String baReference = createProjectReference(bPath, "master", "ba");
		String bbReference = createProjectReference(bPath, "master", "bb");
		String cReference = createProjectReference(cPath, "stable", ".");
		String[] references = new String[] { aReference, baReference, bbReference, cReference };

		capability.addToWorkspace(references,
						new ProjectSetSerializationContext(),
						new NullProgressMonitor());

		pathsToClean.add(root.getLocation().append("b").toFile());

		IProject aImported = root.getProject("a");
		createdProjects.add(aImported);
		assertTrue(aImported.exists());
		assertNotNull(RepositoryMapping.getMapping(aImported));

		IProject baImported = root.getProject("ba");
		createdProjects.add(baImported);
		assertTrue(baImported.exists());
		assertEquals(root.getLocation().append("b/ba"), baImported.getLocation());
		assertNotNull(RepositoryMapping.getMapping(baImported));

		IProject bbImported = root.getProject("bb");
		createdProjects.add(bbImported);
		assertTrue(bbImported.exists());
		assertEquals(root.getLocation().append("b/bb"), bbImported.getLocation());
		assertNotNull(RepositoryMapping.getMapping(bbImported));

		IProject cImported = root.getProject("c");
		createdProjects.add(cImported);
		assertTrue(cImported.exists());
		RepositoryMapping cMapping = RepositoryMapping.getMapping(cImported);
		assertNotNull(cMapping);
		assertEquals("stable", cMapping.getRepository().getBranch());
	}

	@Test
	public void testImportWithDifferentBranchesOfSameRepo() throws Exception {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IPath reposPath = root.getLocation().append("repos");
		pathsToClean.add(reposPath.toFile());

		IPath xPath = reposPath.append("x");
		IProject xaProject = createProject(xPath, "xa");
		IProject xbProject = createProject(xPath, "xb");
		createRepository(xPath, "notused", "stable");
		xaProject.delete(false, true, null);
		xbProject.delete(false, true, null);

		String xaMasterReference = createProjectReference(xPath, "master", "xa");
		String xbStableReference = createProjectReference(xPath, "stable", "xb");
		String[] references = new String[] { xaMasterReference, xbStableReference };

		capability.addToWorkspace(references,
						new ProjectSetSerializationContext(),
						new NullProgressMonitor());

		pathsToClean.add(root.getLocation().append("x").toFile());
		pathsToClean.add(root.getLocation().append("x_stable").toFile());

		IProject xaImported = root.getProject("xa");
		createdProjects.add(xaImported);
		assertTrue(xaImported.exists());
		assertEquals(root.getLocation().append("x/xa"), xaImported.getLocation());
		RepositoryMapping xaMapping = RepositoryMapping.getMapping(xaImported);
		assertNotNull(xaMapping);
		assertEquals("master", xaMapping.getRepository().getBranch());

		IProject xbImported = root.getProject("xb");
		createdProjects.add(xbImported);
		assertTrue(xbImported.exists());
		assertEquals(root.getLocation().append("x_stable/xb"), xbImported.getLocation());
		RepositoryMapping xbMapping = RepositoryMapping.getMapping(xbImported);
		assertNotNull(xbMapping);
		assertEquals("stable", xbMapping.getRepository().getBranch());
	}

	@Test
	public void testImportWithMultipleCallsForSameDestinationRepo() throws Exception {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IPath reposPath = root.getLocation().append("repos");
		pathsToClean.add(reposPath.toFile());

		IPath xPath = reposPath.append("x");
		IProject xaProject = createProject(xPath, "xa");
		IProject xbProject = createProject(xPath, "xb");
		String url = createUrl(xPath);
		createRepository(xPath, url, "stable");
		xaProject.delete(false, true, null);
		xbProject.delete(false, true, null);

		String xaReference = createProjectReference(xPath, "master", "xa");
		String xbReference = createProjectReference(xPath, "master", "xb");

		// This should work because there is not yet a repo at the destination
		capability.addToWorkspace(new String[] { xaReference },
				new ProjectSetSerializationContext(),
				new NullProgressMonitor());

		// This should work because the repo that is already there is for the
		// same remote URL. It's assumed to be ok and will skip cloning and
		// directly import the project.
		capability.addToWorkspace(new String[] { xbReference },
				new ProjectSetSerializationContext(),
				new NullProgressMonitor());

		pathsToClean.add(root.getLocation().append("x").toFile());

		IPath otherPathWithX = reposPath.append("other").append("x");
		String otherReferenceWithDifferentUrl = createProjectReference(otherPathWithX, "master", "xb");

		try {
			capability.addToWorkspace(new String[] { otherReferenceWithDifferentUrl },
					new ProjectSetSerializationContext(),
					new NullProgressMonitor());
			fail("Should throw TeamException when a repo exists at the place but doesn't have the same URL.");
		} catch (TeamException e) {
			// This is expected
		}
	}

	private IProject createProject(String name) throws CoreException {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IProject p = root.getProject(name);
		p.create(null);
		p.open(null);

		createdProjects.add(p);
		return p;
	}

	private IProject createProject(IPath parentLocation, String name) throws CoreException {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();

		IProject p = root.getProject(name);
		IProjectDescription projectDescription = ResourcesPlugin.getWorkspace().newProjectDescription(p.getName());
		projectDescription.setLocation(parentLocation.append(name));
		p.create(projectDescription, null);
		p.open(null);

		createdProjects.add(p);
		return p;
	}

	private File createRepository(IPath location, String url, String branch) throws Exception {
		File gitDirectory = new File(location.toFile(), Constants.DOT_GIT);
		Repository repo = new FileRepository(gitDirectory);
		repo.getConfig().setString(ConfigConstants.CONFIG_REMOTE_SECTION, "origin", ConfigConstants.CONFIG_KEY_URL, url);
		repo.getConfig().setString(ConfigConstants.CONFIG_BRANCH_SECTION, branch, ConfigConstants.CONFIG_KEY_REMOTE, "origin");
		repo.create();
		repo.close();

		Git git = new Git(repo);
		git.add().addFilepattern(".").call();
		git.commit().setMessage("initial").call();
		if (!branch.equals("master"))
			git.checkout().setName(branch).setCreateBranch(true).call();

		pathsToClean.add(gitDirectory);
		return gitDirectory;
	}

	private void connectProject(IProject project, File gitDir) throws CoreException {
		ConnectProviderOperation operation = new ConnectProviderOperation(
				project.getProject(), gitDir);
		operation.execute(null);
	}

	private static String createProjectReference(IPath repoPath, String branch, String projectPath) {
		return "1.0," + createUrl(repoPath) + "," + branch + "," + projectPath;
	}

	private static String createUrl(IPath repoPath) {
		return repoPath.toFile().toURI().toString();
	}
}
