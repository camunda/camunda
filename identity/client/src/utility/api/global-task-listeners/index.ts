/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { ApiDefinition, ApiPromise } from "src/utility/api/request";
import { SearchResponse } from "src/utility/api";
import { PageSearchParams } from "../hooks/usePagination";

export const GLOBAL_TASK_LISTENERS_ENDPOINT = "/global-listeners/user-task";

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

export type GlobalTaskListenerKeys =
  | "id"
  | "type"
  | "eventTypes"
  | "retries"
  | "afterNonGlobal";

/*
 * TODO: Replace mock implementation with actual OC REST API calls
 *
 * The following endpoints need to be implemented:
 *
 * 1. GET /v2/global-task-listeners/{id}
 *    - Retrieve listener by id
 *    - Permission: GLOBAL_LISTENER:READ
 *    - Response: { id, type, event-types, retries, after-non-global, priority, source }
 *
 * 2. POST /v2/global-task-listeners
 *    - Create new listener
 *    - Permission: GLOBAL_LISTENER:CREATE_TASK_LISTENER
 *    - Payload: { id, type, event-types, retries?, after-non-global?, priority? }
 *    - Note: source field is automatically set to API
 *
 * 3. PUT /v2/global-task-listeners/{id}
 *    - Update existing listener
 *    - Permission: GLOBAL_LISTENER:UPDATE_TASK_LISTENER
 *    - Payload: { type, event-types, retries?, after-non-global?, priority? }
 *    - Note: id and source fields cannot be changed
 *
 * 4. DELETE /v2/global-task-listeners/{id}
 *    - Delete existing listener
 *    - Permission: GLOBAL_LISTENER:DELETE_TASK_LISTENER
 */

// Mock data store for prototype - will be replaced with actual API calls
let mockGlobalTaskListeners: GlobalTaskListener[] = [
  {
    id: "example-listener-1",
    type: "io.camunda.MyGlobalTaskListener",
    eventTypes: ["creating", "completing"],
    retries: 3,
    afterNonGlobal: false,
    priority: 10,
    source: "API",
  },
  {
    id: "example-listener-2",
    type: "io.camunda.AuditListener",
    eventTypes: ["all"],
    retries: 5,
    afterNonGlobal: true,
    priority: 5,
    source: "API",
  },
];

// Helper to simulate async API delay
const simulateDelay = (ms: number): Promise<void> =>
  new Promise((resolve) => setTimeout(resolve, ms));

// Mock API functions - these simulate API calls and will be replaced with real endpoints later
// TODO: Implement actual API call to POST /v2/global-task-listeners/search (if search endpoint is needed)
export const searchGlobalTaskListeners: ApiDefinition<
  SearchResponse<GlobalTaskListener>,
  PageSearchParams | Record<string, unknown> | undefined
> = () => async (): ApiPromise<SearchResponse<GlobalTaskListener>> => {
  await simulateDelay(300);
  return {
    success: true,
    data: {
      items: [...mockGlobalTaskListeners],
    },
    error: null,
    status: 200,
  };
};

type GetGlobalTaskListenerParams = {
  id: string;
};

// TODO: Implement actual API call to GET /v2/global-task-listeners/{id}
export const getGlobalTaskListenerDetails: ApiDefinition<
  GlobalTaskListener,
  GetGlobalTaskListenerParams
> =
  ({ id }) =>
  async (): ApiPromise<GlobalTaskListener> => {
    await simulateDelay(200);
    const listener = mockGlobalTaskListeners.find((l) => l.id === id);
    if (listener) {
      return { success: true, data: listener, error: null, status: 200 };
    }
    return {
      success: false,
      data: null,
      error: null,
      status: 404,
    };
  };

export type CreateGlobalTaskListenerParams = Omit<GlobalTaskListener, "id" | "source"> & {
  id?: string;
};

// TODO: Implement actual API call to POST /v2/global-task-listeners
export const createGlobalTaskListener: ApiDefinition<
  undefined,
  CreateGlobalTaskListenerParams
> = (params) => async (): ApiPromise<undefined> => {
  await simulateDelay(300);
  const newListener: GlobalTaskListener = {
    id:
      params.id ||
      `listener-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    type: params.type,
    eventTypes: params.eventTypes,
    retries: params.retries,
    afterNonGlobal: params.afterNonGlobal,
    priority: params.priority,
    source: "API",
  };
  mockGlobalTaskListeners.push(newListener);
  return { success: true, data: undefined, error: null, status: 201 };
};

export type UpdateGlobalTaskListenerParams = Omit<GlobalTaskListener, "source"> & {
  id: string;
};

// TODO: Implement actual API call to PUT /v2/global-task-listeners/{id}
export const updateGlobalTaskListener: ApiDefinition<
  undefined,
  UpdateGlobalTaskListenerParams
> = (params) => async (): ApiPromise<undefined> => {
  await simulateDelay(300);
  const index = mockGlobalTaskListeners.findIndex((l) => l.id === params.id);
  if (index !== -1) {
    mockGlobalTaskListeners[index] = {
      ...params,
      source: mockGlobalTaskListeners[index].source,
    };
    return { success: true, data: undefined, error: null, status: 200 };
  }
  return { success: false, data: null, error: null, status: 404 };
};

export type DeleteGlobalTaskListenerParams = {
  id: string;
  type: string;
};

// TODO: Implement actual API call to DELETE /v2/global-task-listeners/{id}
export const deleteGlobalTaskListener: ApiDefinition<undefined, { id: string }> =
  ({ id }) =>
  async (): ApiPromise<undefined> => {
    await simulateDelay(300);
    const index = mockGlobalTaskListeners.findIndex((l) => l.id === id);
    if (index !== -1) {
      mockGlobalTaskListeners.splice(index, 1);
      return { success: true, data: undefined, error: null, status: 200 };
    }
    return { success: false, data: null, error: null, status: 404 };
  };
