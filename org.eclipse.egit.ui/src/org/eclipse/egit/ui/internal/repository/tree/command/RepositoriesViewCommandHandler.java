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
package org.eclipse.egit.ui.internal.repository.tree.command;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.RepositoryUtil;
import org.eclipse.egit.ui.internal.repository.RepositoriesView;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.ui.ISources;

abstract class RepositoriesViewCommandHandler<T> extends AbstractHandler {

	protected final RepositoryUtil util = Activator.getDefault()
			.getRepositoryUtil();

	public RepositoriesView getView(ExecutionEvent event) {
		Object part = ((IEvaluationContext) event.getApplicationContext())
				.getRoot().getVariable(ISources.ACTIVE_PART_NAME);
		return (RepositoriesView) part;
	}

	@SuppressWarnings("unchecked")
	public List<T> getSelectedNodes(ExecutionEvent event) {
		TreeSelection selection = (TreeSelection) ((IEvaluationContext) event
				.getApplicationContext()).getRoot().getVariable(ISources.ACTIVE_CURRENT_SELECTION_NAME);
		return selection.toList();
	}
}
