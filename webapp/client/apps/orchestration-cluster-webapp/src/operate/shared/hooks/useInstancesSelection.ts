/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useState} from 'react';

type Mode = 'INCLUDE' | 'EXCLUDE' | 'ALL';

type SelectionState = {
	selectedIds: string[];
	mode: Mode;
};

const DEFAULT_STATE: SelectionState = {selectedIds: [], mode: 'INCLUDE'};

/**
 * Tri-state row-selection: select individual rows (INCLUDE), select all and then exclude a
 * few (EXCLUDE), or select every row matching the current filter, including rows not yet
 * loaded (ALL). Ported from the legacy `InstancesSelection` MobX store — the tri-state
 * algorithm is generic application logic, not MobX-specific, so it is preserved 1:1 as a hook.
 */
function useInstancesSelection(totalCount: number) {
	const [state, setState] = useState<SelectionState>(DEFAULT_STATE);
	const {selectedIds, mode} = state;

	const reset = useCallback(() => setState(DEFAULT_STATE), []);

	const selectAll = useCallback(() => {
		setState((current) =>
			current.mode === 'INCLUDE' && current.selectedIds.length === 0
				? {selectedIds: [], mode: 'ALL'}
				: {selectedIds: [], mode: 'INCLUDE'},
		);
	}, []);

	// Normalizing to ALL happens inline here (rather than in a reactive effect keyed on
	// selectedIds/mode) once every row ends up individually checked or excluded down to zero.
	const select = useCallback(
		(id: string) => {
			setState((current) => {
				let mode = current.mode;
				let selectedIds = current.selectedIds;

				if (mode === 'ALL') {
					mode = 'EXCLUDE';
				}

				if (selectedIds.includes(id)) {
					selectedIds = selectedIds.filter((selectedId) => selectedId !== id);
					if (mode === 'EXCLUDE' && selectedIds.length === 0) {
						return {selectedIds: [], mode: 'ALL'};
					}
				} else {
					selectedIds = [...selectedIds, id];
					if (mode === 'INCLUDE' && selectedIds.length === totalCount && totalCount !== 0) {
						return {selectedIds: [], mode: 'ALL'};
					}
				}

				return {selectedIds, mode};
			});
		},
		[totalCount],
	);

	const isAllSelected = mode === 'ALL';
	const selectedCount =
		mode === 'INCLUDE' ? selectedIds.length : mode === 'EXCLUDE' ? totalCount - selectedIds.length : totalCount;
	const isIndeterminate = !isAllSelected && selectedCount > 0;

	const isRowSelected = useCallback(
		(id: string) => {
			switch (mode) {
				case 'INCLUDE':
					return selectedIds.includes(id);
				case 'EXCLUDE':
					return !selectedIds.includes(id);
				default:
					return true;
			}
		},
		[mode, selectedIds],
	);

	const includedIds = mode === 'INCLUDE' ? selectedIds : [];
	const excludedIds = mode === 'EXCLUDE' ? selectedIds : [];

	return {
		selectedCount,
		isAllSelected,
		isIndeterminate,
		isRowSelected,
		selectAll,
		select,
		reset,
		includedIds,
		excludedIds,
	};
}

export {useInstancesSelection};
