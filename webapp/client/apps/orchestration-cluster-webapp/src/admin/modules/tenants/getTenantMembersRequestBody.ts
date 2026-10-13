/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {DEFAULT_PAGE_SIZE} from './searchSchema';
import type {TenantDetailSearch} from './detailSearchSchema';

function getTenantMembersRequestBody<F extends string>(search: TenantDetailSearch, sortField: F) {
	const pageSize = search.pageSize ?? DEFAULT_PAGE_SIZE;

	return {
		sort: [{field: sortField, order: search.sortOrder ?? 'ASC'}],
		page: {from: ((search.page ?? 1) - 1) * pageSize, limit: pageSize},
	};
}

export {getTenantMembersRequestBody};
