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

const globalListenerSourceSchema = z.enum(['CONFIGURATION', 'API']);
type GlobalListenerSource = z.infer<typeof globalListenerSourceSchema>;

const globalTaskListenerEventTypeSchema = z.enum([
	'all',
	'creating',
	'assigning',
	'updating',
	'completing',
	'canceling',
]);
type GlobalTaskListenerEventType = z.infer<typeof globalTaskListenerEventTypeSchema>;

const globalTaskListenerSchema = z.object({
	id: z.string(),
	type: z.string(),
	eventTypes: z.array(globalTaskListenerEventTypeSchema),
	retries: z.number().int().nullable(),
	afterNonGlobal: z.boolean().nullable(),
	priority: z.number().int().nullable(),
	source: globalListenerSourceSchema.optional(),
});
type GlobalTaskListener = z.infer<typeof globalTaskListenerSchema>;

const createGlobalTaskListenerRequestBodySchema = z.object({
	id: z.string(),
	type: z.string(),
	eventTypes: z.array(globalTaskListenerEventTypeSchema),
	retries: z.number().int().optional(),
	afterNonGlobal: z.boolean().optional(),
	priority: z.number().int().optional(),
});
type CreateGlobalTaskListenerRequestBody = z.infer<typeof createGlobalTaskListenerRequestBodySchema>;

const updateGlobalTaskListenerRequestBodySchema = z.object({
	type: z.string(),
	eventTypes: z.array(globalTaskListenerEventTypeSchema),
	retries: z.number().int().optional(),
	afterNonGlobal: z.boolean().optional(),
	priority: z.number().int().optional(),
});
type UpdateGlobalTaskListenerRequestBody = z.infer<typeof updateGlobalTaskListenerRequestBodySchema>;

const queryGlobalTaskListenersRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['id', 'type', 'afterNonGlobal', 'priority', 'source'] as const,
	filter: z.object({
		id: advancedStringFilterSchema.optional(),
		type: advancedStringFilterSchema.optional(),
		retries: advancedIntegerFilterSchema.optional(),
		eventTypes: z.array(getEnumFilterSchema(globalTaskListenerEventTypeSchema)).optional(),
		afterNonGlobal: z.boolean().optional(),
		priority: advancedIntegerFilterSchema.optional(),
		source: getEnumFilterSchema(globalListenerSourceSchema).optional(),
	}),
});
type QueryGlobalTaskListenersRequestBody = z.infer<typeof queryGlobalTaskListenersRequestBodySchema>;

const queryGlobalTaskListenersResponseBodySchema = getQueryResponseBodySchema(globalTaskListenerSchema);
type QueryGlobalTaskListenersResponseBody = z.infer<typeof queryGlobalTaskListenersResponseBodySchema>;

const searchGlobalTaskListeners: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/global-task-listeners/search`,
};

const createGlobalTaskListener: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/global-task-listeners`,
};

const getGlobalTaskListener: Endpoint<{id: string}> = {
	method: 'GET',
	getUrl: ({id}) => `/${API_VERSION}/global-task-listeners/${id}`,
};

const updateGlobalTaskListener: Endpoint<{id: string}> = {
	method: 'PUT',
	getUrl: ({id}) => `/${API_VERSION}/global-task-listeners/${id}`,
};

const deleteGlobalTaskListener: Endpoint<{id: string}> = {
	method: 'DELETE',
	getUrl: ({id}) => `/${API_VERSION}/global-task-listeners/${id}`,
};

export {
	globalListenerSourceSchema,
	globalTaskListenerEventTypeSchema,
	globalTaskListenerSchema,
	createGlobalTaskListenerRequestBodySchema,
	updateGlobalTaskListenerRequestBodySchema,
	queryGlobalTaskListenersRequestBodySchema,
	queryGlobalTaskListenersResponseBodySchema,
	searchGlobalTaskListeners,
	createGlobalTaskListener,
	getGlobalTaskListener,
	updateGlobalTaskListener,
	deleteGlobalTaskListener,
};
export type {
	GlobalListenerSource,
	GlobalTaskListenerEventType,
	GlobalTaskListener,
	CreateGlobalTaskListenerRequestBody,
	UpdateGlobalTaskListenerRequestBody,
	QueryGlobalTaskListenersRequestBody,
	QueryGlobalTaskListenersResponseBody,
};
