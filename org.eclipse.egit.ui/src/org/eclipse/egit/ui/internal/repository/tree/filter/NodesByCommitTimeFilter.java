/*******************************************************************************
 * Copyright (c) 2020, Alexander Nittka <alex@nittka.de>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.egit.ui.internal.repository.tree.filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.egit.ui.internal.repository.tree.RepositoryTreeNode;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Filter class holding repository tree nodes filtered by commit time. Currently
 * it supports the filter pattern #n where n is the maximal number nodes to
 * show.
 */
public class NodesByCommitTimeFilter {

	private static final Pattern FILTER_ACTIVE_PATTERN = Pattern
			.compile("#\\d*"); //$NON-NLS-1$

	private boolean active;

	private boolean filtering;

	private int maxCount = -1;

	private int thresholdTime = Integer.MIN_VALUE;

	List<TimedNode> nodes = new ArrayList<>();

	List<RepositoryTreeNode<?>> allNodes = new ArrayList<>();

	/**
	 * @param filterText
	 *            user defined filter pattern
	 */
	public NodesByCommitTimeFilter(String filterText) {
		if (filterText != null) {
			Matcher matcher = FILTER_ACTIVE_PATTERN.matcher(filterText);
			active = matcher.matches();
			if (active) {
				if (filterText.length() > 1) {
					maxCount = Integer.parseInt(filterText.substring(1));
				}
				filtering = maxCount > 0;
			}
		}
	}

	/**
	 * @param treeNode
	 *            node to be filtered
	 * @param timeCarrier
	 *            object from which the commit time associated with the node can
	 *            be extracted (currently only RevCommit)
	 */
	public void addNode(RepositoryTreeNode<?> treeNode, Object timeCarrier) {
		if (filtering) {
			int time = Integer.MIN_VALUE;
			if (timeCarrier instanceof RevCommit) {
				time = ((RevCommit) timeCarrier).getCommitTime();
			}
			if (time >= thresholdTime) {
				TimedNode node = new TimedNode(treeNode, time);
				nodes.add(node);
				int size = nodes.size();
				if (size > maxCount) {
					Collections.sort(nodes);
					nodes = nodes.subList(size - maxCount, size);
					thresholdTime = nodes.get(0).time;
				}
			}
		} else {
			allNodes.add(treeNode);
		}
	}

	/**
	 * @return filtered list of added nodes; all if the filter text did not
	 *         activate the filter
	 */
	public List<RepositoryTreeNode<?>> getFilteredNodes() {
		if (filtering) {
			return nodes.stream().map(node -> node.node)
					.collect(Collectors.toList());
		} else {
			return allNodes;
		}
	}

	/**
	 * @return is this filter activated by the filtertext
	 */
	public boolean isFilterActive() {
		return active;
	}

	private static class TimedNode implements Comparable<TimedNode> {

		RepositoryTreeNode<?> node;

		int time;

		TimedNode(RepositoryTreeNode<?> node, int time) {
			this.node = node;
			this.time = time;
		}

		@Override
		public int compareTo(TimedNode o) {
			return Integer.compare(time, o.time);
		}
	}
}