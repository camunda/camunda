/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from './common';
import {
	globalListenerSourceEnumSchema,
	globalTaskListenerEventTypeEnumSchema,
	globalTaskListenerResultSchema,
	createGlobalTaskListenerRequestSchema,
	updateGlobalTaskListenerRequestSchema,
	globalTaskListenerSearchQueryRequestSchema,
	globalTaskListenerSearchQueryResultSchema,
} from './gen';

const globalListenerSourceSchema = globalListenerSourceEnumSchema;
type GlobalListenerSource = z.infer<typeof globalListenerSourceSchema>;

const globalTaskListenerEventTypeSchema = globalTaskListenerEventTypeEnumSchema;
type GlobalTaskListenerEventType = z.infer<typeof globalTaskListenerEventTypeSchema>;

const globalTaskListenerSchema = globalTaskListenerResultSchema;
type GlobalTaskListener = z.infer<typeof globalTaskListenerSchema>;

const createGlobalTaskListenerRequestBodySchema = createGlobalTaskListenerRequestSchema;
type CreateGlobalTaskListenerRequestBody = z.infer<typeof createGlobalTaskListenerRequestBodySchema>;

const updateGlobalTaskListenerRequestBodySchema = updateGlobalTaskListenerRequestSchema;
type UpdateGlobalTaskListenerRequestBody = z.infer<typeof updateGlobalTaskListenerRequestBodySchema>;

const queryGlobalTaskListenersRequestBodySchema = globalTaskListenerSearchQueryRequestSchema;
type QueryGlobalTaskListenersRequestBody = z.infer<typeof queryGlobalTaskListenersRequestBodySchema>;

const queryGlobalTaskListenersResponseBodySchema = globalTaskListenerSearchQueryResultSchema;
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
