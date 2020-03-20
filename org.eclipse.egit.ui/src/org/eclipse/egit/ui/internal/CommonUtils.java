/*******************************************************************************
 * Copyright (C) 2011, Dariusz Luksza <dariusz@luksza.org>
 * Copyright (C) 2011, 2013 Robin Stocker <robin@nibor.org>
 * Copyright (C) 2011, Bernard Leach <leachbj@bouncycastle.org>
 * Copyright (C) 2013, Michael Keppler <michael.keppler@gmx.de>
 * Copyright (C) 2014, IBM Corporation (Markus Keller <markus_keller@ch.ibm.com>)
 * Copyright (C) 2015, IBM Corporation (Dani Megert <daniel_megert@ch.ibm.com>)
 * Copyright (C) 2015, Thomas Wolf <thomas.wolf@paranor.ch>
 * Copyright (C) 2016, Stefan Dirix <sdirix@eclipsesource.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.egit.ui.internal;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.core.commands.State;
import org.eclipse.core.commands.common.CommandException;
import org.eclipse.core.expressions.EvaluationContext;
import org.eclipse.core.resources.IResource;
import org.eclipse.egit.ui.Activator;
import org.eclipse.jface.util.Policy;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.ISources;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.handlers.RegistryToggleState;
import org.eclipse.ui.services.IServiceLocator;

/**
 * Class containing all common utils
 */
public class CommonUtils {

	/**
	 * Pattern to figure out where the footer lines in a commit are.
	 *
	 * @see org.eclipse.jgit.revwalk.RevCommit#getFooterLines()
	 */
	private static final Pattern FOOTER_PATTERN = Pattern
			.compile("(?:\n(?:[A-Za-z0-9-]+:[^\n]*))+\\s*$"); //$NON-NLS-1$

	/**
	 * The toggle tag sorting command id.
	 */
	public static String TOGGLE_TAG_SORTING_COMMAND_ID = "org.eclipse.egit.ui.RepositoriesToggleTagSorting";//$NON-NLS-1$

	/**
	 * Minimum inset to draw table column text without shortening on Windows.
	 * The code of {@link Table} suggests 4, but that lead to shortening of
	 * text.
	 */
	private static final int TABLE_INSET = 5;

	private static volatile Boolean sortTagsAscending;

	private CommonUtils() {
		// non-instantiable utility class
	}

	/**
	 * Instance of comparator that sorts strings in ascending alphabetical and
	 * numerous order (also known as natural order), case insensitive.
	 *
	 * The comparator is guaranteed to return a non-zero value if
	 * {@code string1.equals(string2)} returns {@code false}.
	 */
	public static final Comparator<String> STRING_ASCENDING_COMPARATOR = new Comparator<String>() {
		@Override
		public int compare(String o1, String o2) {
			if (o1.length() == 0 || o2.length() == 0)
				return o1.length() - o2.length();

			LinkedList<String> o1Parts = splitIntoDigitAndNonDigitParts(o1);
			LinkedList<String> o2Parts = splitIntoDigitAndNonDigitParts(o2);

			Iterator<String> o2PartsIterator = o2Parts.iterator();

			for (String o1Part : o1Parts) {
				if (!o2PartsIterator.hasNext())
					return 1;

				String o2Part = o2PartsIterator.next();

				int result;

				if (Character.isDigit(o1Part.charAt(0))
						&& Character.isDigit(o2Part.charAt(0))) {
					o1Part = stripLeadingZeros(o1Part);
					o2Part = stripLeadingZeros(o2Part);
					result = o1Part.length() - o2Part.length();
					if (result == 0)
						result = o1Part.compareToIgnoreCase(o2Part);
				} else {
					result = o1Part.compareToIgnoreCase(o2Part);
				}

				if (result != 0)
					return result;
			}

			if (o2PartsIterator.hasNext())
				return -1;
			else {
				// strings are equal (in the Object.equals() sense)
				// or only differ in case and/or leading zeros
				return o1.compareTo(o2);
			}
		}

		private LinkedList<String> splitIntoDigitAndNonDigitParts(
				String input) {
			LinkedList<String> parts = new LinkedList<>();
			int partStart = 0;
			boolean previousWasDigit = Character.isDigit(input.charAt(0));
			for (int i = 1; i < input.length(); i++) {
				boolean isDigit = Character.isDigit(input.charAt(i));
				if (isDigit != previousWasDigit) {
					parts.add(input.substring(partStart, i));
					partStart = i;
					previousWasDigit = isDigit;
				}
			}
			parts.add(input.substring(partStart));
			return parts;
		}

		private String stripLeadingZeros(String input) {
			for (int i = 0; i < input.length(); i++) {
				if (input.charAt(i) != '0') {
					return input.substring(i);
				}
			}
			return ""; //$NON-NLS-1$
		}
	};

