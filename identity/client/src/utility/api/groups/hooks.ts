/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  Group,
  MappingRule,
  QueryClientsByGroupRequestBody,
  QueryGroupsRequestBody,
  QueryMappingRulesByGroupRequestBody,
  QueryRolesByGroupRequestBody,
  Role,
  TenantClient,
} from "@camunda/camunda-api-zod-schemas/8.10";
import { getApiBaseUrl } from "src/configuration/urlConfig";
import { unwrap } from "../request";
import { queryKeys } from "../queryKeys";
import {
  assignGroupClient,
  assignGroupMappingRule,
  assignGroupRole,
  createGroup,
  deleteGroup,
  getClientsByGroupId,
  getGroupDetails,
  getMappingRulesByGroupId,
  searchGroups,
  searchRolesByGroupId,
  unassignGroupClient,
  unassignGroupMappingRule,
  unassignGroupRole,
  updateGroup,
} from ".";

type SearchGroupsParams = QueryGroupsRequestBody & { groupIds?: string[] };

export const useSearchGroups = (
  params?: SearchGroupsParams | Record<string, unknown>,
  options?: { enabled?: boolean },
) =>
  useQuery({
    queryKey: queryKeys.groups.search(params),
    queryFn: () =>
      unwrap(searchGroups(params as SearchGroupsParams)(getApiBaseUrl())),
    enabled: options?.enabled,
  });

export const useGroupDetails = (groupId: string | undefined) =>
  useQuery({
    queryKey: queryKeys.groups.detail(groupId ?? ""),
    queryFn: () =>
      unwrap(getGroupDetails({ groupId: groupId as string })(getApiBaseUrl())),
    enabled: !!groupId,
  });

export const useCreateGroup = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Group) => unwrap(createGroup(body)(getApiBaseUrl())),
    meta: { skipErrorNotification: true },
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.groups.all }),
  });
};

export const useUpdateGroup = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Group) => unwrap(updateGroup(body)(getApiBaseUrl())),
    meta: { skipErrorNotification: true },
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.groups.all }),
  });
};

export const useDeleteGroup = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<Group, "groupId">) =>
      unwrap(deleteGroup(body)(getApiBaseUrl())),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.groups.all }),
  });
};

// ----------------- Roles within a Group -----------------

export const useGroupRoles = (
  groupId: string,
  params?: QueryRolesByGroupRequestBody | Record<string, unknown>,
) =>
  useQuery({
    queryKey: queryKeys.groups.roles(groupId, params),
    queryFn: () =>
      unwrap(
        searchRolesByGroupId({
          groupId,
          ...(params as QueryRolesByGroupRequestBody),
        })(getApiBaseUrl()),
      ),
    enabled: !!groupId,
  });

export const useAssignGroupRole = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<Group, "groupId"> & Pick<Role, "roleId">) =>
      unwrap(assignGroupRole(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["groups", "roles", variables.groupId],
      }),
  });
};

export const useUnassignGroupRole = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<Group, "groupId"> & Pick<Role, "roleId">) =>
      unwrap(unassignGroupRole(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["groups", "roles", variables.groupId],
      }),
  });
};

// ----------------- Mapping rules within a Group -----------------

export const useGroupMappingRules = (
  groupId: string,
  params?: QueryMappingRulesByGroupRequestBody | Record<string, unknown>,
) =>
  useQuery({
    queryKey: queryKeys.groups.mappingRules(groupId, params),
    queryFn: () =>
      unwrap(
        getMappingRulesByGroupId({
          groupId,
          ...(params as QueryMappingRulesByGroupRequestBody),
        })(getApiBaseUrl()),
      ),
    enabled: !!groupId,
  });

export const useAssignGroupMappingRule = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (
      body: Pick<Group, "groupId"> & Pick<MappingRule, "mappingRuleId">,
    ) => unwrap(assignGroupMappingRule(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["groups", "mappingRules", variables.groupId],
      }),
  });
};

export const useUnassignGroupMappingRule = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (
      body: Pick<Group, "groupId"> & Pick<MappingRule, "mappingRuleId">,
    ) => unwrap(unassignGroupMappingRule(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["groups", "mappingRules", variables.groupId],
      }),
  });
};

// ----------------- Clients within a Group -----------------

export const useGroupClients = (
  groupId: string,
  params?: QueryClientsByGroupRequestBody | Record<string, unknown>,
) =>
  useQuery({
    queryKey: queryKeys.groups.clients(groupId, params),
    queryFn: () =>
      unwrap(
        getClientsByGroupId({
          groupId,
          ...(params as QueryClientsByGroupRequestBody),
        })(getApiBaseUrl()),
      ),
    enabled: !!groupId,
  });

export const useAssignGroupClient = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (
      body: Pick<Group, "groupId"> & Pick<TenantClient, "clientId">,
    ) => unwrap(assignGroupClient(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["groups", "clients", variables.groupId],
      }),
  });
};

export const useUnassignGroupClient = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (
      body: Pick<Group, "groupId"> & Pick<TenantClient, "clientId">,
    ) => unwrap(unassignGroupClient(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["groups", "clients", variables.groupId],
      }),
  });
};
