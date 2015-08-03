/*******************************************************************************
 * Copyright (C) 2008, Roger C. Soares <rogersoares@intelinet.com.br>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
 * Copyright (C) 2012, Daniel Megert <daniel_megert@ch.ibm.com>
 * Copyright (C) 2013, Robin Stocker <robin@nibor.org>
 * Copyright (C) 2015, Jan-Ove Weichel <ovi.weichel@gmail.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.egit.ui.internal.decorators.GitLightweightDecorator;
import org.eclipse.egit.ui.internal.history.FindToolbar;
import org.eclipse.egit.ui.internal.staging.StagingView;
import org.eclipse.jface.preference.IPreferenceStore;

/**
 * Plugin extension point to initialize the plugin runtime preferences.
 */
public class PluginPreferenceInitializer extends AbstractPreferenceInitializer {

	/**
	 * Calls super constructor.
	 */
	public PluginPreferenceInitializer() {
		super();
	}

	/**
	 * This method initializes the plugin preferences with default values.
	 */
	@Override
	public void initializeDefaultPreferences() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		int[] w;

		store.setDefault(UIPreferences.RESOURCEHISTORY_SHOW_RELATIVE_DATE, true);
		store.setDefault(UIPreferences.RESOURCEHISTORY_SHOW_EMAIL_ADDRESSES, false);
		store.setDefault(UIPreferences.RESOURCEHISTORY_SHOW_NOTES, false);
		store.setDefault(UIPreferences.RESOURCEHISTORY_SHOW_COMMENT_WRAP, true);
		store.setDefault(UIPreferences.RESOURCEHISTORY_SHOW_REV_DETAIL, true);
		store.setDefault(UIPreferences.RESOURCEHISTORY_SHOW_REV_COMMENT, true);
		store.setDefault(UIPreferences.RESOURCEHISTORY_SHOW_TOOLTIPS, false);
		store.setDefault(UIPreferences.RESOURCEHISTORY_SHOW_ALL_BRANCHES, false);
		store.setDefault(UIPreferences.RESOURCEHISTORY_SHOW_ADDITIONAL_REFS,
				false);
		store.setDefault(UIPreferences.RESOURCEHISTORY_FOLLOW_RENAMES, true);
		store.setDefault(UIPreferences.RESOURCEHISTORY_COMPARE_MODE, false);

		store.setDefault(UIPreferences.DECORATOR_RECOMPUTE_ANCESTORS, true);
		store.setDefault(UIPreferences.DECORATOR_FILETEXT_DECORATION,
				GitLightweightDecorator.DecorationHelper.FILE_FORMAT_DEFAULT);
		store.setDefault(UIPreferences.DECORATOR_FOLDERTEXT_DECORATION,
				GitLightweightDecorator.DecorationHelper.FOLDER_FORMAT_DEFAULT);
		store.setDefault(UIPreferences.DECORATOR_PROJECTTEXT_DECORATION,
				GitLightweightDecorator.DecorationHelper.PROJECT_FORMAT_DEFAULT);
		store.setDefault(UIPreferences.DECORATOR_SHOW_TRACKED_ICON, true);
		store.setDefault(UIPreferences.DECORATOR_SHOW_UNTRACKED_ICON, true);
		store.setDefault(UIPreferences.DECORATOR_SHOW_STAGED_ICON, true);
		store.setDefault(UIPreferences.DECORATOR_SHOW_CONFLICTS_ICON, true);
		store.setDefault(UIPreferences.DECORATOR_SHOW_ASSUME_VALID_ICON, true);
		store.setDefault(UIPreferences.DECORATOR_SHOW_DIRTY_ICON, false);

		w = new int[] { 500, 500 };
		store.setDefault(UIPreferences.RESOURCEHISTORY_GRAPH_SPLIT, UIPreferences.intArrayToString(w));
		w = new int[] { 700, 300 };
		store.setDefault(UIPreferences.RESOURCEHISTORY_REV_SPLIT, UIPreferences.intArrayToString(w));

