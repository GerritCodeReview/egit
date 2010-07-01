/*******************************************************************************
 * Copyright (c) 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Stefan Lay (SAP AG) - initial implementation
 *    Mathias Kinzler (SAP AG) - use the abstract super class
 *******************************************************************************/
package org.eclipse.egit.ui.internal.dialogs;

import org.eclipse.egit.core.op.ResetOperation.ResetType;
import org.eclipse.egit.ui.UIText;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.window.Window;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

/**
 * Dialog for selecting a reset target.
 */
public class ResetTargetSelectionDialog extends AbstractBranchSelectionDialog {

	private ResetType resetType = ResetType.MIXED;

	/**
	 * Construct a dialog to select a branch to reset to
	 *
	 * @param parentShell
	 * @param repo
	 */
	public ResetTargetSelectionDialog(Shell parentShell, Repository repo) {
		super(parentShell, repo);
	}

	@Override
	protected void createCustomArea(Composite parent) {
		Group g = new Group(parent, SWT.NONE);
		g.setText(UIText.ResetTargetSelectionDialog_ResetTypeGroup);
		g.setLayoutData(GridDataFactory.fillDefaults().align(SWT.CENTER,
				SWT.CENTER).create());
		g.setLayout(new GridLayout(1, false));

		Button soft = new Button(g, SWT.RADIO);
		soft.setText(UIText.ResetTargetSelectionDialog_ResetTypeSoftButton);
		soft.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				resetType = ResetType.SOFT;
			}
		});

		Button medium = new Button(g, SWT.RADIO);
		medium.setSelection(true);
		medium.setText(UIText.ResetTargetSelectionDialog_ResetTypeMixedButton);
		medium.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				resetType = ResetType.MIXED;
			}
		});

		Button hard = new Button(g, SWT.RADIO);
		hard.setText(UIText.ResetTargetSelectionDialog_ResetTypeHardButton);
		hard.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				resetType = ResetType.HARD;
			}
		});
	}

	@Override
	protected void refNameSelected(String refName) {
		// TODO pending bug about missing reset for tags: 317350
		// boolean tagSelected = refName != null
		// && refName.startsWith(Constants.R_TAGS);

		boolean branchSelected = refName != null
				&& (refName.startsWith(Constants.R_HEADS) || refName
						.startsWith(Constants.R_REMOTES));

		getButton(Window.OK).setEnabled(branchSelected);

	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		super.createButtonsForButtonBar(parent);
		getButton(Window.OK).setText(UIText.ResetTargetSelectionDialog_ResetButton);
	}

	@Override
	protected String getTitle() {
		return NLS.bind(UIText.ResetTargetSelectionDialog_ResetTitle, repo
				.getDirectory().toString());
	}

	/**
	 * @return Type of Reset
	 */
	public ResetType getResetType() {
		return resetType;
	}

	@Override
	protected void okPressed() {
		if (resetType == ResetType.HARD) {
			if (!MessageDialog.openQuestion(getShell(),
					UIText.ResetTargetSelectionDialog_ResetQuestion,
					UIText.ResetTargetSelectionDialog_ResetConfirmQuestion)) {
				return;
			}
		}
		super.okPressed();
	}

	@Override
	protected String getMessageText() {
		return UIText.ResetTargetSelectionDialog_SelectBranchForResetMessage;
	}
}