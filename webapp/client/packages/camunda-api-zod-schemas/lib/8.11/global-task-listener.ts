/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {createGlobalTaskListenerRequestSchema} from './gen/zod/createGlobalTaskListenerRequestSchema';
import {globalListenerSourceEnumSchema} from './gen/zod/globalListenerSourceEnumSchema';
import {globalTaskListenerEventTypeEnumSchema} from './gen/zod/globalTaskListenerEventTypeEnumSchema';
import {globalTaskListenerResultSchema} from './gen/zod/globalTaskListenerResultSchema';
import {globalTaskListenerSearchQueryRequestSchema} from './gen/zod/globalTaskListenerSearchQueryRequestSchema';
import {globalTaskListenerSearchQueryResultSchema} from './gen/zod/globalTaskListenerSearchQueryResultSchema';
import {updateGlobalTaskListenerRequestSchema} from './gen/zod/updateGlobalTaskListenerRequestSchema';
import type {CreateGlobalTaskListenerRequest} from './gen/types/CreateGlobalTaskListenerRequest';
import type {GlobalListenerSourceEnumKey} from './gen/types/GlobalListenerSourceEnum';
import type {GlobalTaskListenerEventTypeEnumKey} from './gen/types/GlobalTaskListenerEventTypeEnum';
import type {GlobalTaskListenerResult} from './gen/types/GlobalTaskListenerResult';
import type {GlobalTaskListenerSearchQueryRequest} from './gen/types/GlobalTaskListenerSearchQueryRequest';
import type {GlobalTaskListenerSearchQueryResult} from './gen/types/GlobalTaskListenerSearchQueryResult';
import type {UpdateGlobalTaskListenerRequest} from './gen/types/UpdateGlobalTaskListenerRequest';

const globalListenerSourceSchema = globalListenerSourceEnumSchema;
type GlobalListenerSource = GlobalListenerSourceEnumKey;

const globalTaskListenerEventTypeSchema = globalTaskListenerEventTypeEnumSchema;
type GlobalTaskListenerEventType = GlobalTaskListenerEventTypeEnumKey;

const globalTaskListenerSchema = globalTaskListenerResultSchema;
type GlobalTaskListener = GlobalTaskListenerResult;

const createGlobalTaskListenerRequestBodySchema = createGlobalTaskListenerRequestSchema;
type CreateGlobalTaskListenerRequestBody = CreateGlobalTaskListenerRequest;

const updateGlobalTaskListenerRequestBodySchema = updateGlobalTaskListenerRequestSchema;
type UpdateGlobalTaskListenerRequestBody = UpdateGlobalTaskListenerRequest;

const queryGlobalTaskListenersRequestBodySchema = globalTaskListenerSearchQueryRequestSchema;
type QueryGlobalTaskListenersRequestBody = GlobalTaskListenerSearchQueryRequest;

const queryGlobalTaskListenersResponseBodySchema = globalTaskListenerSearchQueryResultSchema;
type QueryGlobalTaskListenersResponseBody = GlobalTaskListenerSearchQueryResult;

const searchGlobalTaskListeners = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/global-task-listeners/search` as const,
} as const satisfies Endpoint;

const createGlobalTaskListener = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/global-task-listeners` as const,
} as const satisfies Endpoint;

const getGlobalTaskListener = {
	method: 'GET',
	getUrl: ({id}) => `/${API_VERSION}/global-task-listeners/${id}` as const,
} as const satisfies Endpoint<{id: string}>;

const updateGlobalTaskListener = {
	method: 'PUT',
	getUrl: ({id}) => `/${API_VERSION}/global-task-listeners/${id}` as const,
} as const satisfies Endpoint<{id: string}>;

const deleteGlobalTaskListener = {
	method: 'DELETE',
	getUrl: ({id}) => `/${API_VERSION}/global-task-listeners/${id}` as const,
} as const satisfies Endpoint<{id: string}>;

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