		store.setDefault(UIPreferences.FINDTOOLBAR_IGNORE_CASE, true);
		store.setDefault(UIPreferences.FINDTOOLBAR_FIND_IN,
				FindToolbar.PREFS_FINDIN_ALL);
		store.setDefault(UIPreferences.COMMIT_DIALOG_HARD_WRAP_MESSAGE, true);
		store.setDefault(UIPreferences.COMMIT_DIALOG_SIGNED_OFF_BY, false);

		store.setDefault(UIPreferences.REFESH_ON_INDEX_CHANGE, true);
		store.setDefault(UIPreferences.REFESH_ONLY_WHEN_ACTIVE, true);

		store.setDefault(UIPreferences.SHOW_REBASE_CONFIRM, true);
		store.setDefault(UIPreferences.SHOW_INITIAL_CONFIG_DIALOG, true);
		store.setDefault(UIPreferences.SHOW_HOME_DIR_WARNING, true);
		store.setDefault(UIPreferences.SHOW_DETACHED_HEAD_WARNING, true);
		store.setDefault(UIPreferences.SHOW_CHECKOUT_CONFIRMATION, true);
		store.setDefault(UIPreferences.SHOW_RUNNING_LAUNCH_ON_CHECKOUT_WARNING,
				true);


		store.setDefault(UIPreferences.SYNC_VIEW_CHANGESET_LABEL_FORMAT,
				UIPreferences.DEFAULT_CHANGESET_FORMAT);
		store.setDefault(UIPreferences.SYNC_VIEW_ALWAYS_SHOW_CHANGESET_MODEL,
				false);
		store.setDefault(UIPreferences.SYNC_VIEW_FETCH_BEFORE_LAUNCH, true);
		store.setDefault(UIPreferences.DATE_FORMAT,
				UIPreferences.DEFAULT_DATE_FORMAT);
		store.setDefault(UIPreferences.HISTORY_MAX_NUM_COMMITS, 10000);
		store.setDefault(UIPreferences.HISTORY_SHOW_BRANCH_SEQUENCE, true);
		store.setDefault(UIPreferences.HISTORY_SHOW_TAG_SEQUENCE, false);
		store.setDefault(UIPreferences.BLAME_IGNORE_WHITESPACE, false);
		store.setDefault(UIPreferences.REMOTE_CONNECTION_TIMEOUT, 30 /* seconds */);
		store.setDefault(UIPreferences.STAGING_VIEW_PRESENTATION,
				StagingView.Presentation.LIST.name());
		store.setDefault(UIPreferences.STAGING_VIEW_FILENAME_MODE, true);
		store.setDefault(UIPreferences.STAGING_VIEW_COMPARE_MODE, true);
		store.setDefault(UIPreferences.STAGING_VIEW_MAX_LIMIT_LIST_MODE, 10000);
		store.setDefault(UIPreferences.STAGING_VIEW_PRESENTATION_CHANGED,
				false);
		store.setDefault(UIPreferences.CLONE_WIZARD_STORE_SECURESTORE, false);
		store.setDefault(UIPreferences.COMMIT_DIALOG_HISTORY_SIZE, 10);
		store.setDefault(UIPreferences.CHECKOUT_PROJECT_RESTORE, true);
		store.setDefault(UIPreferences.HISTORY_MAX_TAG_LENGTH, 18);
		store.setDefault(UIPreferences.HISTORY_MAX_BRANCH_LENGTH, 18);
		store.setDefault(UIPreferences.HISTORY_MAX_DIFF_LINES, 1000);
		store.setDefault(UIPreferences.CLONE_WIZARD_SHOW_DETAILED_FAILURE_DIALOG, true);
		store.setDefault(UIPreferences.MERGE_MODE, "2"); //$NON-NLS-1$
		store.setDefault(UIPreferences.USE_LOGICAL_MODEL, true);

		store.setDefault(UIPreferences.REBASE_INTERACTIVE_SYNC_SELECTION, true);
	}

}
