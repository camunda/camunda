/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
	DecisionInstanceState,
	QueryDecisionInstancesRequestBody,
	QuerySortOrder,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {z} from 'zod';
import {parseIds} from '#/operate/shared/utils/parseIds';
import {createSortSearchParamSchema} from '#/shared/sortSearchParam';
import {decodeAdvancedStringFilter} from '#/operate/shared/utils/advancedStringFilter';
import {isSpecificTenant} from '#/operate/shared/utils/isSpecificTenant';
import {buildInstanceKeyCriterion} from '#/operate/shared/utils/buildInstanceKeyCriterion';

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

type ResolvedDecisionInstancesSort = [{field: DecisionInstancesSortField; order: QuerySortOrder}];

const DEFAULT_SORT: ResolvedDecisionInstancesSort = [{field: 'evaluationDate', order: 'DESC'}];
// The only two sortable columns InstancesTable actually wires up — the app itself never produces
// a `sort` value outside this set, so anything else can only come from a hand-edited URL.
const SORTABLE_FIELDS = ['evaluationDate', 'businessId'] as const satisfies readonly DecisionInstancesSortField[];
const decisionInstancesSortSchema = createSortSearchParamSchema(z.enum(SORTABLE_FIELDS));

/**
 * Parses the `sort` search param (`"field+order"`) into the API sort shape, falling back to
 * evaluation date descending when the field or order is missing or unrecognized — mirroring
 * legacy's `parseSortParamsV2`, which validates both parts rather than trusting the URL.
 */
function mapDecisionInstancesSort(sort: string | undefined): ResolvedDecisionInstancesSort {
	const result = decisionInstancesSortSchema.safeParse(sort);

	return result.success ? [result.data] : DEFAULT_SORT;
}

export {mapDecisionInstancesFilter, mapDecisionInstancesSort, buildInstanceKeyCriterion};
export type {DecisionsSearch, DecisionInstancesFilter};
