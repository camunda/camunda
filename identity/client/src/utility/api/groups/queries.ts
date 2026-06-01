/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { queryOptions } from "@tanstack/react-query";
import type {
  QueryClientsByGroupRequestBody,
  QueryGroupsRequestBody,
  QueryMappingRulesByGroupRequestBody,
  QueryRolesByGroupRequestBody,
} from "@camunda/camunda-api-zod-schemas/8.10";
import { getApiBaseUrl } from "src/configuration/urlConfig";
import { unwrap } from "../request";
import { queryKeys } from "../queryKeys";
import {
  getClientsByGroupId,
  getGroupDetails,
  getMappingRulesByGroupId,
  searchGroups,
  searchRolesByGroupId,
} from ".";

type SearchGroupsParams = QueryGroupsRequestBody & { groupIds?: string[] };

export const groupQueries = {
  search: (params?: SearchGroupsParams | Record<string, unknown>) =>
    queryOptions({
      queryKey: queryKeys.groups.search(params),
      queryFn: () =>
        unwrap(searchGroups(params as SearchGroupsParams)(getApiBaseUrl())),
    }),
  detail: (groupId: string | undefined) =>
    queryOptions({
      queryKey: queryKeys.groups.detail(groupId ?? ""),
      queryFn: () =>
        unwrap(
          getGroupDetails({ groupId: groupId as string })(getApiBaseUrl()),
        ),
      enabled: !!groupId,
    }),
  roles: (
    groupId: string,
    params?: QueryRolesByGroupRequestBody | Record<string, unknown>,
  ) =>
    queryOptions({
      queryKey: queryKeys.groups.roles(groupId, params),
      queryFn: () =>
        unwrap(
          searchRolesByGroupId({
            groupId,
            ...(params as QueryRolesByGroupRequestBody),
          })(getApiBaseUrl()),
        ),
      enabled: !!groupId,
    }),
  mappingRules: (
    groupId: string,
    params?: QueryMappingRulesByGroupRequestBody | Record<string, unknown>,
  ) =>
    queryOptions({
      queryKey: queryKeys.groups.mappingRules(groupId, params),
      queryFn: () =>
        unwrap(
          getMappingRulesByGroupId({
            groupId,
            ...(params as QueryMappingRulesByGroupRequestBody),
          })(getApiBaseUrl()),
        ),
      enabled: !!groupId,
    }),
  clients: (
    groupId: string,
    params?: QueryClientsByGroupRequestBody | Record<string, unknown>,
  ) =>
    queryOptions({
      queryKey: queryKeys.groups.clients(groupId, params),
      queryFn: () =>
        unwrap(
          getClientsByGroupId({
            groupId,
            ...(params as QueryClientsByGroupRequestBody),
          })(getApiBaseUrl()),
        ),
      enabled: !!groupId,
    }),
};
