/*******************************************************************************
 * Copyright (c) 2018 Thomas Wolf <thomas.wolf@paranor.ch>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.egit.ui.internal.repository;

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.core.commands.IStateListener;
import org.eclipse.core.commands.State;
import org.eclipse.egit.core.RepositoryUtil;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.internal.CommonUtils;
import org.eclipse.egit.ui.internal.GitLabels;
import org.eclipse.egit.ui.internal.UIIcons;
import org.eclipse.egit.ui.internal.UIText;
import org.eclipse.egit.ui.internal.decorators.GitDecorator;
import org.eclipse.egit.ui.internal.repository.tree.AdditionalRefNode;
import org.eclipse.egit.ui.internal.repository.tree.RefNode;
import org.eclipse.egit.ui.internal.repository.tree.RepositoryTreeNode;
import org.eclipse.egit.ui.internal.repository.tree.RepositoryTreeNodeType;
import org.eclipse.egit.ui.internal.repository.tree.StashedCommitNode;
import org.eclipse.egit.ui.internal.repository.tree.TagNode;
import org.eclipse.egit.ui.internal.repository.tree.command.ToggleBranchCommitCommand;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;

/**
 * Lightweight decorator for {@link RepositoryTreeNode}s. Note that this
 * decorator does <em>not</em> listen on "references changed" events to fire
 * {@link org.eclipse.jface.viewers.LabelProviderChangedEvent
 * LabelProviderChangedEvent}s -- the RepositoriesView does so and refreshes
 * itself completely.
 */
