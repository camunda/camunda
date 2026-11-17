/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
	ElementInstanceInspection,
	QueryElementInstanceInspectionResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {createPaginatedResponse} from './shared';

function createElementInstanceInspection(overrides?: Partial<ElementInstanceInspection>): ElementInstanceInspection {
	return {
		rootProcessInstanceKey: null,
		processInstanceKey: '2251799813685280',
		elementInstanceKey: '2251799813685281',
		elementId: 'message-event',
		elementType: 'INTERMEDIATE_CATCH_EVENT',
		tenantId: '<default>',
		bpmnProcessId: 'my-process',
		details: {waitStateType: 'MESSAGE', messageName: 'payment-received', correlationKey: 'order-1'},
		...overrides,
	};
}

function createQueryElementInstanceInspectionResponse(
	items: ElementInstanceInspection[] = [],
): QueryElementInstanceInspectionResponseBody {
	return createPaginatedResponse({
		items,
		page: {totalItems: items.length, startCursor: null, endCursor: null, hasMoreTotalItems: false},
	});
}

export {createElementInstanceInspection, createQueryElementInstanceInspectionResponse};
