/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryRolesResponseBody, Role} from '@camunda/camunda-api-zod-schemas/8.11';
import {createPaginatedResponse} from './shared';

function createRole(overrides?: Partial<Role>): Role {
	return {
		roleId: 'developers',
		name: 'Developers',
		description: 'The developer role',
		...overrides,
	};
}

function createQueryRolesResponse(overrides?: Partial<QueryRolesResponseBody>): QueryRolesResponseBody {
	const items = overrides?.items ?? [createRole()];

	return createPaginatedResponse({
		items,
		page: {totalItems: items.length, startCursor: null, endCursor: null, hasMoreTotalItems: false, ...overrides?.page},
	});
}

function createMembersPage<TItem>(items: TItem[]) {
	return createPaginatedResponse({
		items,
		page: {totalItems: items.length, startCursor: null, endCursor: null, hasMoreTotalItems: false},
	});
}

export {createRole, createQueryRolesResponse, createMembersPage};
