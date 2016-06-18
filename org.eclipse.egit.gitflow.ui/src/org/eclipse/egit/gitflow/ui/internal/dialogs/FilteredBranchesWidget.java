/*******************************************************************************
 * Copyright (C) 2016, Max Hohenegger <eclipse@hohenegger.eu>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.gitflow.ui.internal.dialogs;

import java.util.List;

import org.eclipse.egit.gitflow.GitFlowRepository;
import org.eclipse.egit.gitflow.ui.internal.UIText;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.TreeColumnLayout;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.dialogs.FilteredTree;
import org.eclipse.ui.dialogs.PatternFilter;

/**
 * Widget for viewing a filtered list of Gitflow branches.
 */
public class FilteredBranchesWidget {
	private TreeViewer branchesViewer;

	private final List<Ref> refs;

	private String prefix;

	private GitFlowRepository gfRepo;

	private BranchComparator comparator;

	FilteredBranchesWidget(List<Ref> refs, String prefix, GitFlowRepository gfRepo) {
		this.refs = refs;
		this.prefix = prefix;
		this.gfRepo = gfRepo;
	}

	Control create(Composite parent) {
		Composite area = new Composite(parent, SWT.NONE);
		GridDataFactory.fillDefaults().grab(true, true).span(2, 1).applyTo(area);
		area.setLayout(new GridLayout(1, false));

		PatternFilter filter = new PatternFilter() {
			@Override
			protected boolean isLeafMatch(Viewer viewer, Object element) {
				TreeViewer treeViewer = (TreeViewer) viewer;
				int numberOfColumns = treeViewer.getTree().getColumnCount();
				boolean isMatch = false;
				for (int columnIndex = 0; columnIndex < numberOfColumns; columnIndex++) {
					ColumnLabelProvider labelProvider = (ColumnLabelProvider) treeViewer
							.getLabelProvider(columnIndex);
					String labelText = labelProvider.getText(element);
					isMatch |= wordMatches(labelText);
				}
				return isMatch;
			}
		};
		filter.setIncludeLeadingWildcard(true);

		final FilteredTree tree = new FilteredTree(area, SWT.MULTI | SWT.H_SCROLL
				| SWT.V_SCROLL | SWT.BORDER | SWT.FULL_SELECTION, filter,
				true);
		tree.setQuickSelectionMode(true);
		branchesViewer = tree.getViewer();
		branchesViewer.getTree().setLinesVisible(false);
		branchesViewer.getTree().setHeaderVisible(true);

		comparator = new BranchComparator();
		branchesViewer.setComparator(comparator);

		DecoratedBranchLabelProvider nameLabelProvider = new DecoratedBranchLabelProvider(gfRepo.getRepository(), prefix);
		TreeColumn nameColumn = createColumn(
				UIText.BranchSelectionTree_NameColumn, branchesViewer,
				nameLabelProvider);
		setSortedColumn(nameColumn, nameLabelProvider);

		TreeColumn idColumn = createColumn(UIText.BranchSelectionTree_IdColumn, branchesViewer, new ColumnLabelProvider() {

			@Override
			public String getText(Object element) {
				if (element instanceof Ref) {
					ObjectId objectId = ((Ref) element).getObjectId();
					if (objectId == null) {
						return ""; //$NON-NLS-1$
					}
					return objectId.abbreviate(7).name();
				}
				return super.getText(element);
			}});
		TreeColumn msgColumn = createColumn(UIText.BranchSelectionTree_MessageColumn, branchesViewer, new ColumnLabelProvider() {

			@Override
			public String getText(Object element) {
				if (element instanceof Ref) {
					String name = ((Ref) element).getName().substring(Constants.R_HEADS.length());
					RevCommit revCommit = gfRepo.findHead(name);
					if (revCommit == null) {
						return ""; //$NON-NLS-1$
					}
					return revCommit.getShortMessage();
				}
				return super.getText(element);
			}});

		GridDataFactory.fillDefaults().grab(true, true).applyTo(branchesViewer.getControl());

		branchesViewer.setContentProvider(new BranchListContentProvider());
		branchesViewer.setInput(refs);

		// Layout tree for maximum width of message column
		TreeColumnLayout layout = new TreeColumnLayout();
		layout.setColumnData(nameColumn, new ColumnWeightData(0));
		layout.setColumnData(idColumn, new ColumnWeightData(0));
		layout.setColumnData(msgColumn, new ColumnWeightData(100));
		branchesViewer.getTree().getParent().setLayout(layout);
		nameColumn.pack();
		idColumn.pack();

		branchesViewer.addFilter(createFilter());
		return area;
	}

	private TreeColumn createColumn(final String name, TreeViewer treeViewer, final ColumnLabelProvider labelProvider) {
		final TreeColumn column = new TreeColumn(treeViewer.getTree(), SWT.LEFT);
		column.setAlignment(SWT.LEFT);
		column.setText(name);
		column.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				setSortedColumn(column, labelProvider);
			}
		});

		TreeViewerColumn treeViewerNameColumn = new TreeViewerColumn(treeViewer, column);
		treeViewerNameColumn.setLabelProvider(labelProvider);
		return column;
	}

	private void setSortedColumn(final TreeColumn column, ColumnLabelProvider labelProvider) {
		comparator.setColumn(column, labelProvider);
		int dir = comparator.getDirection();
		branchesViewer.getTree().setSortDirection(dir);
		branchesViewer.getTree().setSortColumn(column);
		branchesViewer.refresh();
	}

	private ViewerFilter createFilter() {
		return new ViewerFilter() {
			@Override
			public boolean select(Viewer viewer, Object parentElement, Object element) {
				return true;
			}
		};
	}

	@SuppressWarnings("unchecked") // conversion to conform to List<Ref>
	List<Ref> getSelection() {
		return ((IStructuredSelection) branchesViewer.getSelection()).toList();
	}

	TreeViewer getBranchesList() {
		return branchesViewer;
	}
}
