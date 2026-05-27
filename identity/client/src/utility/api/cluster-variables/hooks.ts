/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  ClusterVariable,
  CreateClusterVariableRequestBody,
  QueryClusterVariablesRequestBody,
} from "@camunda/camunda-api-zod-schemas/8.10";
import { getApiBaseUrl } from "src/configuration/urlConfig";
import { unwrap } from "../request";
import { queryKeys } from "../queryKeys";
import {
  createClusterVariable,
  deleteClusterVariable,
  getClusterVariableDetails,
  searchClusterVariables,
  updateClusterVariable,
} from ".";

type SearchClusterVariablesParams = QueryClusterVariablesRequestBody & {
  clusterVariableNames?: string[];
};

export const useSearchClusterVariables = (
  params?: SearchClusterVariablesParams | Record<string, unknown>,
  options?: { enabled?: boolean },
) =>
  useQuery({
    queryKey: queryKeys.clusterVariables.search(params),
    queryFn: () =>
      unwrap(
        searchClusterVariables(params as SearchClusterVariablesParams)(
          getApiBaseUrl(),
        ),
      ),
    enabled: options?.enabled,
  });

export const useClusterVariableDetails = (
  params: Pick<ClusterVariable, "scope" | "tenantId" | "name">,
) =>
  useQuery({
    queryKey: ["clusterVariables", "detail", params],
    queryFn: () => unwrap(getClusterVariableDetails(params)(getApiBaseUrl())),
    enabled: !!params.name && !!params.scope,
  });

export const useCreateClusterVariable = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (
      body: CreateClusterVariableRequestBody &
        Pick<ClusterVariable, "scope" | "tenantId">,
    ) => unwrap(createClusterVariable(body)(getApiBaseUrl())),
    meta: { skipErrorNotification: true },
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.clusterVariables.all }),
  });
};

export const useUpdateClusterVariable = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ClusterVariable) =>
      unwrap(updateClusterVariable(body)(getApiBaseUrl())),
    meta: { skipErrorNotification: true },
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.clusterVariables.all }),
  });
};

export const useDeleteClusterVariable = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<ClusterVariable, "scope" | "tenantId" | "name">) =>
      unwrap(deleteClusterVariable(body)(getApiBaseUrl())),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.clusterVariables.all }),
  });
};
