/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {DecisionInstanceState, QueryDecisionInstancesRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {parseIds} from '#/operate/shared/utils/parseIds';
import {decodeAdvancedStringFilter} from '#/operate/shared/utils/advancedStringFilter';

type DecisionInstancesFilter = NonNullable<QueryDecisionInstancesRequestBody['filter']>;
type DecisionInstancesSort = NonNullable<QueryDecisionInstancesRequestBody['sort']>;
type DecisionInstancesSortField = DecisionInstancesSort[number]['field'];

type DecisionsSearch = {
	decisionDefinitionId?: string;
	decisionDefinitionVersion?: number;
	tenantId?: string;
	evaluated: boolean;
	failed: boolean;
	decisionEvaluationInstanceKey?: string;
	processInstanceKey?: string;
	businessId?: string;
	evaluationDateFrom?: string;
	evaluationDateTo?: string;
	sort?: string;
};

/** The `TenantField` uses the literal `'all'` as its "All tenants" item id (see legacy parity). */
function isSpecificTenant(tenantId: string | undefined): tenantId is string {
	return tenantId !== undefined && tenantId !== 'all';
}

/**
 * Maps route search params into the decision-instances search request filter. Returns
 * `undefined` when no instance-state checkbox is selected, mirroring legacy's
 * `parseDecisionInstancesSearchFilter`, which disables the query entirely in that case.
 */
function mapDecisionInstancesFilter(search: DecisionsSearch): DecisionInstancesFilter | undefined {
	const states: DecisionInstanceState[] = [];
	if (search.evaluated) {
		states.push('EVALUATED');
	}
	if (search.failed) {
		states.push('FAILED');
	}
	if (states.length === 0) {
		return undefined;
	}

	return {
		state: {$in: states},
		decisionEvaluationInstanceKey: search.decisionEvaluationInstanceKey
			? {$in: parseIds(search.decisionEvaluationInstanceKey)}
			: undefined,
		decisionDefinitionId: search.decisionDefinitionId,
		decisionDefinitionVersion: search.decisionDefinitionVersion,
		processInstanceKey: search.processInstanceKey,
		tenantId: isSpecificTenant(search.tenantId) ? search.tenantId : undefined,
		evaluationDate:
			search.evaluationDateFrom || search.evaluationDateTo
				? {$gt: search.evaluationDateFrom, $lt: search.evaluationDateTo}
				: undefined,
		businessId: search.businessId ? decodeAdvancedStringFilter(search.businessId) : undefined,
	};
}

/** Parses the `sort` search param (`"field+order"`) into the API sort shape, defaulting to evaluation date descending. */
function mapDecisionInstancesSort(sort: string | undefined) {
	const [field, order] = (sort ?? 'evaluationDate+desc').split('+') as [DecisionInstancesSortField, 'asc' | 'desc'];
	return [{field, order: order ?? 'desc'}] satisfies DecisionInstancesSort;
}

/**
 * Builds the `decisionEvaluationInstanceKey` criterion for a batch delete request from the
 * current tri-state row selection. `undefined` when everything matching the filter should be
 * targeted (selection mode ALL), matching legacy's `buildInstanceKeyCriterion`.
 */
function buildInstanceKeyCriterion(includeIds: string[], excludeIds: string[]) {
	if (includeIds.length > 0) {
		return {$in: includeIds};
	}
	if (excludeIds.length > 0) {
		return {$notIn: excludeIds};
	}
	return undefined;
}

export {mapDecisionInstancesFilter, mapDecisionInstancesSort, buildInstanceKeyCriterion, isSpecificTenant};
export type {DecisionsSearch, DecisionInstancesFilter};
