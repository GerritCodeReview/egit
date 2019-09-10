/*******************************************************************************
 * Copyright (C) 2019, Tim Neumann <Tim.Neumann@advantest.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.egit.ui.internal.history;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.tools.ant.types.selectors.TokenizedPath;
import org.apache.tools.ant.types.selectors.TokenizedPattern;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIPreferences;
import org.eclipse.jface.preference.IPersistentPreferenceStore;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;

/**
 * A helper class for ref filters.
 */
public class RefFilterHelper {

	private static final String REF_SEPERATOR = ":"; //$NON-NLS-1$

	private static final String MACRO_CURRENT_BRANCH = "[CURRENT-BRANCH]"; //$NON-NLS-1$

	private final IPersistentPreferenceStore store = (IPersistentPreferenceStore) Activator
			.getDefault().getPreferenceStore();

	@NonNull
	private final Repository repository;

	private List<RefFilter> preconfiguredFilters;

	private List<RefFilter> filtersForHEAD;
	private List<RefFilter> filtersForCurrentBranch;
	private List<RefFilter> filtersForAllBranchesAndTags;

	private Map<String, Function<Repository, String>> macros;

	/**
	 * Create a new RefFilterHelper for the given repository.
	 *
	 * @param repository
	 *            The repository to create the helper for; must not be null
	 */
	public RefFilterHelper(@NonNull Repository repository) {
		this.repository = repository;
		setupPreconfigueredFilters();
		setupMacros();
	}

	private RefFilter newPreConfFilter(String filter) {
		return new RefFilter(filter, true);
	}

	private RefFilter newPreConfPrefixFilter(String prefix) {
		return newPreConfFilter(prefix + "**"); //$NON-NLS-1$
	}

	private void setupPreconfigueredFilters() {
		preconfiguredFilters = new ArrayList<>();
		filtersForHEAD = new ArrayList<>();
		filtersForCurrentBranch = new ArrayList<>();
		filtersForAllBranchesAndTags = new ArrayList<>();

		RefFilter head = newPreConfFilter(Constants.HEAD);
		preconfiguredFilters.add(head);
		filtersForHEAD.add(head);
		filtersForAllBranchesAndTags.add(head);

		RefFilter current_branch = newPreConfFilter(
				Constants.R_REFS + "**/" + MACRO_CURRENT_BRANCH); //$NON-NLS-1$
		preconfiguredFilters.add(current_branch);
		filtersForCurrentBranch.add(current_branch);

		RefFilter branches = newPreConfPrefixFilter(Constants.R_HEADS);
		preconfiguredFilters.add(branches);
		filtersForAllBranchesAndTags.add(branches);

		RefFilter remoteBranches = newPreConfPrefixFilter(Constants.R_REMOTES);
		preconfiguredFilters.add(remoteBranches);
		filtersForAllBranchesAndTags.add(remoteBranches);

		RefFilter tags = newPreConfPrefixFilter(Constants.R_TAGS);
		preconfiguredFilters.add(tags);
		filtersForAllBranchesAndTags.add(tags);
	}

	private void setupMacros() {
		macros = new HashMap<>();
		macros.put(MACRO_CURRENT_BRANCH, repo -> {
			try {
				return repo.getBranch();
			} catch (IOException e) {
				Activator.handleError(e.getMessage(), e, false);
			}
			return ""; //$NON-NLS-1$
		});
	}

	/**
	 * Get a preference string from the preference store. This should return the
	 * repository specific preference string if applicable.
	 *
	 * @param preferenceName
	 *            the name of the preference
	 * @return the string-valued preference
	 */
	protected String getPreferenceString(String preferenceName) {
		String repoSepcificPrefName = Activator.getDefault().getRepositoryUtil()
				.getRepositorySpecificPreferenceKey(this.repository,
						preferenceName);

		// Init from default if not existing
		if (!store.contains(repoSepcificPrefName)) {
			store.setValue(repoSepcificPrefName,
					store.getDefaultString(preferenceName));
		}

		return store.getString(repoSepcificPrefName);
	}

	private List<String> getFiltersFromPref(String preferenceName) {
		String refFiltersString = getPreferenceString(preferenceName);
		String[] filters = refFiltersString.split(REF_SEPERATOR);

		return Arrays.asList(filters);
	}

