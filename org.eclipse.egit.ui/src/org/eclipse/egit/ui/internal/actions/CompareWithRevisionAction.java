/*******************************************************************************
 * Copyright (C) 2007, David Watson <dwatson@mimvista.com>
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2007, Shawn O. Pearce <spearce@spearce.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.team.internal.ui.actions.TeamAction;
import org.eclipse.team.ui.TeamUI;

/**
 *	Compare the resources filtered in the history view with the current
 *	revision.
 */
public class CompareWithRevisionAction extends TeamAction {

	// There are changes in Eclipse 3.3 requiring that execute be implemented
	// for it to compile. while 3.2 requires that run is implemented instead.
	/** See {@link #run}
	 * @param action
	 */
	public void execute(IAction action) {
		run(action);
	}

	@Override
	public void run(IAction action) {
		TeamUI.getHistoryView().showHistoryFor(getSelectedResources()[0]);
	}

	public boolean isEnabled() {
		return !getSelection().isEmpty();
	}
}
