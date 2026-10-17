/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryTenantsResponseBody, Tenant} from '@camunda/camunda-api-zod-schemas/8.11';
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

export {createTenant, createQueryTenantsResponse};
