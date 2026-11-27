/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {DEFAULT_PAGE_SIZE} from './searchSchema';
import type {TenantDetailSearch} from './detailSearchSchema';

const USERS_SORT_FIELDS = ['username'] as const;
const GROUPS_SORT_FIELDS = ['groupId'] as const;
const ROLES_SORT_FIELDS = ['roleId', 'name'] as const;
const MAPPING_RULES_SORT_FIELDS = ['mappingRuleId', 'name', 'claimName', 'claimValue'] as const;
const CLIENTS_SORT_FIELDS = ['clientId'] as const;

function getTenantMembersRequestBody<F extends string>(
	search: TenantDetailSearch,
	sortableFields: readonly [F, ...F[]],
) {
	const pageSize = search.pageSize ?? DEFAULT_PAGE_SIZE;
	const sortField = sortableFields.find((field) => field === search.sortField) ?? sortableFields[0];

	return {
		sort: [{field: sortField, order: search.sortOrder ?? 'ASC'}],
		page: {from: ((search.page ?? 1) - 1) * pageSize, limit: pageSize},
	};
}

export {
	CLIENTS_SORT_FIELDS,
	GROUPS_SORT_FIELDS,
	MAPPING_RULES_SORT_FIELDS,
	ROLES_SORT_FIELDS,
	USERS_SORT_FIELDS,
	getTenantMembersRequestBody,
};
