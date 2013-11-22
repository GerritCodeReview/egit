/*******************************************************************************
 * Copyright (c) 2013 Robin Stocker <robin@nibor.org> and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.push;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.eclipse.egit.core.op.PushOperationResult;
import org.eclipse.egit.core.op.PushOperationSpecification;
import org.eclipse.egit.ui.internal.UIText;
import org.eclipse.egit.ui.internal.components.RepositorySelection;
import org.eclipse.egit.ui.internal.credentials.EGitCredentialsProvider;
import org.eclipse.egit.ui.internal.repository.tree.TagNode;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

/**
 * Wizard for pushing one or more tags to a remote.
 */
public class PushTagsWizard extends Wizard {

	private final Repository repository;

	private final PushTagsPage pushTagsPage;

	private final ConfirmationPage confirmationPage;

	/**
	 * Creates a wizard with the passed parameters and opens a dialog with it in
	 * the active workbench window shell.
	 *
	 * @param repository
	 * @param tagNames
	 */
	public static void openWizardDialog(final Repository repository,
			final String... tagNames) {
		PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
			public void run() {
				Shell shell = PlatformUI.getWorkbench()
						.getActiveWorkbenchWindow().getShell();
				PushTagsWizard wizard = new PushTagsWizard(repository, Arrays
						.asList(tagNames));
				WizardDialog dialog = new WizardDialog(shell, wizard);
				dialog.setHelpAvailable(false);
				dialog.open();
			}
		});
	}

	/**
	 * @param repository
	 * @param tagNamesToSelect
	 */
	public PushTagsWizard(Repository repository,
			Collection<String> tagNamesToSelect) {
		this.repository = repository;
		pushTagsPage = new PushTagsPage(repository, tagNamesToSelect);
		confirmationPage = new ConfirmationPage(repository) {
			@Override
			public void setVisible(boolean visible) {
				if (visible)
					setSelection(getRepositorySelection(), getRefSpecs());
				super.setVisible(visible);
			}
		};
	}

	@Override
	public String getWindowTitle() {
		return UIText.PushTagsWizard_WindowTitle;
	}

	@Override
	public void addPages() {
		addPage(pushTagsPage);
		addPage(confirmationPage);
	}

	@Override
	public IWizardPage getNextPage(IWizardPage page) {
		if (page == pushTagsPage) {
			return confirmationPage;
		}
		return null;
	}

	@Override
	public boolean canFinish() {
		return getContainer().getCurrentPage() == confirmationPage;
	}

	@Override
	public boolean performFinish() {
		try {
			startPush();
			return true;
		} catch (IOException e) {
			confirmationPage.setErrorMessage(e.getMessage());
			return false;
		}
	}

	private RepositorySelection getRepositorySelection() {
		return new RepositorySelection(null,
				pushTagsPage.getSelectedRemoteConfig());
	}

	private List<RefSpec> getRefSpecs() {
		List<RefSpec> specs = new ArrayList<RefSpec>();
		for (TagNode tag : pushTagsPage.getSelectedTags()) {
			String refName = tag.getObject().getName();
			// Force as we have a confirmation page and don't want to add a
			// "force" checkbox
			RefSpec spec = new RefSpec("+" + refName); //$NON-NLS-1$
			specs.add(spec);
		}
		return specs;
	}

	private void startPush() throws IOException {
		PushOperationResult result = confirmationPage.getConfirmedResult();
		PushOperationSpecification pushSpec = result
				.deriveSpecification(confirmationPage
						.isRequireUnchangedSelected());

		PushOperationUI pushOperationUI = new PushOperationUI(repository,
				pushSpec, false);
		pushOperationUI.setCredentialsProvider(new EGitCredentialsProvider());
		pushOperationUI.start();
	}
}