	private void savePreferencStoreIfNeeded() {
		if (store.needsSaving()) {
			try {
				store.save();
			} catch (IOException e) {
				Activator.handleError(e.getMessage(), e, false);
			}
		}
	}

	private void setFiltersInPref(String preferenceName, List<String> filters,
			boolean save) {
		String repoSepcificPrefName = Activator.getDefault().getRepositoryUtil()
				.getRepositorySpecificPreferenceKey(this.repository,
						preferenceName);
		String refFiltersString = String.join(REF_SEPERATOR, filters);
		store.setValue(repoSepcificPrefName, refFiltersString);
		if (save) {
			savePreferencStoreIfNeeded();
		}
	}

	/**
	 * Get the configured ref filters from the preference store.
	 *
	 * @return A list of all configuered ref filter strings
	 */
	public List<String> getConfiguredFilters() {
		return getFiltersFromPref(UIPreferences.RESOURCEHISTORY_REF_FILTERS);
	}

	/**
	 * Set the configured ref filters in the preference store.
	 *
	 * @param filters
	 *            The list of configuered ref filter strings to set
	 * @param save
	 *            Whether to save the preference store if necessary
	 */
	public void setConfiguredFilters(List<String> filters, boolean save) {
		setFiltersInPref(UIPreferences.RESOURCEHISTORY_REF_FILTERS, filters,
				save);
	}

	/**
	 * Get the selected ref filters from the preference store.
	 *
	 * @return A list of all selected ref filter strings
	 */
	public List<String> getSelectedFilters() {
		return getFiltersFromPref(
				UIPreferences.RESOURCEHISTORY_SELECTED_REF_FILTERS);
	}

	/**
	 * Set the selected ref filters in the preference store.
	 *
	 * @param filters
	 *            The list of selected ref filter strings to set
	 * @param save
	 *            Whether to save the preference store if necessary
	 */
	public void setSelectedFilters(List<String> filters, boolean save) {
		setFiltersInPref(UIPreferences.RESOURCEHISTORY_SELECTED_REF_FILTERS,
				filters, save);
	}

	/**
	 * Get the last selected ref filters from the preference store.
	 *
	 * @return A list of the last selected ref filter strings
	 */
	public List<String> getLastSelectedFilters() {
		return getFiltersFromPref(
				UIPreferences.RESOURCEHISTORY_LAST_SELECTED_REF_FILTERS);
	}

	/**
	 * Set the last selected ref filters in the preference store.
	 *
	 * @param filters
	 *            The list of last selected ref filter strings to set
	 * @param save
	 *            Whether to save the preference store if necessary
	 */
	public void setLastSelectedFilters(List<String> filters, boolean save) {
		setFiltersInPref(
				UIPreferences.RESOURCEHISTORY_LAST_SELECTED_REF_FILTERS,
				filters, save);
	}

	private void addPreconfigueredFilters(Map<String, RefFilter> filters) {
		for (RefFilter filter : preconfiguredFilters) {
			filters.put(filter.getFilterString(), filter);
		}
	}

	/**
	 * @return the set of all ref filters
	 */
	public Set<RefFilter> getRefFilters() {
		Map<String, RefFilter> filters = new HashMap<>();
		addPreconfigueredFilters(filters);

		for (String filter : getConfiguredFilters()) {
			if (filter == null || filter.isEmpty())
				continue;
			filters.put(filter, new RefFilter(filter, false));
		}

		for (String filter : getSelectedFilters()) {
			if (filter == null || filter.isEmpty())
				continue;
			// A user could change the pref files manually
			// Therefore we need to make sure all selected filters are also
			// available.
			// So we add them to the set if they are not already there
			filters.putIfAbsent(filter, new RefFilter(filter, false));
			filters.get(filter).setSelected(true);
		}
		return new HashSet<>(filters.values());
	}

	/**
	 * Restore the last selection state.
	 * @param filters The set of filters to restore the state for.
	 */
	public void restoreLastSelectionState(Set<RefFilter> filters) {
		for(RefFilter filter : filters) {
			filter.setSelected(getLastSelectedFilters()
					.contains(filter.getFilterString()));
		}
	}

