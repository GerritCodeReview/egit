/*******************************************************************************
 * Copyright (c) 2018, 2019 Thomas Wolf <thomas.wolf@paranor.ch> and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Alexander Nittka <alex@nittka.de> - Bug 545123
 *******************************************************************************/
package org.eclipse.egit.ui.internal.repository;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Set;

import org.eclipse.core.commands.IStateListener;
import org.eclipse.core.commands.State;
import org.eclipse.egit.core.RepositoryCache;
import org.eclipse.egit.core.RepositoryUtil;
import org.eclipse.egit.core.internal.Utils;
import org.eclipse.egit.core.internal.indexdiff.IndexDiffCache;
import org.eclipse.egit.core.internal.indexdiff.IndexDiffCacheEntry;
import org.eclipse.egit.core.internal.indexdiff.IndexDiffData;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.internal.CommonUtils;
import org.eclipse.egit.ui.internal.GitLabels;
import org.eclipse.egit.ui.internal.UIText;
import org.eclipse.egit.ui.internal.decorators.DecoratableResourceHelper;
import org.eclipse.egit.ui.internal.decorators.GitDecorator;
import org.eclipse.egit.ui.internal.groups.RepositoryGroup;
import org.eclipse.egit.ui.internal.repository.tree.AdditionalRefNode;
import org.eclipse.egit.ui.internal.repository.tree.RefNode;
import org.eclipse.egit.ui.internal.repository.tree.RepositoryGroupNode;
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
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.handlers.RegistryToggleState;

/**
 * Lightweight decorator for {@link RepositoryTreeNode}s. Note that this
 * decorator does <em>not</em> listen on "references changed" events to fire
 * {@link org.eclipse.jface.viewers.LabelProviderChangedEvent
 * LabelProviderChangedEvent}s -- the RepositoriesView does so and refreshes
 * itself completely.
 */
