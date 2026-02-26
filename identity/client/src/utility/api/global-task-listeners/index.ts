/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  GlobalTaskListener as BaseGlobalTaskListener,
  GlobalTaskListenerEventType,
  GlobalListenerSource,
  CreateGlobalTaskListenerRequestBody,
} from "@camunda/camunda-api-zod-schemas/8.9";
import { globalTaskListenerEventTypeSchema } from "@camunda/camunda-api-zod-schemas/8.9";
import {
  ApiDefinition,
  apiDelete,
  apiPost,
  apiPut,
} from "src/utility/api/request";
import { SearchResponse, PageSearchParams } from "src/utility/api";

export const GLOBAL_TASK_LISTENERS_ENDPOINT = "/global-task-listeners";

export type { GlobalTaskListenerEventType, GlobalListenerSource };

export const LISTENER_EVENT_TYPES: GlobalTaskListenerEventType[] = [
  ...globalTaskListenerEventTypeSchema.options,
];

export type GlobalTaskListener = BaseGlobalTaskListener;

export const searchGlobalTaskListeners: ApiDefinition<
  SearchResponse<GlobalTaskListener>,
  PageSearchParams | Record<string, unknown> | undefined
> = (params = {}) => {
  return apiPost(`${GLOBAL_TASK_LISTENERS_ENDPOINT}/search`, params);
};

export type CreateGlobalTaskListenerParams =
  CreateGlobalTaskListenerRequestBody;

export const createGlobalTaskListener: ApiDefinition<
  undefined,
  CreateGlobalTaskListenerParams
> = (params) => apiPost(GLOBAL_TASK_LISTENERS_ENDPOINT, params);

export type UpdateGlobalTaskListenerParams =
  CreateGlobalTaskListenerRequestBody;

export const updateGlobalTaskListener: ApiDefinition<
  undefined,
  UpdateGlobalTaskListenerParams
> = (params) => {
  const { id, ...listener } = params;
  return apiPut(`${GLOBAL_TASK_LISTENERS_ENDPOINT}/${id}`, listener);
};

export type DeleteGlobalTaskListenerParams = {
  id: string;
};

export const deleteGlobalTaskListener: ApiDefinition<
  undefined,
  DeleteGlobalTaskListenerParams
> = ({ id }) => apiDelete(`${GLOBAL_TASK_LISTENERS_ENDPOINT}/${id}`);
