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
  QueryClientsByRoleRequestBody,
  QueryGroupsByRoleRequestBody,
  QueryMappingRulesByRoleRequestBody,
  QueryRolesRequestBody,
  Role,
  TenantClient,
} from "@camunda/camunda-api-zod-schemas/8.10";
import { getApiBaseUrl } from "src/configuration/urlConfig";
import { unwrap } from "../request";
import { queryKeys } from "../queryKeys";
import {
  assignRoleClient,
  assignRoleGroup,
  assignRoleMappingRule,
  createRole,
  deleteRole,
  getClientsByRoleId,
  getGroupsByRoleId,
  getMappingRulesByRoleId,
  getRoleDetails,
  searchRoles,
  unassignRoleClient,
  unassignRoleGroup,
  unassignRoleMappingRule,
  updateRole,
} from ".";

export const useSearchRoles = (
  params?: QueryRolesRequestBody | Record<string, unknown>,
  options?: { enabled?: boolean },
) =>
  useQuery({
    queryKey: queryKeys.roles.search(params),
    queryFn: () =>
      unwrap(searchRoles(params as QueryRolesRequestBody)(getApiBaseUrl())),
    enabled: options?.enabled,
  });

export const useRoleDetails = (roleId: string | undefined) =>
  useQuery({
    queryKey: queryKeys.roles.detail(roleId ?? ""),
    queryFn: () =>
      unwrap(getRoleDetails({ roleId: roleId as string })(getApiBaseUrl())),
    enabled: !!roleId,
  });

export const useCreateRole = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Role) => unwrap(createRole(body)(getApiBaseUrl())),
    meta: { skipErrorNotification: true },
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.roles.all }),
  });
};

export const useUpdateRole = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Role) => unwrap(updateRole(body)(getApiBaseUrl())),
    meta: { skipErrorNotification: true },
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.roles.all }),
  });
};

export const useDeleteRole = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<Role, "roleId">) =>
      unwrap(deleteRole(body)(getApiBaseUrl())),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.roles.all }),
  });
};

// ----------------- Mapping rules within a Role -----------------

export const useRoleMappingRules = (
  roleId: string,
  params?: QueryMappingRulesByRoleRequestBody | Record<string, unknown>,
) =>
  useQuery({
    queryKey: queryKeys.roles.mappingRules(roleId, params),
    queryFn: () =>
      unwrap(
        getMappingRulesByRoleId({
          roleId,
          ...(params as QueryMappingRulesByRoleRequestBody),
        })(getApiBaseUrl()),
      ),
    enabled: !!roleId,
  });

export const useAssignRoleMappingRule = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (
      body: Pick<Role, "roleId"> & Pick<MappingRule, "mappingRuleId">,
    ) => unwrap(assignRoleMappingRule(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["roles", "mappingRules", variables.roleId],
      }),
  });
};

export const useUnassignRoleMappingRule = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (
      body: Pick<Role, "roleId"> & Pick<MappingRule, "mappingRuleId">,
    ) => unwrap(unassignRoleMappingRule(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["roles", "mappingRules", variables.roleId],
      }),
  });
};

// ----------------- Groups within a Role -----------------

export const useRoleGroups = (
  roleId: string,
  params?: QueryGroupsByRoleRequestBody | Record<string, unknown>,
) =>
  useQuery({
    queryKey: queryKeys.roles.groups(roleId, params),
    queryFn: () =>
      unwrap(
        getGroupsByRoleId({
          roleId,
          ...(params as QueryGroupsByRoleRequestBody),
        })(getApiBaseUrl()),
      ),
    enabled: !!roleId,
  });

export const useAssignRoleGroup = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<Role, "roleId"> & Pick<Group, "groupId">) =>
      unwrap(assignRoleGroup(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["roles", "groups", variables.roleId],
      }),
  });
};

export const useUnassignRoleGroup = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<Role, "roleId"> & Pick<Group, "groupId">) =>
      unwrap(unassignRoleGroup(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["roles", "groups", variables.roleId],
      }),
  });
};

// ----------------- Clients within a Role -----------------

export const useRoleClients = (
  roleId: string,
  params?: QueryClientsByRoleRequestBody | Record<string, unknown>,
) =>
  useQuery({
    queryKey: queryKeys.roles.clients(roleId, params),
    queryFn: () =>
      unwrap(
        getClientsByRoleId({
          roleId,
          ...(params as QueryClientsByRoleRequestBody),
        })(getApiBaseUrl()),
      ),
    enabled: !!roleId,
  });

export const useAssignRoleClient = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<Role, "roleId"> & Pick<TenantClient, "clientId">) =>
      unwrap(assignRoleClient(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["roles", "clients", variables.roleId],
      }),
  });
};

export const useUnassignRoleClient = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<Role, "roleId"> & Pick<TenantClient, "clientId">) =>
      unwrap(unassignRoleClient(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["roles", "clients", variables.roleId],
      }),
  });
};
