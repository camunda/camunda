/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  ClusterVariable as BaseClusterVariable,
  ClusterVariableScope,
} from "@camunda/camunda-api-zod-schemas/8.9";
import {
  ApiDefinition,
  apiDelete,
  apiGet,
  apiPost,
  apiPut,
} from "src/utility/api/request";
import { PageSearchParams, SearchResponse } from "src/utility/api";

export const CLUSTER_VARIABLES_ENDPOINT = "/cluster-variables";

export type { ClusterVariableScope };

export type ClusterVariable = BaseClusterVariable;

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

type SearchClusterVariableParams = {
  clusterVariableNames: string[];
};

export const searchClusterVariables: ApiDefinition<
  SearchResponse<ClusterVariable>,
  Partial<SearchClusterVariableParams & PageSearchParams> | undefined
> = (params = {}) => {
  const { clusterVariableNames, ...restParams } = params;

  const filters = clusterVariableNames
    ? { filter: { name: { $in: clusterVariableNames } } }
    : undefined;

  return apiPost(`${CLUSTER_VARIABLES_ENDPOINT}/search`, {
    ...restParams,
    ...filters,
  });
};

type GetClusterVariableParams = {
  scope: ClusterVariableScope;
  tenantId?: string | null;
  name: string;
};

export const getClusterVariableDetails: ApiDefinition<
  SearchResponse<ClusterVariable>,
  GetClusterVariableParams
> = ({ scope, tenantId, name }) =>
  apiGet(`${buildEndpoint(scope, tenantId)}/${name}`);

type CreateClusterVariableParams = GetClusterVariableParams & {
  value: string;
};

export const createClusterVariable: ApiDefinition<
  undefined,
  CreateClusterVariableParams
> = ({ scope, tenantId, ...params }) =>
  apiPost(buildEndpoint(scope, tenantId), params);

export type DeleteClusterVariableParams = GetClusterVariableParams;

export const deleteClusterVariable: ApiDefinition<
  undefined,
  DeleteClusterVariableParams
> = ({ scope, tenantId, name }) =>
  apiDelete(`${buildEndpoint(scope, tenantId)}/${name}`);

type UpdateClusterVariableParams = {
  scope: ClusterVariableScope;
  tenantId?: string | null;
  name: string;
  value: string;
};

export const updateClusterVariable: ApiDefinition<
  ClusterVariable,
  UpdateClusterVariableParams
> = ({ scope, tenantId, name, value }) =>
  apiPut(`${buildEndpoint(scope, tenantId)}/${name}`, { value });
