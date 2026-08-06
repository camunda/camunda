/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
	GetProcessDefinitionResponseBody,
	ProcessDefinition,
	QueryProcessDefinitionsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';

const START_PROCESS_FORM_SCHEMA = JSON.stringify({
	components: [
		{
			key: 'customerName',
			label: 'Customer name',
			type: 'textfield',
			validate: {required: true},
		},
		{
			key: 'invoiceAmount',
			label: 'Invoice amount',
			type: 'number',
		},
	],
	type: 'default',
	id: 'invoice-review-form',
});

const START_PROCESS_FORM_WITH_DOCUMENT_SCHEMA = JSON.stringify({
	components: [
		...JSON.parse(START_PROCESS_FORM_SCHEMA).components,
		{
			key: 'supportingDocument',
			label: 'Supporting document',
			type: 'filepicker',
		},
	],
	type: 'default',
	id: 'invoice-review-form-with-document',
});

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
		state: 'ACTIVE',
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

function createGetProcessDefinitionResponse(
	overrides?: Partial<GetProcessDefinitionResponseBody>,
): GetProcessDefinitionResponseBody {
	return {
		...createProcessDefinition({hasStartForm: true}),
		...overrides,
	};
}

function createProcessStartFormResponse(overrides?: Partial<{schema: string; tenantId: string}>) {
	return {
		tenantId: '<default>',
		formId: 'invoice-review-form',
		schema: START_PROCESS_FORM_SCHEMA,
		version: 1,
		formKey: '2251799813685281',
		...overrides,
	};
}

export {
	START_PROCESS_FORM_WITH_DOCUMENT_SCHEMA,
	createGetProcessDefinitionResponse,
	createProcessDefinition,
	createProcessStartFormResponse,
	createQueryProcessDefinitionsResponse,
};