public class RepositoryTreeNodeDecorator extends GitDecorator
		implements IStateListener {

	private static final String HAS_CHANGES_PREFIX = "> "; //$NON-NLS-1$

	private static final String OPEN_BRACKET = " ["; //$NON-NLS-1$

	private static final String OPEN_PARENTHESIS = " ("; //$NON-NLS-1$

	private static final String MULTIPLE_REPOSITORIES = "*"; //$NON-NLS-1$

	private final State verboseBranchModeState;

	private boolean verboseBranchMode = false;

	/**
	 * Constructs a repositories view label provider
	 */
	public RepositoryTreeNodeDecorator() {
		ICommandService srv = CommonUtils.getService(PlatformUI.getWorkbench(), ICommandService.class);
		verboseBranchModeState = srv.getCommand(ToggleBranchCommitCommand.ID)
				.getState(RegistryToggleState.STATE_ID);
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
		try {
			if (repository != null) {
				decorateText(node, repository, decoration);
			} else if (node.getType() == RepositoryTreeNodeType.REPOGROUP) {
				decorateRepositoryGroup(node, decoration);
			}
		} catch (IOException e) {
			Activator.logError(MessageFormat.format(
					UIText.GitLabelProvider_UnableToRetrieveLabel,
					element.toString()), e);
		}
	}

	private void decorateText(RepositoryTreeNode<?> node,
			@NonNull Repository repository, IDecoration decoration)
			throws IOException {
		boolean decorated = false;
		switch (node.getType()) {
		case REPO:
			decorated = decorateRepository(node, repository, decoration);
			break;
		case ADDITIONALREF:
			decorated = decorateAdditionalRef((AdditionalRefNode) node,
					decoration);
			break;
		case REF:
			decorated = decorateRef((RefNode) node, decoration);
			break;
		case TAG:
			decorated = decorateTag((TagNode) node, decoration);
			break;
		case STASHED_COMMIT:
			decorated = decorateStash((StashedCommitNode) node, decoration);
			break;
		case SUBMODULES:
			decorated = decorateSubmodules(repository, decoration);
			break;
		default:
			return;
		}
		if (!decorated) {
			ensureCorrectLabelCaching(decoration);
		}
	}

	private boolean decorateAdditionalRef(AdditionalRefNode node,
			IDecoration decoration) {
		Ref ref = node.getObject();
		StringBuilder suffix = new StringBuilder();
		if (ref.isSymbolic()) {
			suffix.append(OPEN_BRACKET).append(ref.getLeaf().getName())
					.append(']');
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
		return true;
	}

	private boolean decorateRef(RefNode node, IDecoration decoration) {
		if (verboseBranchMode) {
			RevCommit latest = getLatestCommit(node);
			if (latest != null) {
				decoration.addSuffix(" " + abbreviate(latest) + ' ' //$NON-NLS-1$
						+ latest.getShortMessage());
				return true;
			}
		}
		return false;
	}

	private boolean decorateRepository(RepositoryTreeNode<?> node,
			@NonNull Repository repository, IDecoration decoration)
			throws IOException {
		boolean isSubModule = node.getParent() != null && node.getParent()
				.getType() == RepositoryTreeNodeType.SUBMODULES;
		if (RepositoryUtil.hasChanges(repository)) {
			decoration.addPrefix(HAS_CHANGES_PREFIX);
		}
		StringBuilder suffix = new StringBuilder();
		if (isSubModule) {
			Ref head = repository.exactRef(Constants.HEAD);
			if (head == null) {
				return false;
			}
			suffix.append(OPEN_BRACKET);
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
				return false;
			}
			suffix.append(OPEN_BRACKET);
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
			suffix.append(']');
		}
		decoration.addSuffix(suffix.toString());
		return true;
	}

	private void decorateRepositoryGroup(RepositoryTreeNode<?> node,
			IDecoration decoration) throws IOException {
		RepositoryCache cache = org.eclipse.egit.core.Activator.getDefault()
				.getRepositoryCache();
		RepositoryGroup group = ((RepositoryGroupNode) node).getObject();
		boolean markGroupDirty = false;
		int numberOfBranches = 0;
		String singleBranch = null;
		String singleRepoName = null;
		int numberOfDirectories = group.getRepositoryDirectories().size();
		for (File repoDir : group.getRepositoryDirectories()) {
			Repository repo = cache.getRepository(repoDir);
			if (repo != null) {
				if (numberOfDirectories == 1) {
					singleRepoName = DecoratableResourceHelper
							.getRepositoryName(repo);
				}
				if (RepositoryUtil.hasChanges(repo)) {
					markGroupDirty = true;
				}
				if (numberOfBranches <= 1) {
					String thisBranch = DecoratableResourceHelper
							.getShortBranch(repo);
					if (!thisBranch.equals(singleBranch)) {
						numberOfBranches++;
					}
					if (singleBranch == null) {
						singleBranch = thisBranch;
					}
				}
				if (markGroupDirty && numberOfBranches > 1) {
					break;
				}
			}
		}
		boolean decorated = false;
		if (markGroupDirty) {
			decorated = true;
			decoration.addPrefix(HAS_CHANGES_PREFIX);
		}
		if (numberOfBranches == 1) {
			decorated = true;
			String repoLabel = singleRepoName != null ? singleRepoName
					: MULTIPLE_REPOSITORIES;
			decoration.addSuffix(
					OPEN_BRACKET + repoLabel + ' ' + singleBranch + ']');
		} else if (numberOfDirectories > 1) {
			decorated = true;
			decoration.addSuffix(OPEN_PARENTHESIS + numberOfDirectories + ')');
		}
		if (!decorated) {
			ensureCorrectLabelCaching(decoration);
		}
	}

	private void ensureCorrectLabelCaching(IDecoration decoration) {
		// Ensure the caching of last labels in
		// RepositoryTreeNodeLabelProvider works
		decoration.addSuffix(" ");//$NON-NLS-1$
	}

	private boolean decorateStash(StashedCommitNode node,
			IDecoration decoration) {
		RevCommit commit = node.getObject();
		decoration.addSuffix(
				OPEN_BRACKET + abbreviate(commit) + "] " //$NON-NLS-1$
				+ commit.getShortMessage());
		return true;
	}

	private boolean decorateSubmodules(@NonNull Repository repository,
			IDecoration decoration) {
		if (haveSubmoduleChanges(repository)) {
			decoration.addPrefix(HAS_CHANGES_PREFIX);
			return true;
		}
		return false;
	}

	private boolean decorateTag(TagNode node, IDecoration decoration) {
		if (verboseBranchMode && node.getCommitId() != null
				&& node.getCommitId().length() > 0) {
			decoration.addSuffix(" " + node.getCommitId().substring(0, 7) + ' ' //$NON-NLS-1$
					+ node.getCommitShortMessage());
			return true;
		}
		return false;
	}

	private RevCommit getLatestCommit(RepositoryTreeNode node) {
		Ref ref = (Ref) node.getObject();
		ObjectId id;
		if (ref.isSymbolic()) {
			id = ref.getLeaf().getObjectId();
		} else {
			id = ref.getObjectId();
		}
		if (id == null) {
			return null;
		}
		try (RevWalk walk = new RevWalk(node.getRepository())) {
			walk.setRetainBody(true);
			return walk.parseCommit(id);
		} catch (IOException ignored) {
			return null;
		}
	}

	private String abbreviate(final ObjectId id) {
		if (id != null) {
			return Utils.getShortObjectId(id);
		} else {
			return Utils.getShortObjectId(ObjectId.zeroId());
		}
	}

	private boolean haveSubmoduleChanges(@NonNull Repository repository) {
		IndexDiffCache cache = org.eclipse.egit.core.Activator.getDefault()
				.getIndexDiffCache();
		if (cache == null) {
			return false;
		}
		IndexDiffCacheEntry entry = cache.getIndexDiffCacheEntry(repository);
		IndexDiffData data = entry != null ? entry.getIndexDiff() : null;
		if (data == null) {
			return false;
		}
		Set<String> modified = data.getModified();
		return data.getSubmodules().stream()
				.anyMatch(s -> modified.contains(s));
	}

	@Override
	protected String getName() {
		return UIText.RepositoryTreeNodeDecorator_name;
	}
}