public class RepositoryTreeNodeDecorator extends GitDecorator
		implements IStateListener {

	private final State verboseBranchModeState;

	private boolean verboseBranchMode = false;

	/**
	 * Constructs a repositories view label provider
	 */
	public RepositoryTreeNodeDecorator() {
		ICommandService srv = CommonUtils.getService(PlatformUI.getWorkbench(), ICommandService.class);
		verboseBranchModeState = srv.getCommand(ToggleBranchCommitCommand.ID)
				.getState(ToggleBranchCommitCommand.TOGGLE_STATE);
		verboseBranchModeState.addListener(this);
		try {
			this.verboseBranchMode = ((Boolean) verboseBranchModeState
					.getValue()).booleanValue();
		} catch (Exception e) {
			Activator.logError(e.getMessage(), e);
		}

	}

	@Override
	public void dispose() {
		verboseBranchModeState.removeListener(this);
		super.dispose();
	}

	@Override
	public void handleStateChange(State state, Object oldValue) {
		try {
			boolean newValue = ((Boolean) state.getValue())
					.booleanValue();
			if (newValue != verboseBranchMode) {
				verboseBranchMode = newValue;
				postLabelEvent();
			}
		} catch (Exception e) {
			Activator.logError(e.getMessage(), e);
		}
	}

	@Override
	public void decorate(Object element, IDecoration decoration) {
		RepositoryTreeNode<?> node = (RepositoryTreeNode) element;
		Repository repository = node.getRepository();
		if (repository != null) {
			try {
				decorateText(node, repository, decoration);
				decorateImage(node, repository, decoration);
			} catch (IOException e) {
				Activator.logError(MessageFormat.format(
						UIText.GitLabelProvider_UnableToRetrieveLabel,
						element.toString()), e);
			}
		}
	}

	private void decorateText(RepositoryTreeNode<?> node,
			@NonNull Repository repository, IDecoration decoration)
			throws IOException {
		switch (node.getType()) {
		case REPO:
			decorateRepository(node, repository, decoration);
			break;
		case ADDITIONALREF:
			decorateAdditionalRef((AdditionalRefNode) node, decoration);
			break;
		case REF:
			decorateRef((RefNode) node, decoration);
			break;
		case TAG:
			decorateTag((TagNode) node, decoration);
			break;
		case STASHED_COMMIT:
			decorateStash((StashedCommitNode) node, decoration);
			break;
		case SUBMODULES:
			decorateSubmodules(repository, decoration);
			break;
		case WORKINGDIR:
			decorateWorkTree(repository, decoration);
			break;
		default:
			break;
		}
	}

	private void decorateAdditionalRef(AdditionalRefNode node,
			IDecoration decoration) {
		Ref ref = node.getObject();
		StringBuilder suffix = new StringBuilder();
		if (ref.isSymbolic()) {
			suffix.append(" [").append(ref.getLeaf().getName()).append(']'); //$NON-NLS-1$
		}
		ObjectId refId = ref.getObjectId();
		suffix.append(' ');
		RevCommit commit = getLatestCommit(node);
		if (commit != null) {
			suffix.append(abbreviate(commit)).append(' ')
					.append(commit.getShortMessage());
		} else if (!ref.isSymbolic() || refId != null) {
			suffix.append(abbreviate(refId));
		} else {
			suffix.append(
					UIText.RepositoriesViewLabelProvider_UnbornBranchText);
		}
		decoration.addSuffix(suffix.toString());
	}

	private void decorateRef(RefNode node, IDecoration decoration) {
		if (verboseBranchMode) {
			RevCommit latest = getLatestCommit(node);
			if (latest != null) {
				decoration.addSuffix(" " + abbreviate(latest) + ' ' //$NON-NLS-1$
						+ latest.getShortMessage());
			}
		}
	}

	private void decorateRepository(RepositoryTreeNode<?> node,
			@NonNull Repository repository, IDecoration decoration)
			throws IOException {
		boolean isSubModule = node.getParent() != null && node.getParent()
				.getType() == RepositoryTreeNodeType.SUBMODULES;
		if (RepositoryUtil.hasChanges(repository)) {
			decoration.addPrefix("> "); //$NON-NLS-1$
		}
		StringBuilder suffix = new StringBuilder();
		if (isSubModule) {
			Ref head = repository.exactRef(Constants.HEAD);
			if (head == null) {
				return;
			}
			suffix.append(" ["); //$NON-NLS-1$
			if (head.isSymbolic()) {
				suffix.append(
						Repository.shortenRefName(head.getLeaf().getName()));
			} else if (head.getObjectId() != null) {
				suffix.append(abbreviate(head.getObjectId()));
			}
			suffix.append(']');
			if (verboseBranchMode && head.getObjectId() != null) {
				try (RevWalk walk = new RevWalk(repository)) {
					RevCommit commit = walk.parseCommit(head.getObjectId());
					suffix.append(' ').append(commit.getShortMessage());
				} catch (IOException ignored) {
					// Ignored
				}
			}
		} else {
			// Not a submodule
			String branch = Activator.getDefault().getRepositoryUtil()
					.getShortBranch(repository);
			if (branch == null) {
				return;
			}
			suffix.append(" ["); //$NON-NLS-1$
			suffix.append(branch);

			BranchTrackingStatus trackingStatus = BranchTrackingStatus
					.of(repository, branch);
			if (trackingStatus != null && (trackingStatus.getAheadCount() != 0
					|| trackingStatus.getBehindCount() != 0)) {
				String formattedTrackingStatus = GitLabels
						.formatBranchTrackingStatus(trackingStatus);
				suffix.append(' ').append(formattedTrackingStatus);
			}

			RepositoryState repositoryState = repository.getRepositoryState();
			if (repositoryState != RepositoryState.SAFE) {
				suffix.append(" - ") //$NON-NLS-1$
						.append(repositoryState.getDescription());
			}
			suffix.append("] - ") //$NON-NLS-1$
					.append(repository.getDirectory().getAbsolutePath());
		}
		decoration.addSuffix(suffix.toString());
	}

	private void decorateStash(StashedCommitNode node, IDecoration decoration) {
		RevCommit commit = node.getObject();
		decoration.addSuffix(
				" [" + abbreviate(commit) + "] " + commit.getShortMessage()); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private void decorateSubmodules(@NonNull Repository repository,
			IDecoration decoration) throws IOException {
		if (haveSubmoduleChanges(repository)) {
			decoration.addPrefix("> "); //$NON-NLS-1$
		}
	}

	private void decorateTag(TagNode node, IDecoration decoration) {
		if (verboseBranchMode && node.getCommitId() != null
				&& node.getCommitId().length() > 0) {
			decoration.addSuffix(" " + node.getCommitId().substring(0, 7) + ' ' //$NON-NLS-1$
					+ node.getCommitShortMessage());
		}
	}

	private void decorateWorkTree(@NonNull Repository repository,
			IDecoration decoration) {
		decoration
				.addSuffix(" - " + repository.getWorkTree().getAbsolutePath()); //$NON-NLS-1$
	}

	private void decorateImage(RepositoryTreeNode<?> node,
			@NonNull Repository repository, IDecoration decoration)
			throws IOException {

		switch (node.getType()) {
		case TAG:
		case ADDITIONALREF:
		case REF:
			// if the branch or tag is checked out,
			// we want to decorate the corresponding
			// node with a little check indicator
			String refName = ((Ref) node.getObject()).getName();
			Ref leaf = ((Ref) node.getObject()).getLeaf();

			String compareString = null;

			String branchName = repository.getFullBranch();
			if (branchName == null) {
				return;
			}
			if (refName.startsWith(Constants.R_HEADS)) {
				// local branch: HEAD would be on the branch
				compareString = refName;
			} else if (refName.startsWith(Constants.R_TAGS)) {
				// tag: HEAD would be on the commit id to which the tag is
				// pointing
				TagNode tagNode = (TagNode) node;
				compareString = tagNode.getCommitId();
			} else if (refName.startsWith(Constants.R_REMOTES)) {
				// remote branch: HEAD would be on the commit id to which
				// the branch is pointing
				ObjectId id = node.getRepository().resolve(refName);
				if (id == null) {
					return;
				}
				try (RevWalk rw = new RevWalk(node.getRepository())) {
					RevCommit commit = rw.parseCommit(id);
					compareString = commit.getId().name();
				}
			} else if (refName.equals(Constants.HEAD)) {
				decoration.addOverlay(UIIcons.OVR_CHECKEDOUT,
						IDecoration.TOP_LEFT);
				return;
			} else {
				String leafname = leaf.getName();
				if (leafname.startsWith(Constants.R_REFS) && leafname
						.equals(node.getRepository().getFullBranch())) {
					decoration.addOverlay(UIIcons.OVR_CHECKEDOUT,
							IDecoration.TOP_LEFT);
					return;
				}
				ObjectId objectId = leaf.getObjectId();
				if (objectId != null && objectId
						.equals(node.getRepository().resolve(Constants.HEAD))) {
					decoration.addOverlay(UIIcons.OVR_CHECKEDOUT,
							IDecoration.TOP_LEFT);
					return;
				}
				// some other symbolic reference
				return;
			}

			if (compareString != null && compareString.equals(branchName)) {
				decoration.addOverlay(UIIcons.OVR_CHECKEDOUT,
						IDecoration.TOP_LEFT);
			}

			break;
		default:
			break;
		}
	}

	private RevCommit getLatestCommit(RepositoryTreeNode node) {
		Ref ref = (Ref) node.getObject();
		ObjectId id;
		if (ref.isSymbolic())
			id = ref.getLeaf().getObjectId();
		else
			id = ref.getObjectId();
		if (id == null)
			return null;
		try (RevWalk walk = new RevWalk(node.getRepository())) {
			walk.setRetainBody(true);
			return walk.parseCommit(id);
		} catch (IOException ignored) {
			return null;
		}
	}

	private String abbreviate(final ObjectId id) {
		if (id != null)
			return id.abbreviate(7).name();
		else
			return ObjectId.zeroId().abbreviate(7).name();
	}

	private boolean haveSubmoduleChanges(@NonNull Repository repository)
			throws IOException {
		boolean hasChanges = false;
		try (SubmoduleWalk walk = SubmoduleWalk.forIndex(repository)) {
			while (!hasChanges && walk.next()) {
				Repository submodule = walk.getRepository();
				if (submodule != null) {
					Repository cached = org.eclipse.egit.core.Activator
							.getDefault().getRepositoryCache().lookupRepository(
									submodule.getDirectory().getAbsoluteFile());
					hasChanges = cached != null
							&& RepositoryUtil.hasChanges(cached);
					submodule.close();
				}
			}
		}
		return hasChanges;
	}
}
