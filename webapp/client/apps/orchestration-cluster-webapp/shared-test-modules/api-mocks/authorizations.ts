/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Authorization, QueryAuthorizationsResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';

function createAuthorization(overrides?: Partial<Authorization>): Authorization {
	return {
		authorizationKey: '2251799813685249',
		ownerId: 'demo',
		ownerType: 'USER',
		resourceType: 'PROCESS_DEFINITION',
		resourceId: 'order-process',
		resourcePropertyName: null,
		permissionTypes: ['READ_PROCESS_DEFINITION', 'READ_PROCESS_INSTANCE'],
		...overrides,
	};
}

function createQueryAuthorizationsResponse(overrides?: {
	items?: Authorization[];
	page?: Partial<QueryAuthorizationsResponseBody['page']>;
}): QueryAuthorizationsResponseBody {
	const items = overrides?.items ?? [];

	return {
		items,
		page: {
			totalItems: items.length,
			startCursor: null,
			endCursor: null,
			hasMoreTotalItems: false,
			...overrides?.page,
		},
	};
}

const ADMIN_CLIENT_CONFIG = {
	idPattern: '^[a-zA-Z0-9_~@.+-]{1,256}$',
	resourcePermissions: {
		PROCESS_DEFINITION: ['READ_PROCESS_DEFINITION', 'READ_PROCESS_INSTANCE', 'CREATE_PROCESS_INSTANCE'],
		USER_TASK: ['READ', 'UPDATE', 'CLAIM', 'COMPLETE'],
		SECRET: ['READ'],
		AUTHORIZATION: ['CREATE', 'READ', 'UPDATE', 'DELETE'],
	},
	defaultRoleIds: ['admin'],
};

function createAdminClientConfigScript(overrides?: Partial<typeof ADMIN_CLIENT_CONFIG>): string {
	return `window.clientConfig = ${JSON.stringify({...ADMIN_CLIENT_CONFIG, ...overrides})};`;
}

export {createAdminClientConfigScript, createAuthorization, createQueryAuthorizationsResponse};
