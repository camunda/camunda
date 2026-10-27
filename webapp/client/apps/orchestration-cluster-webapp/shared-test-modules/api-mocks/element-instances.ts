/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ElementInstance, QueryElementInstancesResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {createPaginatedResponse} from './shared';

function createElementInstance(
	overrides?: Partial<ElementInstance> & {elementInstanceScopeKey?: string},
): ElementInstance {
	return {
		processDefinitionId: 'my-process',
		processDefinitionKey: '2251799813685279',
		processInstanceKey: '2251799813685280',
		rootProcessInstanceKey: null,
		elementInstanceKey: '2251799813685281',
		elementId: 'user-task',
		elementName: 'User Task',
		type: 'USER_TASK',
		state: 'ACTIVE',
		hasIncident: false,
		incidentKey: null,
		startDate: '2026-01-15T10:00:00.000Z',
		endDate: null,
		tenantId: '<default>',
		...overrides,
	};
}

function createQueryElementInstancesResponse(
	items: ElementInstance[] = [],
	totalItems = items.length,
): QueryElementInstancesResponseBody {
	return createPaginatedResponse({
		items,
		page: {totalItems, startCursor: null, endCursor: null, hasMoreTotalItems: false},
	});
}

export {createElementInstance, createQueryElementInstancesResponse};
