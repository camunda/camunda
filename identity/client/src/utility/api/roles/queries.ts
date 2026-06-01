/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { queryOptions } from "@tanstack/react-query";
import type {
  QueryClientsByRoleRequestBody,
  QueryGroupsByRoleRequestBody,
  QueryMappingRulesByRoleRequestBody,
  QueryRolesRequestBody,
} from "@camunda/camunda-api-zod-schemas/8.10";
import { getApiBaseUrl } from "src/configuration/urlConfig";
import { unwrap } from "../request";
import { queryKeys } from "../queryKeys";
import {
  getClientsByRoleId,
  getGroupsByRoleId,
  getMappingRulesByRoleId,
  getRoleDetails,
  searchRoles,
} from ".";

export const roleQueries = {
  search: (params?: QueryRolesRequestBody | Record<string, unknown>) =>
    queryOptions({
      queryKey: queryKeys.roles.search(params),
      queryFn: () =>
        unwrap(searchRoles(params as QueryRolesRequestBody)(getApiBaseUrl())),
    }),
  detail: (roleId: string | undefined) =>
    queryOptions({
      queryKey: queryKeys.roles.detail(roleId ?? ""),
      queryFn: () =>
        unwrap(getRoleDetails({ roleId: roleId as string })(getApiBaseUrl())),
      enabled: !!roleId,
    }),
  mappingRules: (
    roleId: string,
    params?: QueryMappingRulesByRoleRequestBody | Record<string, unknown>,
  ) =>
    queryOptions({
      queryKey: queryKeys.roles.mappingRules(roleId, params),
      queryFn: () =>
        unwrap(
          getMappingRulesByRoleId({
            roleId,
            ...(params as QueryMappingRulesByRoleRequestBody),
          })(getApiBaseUrl()),
        ),
      enabled: !!roleId,
    }),
  groups: (
    roleId: string,
    params?: QueryGroupsByRoleRequestBody | Record<string, unknown>,
  ) =>
    queryOptions({
      queryKey: queryKeys.roles.groups(roleId, params),
      queryFn: () =>
        unwrap(
          getGroupsByRoleId({
            roleId,
            ...(params as QueryGroupsByRoleRequestBody),
          })(getApiBaseUrl()),
        ),
      enabled: !!roleId,
    }),
  clients: (
    roleId: string,
    params?: QueryClientsByRoleRequestBody | Record<string, unknown>,
  ) =>
    queryOptions({
      queryKey: queryKeys.roles.clients(roleId, params),
      queryFn: () =>
        unwrap(
          getClientsByRoleId({
            roleId,
            ...(params as QueryClientsByRoleRequestBody),
          })(getApiBaseUrl()),
        ),
      enabled: !!roleId,
    }),
};
