/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessDefinition, QueryProcessDefinitionsResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';

function createProcessDefinition(overrides?: Partial<ProcessDefinition>): ProcessDefinition {
	return {
		name: 'My Process',
		resourceName: 'my-process.bpmn',
		version: 1,
		versionTag: null,
		processDefinitionId: 'my-process:1:0',
		tenantId: '<default>',
		processDefinitionKey: '2251799813685279',
		hasStartForm: false,
		...overrides,
	};
}

function createQueryProcessDefinitionsResponse(overrides?: {
	items?: ProcessDefinition[];
	page?: Partial<QueryProcessDefinitionsResponseBody['page']>;
}): QueryProcessDefinitionsResponseBody {
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

export {createProcessDefinition, createQueryProcessDefinitionsResponse};
