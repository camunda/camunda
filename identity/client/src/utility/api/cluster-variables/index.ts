/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  ClusterVariable,
  ClusterVariableScope,
  QueryClusterVariablesRequestBody,
  QueryClusterVariablesResponseBody,
  CreateClusterVariableRequestBody,
} from "@camunda/camunda-api-zod-schemas/8.10";
import {
  ApiDefinition,
  apiDelete,
  apiGet,
  apiPost,
  apiPut,
} from "src/utility/api/request";

export const CLUSTER_VARIABLES_ENDPOINT = "/cluster-variables";

function buildEndpoint(
  scopeType: ClusterVariableScope,
  tenantId?: string | null,
): string {
  switch (scopeType) {
    case "GLOBAL":
      return `${CLUSTER_VARIABLES_ENDPOINT}/global`;
    case "TENANT":
      if (!tenantId) {
        throw new Error("tenantId is required for TENANT scope");
      }
      return `${CLUSTER_VARIABLES_ENDPOINT}/tenants/${tenantId}`;
    default:
      throw new Error(`Unknown scope type: ${scopeType}`);
  }
}

export const searchClusterVariables: ApiDefinition<
  QueryClusterVariablesResponseBody,
  | (QueryClusterVariablesRequestBody & { clusterVariableNames?: string[] })
  | undefined
> = (params = {}) => {
  const { clusterVariableNames, ...restParams } = params;

  const filters: QueryClusterVariablesRequestBody | undefined =
    clusterVariableNames
      ? { filter: { name: { $in: clusterVariableNames } } }
      : undefined;

  return apiPost(`${CLUSTER_VARIABLES_ENDPOINT}/search`, {
    ...restParams,
    ...filters,
  });
};

export const getClusterVariableDetails: ApiDefinition<
  QueryClusterVariablesResponseBody,
  Pick<ClusterVariable, "scope" | "tenantId" | "name">
> = ({ scope, tenantId, name }) =>
  apiGet(`${buildEndpoint(scope, tenantId)}/${name}`);

export const createClusterVariable: ApiDefinition<
  undefined,
  CreateClusterVariableRequestBody & Pick<ClusterVariable, "scope" | "tenantId">
> = ({ scope, tenantId, ...params }) =>
  apiPost(buildEndpoint(scope, tenantId), params);

export const deleteClusterVariable: ApiDefinition<
  undefined,
  Pick<ClusterVariable, "scope" | "tenantId" | "name">
> = ({ scope, tenantId, name }) =>
  apiDelete(`${buildEndpoint(scope, tenantId)}/${name}`);

export const updateClusterVariable: ApiDefinition<
  ClusterVariable,
  ClusterVariable
> = ({ scope, tenantId, name, value }) =>
  apiPut(`${buildEndpoint(scope, tenantId)}/${name}`, { value });
