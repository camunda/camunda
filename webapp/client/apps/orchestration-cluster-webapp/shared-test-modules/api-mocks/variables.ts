/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryVariablesByUserTaskResponseBody, Variable} from '@camunda/camunda-api-zod-schemas/8.10';

function createVariable(overrides?: Partial<Variable>): Variable {
	return {
		name: 'amount',
		value: '100',
		tenantId: '<default>',
		isTruncated: false,
		variableKey: '2251799813685283',
		scopeKey: '2251799813685282',
		processInstanceKey: '2251799813685280',
		rootProcessInstanceKey: null,
		...overrides,
	};
}

function createQueryVariablesByUserTaskResponse(overrides?: {
	items?: Variable[];
	page?: Partial<QueryVariablesByUserTaskResponseBody['page']>;
}): QueryVariablesByUserTaskResponseBody {
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

export {createVariable, createQueryVariablesByUserTaskResponse};
