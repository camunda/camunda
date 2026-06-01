/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { queryOptions } from "@tanstack/react-query";
import type {
  ClusterVariable,
  QueryClusterVariablesRequestBody,
} from "@camunda/camunda-api-zod-schemas/8.10";
import { getApiBaseUrl } from "src/configuration/urlConfig";
import { unwrap } from "../request";
import { queryKeys } from "../queryKeys";
import { getClusterVariableDetails, searchClusterVariables } from ".";

type SearchClusterVariablesParams = QueryClusterVariablesRequestBody & {
  clusterVariableNames?: string[];
};

export const clusterVariableQueries = {
  search: (params?: SearchClusterVariablesParams | Record<string, unknown>) =>
    queryOptions({
      queryKey: queryKeys.clusterVariables.search(params),
      queryFn: () =>
        unwrap(
          searchClusterVariables(params as SearchClusterVariablesParams)(
            getApiBaseUrl(),
          ),
        ),
    }),
  detail: (params: Pick<ClusterVariable, "scope" | "tenantId" | "name">) =>
    queryOptions({
      queryKey: ["clusterVariables", "detail", params],
      queryFn: () => unwrap(getClusterVariableDetails(params)(getApiBaseUrl())),
      enabled: !!params.name && !!params.scope,
    }),
};