	/**
	 * Instance of comparator which sorts {@link Ref} names using
	 * {@link CommonUtils#STRING_ASCENDING_COMPARATOR}.
	 */
	public static final Comparator<Ref> REF_ASCENDING_COMPARATOR = new Comparator<Ref>() {
		@Override
		public int compare(Ref o1, Ref o2) {
			String name1 = o1.getName();
			String name2 = o2.getName();
			if (name1.startsWith(Constants.R_TAGS)
					&& name2.startsWith(Constants.R_TAGS)
					&& !isSortTagsAscending()) {
				name1 = name2;
				name2 = o1.getName();
			}
			return STRING_ASCENDING_COMPARATOR.compare(name1, name2);
		}
	};

	private static boolean isSortTagsAscending() {
		if (sortTagsAscending == null) {
			ICommandService srv = CommonUtils.getService(
					PlatformUI.getWorkbench(), ICommandService.class);
			State currentToggleState = srv
					.getCommand(TOGGLE_TAG_SORTING_COMMAND_ID)
					.getState(RegistryToggleState.STATE_ID);
			if (currentToggleState != null) {
				Object value = currentToggleState.getValue();
				if (value instanceof Boolean) {
					sortTagsAscending = ((Boolean) value);
				}
			}
			if (sortTagsAscending == null) {
				Activator.logError("Using default sort order", //$NON-NLS-1$
						new IllegalStateException(
								"Could not determine current tag sorting toggle state"));//$NON-NLS-1$
				sortTagsAscending = Boolean.FALSE;
			}
		}
		return sortTagsAscending.booleanValue();
	}

	/**
	 * Force re-evaluation of the sort direction toggle state when next sorting
	 * tags.
	 */
	public static void unsetTagSortingOrder() {
		sortTagsAscending = null;
	}

	/**
	 * Instance of comparator for tag names using
	 * {@link CommonUtils#STRING_ASCENDING_COMPARATOR}. The sort order depends
	 * on the state of the tag sorting toggle.
	 */
	public static final Comparator<String> TAG_STRING_COMPARATOR = new Comparator<String>() {
		@Override
		public int compare(String o1, String o2) {
			if (isSortTagsAscending()) {
				return STRING_ASCENDING_COMPARATOR.compare(o1, o2);
			} else {
				return STRING_ASCENDING_COMPARATOR.compare(o2, o1);
			}
		}
	};

	/**
	 * Comparator for comparing {@link IResource} by the result of
	 * {@link IResource#getName()}.
	 */
	public static final Comparator<IResource> RESOURCE_NAME_COMPARATOR = //
			(a, b) -> Policy.getComparator().compare(a.getName(), b.getName());

	/**
	 * Comparator for comparing (@link Path} by the result of
	 * {@link Path#toAbsolutePath()}
	 */
	public static final Comparator<Path> PATH_STRING_COMPARATOR = Comparator
			.comparing(p -> p.toAbsolutePath().toString(),
					STRING_ASCENDING_COMPARATOR);