	/**
	 * Set's the given rev filters in the preference store.
	 * <p>
	 * This overrides the selected and the configuered filters in the preference
	 * store.
	 * <p>
	 *
	 * @param filters
	 *            The set of filters to save.
	 */
	public void setRefFilters(Set<RefFilter> filters) {
		List<String> selected = filters.stream().filter(RefFilter::isSelected)
				.map(RefFilter::getFilterString).collect(Collectors.toList());
		setSelectedFilters(selected, false);

		List<String> configured = filters.stream()
				.filter(f -> !f.isPreconfigured())
				.map(RefFilter::getFilterString).collect(Collectors.toList());
		setConfiguredFilters(configured, false);

		savePreferencStoreIfNeeded();
	}

	/**
	 * Save the selection state of the given filter set as the last selection
	 * state.
	 *
	 * @param filters
	 *            The filters to get the selection state from
	 */
	public void saveSelectionStateAsLastSelectionState(Set<RefFilter> filters) {
		List<String> selected = new ArrayList<>();
		for(RefFilter filter : filters) {
			if (filter.isSelected()) {
				selected.add(filter.getFilterString());
			}
		}
		setLastSelectedFilters(selected, true);
	}

	/**
	 * Get all matching refs in the given repository for the currently selected
	 * ref filters.
	 *
	 * @return All matching refs from the repo
	 * @throws IOException
	 *             the reference space cannot be accessed.
	 */
	public Set<Ref> getMatchingRefsForSelectedRefFilters()
			throws IOException {
		RefDatabase db = this.repository.getRefDatabase();
		Set<Ref> result = new HashSet<>();
		Set<RefFilter> selectedFilters = getRefFilters().stream()
				.filter(f -> f.isSelected()).collect(Collectors.toSet());

		db.getAdditionalRefs();

		for (Ref ref : db.getRefs()) {
			TokenizedPath refPath = new TokenizedPath(ref.getName());
			for (RefFilter filter : selectedFilters) {
				if (filter.matches(refPath)) {
					result.add(ref);
					break;
				}
			}
		}

		return result;
	}

	/**
	 * Select only the HEAD preconfigured ref filter.
	 * <p>
	 * This will modify objects in the given list.
	 * </p>
	 *
	 * @param filters
	 *            The filters to change the selection of
	 */
	public void selectOnlyHEAD(Set<RefFilter> filters) {
		for (RefFilter filter : filters) {
			filter.setSelected(filtersForHEAD.contains(filter));
		}
	}

	/**
	 * Check whether only the HEAD preconfigured ref filter is selected.
	 *
	 * @param filters
	 *            The filters to check
	 * @return Whether exactly HEAD is selected
	 */
	public boolean isOnlyHEADSelected(Set<RefFilter> filters) {
		for (RefFilter filter : filters) {
			if (filter.isSelected()) {
				if (!filtersForHEAD.contains(filter))
					return false;
			} else {
				if (filtersForHEAD.contains(filter))
					return false;
			}
		}
		return true;
	}

	/**
	 * Select only the preconfigured ref filter for the current branch (local
	 * and remote).
	 * <p>
	 * This will modify objects in the given list.
	 * </p>
	 *
	 * @param filters
	 *            The filters to change the selection of
	 */
	public void selectOnlyCurrentBranch(Set<RefFilter> filters) {
		for (RefFilter filter : filters) {
			filter.setSelected(filtersForCurrentBranch.contains(filter));
		}
	}

	/**
	 * Check whether only the preconfigured ref filter for the current branch
	 * (local and remote) is selected.
	 *
	 * @param filters
	 *            The filters to check
	 * @return Whether exactly the current branch is selected
	 */
	public boolean isOnlyCurrentBranchSelected(Set<RefFilter> filters) {
		for (RefFilter filter : filters) {
			if (filter.isSelected()) {
				if (!filtersForCurrentBranch.contains(filter))
					return false;
			} else {
				if (filtersForCurrentBranch.contains(filter))
					return false;
			}
		}
		return true;
	}

	/**
	 * Select exactly the preconfigured ref filters, which represent all
	 * branches and tags.
	 * <p>
	 * This will modify objects in the given list.
	 * </p>
	 *
	 * @param filters
	 *            The filters to change the selection of.
	 */
	public void selectExactlyAllBranchesAndTags(Set<RefFilter> filters) {
		for (RefFilter filter : filters) {
			filter.setSelected(filtersForAllBranchesAndTags.contains(filter));
		}
	}

