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
}: {
	active: boolean;
	incidents: boolean;
	completed: boolean;
	canceled: boolean;
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

	if (incidents) {
		if (stateFilter === undefined) {
			return {hasIncident: true};
		}

		return {$or: [stateFilter, {hasIncident: true}]};
	}

	if (stateFilter === undefined) {
		return undefined;
	}

	return {...stateFilter, hasIncident: false};
}

export {getStatisticsFilter};
