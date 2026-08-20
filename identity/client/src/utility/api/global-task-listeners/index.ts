/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  GlobalTaskListener,
  GlobalTaskListenerEventType,
  QueryGlobalTaskListenersRequestBody,
  QueryGlobalTaskListenersResponseBody,
  CreateGlobalTaskListenerRequestBody,
} from "@camunda/camunda-api-zod-schemas/8.10";
import { globalTaskListenerEventTypeSchema } from "@camunda/camunda-api-zod-schemas/8.10";
import {
  ApiDefinition,
  apiDelete,
  apiPost,
  apiPut,
} from "src/utility/api/request";

export const GLOBAL_TASK_LISTENERS_ENDPOINT = "/global-task-listeners";

export const LISTENER_EVENT_TYPES: GlobalTaskListenerEventType[] = [
  ...globalTaskListenerEventTypeSchema.options,
];

export const searchGlobalTaskListeners: ApiDefinition<
  QueryGlobalTaskListenersResponseBody,
  QueryGlobalTaskListenersRequestBody | undefined
> = (params = {}) => {
  return apiPost(`${GLOBAL_TASK_LISTENERS_ENDPOINT}/search`, params);
};

export const createGlobalTaskListener: ApiDefinition<
  undefined,
  CreateGlobalTaskListenerRequestBody
> = (params) => apiPost(GLOBAL_TASK_LISTENERS_ENDPOINT, params);

export const updateGlobalTaskListener: ApiDefinition<
  undefined,
  CreateGlobalTaskListenerRequestBody
> = (params) => {
  const { id, ...listener } = params;
  return apiPut(`${GLOBAL_TASK_LISTENERS_ENDPOINT}/${id}`, listener);
};

export const deleteGlobalTaskListener: ApiDefinition<
  undefined,
  Pick<GlobalTaskListener, "id">
> = ({ id }) => apiDelete(`${GLOBAL_TASK_LISTENERS_ENDPOINT}/${id}`);
