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
package org.eclipse.egit.ui.view.repositories;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.test.ContextMenuHelper;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * SWTBot Tests for the Git Repositories View tag handling support
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class GitRepositoriesViewTagHandlingTest extends
		GitRepositoriesViewTestBase {

	private static File repositoryFile;

	private Repository repository;

	private RevWalk revWalk;

	@BeforeClass
	public static void beforeClass() throws Exception {
		repositoryFile = createProjectAndCommitToRepository();
		Activator.getDefault().getRepositoryUtil().addConfiguredRepository(
				repositoryFile);
	}

	@Before
	public void before() throws Exception {
		repository = lookupRepository(repositoryFile);
		for (String ref : repository.getTags().keySet()) {
			RefUpdate op = repository.updateRef(ref, true);
			op.setRefLogMessage("tag deleted", //$NON-NLS-1$
					false);
			// we set the force update in order
			// to avoid having this rejected
			// due to minor issues
			op.setForceUpdate(true);
			op.delete();
		}
		revWalk = new RevWalk(repository);
	}

	@Test
	public void testCreateTags() throws Exception {
		SWTBotTree tree = getOrOpenView().bot().tree();
		int initialCount = getTagsItem(tree, repositoryFile).expand()
				.rowCount();

		String initialObjid = getObjectIdOfCommit();
		createTag("FirstTag", "The first tag");
		touchAndSubmit();
		String newObject = getObjectIdOfCommit();
		createTag("SecondTag", "The second tag");
		refreshAndWait();
		SWTBotTreeItem[] items = getTagsItem(tree, repositoryFile).expand()
				.getItems();
		assertEquals("Wrong number of tags", initialCount + 2, items.length);

		assertTrue("Wrong commit id", initialObjid
				.equals(getCommitIdOfTag("FirstTag")));
		assertTrue("Wrong commit id", newObject
				.equals(getCommitIdOfTag("SecondTag")));
	}

	@Test
	public void testResetToTag() throws Exception {
		SWTBotTree tree = getOrOpenView().bot().tree();

		String initialContent = getTestFileContent();
		createTag("ResetToFirst", "The first tag");
		touchAndSubmit();
		String newContent = getTestFileContent();
		assertFalse("Wrong content", initialContent.equals(newContent));
		createTag("ResetToSecond", "The second tag");
		refreshAndWait();
		getTagsItem(tree, repositoryFile).expand().getNode("ResetToFirst")
				.select();
		ContextMenuHelper.clickContextMenu(tree, myUtil
				.getPluginLocalizedValue("ResetCommand"));

		SWTBotShell resetDialog = bot.shell(UIText.ResetCommand_WizardTitle);
		pressAltAndChar(resetDialog, 'H');
		resetDialog.bot().button(IDialogConstants.FINISH_LABEL).click();
		waitInUI();

		bot.shell(UIText.ResetTargetSelectionDialog_ResetQuestion).bot()
				.button(IDialogConstants.YES_LABEL).click();
		assertEquals("Wrong content", initialContent, getTestFileContent());
	}

	private String getCommitIdOfTag(String tagName) throws Exception {
		return revWalk.parseTag(repository.resolve(tagName)).getObject()
				.getId().name();
	}

	private void createTag(String name, String message) throws Exception {
		SWTBotTree tree = getOrOpenView().bot().tree();
		getTagsItem(tree, repositoryFile).select();
		ContextMenuHelper.clickContextMenu(tree, myUtil
				.getPluginLocalizedValue("CreateTagCommand"));
		String branchName = repository.getBranch();
		String shellTitle = NLS.bind(
				UIText.CreateTagDialog_questionNewTagTitle, branchName);
		SWTBotShell createDialog = bot.shell(shellTitle).activate();
		createDialog.bot().textWithLabel(UIText.CreateTagDialog_tagName)
				.setText(name);
		createDialog.bot().textWithLabel(UIText.CreateTagDialog_tagMessage)
				.setText("dummy");
		// TODO the ok button does not enable upon the first setText
		createDialog.bot().textWithLabel(UIText.CreateTagDialog_tagMessage)
				.setText(message);
		waitInUI();
		createDialog.bot().button(IDialogConstants.OK_LABEL).click();
	}

	private String getObjectIdOfCommit() throws Exception {

		String branch = repository.getFullBranch();
		if (ObjectId.isId(branch))
			return branch;
		if (branch.startsWith(Constants.R_REFS)) {
			RevCommit commit = revWalk.parseCommit(repository.resolve(branch));
			return commit.getId().getName();
		}
		if (branch.startsWith(Constants.R_TAGS)) {
			RevTag tag = revWalk.parseTag(repository.resolve(branch));
			return tag.getObject().getId().name();
		}
		throw new IllegalStateException("Can't resolve commit");
	}
}
