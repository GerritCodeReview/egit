/*******************************************************************************
 * Copyright (c) 2011, Chris Aniszczyk <caniszczyk@gmail.com> and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Chris Aniszczyk <caniszczyk@gmail.com> - initial implementation
 *******************************************************************************/
package org.eclipse.egit.ui.internal.reflog;

import java.io.IOException;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIIcons;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.UIUtils;
import org.eclipse.egit.ui.internal.commit.CommitEditor;
import org.eclipse.egit.ui.internal.commit.RepositoryCommit;
import org.eclipse.egit.ui.internal.repository.tree.RepositoryTreeNode;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.resource.ResourceManager;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.ReflogEntry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.forms.widgets.Form;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.part.ViewPart;

/**
 * A view that shows reflog entries
 */
public class ReflogView extends ViewPart {

	/**
	 * View id
	 */
	public static final String VIEW_ID = "org.eclipse.egit.ui.ReflogView"; //$NON-NLS-1$

	private Form form;

	private TableViewer reflogTableViewer;

	private ISelectionListener selectionChangedListener;

	@Override
	public void createPartControl(Composite parent) {
		GridLayoutFactory.fillDefaults().applyTo(parent);

		final FormToolkit toolkit = new FormToolkit(parent.getDisplay());
		parent.addDisposeListener(new DisposeListener() {

			public void widgetDisposed(DisposeEvent e) {
				toolkit.dispose();
			}
		});

		form = toolkit.createForm(parent);

		Image repoImage = UIIcons.REPOSITORY.createImage();
		UIUtils.hookDisposal(form, repoImage);
		final Image branchImage = UIIcons.BRANCH.createImage();
		UIUtils.hookDisposal(form, branchImage);
		form.setImage(repoImage);
		form.setText(UIText.StagingView_NoSelectionTitle);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(form);
		toolkit.decorateFormHeading(form);
		GridLayoutFactory.fillDefaults().applyTo(form.getBody());

		Composite tableComposite = toolkit.createComposite(form.getBody());
		final TableColumnLayout layout = new TableColumnLayout();
		tableComposite.setLayout(layout);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(tableComposite);

		reflogTableViewer = new TableViewer(toolkit.createTable(tableComposite,
				SWT.FULL_SELECTION | SWT.MULTI));
		reflogTableViewer.getTable().setLinesVisible(true);
		reflogTableViewer.getTable().setHeaderVisible(true);
		reflogTableViewer.setContentProvider(new ReflogViewContentProvider());
		ColumnViewerToolTipSupport.enableFor(reflogTableViewer);

		TableViewerColumn fromColum = createColumn(layout, "From", 10, SWT.LEFT); //$NON-NLS-1$
		fromColum.setLabelProvider(new ColumnLabelProvider() {

			@Override
			public String getText(Object element) {
				final ReflogEntry entry = (ReflogEntry) element;
				return entry.getOldId().abbreviate(6).name();
			}

			@Override
			public String getToolTipText(Object element) {
				final ReflogEntry entry = (ReflogEntry) element;
				return entry.getOldId().name();
			}

			@Override
			public Image getImage(Object element) {
				return branchImage;
			}
		});

		TableViewerColumn toColumn = createColumn(layout, "To", 10, SWT.LEFT); //$NON-NLS-1$
		toColumn.setLabelProvider(new ColumnLabelProvider() {

			@Override
			public String getText(Object element) {
				final ReflogEntry entry = (ReflogEntry) element;
				return entry.getNewId().abbreviate(6).name();
			}

			@Override
			public String getToolTipText(Object element) {
				final ReflogEntry entry = (ReflogEntry) element;
				return entry.getNewId().name();
			}

			@Override
			public Image getImage(Object element) {
				return branchImage;
			}

		});
		TableViewerColumn messageColumn = createColumn(layout,
				"Message", 50, SWT.LEFT); //$NON-NLS-1$
		messageColumn.setLabelProvider(new ColumnLabelProvider() {

			private ResourceManager resourceManager = new LocalResourceManager(
					JFaceResources.getResources());

			@Override
			public String getText(Object element) {
				final ReflogEntry entry = (ReflogEntry) element;
				return entry.getComment();
			}

			public Image getImage(Object element) {
				String comment = ((ReflogEntry) element).getComment();
				if (comment.startsWith("commit:")) //$NON-NLS-1$
					return (Image) resourceManager.get(UIIcons.CHANGESET);
				if (comment.startsWith("commit (amend):")) //$NON-NLS-1$
					return (Image) resourceManager.get(UIIcons.AMEND_COMMIT);
				if (comment.startsWith("pull :")) //$NON-NLS-1$
					return (Image) resourceManager.get(UIIcons.PULL);
				if (comment.startsWith("clone:")) //$NON-NLS-1$
					return (Image) resourceManager.get(UIIcons.CLONEGIT);
				if (comment.startsWith("rebase finished:")) //$NON-NLS-1$
					return (Image) resourceManager.get(UIIcons.REBASE);
				return null;
			}

			public void dispose() {
				resourceManager.dispose();
				super.dispose();
			}
		});
		reflogTableViewer.addOpenListener(new IOpenListener() {

			public void open(OpenEvent event) {
				IStructuredSelection selection = (IStructuredSelection) event
						.getSelection();
				if (selection.isEmpty())
					return;
				Repository repo = (Repository) reflogTableViewer.getInput();
				if (repo == null)
					return;
				RevWalk walk = new RevWalk(repo);
				try {
					for (Object element : selection.toArray()) {
						ReflogEntry entry = (ReflogEntry) element;
						ObjectId id = entry.getNewId();
						if (id == null || id.equals(ObjectId.zeroId()))
							id = entry.getOldId();
						if (id != null && !id.equals(ObjectId.zeroId()))
							CommitEditor.openQuiet(new RepositoryCommit(repo,
									walk.parseCommit(id)));
					}
				} catch (IOException e) {
					Activator.logError("Error opening commit", e); //$NON-NLS-1$
				} finally {
					walk.release();
				}

			}
		});

		selectionChangedListener = new ISelectionListener() {
			public void selectionChanged(IWorkbenchPart part,
					ISelection selection) {
				if (part instanceof IEditorPart) {
					IEditorInput input = ((IEditorPart) part).getEditorInput();
					if (input instanceof IFileEditorInput)
						reactOnSelection(new StructuredSelection(
								((IFileEditorInput) input).getFile()));
				} else
					reactOnSelection(selection);
			}
		};

		ISelectionService service = (ISelectionService) getSite().getService(
				ISelectionService.class);
		service.addPostSelectionListener(selectionChangedListener);

		getSite().setSelectionProvider(reflogTableViewer);
	}

