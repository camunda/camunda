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

export type GlobalExecutionListenerEventType =
  | "start"
  | "end"
  | "cancel"
  | "all";

export type GlobalExecutionListenerElementType =
  | "process"
  | "subprocess"
  | "eventSubprocess"
  | "serviceTask"
  | "userTask"
  | "sendTask"
  | "receiveTask"
  | "scriptTask"
  | "businessRuleTask"
  | "callActivity"
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
  | "events";

export type GlobalExecutionListenerSource = "CONFIGURATION" | "API";

export interface GlobalExecutionListener {
  id: string;
  type: string;
  eventTypes: GlobalExecutionListenerEventType[];
  elementTypes?: GlobalExecutionListenerElementType[];
  categories?: GlobalExecutionListenerCategory[];
  retries: number;
  afterNonGlobal: boolean;
  priority: number;
  source: GlobalExecutionListenerSource;
}

export interface CreateGlobalExecutionListenerRequestBody {
  id: string;
  type: string;
  eventTypes: GlobalExecutionListenerEventType[];
  elementTypes?: GlobalExecutionListenerElementType[];
  categories?: GlobalExecutionListenerCategory[];
  retries: number;
  afterNonGlobal: boolean;
  priority: number;
}

export interface QueryGlobalExecutionListenersResponseBody {
  items: GlobalExecutionListener[];
  page: {
    totalItems: number;
  };
}

export interface QueryGlobalExecutionListenersRequestBody {
  page?: {
    from?: number;
    limit?: number;
  };
  sort?: {
    field: string;
    order: "ASC" | "DESC";
  }[];
  filter?: {
    id?: string;
  };
}

export const LISTENER_EVENT_TYPES: GlobalExecutionListenerEventType[] = [
  "all",
  "start",
  "end",
  "cancel",
];

export const LISTENER_ELEMENT_TYPES: GlobalExecutionListenerElementType[] = [
  "process",
  "subprocess",
  "eventSubprocess",
  "serviceTask",
  "userTask",
  "sendTask",
  "receiveTask",
  "scriptTask",
  "businessRuleTask",
  "callActivity",
  "multiInstanceBody",
  "exclusiveGateway",
  "parallelGateway",
  "inclusiveGateway",
  "eventBasedGateway",
  "startEvent",
  "endEvent",
  "intermediateCatchEvent",
  "intermediateThrowEvent",
  "boundaryEvent",
];

export const LISTENER_CATEGORIES: GlobalExecutionListenerCategory[] = [
  "all",
  "tasks",
  "gateways",
  "events",
];

export const GLOBAL_EXECUTION_LISTENERS_ENDPOINT =
  "/global-execution-listeners";

export const searchGlobalExecutionListeners: ApiDefinition<
  QueryGlobalExecutionListenersResponseBody,
  QueryGlobalExecutionListenersRequestBody | undefined
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
