/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { queryOptions } from "@tanstack/react-query";
import type {
  QueryClientsByTenantRequestBody,
  QueryGroupsByTenantRequestBody,
  QueryMappingRulesByTenantRequestBody,
  QueryRolesByTenantRequestBody,
  QueryTenantsRequestBody,
  Tenant,
} from "@camunda/camunda-api-zod-schemas/8.10";
import { getApiBaseUrl } from "src/configuration/urlConfig";
import { unwrap } from "../request";
import { queryKeys } from "../queryKeys";
import {
  getClientsByTenantId,
  getGroupsByTenantId,
  getMappingRulesByTenantId,
  getRolesByTenantId,
  getTenantDetails,
  searchTenant,
} from ".";

export const tenantQueries = {
  search: (params: QueryTenantsRequestBody | Record<string, unknown>) =>
    queryOptions({
      queryKey: queryKeys.tenants.search(params),
      queryFn: () =>
        unwrap(
          searchTenant(params as QueryTenantsRequestBody)(getApiBaseUrl()),
        ),
    }),
  detail: (params: Partial<Pick<Tenant, "tenantId" | "name">>) =>
    queryOptions({
      queryKey: queryKeys.tenants.detail(params.tenantId ?? params.name ?? ""),
      queryFn: () => unwrap(getTenantDetails(params)(getApiBaseUrl())),
      enabled: !!(params.tenantId || params.name),
    }),
  groups: (
    tenantId: string,
    params?: QueryGroupsByTenantRequestBody | Record<string, unknown>,
  ) =>
    queryOptions({
      queryKey: queryKeys.tenants.groups(tenantId, params),
      queryFn: () =>
        unwrap(
          getGroupsByTenantId({
            tenantId,
            ...(params as QueryGroupsByTenantRequestBody),
          })(getApiBaseUrl()),
        ),
      enabled: !!tenantId,
    }),
  roles: (
    tenantId: string,
    params?: QueryRolesByTenantRequestBody | Record<string, unknown>,
  ) =>
    queryOptions({
      queryKey: queryKeys.tenants.roles(tenantId, params),
      queryFn: () =>
        unwrap(
          getRolesByTenantId({
            tenantId,
            ...(params as QueryRolesByTenantRequestBody),
          })(getApiBaseUrl()),
        ),
      enabled: !!tenantId,
    }),
  mappingRules: (
    tenantId: string,
    params?: QueryMappingRulesByTenantRequestBody | Record<string, unknown>,
  ) =>
    queryOptions({
      queryKey: queryKeys.tenants.mappingRules(tenantId, params),
      queryFn: () =>
        unwrap(
          getMappingRulesByTenantId({
            tenantId,
            ...(params as QueryMappingRulesByTenantRequestBody),
          })(getApiBaseUrl()),
        ),
      enabled: !!tenantId,
    }),
  clients: (
    tenantId: string,
    params?: QueryClientsByTenantRequestBody | Record<string, unknown>,
  ) =>
    queryOptions({
      queryKey: queryKeys.tenants.clients(tenantId, params),
      queryFn: () =>
        unwrap(
          getClientsByTenantId({
            tenantId,
            ...(params as QueryClientsByTenantRequestBody),
          })(getApiBaseUrl()),
        ),
      enabled: !!tenantId,
    }),
};
