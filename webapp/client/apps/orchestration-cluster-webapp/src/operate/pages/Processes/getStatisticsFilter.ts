/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
	GetProcessDefinitionStatisticsRequestBody,
	ProcessInstanceState,
} from '@camunda/camunda-api-zod-schemas/8.10';

type StatisticsFilter = NonNullable<GetProcessDefinitionStatisticsRequestBody['filter']>;

/**
 * Mirrors legacy Operate's `parseProcessInstancesSearchFilter` state/incidents combination:
 * `incidents` is never scoped to `ACTIVE` — an element keeps an incident regardless of its
 * current state, so it must stay visible even when only non-active states are selected.
 * `suspended` is its own branch rather than a member of the state list because a suspended
 * instance must stay visible independently of the active/completed/canceled selection, and is
 * excluded from the incidents branch to avoid asserting two contradictory states for the same
 * instance. Mirrors `buildStateFilter` in processesFilter.ts, which the instances table uses.
 */
function getStateFilter(states: ProcessInstanceState[]): Pick<StatisticsFilter, 'state'> | undefined {
	if (states.length === 0) {
		return undefined;
	}

	return {state: states.length === 1 ? {$eq: states[0]!} : {$in: states}};
}

/** Returns `undefined` when no instance state is selected — the caller should skip the request entirely. */
function getStatisticsFilter({
	active,
	incidents,
	completed,
	canceled,
	suspended,
}: {
	active: boolean;
	incidents: boolean;
	completed: boolean;
	canceled: boolean;
	suspended: boolean;
}): StatisticsFilter | undefined {
	const states: ProcessInstanceState[] = [];
	if (active) {
		states.push('ACTIVE');
	}
	if (completed) {
		states.push('COMPLETED');
	}
	if (canceled) {
		states.push('TERMINATED');
	}

	const stateFilter = getStateFilter(states);
	const branches: StatisticsFilter[] = [];

	// hasIncident is only pinned to false here when there is no separate incidents branch below —
	// otherwise an active instance with an incident would match neither branch and drop out.
	if (stateFilter !== undefined) {
		branches.push(incidents ? stateFilter : {...stateFilter, hasIncident: false});
	}
	if (suspended) {
		branches.push({state: {$eq: 'SUSPENDED'}});
	}
	if (incidents) {
		branches.push(suspended ? {hasIncident: true, state: {$neq: 'SUSPENDED'}} : {hasIncident: true});
	}

	if (branches.length === 0) {
		return undefined;
	}

	return branches.length === 1 ? branches[0]! : {$or: branches};
}

export {getStatisticsFilter};
