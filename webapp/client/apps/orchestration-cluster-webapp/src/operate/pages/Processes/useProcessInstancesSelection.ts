/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import type {
	ProcessInstance,
	ProcessInstanceState,
	CreateCancellationBatchOperationRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {mapProcessInstancesFilter, type ProcessesSearch} from './processesFilter';
import {buildInstanceKeyCriterion} from '#/operate/shared/utils/buildInstanceKeyCriterion';
import {useInstancesSelection} from '#/operate/shared/hooks/useInstancesSelection';
import {narrowTopLevelState} from './narrowTopLevelState';

type ProcessBulkAction = 'delete' | 'cancel' | 'retry' | 'suspend' | 'resume';
const ALLOWED_BATCH_STATES: Partial<Record<ProcessBulkAction | 'modify', readonly ProcessInstanceState[]>> = {
	cancel: ['ACTIVE', 'SUSPENDED'],
	suspend: ['ACTIVE'],
};
type SelectedInstance = Pick<ProcessInstance, 'processInstanceKey' | 'state' | 'hasIncident'>;

function useProcessInstancesSelection(
	search: ProcessesSearch,
	instances: ProcessInstance[],
	totalCount: number,
	hasMoreTotalItems: boolean,
) {
	const filter = mapProcessInstancesFilter(search);
	const filterIdentity = JSON.stringify(filter ?? null);
	const [identity, setIdentity] = useState(filterIdentity);
	const [retained, setRetained] = useState<SelectedInstance[]>([]);
	const selection = useInstancesSelection(totalCount);
	const mode = selection.isAllSelected ? 'ALL' : selection.excludedIds.length > 0 ? 'EXCLUDE' : 'INCLUDE';
	const selected =
		mode === 'INCLUDE'
			? selection.includedIds
					.map(
						(key) =>
							instances.find(({processInstanceKey}) => key === processInstanceKey) ??
							retained.find(({processInstanceKey}) => key === processInstanceKey),
					)
					.filter((instance): instance is SelectedInstance => instance !== undefined)
			: instances.filter(({processInstanceKey}) => selection.isRowSelected(processInstanceKey));
	const nextRetained =
		mode === 'INCLUDE'
			? selected.map(({processInstanceKey, state, hasIncident}) => ({processInstanceKey, state, hasIncident}))
			: [];
	// Guarded same-component updates reset selection before children commit.
	// An effect would briefly expose stale selection after a filter change.
	if (identity !== filterIdentity) {
		setIdentity(filterIdentity);
		selection.reset();
		setRetained([]);
	} else if (JSON.stringify(nextRetained) !== JSON.stringify(retained)) {
		setRetained(nextRetained);
	}
	const running = selected.filter(({state}) => state === 'ACTIVE');
	const finished = selected.filter(({state}) => state === 'COMPLETED' || state === 'TERMINATED');
	const incidents = running.filter(({hasIncident}) => hasIncident);
	const suspended = selected.filter(({state}) => state === 'SUSPENDED');
	const cancelable = [...running, ...suspended];
	const hasStateFilter = search.active || search.incidents || search.completed || search.canceled || search.suspended;
	const hasRunning = mode === 'INCLUDE' ? running.length > 0 : !hasStateFilter || search.active || search.incidents;
	const eligibility = {
		delete: mode === 'INCLUDE' ? finished.length > 0 : !hasStateFilter || search.completed || search.canceled,
		cancel:
			mode === 'INCLUDE'
				? cancelable.length > 0
				: !hasStateFilter || search.active || search.incidents || search.suspended,
		retry: mode === 'INCLUDE' ? incidents.length > 0 : !hasStateFilter || search.incidents,
		// Suspend targets the same running set as Move and Migrate — legacy suspends any active instance
		// regardless of incident state, it does not require re-selecting by incident status.
		suspend: hasRunning,
		resume: mode === 'INCLUDE' ? suspended.length > 0 : !hasStateFilter || search.suspended,
		running: hasRunning,
	};

	const withSelection = (
		baseFilter: CreateCancellationBatchOperationRequestBody['filter'] | undefined,
		included: string[],
	): CreateCancellationBatchOperationRequestBody['filter'] => {
		const criterion = buildInstanceKeyCriterion(mode === 'INCLUDE' ? included : [], selection.excludedIds);
		const baseKey = baseFilter?.processInstanceKey;
		const baseCriterion = typeof baseKey === 'string' ? {$eq: baseKey} : baseKey;
		return {
			...baseFilter,
			...(criterion ? {processInstanceKey: {...baseCriterion, ...criterion}} : {}),
		};
	};

	return {
		...selection,
		mode,
		filterIdentity,
		isCountTruncated: hasMoreTotalItems && mode !== 'INCLUDE',
		isSelected: selection.isRowSelected,
		eligibility,
		runningCount: running.length,
		incidentCount: incidents.length,
		suspendedCount: suspended.length,
		toggle: selection.select,
		getRequest: (action: ProcessBulkAction | 'modify'): CreateCancellationBatchOperationRequestBody => {
			const eligible =
				action === 'delete' ? finished : action === 'resume' ? suspended : action === 'cancel' ? cancelable : running;
			const included =
				eligible.length > 0 ? eligible.map(({processInstanceKey}) => processInstanceKey) : selection.includedIds;
			const allowedStates = ALLOWED_BATCH_STATES[action];
			const baseFilter =
				action === 'modify'
					? mapProcessInstancesFilter({...search, suspended: false})
					: filter && allowedStates
						? narrowTopLevelState(filter, allowedStates)
						: filter;
			return {filter: withSelection(baseFilter, included)};
		},
		getModificationStatisticsFilter: (): CreateCancellationBatchOperationRequestBody['filter'] =>
			withSelection(filter, selection.includedIds),
	};
}

type ProcessInstancesSelection = ReturnType<typeof useProcessInstancesSelection>;

export {useProcessInstancesSelection, type ProcessInstancesSelection, type ProcessBulkAction};