	/**
	 * Check whether exactly the preconfigured ref filters which represent all
	 * branches and tags.
	 *
	 * @param filters
	 *            The filters to check
	 * @return Whether exactly all branches and tags are selected
	 */
	public boolean isExactlyAllBranchesAndTagsSelected(Set<RefFilter> filters) {
		for (RefFilter filter : filters) {
			if (filter.isSelected()) {
				if (!filtersForAllBranchesAndTags.contains(filter))
					return false;
			} else {
				if (filtersForAllBranchesAndTags.contains(filter))
					return false;
			}
		}
		return true;
	}

	/**
	 * Get the default ref filters
	 *
	 * @return a set of the default ref filters.
	 */
	public Set<RefFilter> getDefaults() {
		RefFilterHelper defaultsHelper = new RefFilterHelper(this.repository) {
			@Override
			protected String getPreferenceString(String preferenceName) {
				return store.getDefaultString(preferenceName);
			}
		};
		return defaultsHelper.getRefFilters();
	}

	/**
	 * Representation of a ref filter
	 */
	public class RefFilter {
		private final boolean preconfigured;

		private String filterString;
		private TokenizedPattern filterPattern;

		private boolean selected = false;

		/**
		 * Create a new ref filter
		 *
		 * @param filterString
		 *            The filter string for the new ref filter; must not be
		 *            null; must not be empty.
		 *
		 * @throws IllegalArgumentException
		 *             if the filter string is null or empty
		 */
		public RefFilter(String filterString) {
			this(filterString, false);
		}

		/**
		 * Create a new ref filter
		 *
		 * @param filterString
		 *            The filter string for the new ref filter; must not be
		 *            null; must not be empty.
		 * @param isPreconfigured
		 *            Whether the new Filter is a preconfiguered one
		 *
		 * @throws IllegalArgumentException
		 *             if the filter string is null or empty
		 */
		private RefFilter(String filterString, boolean isPreconfigured) {
			if (filterString == null || filterString.isEmpty())
				throw new IllegalArgumentException(
						"Filter string is null or empty."); //$NON-NLS-1$
			this.filterString = filterString;
			this.filterPattern = new TokenizedPattern(filterString);
			this.preconfigured = isPreconfigured;
		}

		/**
		 * @return whether this is a preconfigured filter
		 */
		public boolean isPreconfigured() {
			return preconfigured;
		}

		private TokenizedPattern patternWithExpandedMacros() {
			TokenizedPattern currentPattern = filterPattern;
			for(Map.Entry<String, Function<Repository, String>> macro : macros.entrySet()) {
				if (currentPattern.containsPattern(macro.getKey())) {
					String oldString = currentPattern.getPattern();
					String macroString = macro.getKey();
					String replacingString = macro.getValue().apply(repository);
					String newString = oldString.replace(macroString,
							replacingString);
					currentPattern = new TokenizedPattern(newString);
				}
			}
			return currentPattern;
		}

		/**
		 * Tries to match the given ref against this filter.
		 *
		 * @param refPath
		 *            The path of the ref to match
		 * @return true if the ref path matches the pattern of this filter
		 */
		public boolean matches(TokenizedPath refPath) {
			return patternWithExpandedMacros().matchPath(refPath,
					true);
		}

		/**
		 * @return the filter string; cannot be null; cannot be empty
		 */
		public String getFilterString() {
			return this.filterString;
		}

		/**
		 * @param filterString
		 *            the filterString to set; must not be null; must not be
		 *            empty
		 * @throws IllegalArgumentException
		 *             if the filter string is null or empty
		 * @throws IllegalStateException
		 *             if this is a preconfigured filter
		 */
		public void setFilterString(String filterString) {
			if (filterString == null || filterString.isEmpty())
				throw new IllegalArgumentException(
						"Filter string is null or empty."); //$NON-NLS-1$
			if (preconfigured)
				throw new IllegalStateException(
						"Cannot change a preconfigured filter."); //$NON-NLS-1$
			this.filterString = filterString;
			this.filterPattern = new TokenizedPattern(filterString);
		}

		/**
		 * @return whether this filter is currently selected
		 */
		public boolean isSelected() {
			return selected;
		}

		/**
		 * @param selected
		 *            whether this filter is selected
		 */
		public void setSelected(boolean selected) {
			this.selected = selected;
		}

		@Override
		public int hashCode() {
			return filterPattern.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof RefFilter))
				return false;
			return filterPattern.equals(((RefFilter) obj).filterPattern);
		}
	}
}
