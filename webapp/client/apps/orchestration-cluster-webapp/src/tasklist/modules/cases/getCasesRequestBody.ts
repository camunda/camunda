/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryProcessInstancesRequestBody} from '@camunda/camunda-api-zod-schemas/8.11';
import type {CasesSearch} from './searchSchema';

const CASES_PAGE_SIZE = 50;

function getCasesRequestBody(search: CasesSearch): QueryProcessInstancesRequestBody {
	return {
		filter: {
			state: 'ACTIVE',
			parentProcessInstanceKey: {$exists: false},
			businessId: search.search === undefined ? undefined : {$like: `*${search.search}*`},
		},
		sort: [{field: search.sortField, order: search.sortOrder === 'desc' ? 'DESC' : 'ASC'}],
		page: {from: (search.page - 1) * CASES_PAGE_SIZE, limit: CASES_PAGE_SIZE},
	};
}

export {CASES_PAGE_SIZE, getCasesRequestBody};
