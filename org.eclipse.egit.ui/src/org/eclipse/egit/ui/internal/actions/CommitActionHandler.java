/*******************************************************************************
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2007, Jing Xue <jingxue@digizenstudio.com>
 * Copyright (C) 2007, Robin Rosenberg <me@lathund.dewire.com>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2010, Stefan Lay <stefan.lay@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.actions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.core.op.CommitOperation;
import org.eclipse.egit.core.project.GitProjectData;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.decorators.GitLightweightDecorator;
import org.eclipse.egit.ui.internal.dialogs.CommitDialog;
import org.eclipse.egit.ui.internal.trace.GitTraceLocation;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.lib.Commit;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.GitIndex;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.lib.Tree;
import org.eclipse.jgit.lib.TreeEntry;
import org.eclipse.jgit.lib.UserConfig;
import org.eclipse.jgit.lib.GitIndex.Entry;
import org.eclipse.osgi.util.NLS;
import org.eclipse.team.core.Team;
import org.eclipse.team.core.TeamException;
import org.eclipse.ui.PlatformUI;

/**
 * Scan for modified resources in the same project as the selected resources.
 */
public class CommitActionHandler extends RepositoryActionHandler {

	private ArrayList<IFile> notIndexed;

	private ArrayList<IFile> indexChanges;

	private ArrayList<IFile> notTracked;

	private ArrayList<IFile> files;

	private Commit previousCommit;

	private boolean amendAllowed;

	private boolean amending;