	@Override
	public void setFocus() {
		reflogTableViewer.getControl().setFocus();
	}

	@Override
	public void dispose() {
		super.dispose();
		ISelectionService service = (ISelectionService) getSite().getService(
				ISelectionService.class);
		service.removePostSelectionListener(selectionChangedListener);
	}

	private void reactOnSelection(ISelection selection) {
		if (selection instanceof StructuredSelection) {
			StructuredSelection ssel = (StructuredSelection) selection;
			if (ssel.size() != 1)
				return;
			Repository repository = null;
			if (ssel.getFirstElement() instanceof IResource) {
				IResource resource = (IResource) ssel.getFirstElement();
				RepositoryMapping mapping = RepositoryMapping
						.getMapping(resource.getProject());
				repository = mapping.getRepository();
			}
			if (ssel.getFirstElement() instanceof IAdaptable) {
				IResource adapted = (IResource) ((IAdaptable) ssel
						.getFirstElement()).getAdapter(IResource.class);
				if (adapted != null) {
					RepositoryMapping mapping = RepositoryMapping
							.getMapping(adapted);
					if (mapping != null) {
						repository = mapping.getRepository();
					}
				}
			} else if (ssel.getFirstElement() instanceof RepositoryTreeNode) {
				RepositoryTreeNode repoNode = (RepositoryTreeNode) ssel
						.getFirstElement();
				repository = repoNode.getRepository();
			}
			reflogTableViewer.setInput(repository);
			if (repository != null) {
				form.setText(getRepositoryName(repository));
			} else {
				form.setText(UIText.StagingView_NoSelectionTitle);
			}
		}
	}

	private TableViewerColumn createColumn(
			final TableColumnLayout columnLayout, final String text,
			final int weight, final int style) {
		final TableViewerColumn viewerColumn = new TableViewerColumn(
				reflogTableViewer, style);
		final TableColumn column = viewerColumn.getColumn();
		column.setText(text);
		columnLayout.setColumnData(column, new ColumnWeightData(weight, 10));
		return viewerColumn;
	}

	private static String getRepositoryName(Repository repository) {
		String repoName = Activator.getDefault().getRepositoryUtil()
				.getRepositoryName(repository);
		RepositoryState state = repository.getRepositoryState();
		if (state != RepositoryState.SAFE)
			return repoName + '|' + state.getDescription();
		else
			return repoName;
	}

}
