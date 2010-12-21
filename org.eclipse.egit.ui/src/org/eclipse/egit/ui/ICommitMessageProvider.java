/*******************************************************************************
 * Copyright (C) 2010, Eclipse Egit Team <
 *
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui;

import org.eclipse.egit.ui.internal.dialogs.CommitDialog;

/**
 * This interface must be implemented to be a commit message provider.
 * A commit message provider provides the complete or a fragment of a
 * commit message. This message will be added to the text field in the
 * {@link CommitDialog}.
 * <br/>
 * Primarily use is the integration of mylyns commit templates.
 *
 * @see CommitDialog
 *
 * @author Thorsten Kamann <thorsten@itemis.de>
 * @since 0.10
 */
public interface ICommitMessageProvider {

	/**
	 * @return the message the CommitDialogs should use as default message
	 */
	public String getMessage();
}
