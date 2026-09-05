/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessInstanceState, QueryProcessInstancesRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {z} from 'zod';
import {parseIds} from '#/operate/shared/utils/parseIds';
import {decodeAdvancedStringFilter} from '#/operate/shared/utils/advancedStringFilter';
import {isSpecificTenant} from '#/operate/shared/utils/isSpecificTenant';

type ProcessInstancesFilter = NonNullable<QueryProcessInstancesRequestBody['filter']>;
type ProcessInstancesSort = NonNullable<QueryProcessInstancesRequestBody['sort']>;
type ProcessInstancesSortField = ProcessInstancesSort[number]['field'];

type ProcessesSearch = {
	process?: string;
	version?: number;
	elementId?: string;
	tenantId?: string;
	processInstanceKey?: string;
	parentProcessInstanceKey?: string;
	businessId?: string;
	batchOperationKey?: string;
	errorMessage?: string;
	hasRetriesLeft?: boolean;
	startDateFrom?: string;
	startDateTo?: string;
	endDateFrom?: string;
	endDateTo?: string;
	active: boolean;
	incidents: boolean;
	completed: boolean;
	canceled: boolean;
	sort?: string;
};

// The route schema types the date params as plain strings, so a hand-edited URL can carry
// something `Date` cannot parse. Drop the bound rather than letting `toISOString` throw and
// take the whole page down with it.
function toISO(value: string | undefined): string | undefined {
	if (!value) {
		return undefined;
	}
	const date = new Date(value);
	return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

function getSelectedStates({active, completed, canceled}: ProcessesSearch): ProcessInstanceState[] {
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
	return states;
}

function buildStateCriterion(states: ProcessInstanceState[]): ProcessInstancesFilter['state'] {
	return states.length === 1 ? {$eq: states[0]!} : {$in: states};
}

/**
 * `incidents` is never scoped to a state: an instance keeps its incident regardless of the state
 * it is in, so it must stay visible even when only non-active states are selected. Mirrors
 * legacy's `buildStateFilter`.
 */
function buildStateFilter(search: ProcessesSearch): ProcessInstancesFilter {
	const states = getSelectedStates(search);

	if (search.incidents && states.length > 0) {
		return {$or: [{state: {$in: states}}, {hasIncident: true}]};
	}
	if (search.incidents) {
		return {hasIncident: true};
	}
	if (states.length > 0) {
		return {state: buildStateCriterion(states), hasIncident: false};
	}
	return {};
}

function buildElementFilter(elementId: string, matchActiveElement: boolean): ProcessInstancesFilter {
	return {
		elementId: {$eq: elementId},
		...(matchActiveElement && {elementInstanceState: {$eq: 'ACTIVE' as const}}),
	};
}

/**
 * A finished-state filter looks at elements the instance *executed*, a running-state one at
 * elements currently *active*. When both are selected the two cannot be expressed as one
 * criterion, so each state contributes its own `$or` branch. Mirrors legacy's
 * `buildMixedStateElementFilter`.
 */
function buildMixedStateElementFilter(search: ProcessesSearch, elementId: string): ProcessInstancesFilter {
	const finishedStates = getSelectedStates(search).filter((state) => state !== 'ACTIVE');
	const branches: ProcessInstancesFilter[] = [];

	if (search.active) {
		branches.push({...buildElementFilter(elementId, true), state: {$eq: 'ACTIVE'}, hasIncident: false});
	}
	if (finishedStates.length > 0) {
		branches.push({
			...buildElementFilter(elementId, false),
			state: buildStateCriterion(finishedStates),
			hasIncident: false,
		});
	}
	if (search.incidents) {
		branches.push({...buildElementFilter(elementId, true), hasIncident: true});
	}

	return {$or: branches};
}

function buildStateAndElementFilter(search: ProcessesSearch): ProcessInstancesFilter {
	const stateFilter = buildStateFilter(search);

	if (!search.elementId) {
		return stateFilter;
	}

	const hasFinishedStateFilter = search.completed || search.canceled;
	const hasActiveElementStateFilter = search.active || search.incidents;

	if (hasFinishedStateFilter && hasActiveElementStateFilter) {
		return buildMixedStateElementFilter(search, search.elementId);
	}

	return {...stateFilter, ...buildElementFilter(search.elementId, !hasFinishedStateFilter)};
}

/**
 * Maps route search params into the process-instances search request filter. Returns `undefined`
 * when nothing narrows the result set — no instance state, no batch operation, no element — so the
 * caller skips the request entirely, mirroring legacy's `parseProcessInstancesSearchFilter`.
 */
function mapProcessInstancesFilter(search: ProcessesSearch): ProcessInstancesFilter | undefined {
	const hasStateFilters = search.active || search.incidents || search.completed || search.canceled;

	if (!hasStateFilters && !search.batchOperationKey && !search.elementId) {
		return undefined;
	}

	const filter = buildStateAndElementFilter(search);
	const instanceKeys = search.processInstanceKey ? parseIds(search.processInstanceKey) : [];

	return {
		...filter,
		processDefinitionId: search.process ? {$eq: search.process} : undefined,
		processDefinitionVersion: search.version,
		tenantId: search.tenantId && isSpecificTenant(search.tenantId) ? {$eq: search.tenantId} : undefined,
		processInstanceKey: instanceKeys.length > 0 ? {$in: instanceKeys} : undefined,
		parentProcessInstanceKey: search.parentProcessInstanceKey ? {$eq: search.parentProcessInstanceKey} : undefined,
		batchOperationKey: search.batchOperationKey ? {$eq: search.batchOperationKey} : undefined,
		errorMessage: search.errorMessage ? {$in: [search.errorMessage]} : undefined,
		hasRetriesLeft: search.hasRetriesLeft ? true : undefined,
		startDate:
			search.startDateFrom || search.startDateTo
				? {$gt: toISO(search.startDateFrom), $lt: toISO(search.startDateTo)}
				: undefined,
		endDate:
			search.endDateFrom || search.endDateTo
				? {$gt: toISO(search.endDateFrom), $lt: toISO(search.endDateTo)}
				: undefined,
		businessId: search.businessId ? decodeAdvancedStringFilter(search.businessId) : undefined,
	};
}

type ResolvedProcessInstancesSort = [{field: ProcessInstancesSortField; order: 'asc' | 'desc'}];

const DEFAULT_SORT: ResolvedProcessInstancesSort = [{field: 'startDate', order: 'desc'}];

// The sortable columns InstancesTable wires up — the app never produces a `sort` value outside
// this set, so anything else can only come from a hand-edited URL.
const SORTABLE_FIELDS = [
	'processDefinitionName',
	'processInstanceKey',
	'processDefinitionVersion',
	'businessId',
	'tenantId',
	'startDate',
	'endDate',
	'parentProcessInstanceKey',
] as const satisfies readonly ProcessInstancesSortField[];

const processInstancesSortSchema = z.object({
	field: z.enum(SORTABLE_FIELDS),
	order: z.enum(['asc', 'desc']),
});

/**
 * Parses the `sort` search param (`"field+order"`) into the API sort shape, falling back to start
 * date descending when the field or order is missing or unrecognized — mirroring legacy's
 * `parseSortParamsV2`, which validates both parts rather than trusting the URL.
 */
function mapProcessInstancesSort(sort: string | undefined): ResolvedProcessInstancesSort {
	if (sort === undefined) {
		return DEFAULT_SORT;
	}

	const [field, order] = sort.split('+');
	const result = processInstancesSortSchema.safeParse({field, order});

	return result.success ? [result.data] : DEFAULT_SORT;
}

export {mapProcessInstancesFilter, mapProcessInstancesSort};
export type {ProcessesSearch};
