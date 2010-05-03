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
package org.eclipse.egit.ui.internal.clone;

import java.io.File;
import java.io.FilenameFilter;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.egit.ui.UIText;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

/**
 * Allows to import a directory in the local file system as "General" project
 * <p>
 * Asks the user to provide a project name and shows the directory to be shared.
 * <p>
 * TODO String externalization
 */
public class GitCreateGeneralProjectPage extends WizardPage {

	private final File myDirectory;

	private Text projectText;

	private Text directoryText;

	private IProject[] wsProjects;

	/**
	 * Creates a new project creation wizard page.
	 *
	 * @param path
	 *            the path to a directory in the local file system
	 */
	public GitCreateGeneralProjectPage(String path) {
		super(GitCreateGeneralProjectPage.class.getName());
		myDirectory = new File(path);
		setPageComplete(false);
		setTitle(UIText.WizardProjectsImportPage_ImportProjectsTitle);
		setDescription(UIText.WizardProjectsImportPage_ImportProjectsDescription);
	}

	public void createControl(Composite parent) {

		initializeDialogUnits(parent);

		Composite workArea = new Composite(parent, SWT.NONE);
		setControl(workArea);

		workArea.setLayout(new GridLayout(2, false));
		workArea.setLayoutData(new GridData(GridData.FILL_BOTH
				| GridData.GRAB_HORIZONTAL | GridData.GRAB_VERTICAL));

		new Label(workArea, SWT.NONE).setText(UIText.GitCreateGeneralProjectPage_ProjectNameLabel);
		projectText = new Text(workArea, SWT.BORDER);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(projectText);
		projectText.addModifyListener(new ModifyListener() {

			public void modifyText(ModifyEvent e) {
				checkPage();
			}
		});

		new Label(workArea, SWT.NONE).setText(UIText.GitCreateGeneralProjectPage_DirLabel);
		directoryText = new Text(workArea, SWT.BORDER);
		directoryText.setEnabled(false);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(directoryText);

		Dialog.applyDialogFont(workArea);

	}

	@Override
	public void setVisible(boolean visible) {
		if (visible) {
			projectText.setText(myDirectory.getName());
			directoryText.setText(myDirectory.getPath());
			checkPage();
		}
		super.setVisible(visible);
	}

	/**
	 * @return the project name
	 */
	public String getProjectName() {
		return projectText.getText();
	}

	private void checkPage() {
		String projectName = projectText.getText();
		setErrorMessage(null);
		try {
			// make sure the directory exists
			if (!myDirectory.exists()) {
				setErrorMessage(NLS.bind(UIText.GitCreateGeneralProjectPage_DirNotExistMessage,
						myDirectory.getPath()));
				return;
			}
			// make sure we don't have a file
			if (!myDirectory.isDirectory()) {
				setErrorMessage(NLS.bind(UIText.GitCreateGeneralProjectPage_FileNotDirMessage,
						myDirectory.getPath()));
				return;
			}
			// make sure there is not already a .project file
			if (myDirectory.list(new FilenameFilter() {

				public boolean accept(File dir, String name) {
					if (name.equals(".project")) //$NON-NLS-1$
						return true;
					return false;
				}
			}).length > 0) {
				setErrorMessage(NLS.bind(
						UIText.GitCreateGeneralProjectPage_FileExistsInDirMessage,
						".project", myDirectory.getPath())); //$NON-NLS-1$
				return;
			}
			// project name empty
			if (projectName.length() == 0) {
				setErrorMessage(UIText.GitCreateGeneralProjectPage_EnterProjectNameMessage);
				return;
			}
			// project name valid (no strange chars...)
			IStatus result = ResourcesPlugin.getWorkspace().validateName(
					projectName, IResource.PROJECT);
			if (!result.isOK()) {
				setErrorMessage(result.getMessage());
				return;
			}
			// project already exists
			if (isProjectInWorkspace(projectName)) {
				setErrorMessage(NLS.bind(UIText.GitCreateGeneralProjectPage_PorjectAlreadyExistsMessage,
						projectName));
				return;
			}
		} finally {
			setPageComplete(getErrorMessage() == null);
		}

	}

	private IProject[] getProjectsInWorkspace() {
		if (wsProjects == null) {
			wsProjects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		}
		return wsProjects;
	}

	private boolean isProjectInWorkspace(String projectName) {
		if (projectName == null) {
			return false;
		}
		IProject[] workspaceProjects = getProjectsInWorkspace();
		for (int i = 0; i < workspaceProjects.length; i++) {
			if (projectName.equals(workspaceProjects[i].getName())) {
				return true;
			}
		}
		return false;
	}

}
