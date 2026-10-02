/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryProcessInstancesRequestBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {CASE_PREFIX, escapeLike} from '#/tasklist/modules/cases/caseId';
import type {CasesSearch} from '#/tasklist/modules/cases/searchSchema';

function getCasesRequestBody({
	search,
	sortField,
	sortOrder,
	page,
	pageSize,
}: CasesSearch): QueryProcessInstancesRequestBody {
	const searchTerm = search?.trim() ?? '';
	const businessIdPattern = searchTerm === '' ? `${CASE_PREFIX}*` : `${CASE_PREFIX}*${escapeLike(searchTerm)}*`;

	return {
		filter: {
			parentProcessInstanceKey: {$exists: false},
			state: {$eq: 'ACTIVE'},
			$or: [{businessId: {$like: businessIdPattern}}],
		},
		sort: [{field: sortField, order: sortOrder}],
		page: {
			from: (page - 1) * pageSize,
			limit: pageSize,
		},
	};
}

export {getCasesRequestBody};
