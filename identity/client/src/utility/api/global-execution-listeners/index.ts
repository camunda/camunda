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

export const GLOBAL_EXECUTION_LISTENERS_ENDPOINT =
  "/global-execution-listeners";

export type GlobalExecutionListener = {
  id: string;
  type: string;
  eventTypes: string[];
  elementTypes?: string[];
  categories?: string[];
  retries?: number;
  afterNonGlobal?: boolean;
  priority?: number;
  source?: string;
};

export type CreateGlobalExecutionListenerRequestBody = {
  id: string;
  type: string;
  eventTypes: string[];
  elementTypes?: string[];
  categories?: string[];
  retries?: number;
  afterNonGlobal?: boolean;
  priority?: number;
};

export type QueryGlobalExecutionListenersResponseBody = {
  items: GlobalExecutionListener[];
  page: {
    totalItems: number;
  };
};

export type QueryGlobalExecutionListenersRequestBody = {
  filter?: Record<string, unknown>;
  sort?: Array<Record<string, unknown>>;
  page?: Record<string, unknown>;
};

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
