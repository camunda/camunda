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

export type GlobalExecutionListenerEventType = "start" | "end";

export type GlobalExecutionListenerElementType =
  | "serviceTask"
  | "userTask"
  | "sendTask"
  | "receiveTask"
  | "scriptTask"
  | "businessRuleTask"
  | "callActivity"
  | "subProcess"
  | "eventSubProcess"
  | "multiInstanceBody"
  | "exclusiveGateway"
  | "parallelGateway"
  | "inclusiveGateway"
  | "eventBasedGateway"
  | "startEvent"
  | "endEvent"
  | "intermediateCatchEvent"
  | "intermediateThrowEvent"
  | "boundaryEvent";

export type GlobalExecutionListenerCategory =
  | "all"
  | "tasks"
  | "gateways"
  | "events"
  | "containers";

export interface GlobalExecutionListener {
  id: string;
  type: string;
  eventTypes: GlobalExecutionListenerEventType[];
  retries: number;
  afterNonGlobal: boolean;
  priority: number;
  elementTypes?: GlobalExecutionListenerElementType[];
  categories?: GlobalExecutionListenerCategory[];
}

export interface CreateGlobalExecutionListenerRequestBody {
  id: string;
  type: string;
  eventTypes: GlobalExecutionListenerEventType[];
  retries: number;
  afterNonGlobal: boolean;
  priority: number;
  elementTypes?: GlobalExecutionListenerElementType[];
  categories?: GlobalExecutionListenerCategory[];
}

export interface QueryGlobalExecutionListenersResponseBody {
  items: GlobalExecutionListener[];
  page?: {
    totalItems: number;
  };
}

export const GLOBAL_EXECUTION_LISTENERS_ENDPOINT =
  "/global-execution-listeners";

export const LISTENER_EVENT_TYPES: GlobalExecutionListenerEventType[] = [
  "start",
  "end",
];

export const LISTENER_CATEGORIES: GlobalExecutionListenerCategory[] = [
  "all",
  "tasks",
  "gateways",
  "events",
  "containers",
];

export const searchGlobalExecutionListeners: ApiDefinition<
  QueryGlobalExecutionListenersResponseBody,
  Record<string, unknown> | undefined
> = (params = {}) => {
  return apiPost(`${GLOBAL_EXECUTION_LISTENERS_ENDPOINT}/search`, params);
};

export const createGlobalExecutionListener: ApiDefinition<
  undefined,
  CreateGlobalExecutionListenerRequestBody
> = (params) => apiPost(GLOBAL_EXECUTION_LISTENERS_ENDPOINT, params);

export const updateGlobalExecutionListener: ApiDefinition<
  undefined,
  CreateGlobalExecutionListenerRequestBody
> = (params) => {
  const { id, ...listener } = params;
  return apiPut(`${GLOBAL_EXECUTION_LISTENERS_ENDPOINT}/${id}`, listener);
};

export const deleteGlobalExecutionListener: ApiDefinition<
  undefined,
  Pick<GlobalExecutionListener, "id">
> = ({ id }) => apiDelete(`${GLOBAL_EXECUTION_LISTENERS_ENDPOINT}/${id}`);