	/**
	 * Programmatically run command based on its id and given selection
	 *
	 * @param commandId
	 *            id of command that should be run
	 * @param selection
	 *            given selection
	 * @return {@code true} when command was successfully executed,
	 *         {@code false} otherwise
	 */
	public static boolean runCommand(String commandId,
			IStructuredSelection selection) {
		ICommandService commandService = CommonUtils
				.getService(PlatformUI.getWorkbench(), ICommandService.class);
		Command cmd = commandService.getCommand(commandId);
		if (!cmd.isDefined())
			return false;

		IHandlerService handlerService = CommonUtils
				.getService(PlatformUI.getWorkbench(), IHandlerService.class);
		EvaluationContext c = null;
		if (selection != null) {
			c = new EvaluationContext(
					handlerService.createContextSnapshot(false),
					selection.toList());
			c.addVariable(ISources.ACTIVE_CURRENT_SELECTION_NAME, selection);
			c.removeVariable(ISources.ACTIVE_MENU_SELECTION_NAME);
		}
		try {
			if (c != null)
				handlerService.executeCommandInContext(
						new ParameterizedCommand(cmd, null), null, c);
			else
				handlerService.executeCommand(commandId, null);

			return true;
		} catch (CommandException ignored) {
			// Ignored
		}
		return false;
	}

	/**
	 * Retrieves the service corresponding to the given API.
	 * <p>
	 * Workaround for "Unnecessary cast" errors, see bug 441615. Can be removed
	 * when EGit depends on Eclipse 4.5 or higher.
	 *
	 * @param locator
	 *            the service locator, must not be null
	 * @param api
	 *            the interface the service implements, must not be null
	 * @return the service, or null if no such service could be found
	 * @see IServiceLocator#getService(Class)
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getService(IServiceLocator locator, Class<T> api) {
		Object service = locator.getService(api);
		return (T) service;
	}

	/**
	 * Assuming that the string {@code commitMessage} is a commit message,
	 * returns the offset in the string of the footer of the commit message, if
	 * one can be found, or -1 otherwise.
	 * <p>
	 * A footer of a commit message is defined to be the non-empty lines
	 * following the last empty line in the commit message if they have the
	 * format "key: value" as defined by
	 * {@link org.eclipse.jgit.revwalk.RevCommit#getFooterLines()}, like
	 * Change-Id: I000... or Signed-off-by: ... Empty lines at the end of the
	 * commit message are ignored.
	 * </p>
	 *
	 * @param commitMessage
	 *            text of the commit message, assumed to use '\n' as line
	 *            delimiter
	 * @return the index of the beginning of the footer, if any, or -1
	 *         otherwise.
	 */
	public static int getFooterOffset(String commitMessage) {
		if (commitMessage == null) {
			return -1;
		}
		Matcher matcher = FOOTER_PATTERN.matcher(commitMessage);
		if (matcher.find()) {
			int start = matcher.start();
			// Check that the line that ends at start is empty.
			int i = start - 1;
			while (i >= 0) {
				char ch = commitMessage.charAt(i--);
				if (ch == '\n') {
					return start + 1;
				} else if (!Character.isWhitespace(ch)) {
					return -1;
				}
			}
			// No \n but only whitespace: first line is empty
			return start + 1;
		}
		return -1;
	}

	/**
	 * Creates a comma separated list of all non-null resource names. The last
	 * element is separated with an ampersand.
	 *
	 * @param resources
	 *            the collection of {@link IResource}s.
	 * @return A comma separated list of the resource names. The last element is
	 *         separated with an ampersand.
	 */
	public static String getResourceNames(Iterable<IResource> resources) {
		final List<String> names = new LinkedList<>();
		for (IResource resource : resources) {
			if (resource.getName() != null) {
				names.add(resource.getName());
			}
		}

		return StringUtils.join(names, ", ", " & "); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * @param control
	 *            SWT table
	 * @param columnHeading
	 *            column heading
	 * @return column width for a table column of the given table to fit a git
	 *         commit SHA1
	 */
	public static int getCommitIdColumnWidth(Control control,
			String columnHeading) {
		GC gc = new GC(control.getDisplay());
		try {
			gc.setFont(control.getFont());
			return Math.max(gc.stringExtent("bbbbbbb").x, //$NON-NLS-1$
					gc.stringExtent(columnHeading).x) + 2 * TABLE_INSET;
		} finally {
			gc.dispose();
		}
	}
}
