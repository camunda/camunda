/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  ApiDefinition,
  apiDelete,
  apiPost,
  apiPut,
} from "src/utility/api/request";
import { SearchResponse, PageSearchParams } from "src/utility/api";

export const GLOBAL_TASK_LISTENERS_ENDPOINT = "/global-task-listeners";

export enum ListenerSource {
  CONFIGURATION = "CONFIGURATION",
  API = "API",
}

export enum ListenerEventType {
  ALL = "all",
  CREATING = "creating",
  UPDATING = "updating",
  ASSIGNING = "assigning",
  COMPLETING = "completing",
  CANCELING = "canceling",
}

export const LISTENER_EVENT_TYPES = Object.values(ListenerEventType);

export type GlobalTaskListener = {
  id: string;
  type: string;
  eventTypes: ListenerEventType[];
  retries?: number;
  afterNonGlobal?: boolean;
  priority?: number;
  source?: ListenerSource;
};

export const searchGlobalTaskListeners: ApiDefinition<
  SearchResponse<GlobalTaskListener>,
  PageSearchParams | Record<string, unknown> | undefined
> = (params = {}) => {
  return apiPost(`${GLOBAL_TASK_LISTENERS_ENDPOINT}/search`, params);
};

export type CreateGlobalTaskListenerParams = Omit<GlobalTaskListener, "source">;

export const createGlobalTaskListener: ApiDefinition<
  undefined,
  CreateGlobalTaskListenerParams
> = (params) => apiPost(GLOBAL_TASK_LISTENERS_ENDPOINT, params);

export type UpdateGlobalTaskListenerParams = Omit<GlobalTaskListener, "source">;

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