	public Object execute(final ExecutionEvent event) throws ExecutionException {
		// let's see if there is any dirty editor around and
		// ask the user if they want to save or abort
		if (!PlatformUI.getWorkbench().saveAllEditors(true)) {
			return null;
		}

		resetState();
		try {
			buildIndexHeadDiffList(event);
		} catch (IOException e) {
			Activator.handleError(UIText.CommitAction_errorComputingDiffs, e,
					true);
			return null;
		} catch (CoreException e) {
			Activator.handleError(UIText.CommitAction_errorComputingDiffs, e,
					true);
			return null;
		}

		Repository[] repos = getRepositoriesFor(getProjectsForSelectedResources(event));
		Repository repository = null;
		Repository mergeRepository = null;
		amendAllowed = repos.length == 1;
		boolean isMergedResolved = false;
		for (Repository repo : repos) {
			repository = repo;
			RepositoryState state = repo.getRepositoryState();
			if (!state.canCommit()) {
				MessageDialog.openError(getShell(event),
						UIText.CommitAction_cannotCommit, NLS.bind(
								UIText.CommitAction_repositoryState, state
										.getDescription()));
				return null;
			}
			else if (state.equals(RepositoryState.MERGING_RESOLVED)) {
				isMergedResolved = true;
				mergeRepository = repo;
			}
		}

		loadPreviousCommit(event);
		if (files.isEmpty()) {
			if (amendAllowed && previousCommit != null) {
				boolean result = MessageDialog.openQuestion(getShell(event),
						UIText.CommitAction_noFilesToCommit,
						UIText.CommitAction_amendCommit);
				if (!result)
					return null;
				amending = true;
			} else {
				MessageDialog.openWarning(getShell(event),
						UIText.CommitAction_noFilesToCommit,
						UIText.CommitAction_amendNotPossible);
				return null;
			}
		}

		String author = null;
		String committer = null;
		if (repository != null) {
			final UserConfig config = repository.getConfig().get(UserConfig.KEY);
			author = config.getAuthorName();
			final String authorEmail = config.getAuthorEmail();
			author = author + " <" + authorEmail + ">"; //$NON-NLS-1$ //$NON-NLS-2$

			committer = config.getCommitterName();
			final String committerEmail = config.getCommitterEmail();
			committer = committer + " <" + committerEmail + ">"; //$NON-NLS-1$ //$NON-NLS-2$
		}

		CommitDialog commitDialog = new CommitDialog(getShell(event));
		commitDialog.setAmending(amending);
		commitDialog.setAmendAllowed(amendAllowed);
		commitDialog.setFileList(files);
		commitDialog.setPreselectedFiles(getSelectedFiles(event));
		commitDialog.setAuthor(author);
		commitDialog.setCommitter(committer);
		commitDialog.setAllowToChangeSelection(!isMergedResolved);

		if (previousCommit != null) {
			commitDialog.setPreviousCommitMessage(previousCommit.getMessage());
			PersonIdent previousAuthor = previousCommit.getAuthor();
			commitDialog.setPreviousAuthor(previousAuthor.getName()
					+ " <" + previousAuthor.getEmailAddress() + ">"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (isMergedResolved) {
			commitDialog.setCommitMessage(getMergeResolveMessage(mergeRepository, event));
		}

		if (commitDialog.open() != IDialogConstants.OK_ID)
			return null;

		final CommitOperation commitOperation = new CommitOperation(
				commitDialog.getSelectedFiles(), notIndexed, notTracked,
				commitDialog.getAuthor(), commitDialog.getCommitter(),
				commitDialog.getCommitMessage());
		if (commitDialog.isAmending()) {
			commitOperation.setAmending(true);
			commitOperation.setPreviousCommit(previousCommit);
			commitOperation.setRepos(repos);
		}
		commitOperation.setComputeChangeId(commitDialog.getCreateChangeId());
		commitOperation.setMergeResolve(isMergedResolved);
		if (isMergedResolved)
			commitOperation.setRepos(repos);
		String jobname = UIText.CommitAction_CommittingChanges;
		Job job = new Job(jobname) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					commitOperation.execute(monitor);

					for (IProject proj : getProjectsForSelectedResources(event)) {
						RepositoryMapping.getMapping(proj)
								.fireRepositoryChanged();
					}
				} catch (CoreException e) {
					return Activator.createErrorStatus(
							UIText.CommitAction_CommittingFailed, e);
				} catch (ExecutionException e) {
					return Activator.createErrorStatus(
							UIText.CommitAction_CommittingFailed, e);
				} finally {
					GitLightweightDecorator.refresh();
				}
				return Status.OK_STATUS;
			}
		};
		job.setUser(true);
		job.schedule();
		return null;
	}

	private void resetState() {
		files = new ArrayList<IFile>();
		notIndexed = new ArrayList<IFile>();
		indexChanges = new ArrayList<IFile>();
		notTracked = new ArrayList<IFile>();
		amending = false;
		previousCommit = null;
	}

	/**
	 * Retrieves a collection of files that may be committed based on the user's
	 * selection when they performed the commit action. That is, even if the
	 * user only selected one folder when the action was performed, if the
	 * folder contains any files that could be committed, they will be returned.
	 *
	 * @param event
	 *
	 * @return a collection of files that is eligible to be committed based on
	 *         the user's selection
	 * @throws ExecutionException
	 */
	private Collection<IFile> getSelectedFiles(ExecutionEvent event)
			throws ExecutionException {
		List<IFile> preselectionCandidates = new ArrayList<IFile>();
		// get the resources the user selected
		IResource[] selectedResources = getSelectedResources(event);
		// iterate through all the files that may be committed
		for (IFile file : files) {
			for (IResource resource : selectedResources) {
				// if any selected resource contains the file, add it as a
				// preselection candidate
				if (resource.contains(file)) {
					preselectionCandidates.add(file);
					break;
				}
			}
		}
		return preselectionCandidates;
	}

	private void loadPreviousCommit(ExecutionEvent event)
			throws ExecutionException {
		IProject project = getProjectsForSelectedResources(event)[0];

		Repository repo = RepositoryMapping.getMapping(project).getRepository();
		try {
			ObjectId parentId = repo.resolve(Constants.HEAD);
			if (parentId != null)
				previousCommit = repo.mapCommit(parentId);
		} catch (IOException e) {
			Activator.handleError(UIText.CommitAction_errorRetrievingCommit, e,
					true);
		}
	}

	private void buildIndexHeadDiffList(ExecutionEvent event)
			throws IOException, CoreException, ExecutionException {
		HashMap<Repository, HashSet<IProject>> repositories = new HashMap<Repository, HashSet<IProject>>();

		for (IProject project : getProjectsInRepositoryOfSelectedResources(event)) {
			RepositoryMapping repositoryMapping = RepositoryMapping
					.getMapping(project);
			assert repositoryMapping != null;

			Repository repository = repositoryMapping.getRepository();

			HashSet<IProject> projects = repositories.get(repository);

			if (projects == null) {
				projects = new HashSet<IProject>();
				repositories.put(repository, projects);
			}

			projects.add(project);
		}

		for (Map.Entry<Repository, HashSet<IProject>> entry : repositories
				.entrySet()) {
			Repository repository = entry.getKey();
			HashSet<IProject> projects = entry.getValue();

			Tree head = repository.mapTree(Constants.HEAD);
			GitIndex index = repository.getIndex();
			IndexDiff indexDiff = new IndexDiff(head, index);
			indexDiff.diff();

			for (IProject project : projects) {
				includeList(project, indexDiff.getAdded(), indexChanges);
				includeList(project, indexDiff.getChanged(), indexChanges);
				includeList(project, indexDiff.getRemoved(), indexChanges);
				includeList(project, indexDiff.getMissing(), notIndexed);
				includeList(project, indexDiff.getModified(), notIndexed);
				addUntrackedFiles(repository, project);
			}
		}
	}

	private void addUntrackedFiles(final Repository repository,
			final IProject project) throws CoreException, IOException {
		final GitIndex index = repository.getIndex();
		final Tree headTree = repository.mapTree(Constants.HEAD);
		project.accept(new IResourceVisitor() {

			public boolean visit(IResource resource) throws CoreException {
				if (Team.isIgnoredHint(resource))
					return false;
				if (resource.getType() == IResource.FILE) {

					String repoRelativePath = RepositoryMapping.getMapping(
							project).getRepoRelativePath(resource);
					try {
						TreeEntry headEntry = (headTree == null ? null
								: headTree.findBlobMember(repoRelativePath));
						if (headEntry == null) {
							Entry indexEntry = null;
							indexEntry = index.getEntry(repoRelativePath);

							if (indexEntry == null) {
								notTracked.add((IFile) resource);
								files.add((IFile) resource);
							}
						}
					} catch (IOException e) {
						throw new TeamException(
								UIText.CommitAction_InternalError, e);
					}
				}
				return true;
			}
		});

	}

	private void includeList(IProject project, HashSet<String> added,
			ArrayList<IFile> category) {
		String repoRelativePath = RepositoryMapping.getMapping(project)
				.getRepoRelativePath(project);
		if (repoRelativePath.length() > 0) {
			repoRelativePath += "/"; //$NON-NLS-1$
		}

		for (String filename : added) {
			try {
				if (!filename.startsWith(repoRelativePath))
					continue;
				String projectRelativePath = filename
						.substring(repoRelativePath.length());
				IFile member = project.getFile(projectRelativePath);
				if (!files.contains(member))
					files.add(member);
				category.add(member);
			} catch (Exception e) {
				if (GitTraceLocation.UI.isActive())
					GitTraceLocation.getTrace().trace(
							GitTraceLocation.UI.getLocation(), e.getMessage(),
							e);
				continue;
			} // if it's outside the workspace, bad things happen
		}
	}

	boolean tryAddResource(IFile resource, GitProjectData projectData,
			ArrayList<IFile> category) {
		if (files.contains(resource))
			return false;

		try {
			RepositoryMapping repositoryMapping = projectData
					.getRepositoryMapping(resource);

			if (isChanged(repositoryMapping, resource)) {
				files.add(resource);
				category.add(resource);
				return true;
			}
		} catch (Exception e) {
			if (GitTraceLocation.UI.isActive())
				GitTraceLocation.getTrace().trace(
						GitTraceLocation.UI.getLocation(), e.getMessage(), e);
		}
		return false;
	}

	private boolean isChanged(RepositoryMapping map, IFile resource) {
		try {
			Repository repository = map.getRepository();
			GitIndex index = repository.getIndex();
			String repoRelativePath = map.getRepoRelativePath(resource);
			Entry entry = index.getEntry(repoRelativePath);
			if (entry != null)
				return entry.isModified(map.getWorkTree());
			return false;
		} catch (UnsupportedEncodingException e) {
			if (GitTraceLocation.UI.isActive())
				GitTraceLocation.getTrace().trace(
						GitTraceLocation.UI.getLocation(), e.getMessage(), e);
		} catch (IOException e) {
			if (GitTraceLocation.UI.isActive())
				GitTraceLocation.getTrace().trace(
						GitTraceLocation.UI.getLocation(), e.getMessage(), e);
		}
		return false;
	}

	@Override
	public boolean isEnabled() {
		try {
			return getProjectsInRepositoryOfSelectedResources(null).length > 0;
		} catch (ExecutionException e) {
			Activator.handleError(e.getMessage(), e, false);
			return false;
		}
	}

	private String getMergeResolveMessage(Repository mergeRepository,
			ExecutionEvent event) throws ExecutionException {
		File mergeMsg = new File(mergeRepository.getDirectory(), Constants.MERGE_MSG);
		FileReader reader;
		try {
			reader = new FileReader(mergeMsg);
		} catch (FileNotFoundException e) {
			MessageDialog.openError(getShell(event),
					UIText.CommitAction_MergeHeadErrorTitle,
					UIText.CommitAction_MergeHeadErrorMessage);
			throw new IllegalStateException(e);
		}
		BufferedReader br = new BufferedReader(reader);
		StringBuffer message = new StringBuffer();
		String s;
		String newLine = newLine();
		try {
			while ((s = br.readLine()) != null) {
				message.append(s).append(newLine);
			}
		} catch (IOException e) {
			MessageDialog.openError(getShell(event),
					UIText.CommitAction_MergeHeadErrorTitle,
					UIText.CommitAction_ErrorReadingMergeMsg);
			throw new IllegalStateException(e);
		}
		return message.toString();
	}

	private String newLine(){
		if(System.getProperty("os.name").indexOf("Windows") != -1){ //$NON-NLS-1$ //$NON-NLS-2$
			return "\r\n"; //$NON-NLS-1$
		}
		else {
			return "\n"; //$NON-NLS-1$
		}
	}

}
