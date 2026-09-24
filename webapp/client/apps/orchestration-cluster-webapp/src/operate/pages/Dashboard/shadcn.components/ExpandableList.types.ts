/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type React from 'react';

/**
 * One row of a Dashboard list tile.
 *
 * Carries both a pre-composed `content` node and the structured facts behind it
 * because the row's shape is still an open design question: some variants render
 * a single composed cell, others render `name`/`active`/`incidents` as real
 * DataTable columns. Every consumer supplies all of them so the rendering can be
 * switched without touching call sites — see `ExpandableList.variants.ts`.
 */
type ExpandableListRow = {
	id: string;
	/**
	 * Whole-row rendering, used by variants that render a single content cell.
	 * Typically a link wrapping the name plus a count visualisation.
	 */
	content: React.ReactNode;
	/** Row label on its own — a process name or an error message, usually linked. */
	name: React.ReactNode;
	/**
	 * Active instances without incidents. Absent for lists that only ever count
	 * incidents, such as IncidentsByError.
	 */
	activeCount?: number;
	/** Active instances with incidents. */
	incidentsCount: number;
};

type ExpandableListVariantProps = {
	/** Accessible name for the table, and the label for its name column. */
	header: string;
	rows: ExpandableListRow[];
	/**
	 * Ready to hand to DataTable's `expansion` prop. Returns `null` for rows with
	 * nothing to expand. The shell owns this so every variant expands identically.
	 */
	renderExpansion: (row: ExpandableListRow) => React.ReactNode;
};

type ExpandableListVariantComponent = React.FC<ExpandableListVariantProps>;

export type {ExpandableListRow, ExpandableListVariantProps, ExpandableListVariantComponent};
