/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
	CreateProcessInstanceResponseBody,
	ProcessInstance,
	QueryProcessInstancesResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';

function createProcessInstanceResponse(
	overrides?: Partial<CreateProcessInstanceResponseBody>,
): CreateProcessInstanceResponseBody {
	return {
		processDefinitionId: 'my-process',
		processDefinitionVersion: 1,
		processDefinitionKey: '2251799813685279',
		processInstanceKey: '2251799813685280',
		tenantId: '<default>',
		variables: null,
		businessId: null,
		...overrides,
	};
}

function createProcessInstance(overrides?: Partial<ProcessInstance>): ProcessInstance {
	return {
		processInstanceKey: '2251799813685280',
		processDefinitionKey: '2251799813685279',
		processDefinitionId: 'my-process',
		processDefinitionName: 'My Process',
		processDefinitionVersion: 1,
		processDefinitionVersionTag: null,
		startDate: '2026-01-15T10:00:00.000Z',
		endDate: null,
		suspendedDate: null,
		state: 'ACTIVE',
		hasIncident: false,
		tenantId: '<default>',
		parentProcessInstanceKey: null,
		parentElementInstanceKey: null,
		rootProcessInstanceKey: null,
		tags: [],
		businessId: null,
		...overrides,
	};
}

function createQueryProcessInstancesResponse(overrides?: {
	items?: ProcessInstance[];
	page?: Partial<QueryProcessInstancesResponseBody['page']>;
}): QueryProcessInstancesResponseBody {
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

export {createProcessInstanceResponse, createProcessInstance, createQueryProcessInstancesResponse};
