/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {
	API_VERSION,
	advancedStringFilterSchema,
	advancedIntegerFilterSchema,
	getEnumFilterSchema,
	getQueryRequestBodySchema,
	getQueryResponseBodySchema,
	type Endpoint,
} from './common';
import {globalListenerSourceSchema} from './global-task-listener';

const globalExecutionListenerEventTypeSchema = z.enum(['all', 'start', 'end']);
type GlobalExecutionListenerEventType = z.infer<typeof globalExecutionListenerEventTypeSchema>;

const globalExecutionListenerElementTypeSchema = z.enum([
	'serviceTask',
	'userTask',
	'sendTask',
	'receiveTask',
	'businessRuleTask',
	'scriptTask',
	'callActivity',
	'subProcess',
	'eventSubProcess',
	'multiInstanceBody',
	'exclusiveGateway',
	'inclusiveGateway',
	'parallelGateway',
	'eventBasedGateway',
	'startEvent',
	'endEvent',
	'intermediateThrowEvent',
	'intermediateCatchEvent',
	'boundaryEvent',
	'all',
]);
type GlobalExecutionListenerElementType = z.infer<typeof globalExecutionListenerElementTypeSchema>;

const globalExecutionListenerCategorySchema = z.enum([
	'tasks',
	'gateways',
	'events',
	'containers',
	'all',
]);
type GlobalExecutionListenerCategory = z.infer<typeof globalExecutionListenerCategorySchema>;

const globalExecutionListenerSchema = z.object({
	id: z.string(),
	type: z.string(),
	eventTypes: z.array(globalExecutionListenerEventTypeSchema),
	elementTypes: z.array(globalExecutionListenerElementTypeSchema).optional(),
	categories: z.array(globalExecutionListenerCategorySchema).optional(),
	retries: z.number().int().nullable(),
	afterNonGlobal: z.boolean().nullable(),
	priority: z.number().int().nullable(),
	source: globalListenerSourceSchema.optional(),
});
type GlobalExecutionListener = z.infer<typeof globalExecutionListenerSchema>;

const createGlobalExecutionListenerRequestBodySchema = z.object({
	id: z.string(),
	type: z.string(),
	eventTypes: z.array(globalExecutionListenerEventTypeSchema),
	elementTypes: z.array(globalExecutionListenerElementTypeSchema).optional(),
	categories: z.array(globalExecutionListenerCategorySchema).optional(),
	retries: z.number().int().optional(),
	afterNonGlobal: z.boolean().optional(),
	priority: z.number().int().optional(),
});
type CreateGlobalExecutionListenerRequestBody = z.infer<
	typeof createGlobalExecutionListenerRequestBodySchema
>;

const updateGlobalExecutionListenerRequestBodySchema = z.object({
	type: z.string(),
	eventTypes: z.array(globalExecutionListenerEventTypeSchema),
	elementTypes: z.array(globalExecutionListenerElementTypeSchema).optional(),
	categories: z.array(globalExecutionListenerCategorySchema).optional(),
	retries: z.number().int().optional(),
	afterNonGlobal: z.boolean().optional(),
	priority: z.number().int().optional(),
});
type UpdateGlobalExecutionListenerRequestBody = z.infer<
	typeof updateGlobalExecutionListenerRequestBodySchema
>;

const queryGlobalExecutionListenersRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['id', 'type', 'afterNonGlobal', 'priority', 'source'] as const,
	filter: z.object({
		id: advancedStringFilterSchema.optional(),
		type: advancedStringFilterSchema.optional(),
		retries: advancedIntegerFilterSchema.optional(),
		eventTypes: z
			.array(getEnumFilterSchema(globalExecutionListenerEventTypeSchema))
			.optional(),
		afterNonGlobal: z.boolean().optional(),
		priority: advancedIntegerFilterSchema.optional(),
		source: getEnumFilterSchema(globalListenerSourceSchema).optional(),
	}),
});
type QueryGlobalExecutionListenersRequestBody = z.infer<
	typeof queryGlobalExecutionListenersRequestBodySchema
>;

const queryGlobalExecutionListenersResponseBodySchema = getQueryResponseBodySchema(
	globalExecutionListenerSchema,
);
type QueryGlobalExecutionListenersResponseBody = z.infer<
	typeof queryGlobalExecutionListenersResponseBodySchema
>;

const searchGlobalExecutionListeners: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/global-execution-listeners/search`,
};

const createGlobalExecutionListener: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/global-execution-listeners`,
};

const getGlobalExecutionListener: Endpoint<{id: string}> = {
	method: 'GET',
	getUrl: ({id}) => `/${API_VERSION}/global-execution-listeners/${id}`,
};

const updateGlobalExecutionListener: Endpoint<{id: string}> = {
	method: 'PUT',
	getUrl: ({id}) => `/${API_VERSION}/global-execution-listeners/${id}`,
};

const deleteGlobalExecutionListener: Endpoint<{id: string}> = {
	method: 'DELETE',
	getUrl: ({id}) => `/${API_VERSION}/global-execution-listeners/${id}`,
};

export {
	globalExecutionListenerEventTypeSchema,
	globalExecutionListenerElementTypeSchema,
	globalExecutionListenerCategorySchema,
	globalExecutionListenerSchema,
	createGlobalExecutionListenerRequestBodySchema,
	updateGlobalExecutionListenerRequestBodySchema,
	queryGlobalExecutionListenersRequestBodySchema,
	queryGlobalExecutionListenersResponseBodySchema,
	searchGlobalExecutionListeners,
	createGlobalExecutionListener,
	getGlobalExecutionListener,
	updateGlobalExecutionListener,
	deleteGlobalExecutionListener,
};
export type {
	GlobalExecutionListenerEventType,
	GlobalExecutionListenerElementType,
	GlobalExecutionListenerCategory,
	GlobalExecutionListener,
	CreateGlobalExecutionListenerRequestBody,
	UpdateGlobalExecutionListenerRequestBody,
	QueryGlobalExecutionListenersRequestBody,
	QueryGlobalExecutionListenersResponseBody,
};
