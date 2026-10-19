/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryProcessInstancesRequestBody} from '@camunda/camunda-api-zod-schemas/8.11';
import type {CasesSearch} from '#/tasklist/modules/cases/searchSchema';

function escapeLike(value: string): string {
	return value.replace(/[\\*?]/g, (match) => `\\${match}`);
}

// prototype: the design filters by `kind: "CASE_INSTANCE"` (CASE_IDENTITY.md); that field does not exist yet,
// so the list shows every active root process instance.
function getCasesRequestBody({
	search,
	sortField,
	sortOrder,
	page,
	pageSize,
}: CasesSearch): QueryProcessInstancesRequestBody {
	const searchTerm = search?.trim() ?? '';

	return {
		filter: {
			parentProcessInstanceKey: {$exists: false},
			state: {$eq: 'ACTIVE'},
			...(searchTerm === '' ? {} : {businessId: {$like: `*${escapeLike(searchTerm)}*`}}),
		},
		sort: [{field: sortField, order: sortOrder === 'asc' ? 'ASC' : 'DESC'}],
		page: {
			from: (page - 1) * pageSize,
			limit: pageSize,
		},
	};
}

export {getCasesRequestBody};
