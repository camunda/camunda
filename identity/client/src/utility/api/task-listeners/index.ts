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

export const TASK_LISTENERS_ENDPOINT = "/task-listeners";

export type TaskListener = {
  id: string;
  type: string;
  eventTypes: string[];
  retries: number;
  afterNonGlobal: boolean;
  priority: number;
  source?: "CONFIGURATION" | "API";
};

export type TaskListenerKeys =
  | "id"
  | "type"
  | "eventTypes"
  | "retries"
  | "afterNonGlobal";

export const EVENT_TYPE_OPTIONS = [
  "all",
  "creating",
  "updating",
  "assigning",
  "completing",
  "canceling",
] as const;

export type EventTypeOption = (typeof EVENT_TYPE_OPTIONS)[number];

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
let mockTaskListeners: TaskListener[] = [
  {
    id: "example-listener-1",
    type: "io.camunda.MyTaskListener",
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
export const searchTaskListeners: ApiDefinition<
  SearchResponse<TaskListener>,
  PageSearchParams | Record<string, unknown> | undefined
> = () => async (): ApiPromise<SearchResponse<TaskListener>> => {
  await simulateDelay(300);
  return {
    success: true,
    data: {
      items: [...mockTaskListeners],
    },
    error: null,
    status: 200,
  };
};

type GetTaskListenerParams = {
  id: string;
};

// TODO: Implement actual API call to GET /v2/global-task-listeners/{id}
export const getTaskListenerDetails: ApiDefinition<
  TaskListener,
  GetTaskListenerParams
> =
  ({ id }) =>
  async (): ApiPromise<TaskListener> => {
    await simulateDelay(200);
    const listener = mockTaskListeners.find((l) => l.id === id);
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

export type CreateTaskListenerParams = Omit<TaskListener, "id" | "source"> & {
  id?: string;
};

// TODO: Implement actual API call to POST /v2/global-task-listeners
export const createTaskListener: ApiDefinition<
  undefined,
  CreateTaskListenerParams
> = (params) => async (): ApiPromise<undefined> => {
  await simulateDelay(300);
  const newListener: TaskListener = {
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
  mockTaskListeners.push(newListener);
  return { success: true, data: undefined, error: null, status: 201 };
};

export type UpdateTaskListenerParams = Omit<TaskListener, "source"> & {
  id: string;
};

// TODO: Implement actual API call to PUT /v2/global-task-listeners/{id}
export const updateTaskListener: ApiDefinition<
  undefined,
  UpdateTaskListenerParams
> = (params) => async (): ApiPromise<undefined> => {
  await simulateDelay(300);
  const index = mockTaskListeners.findIndex((l) => l.id === params.id);
  if (index !== -1) {
    mockTaskListeners[index] = {
      ...params,
      source: mockTaskListeners[index].source,
    };
    return { success: true, data: undefined, error: null, status: 200 };
  }
  return { success: false, data: null, error: null, status: 404 };
};

export type DeleteTaskListenerParams = {
  id: string;
  type: string;
};

// TODO: Implement actual API call to DELETE /v2/global-task-listeners/{id}
export const deleteTaskListener: ApiDefinition<undefined, { id: string }> =
  ({ id }) =>
  async (): ApiPromise<undefined> => {
    await simulateDelay(300);
    const index = mockTaskListeners.findIndex((l) => l.id === id);
    if (index !== -1) {
      mockTaskListeners.splice(index, 1);
      return { success: true, data: undefined, error: null, status: 200 };
    }
    return { success: false, data: null, error: null, status: 404 };
  };
