/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
	QueryClientsByTenantResponseBody,
	QueryGroupsByTenantResponseBody,
	QueryTenantsResponseBody,
	QueryUsersByTenantResponseBody,
	Tenant,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {createPaginatedResponse} from './shared';

function createTenant(overrides?: Partial<Tenant>): Tenant {
	return {tenantId: 'tenant-a', name: 'Tenant A', description: null, ...overrides};
}

function createQueryTenantsResponse(items: Tenant[] = []): QueryTenantsResponseBody {
	return createPaginatedResponse({
		items,
		page: {totalItems: items.length, startCursor: null, endCursor: null, hasMoreTotalItems: false},
	});
}

function createTenantMembersPage(totalItems: number) {
	return {totalItems, startCursor: null, endCursor: null, hasMoreTotalItems: false};
}

function createQueryUsersByTenantResponse(usernames: string[] = []): QueryUsersByTenantResponseBody {
	return createPaginatedResponse({
		items: usernames.map((username) => ({username})),
		page: createTenantMembersPage(usernames.length),
	});
}

function createQueryGroupsByTenantResponse(groupIds: string[] = []): QueryGroupsByTenantResponseBody {
	return createPaginatedResponse({
		items: groupIds.map((groupId) => ({groupId})),
		page: createTenantMembersPage(groupIds.length),
	});
}

function createQueryClientsByTenantResponse(clientIds: string[] = []): QueryClientsByTenantResponseBody {
	return createPaginatedResponse({
		items: clientIds.map((clientId) => ({clientId})),
		page: createTenantMembersPage(clientIds.length),
	});
}

export {
	createTenant,
	createQueryTenantsResponse,
	createQueryUsersByTenantResponse,
	createQueryGroupsByTenantResponse,
	createQueryClientsByTenantResponse,
};
