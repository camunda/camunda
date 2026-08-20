/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { queryOptions } from "@tanstack/react-query";
import type {
  QueryUsersByGroupRequestBody,
  QueryUsersByRoleRequestBody,
  QueryUsersByTenantRequestBody,
} from "@camunda/camunda-api-zod-schemas/8.10";
import { getApiBaseUrl } from "src/configuration/urlConfig";
import { unwrap } from "../request";
import { queryKeys } from "../queryKeys";
import {
  getMembersByRole,
  getMembersByTenantId,
  searchMembersByGroup,
} from ".";

export const membershipQueries = {
  groupMembers: (
    groupId: string,
    params?: QueryUsersByGroupRequestBody | Record<string, unknown>,
  ) =>
    queryOptions({
      queryKey: queryKeys.groups.members(groupId, params),
      queryFn: () =>
        unwrap(
          searchMembersByGroup({
            groupId,
            ...(params as QueryUsersByGroupRequestBody),
          })(getApiBaseUrl()),
        ),
      enabled: !!groupId,
    }),
  tenantMembers: (
    tenantId: string,
    params?: QueryUsersByTenantRequestBody | Record<string, unknown>,
  ) =>
    queryOptions({
      queryKey: queryKeys.tenants.members(tenantId, params),
      queryFn: () =>
        unwrap(
          getMembersByTenantId({
            tenantId,
            ...(params as QueryUsersByTenantRequestBody),
          })(getApiBaseUrl()),
        ),
      enabled: !!tenantId,
    }),
  roleMembers: (
    roleId: string,
    params?: QueryUsersByRoleRequestBody | Record<string, unknown>,
  ) =>
    queryOptions({
      queryKey: queryKeys.roles.members(roleId, params),
      queryFn: () =>
        unwrap(
          getMembersByRole({
            roleId,
            ...(params as QueryUsersByRoleRequestBody),
          })(getApiBaseUrl()),
        ),
      enabled: !!roleId,
    }),
};
