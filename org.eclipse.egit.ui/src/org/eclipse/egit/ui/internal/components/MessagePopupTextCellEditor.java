/*******************************************************************************
 * Copyright (c) 2019 Thomas Wolf <thomas.wolf@paranor.ch>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.egit.ui.internal.components;

import org.eclipse.egit.core.internal.Utils;
import org.eclipse.egit.ui.Activator;
import org.eclipse.jface.viewers.ICellEditorListener;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.window.DefaultToolTip;
import org.eclipse.jface.window.ToolTip;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

/**
 * A {@link TextCellEditor} that automatically shows any message from an
 * {@link org.eclipse.jface.viewers.ICellEditorValidator ICellEditorValidator}
 * in a {@link DefaultToolTip} pop-up. The tooltip by default has a delay of
 * 200ms and a light red background. The tooltip can be obtained via
 * {@link #getToolTip()} and then customized, if needed.
 * <p>
 * Additionally, this editor <em>cancels</em> the edit if focus is lost.
 * </p>
 */
public class MessagePopupTextCellEditor extends TextCellEditor {

	/** Default pop-up delay in milliseconds. */
	private static final int DEFAULT_DELAY_MILLIS = 200;

	/**
	 * Default background color. See css/egit.css for the use of this pinkish
	 * color.
	 */
	private static final RGB DEFAULT_BACKGROUND = new RGB(0xFF, 0x96, 0x96);

	private DefaultToolTip errorMessage;

	/**
	 * Creates a new {@link MessagePopupTextCellEditor} parented under the given
	 * control.
	 *
	 * @param parent
	 *            the parent control
	 * @see TextCellEditor#TextCellEditor(Composite)
	 */
	public MessagePopupTextCellEditor(Composite parent) {
		super(parent);
	}

	/**
	 * Creates a new {@link MessagePopupTextCellEditor} parented under the given
	 * control using the given style.
	 *
	 * @param parent
	 *            the parent control
	 * @param style
	 *            the style bits
	 * @see TextCellEditor#TextCellEditor(Composite, int)
	 */
	public MessagePopupTextCellEditor(Composite parent, int style) {
		super(parent, style);
	}

	/**
	 * This cell editor uses the built-in focus listener provided by the super
	 * class.
	 */
	@Override
	protected boolean dependsOnExternalFocusListener() {
		return false;
	}

	/**
	 * Invoked when the cell editor has lost the focus; cancels the editor
	 * without applying the value.
	 */
	@Override
	protected void focusLost() {
		// The super implementation applies the value, but that's a bit
		// risky. Edits should be done only if the user explicitly hit
		// <return>.
		if (isActivated()) {
			fireCancelEditor();
		}
	}

	@Override
	protected Control createControl(Composite parent) {
		Control control = super.createControl(parent);
		errorMessage = new DefaultToolTip(control, ToolTip.NO_RECREATE, true);
		// A delay enables us to cancel showing the tooltip if the user keeps
		// typing and the value is valid again.
		errorMessage.setPopupDelay(DEFAULT_DELAY_MILLIS);
		errorMessage.setBackgroundColor(Activator.getDefault()
				.getResourceManager().createColor(DEFAULT_BACKGROUND));
		control.addDisposeListener(event -> errorMessage.hide());
		addListener(new ICellEditorListener() {

			@Override
			public void editorValueChanged(boolean oldValidState,
					boolean newValidState) {
				if (newValidState) {
					errorMessage.hide();
					return;
				}
				Control editor = getControl();
				Point pos = editor.getSize();
				errorMessage.setText(getErrorMessage());
				pos.x = 0;
				errorMessage.show(pos);
			}

			@Override
			public void cancelEditor() {
				errorMessage.hide();
			}

			@Override
			public void applyEditorValue() {
				errorMessage.hide();
			}
		});
		// Since "text" in the super class is protected, we may rely on (a)
		// super.createControl() having created and returned a Text, and (b) it
		// having actually set "text", and (c) control == text.
		if ((text.getStyle() & SWT.SINGLE) != 0) {
			// Prevent pasting multi-line text into a single-line control. See
			// bug 273470.
			text.addVerifyListener(
					event -> event.text = Utils.firstLine(event.text));
		}
		return control;
	}

	/**
	 * Retrieves the {@link DefaultToolTip} use for the validation message
	 * pop-up.
	 *
	 * @return the tooltip
	 */
	public DefaultToolTip getToolTip() {
		return errorMessage;
	}
}
