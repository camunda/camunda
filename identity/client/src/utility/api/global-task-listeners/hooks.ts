/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  CreateGlobalTaskListenerRequestBody,
  GlobalTaskListener,
  QueryGlobalTaskListenersRequestBody,
} from "@camunda/camunda-api-zod-schemas/8.10";
import { getApiBaseUrl } from "src/configuration/urlConfig";
import { unwrap } from "../request";
import { queryKeys } from "../queryKeys";
import {
  createGlobalTaskListener,
  deleteGlobalTaskListener,
  searchGlobalTaskListeners,
  updateGlobalTaskListener,
} from ".";

export const useSearchGlobalTaskListeners = (
  params?: QueryGlobalTaskListenersRequestBody,
  options?: { enabled?: boolean },
) =>
  useQuery({
    queryKey: queryKeys.globalTaskListeners.search(params),
    queryFn: () => unwrap(searchGlobalTaskListeners(params)(getApiBaseUrl())),
    enabled: options?.enabled,
  });

export const useCreateGlobalTaskListener = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateGlobalTaskListenerRequestBody) =>
      unwrap(createGlobalTaskListener(body)(getApiBaseUrl())),
    meta: { skipErrorNotification: true },
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.globalTaskListeners.all }),
  });
};

export const useUpdateGlobalTaskListener = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateGlobalTaskListenerRequestBody) =>
      unwrap(updateGlobalTaskListener(body)(getApiBaseUrl())),
    meta: { skipErrorNotification: true },
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.globalTaskListeners.all }),
  });
};

export const useDeleteGlobalTaskListener = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<GlobalTaskListener, "id">) =>
      unwrap(deleteGlobalTaskListener(body)(getApiBaseUrl())),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.globalTaskListeners.all }),
  });
};
