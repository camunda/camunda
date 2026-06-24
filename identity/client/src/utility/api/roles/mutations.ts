/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { mutationOptions, QueryClient } from "@tanstack/react-query";
import type {
  Group,
  MappingRule,
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
  unassignRoleClient,
  unassignRoleGroup,
  unassignRoleMappingRule,
  updateRole,
} from ".";

export const roleMutations = {
  create: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Role) => unwrap(createRole(body)(getApiBaseUrl())),
      meta: { skipErrorNotification: true },
      onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.roles.all }),
    }),
  update: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Role) => unwrap(updateRole(body)(getApiBaseUrl())),
      meta: { skipErrorNotification: true },
      onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.roles.all }),
    }),
  delete: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Role, "roleId">) =>
        unwrap(deleteRole(body)(getApiBaseUrl())),
      onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.roles.all }),
    }),
  assignMappingRule: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (
        body: Pick<Role, "roleId"> & Pick<MappingRule, "mappingRuleId">,
      ) => unwrap(assignRoleMappingRule(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: queryKeys.roles.mappingRules(variables.roleId),
        }),
    }),
  unassignMappingRule: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (
        body: Pick<Role, "roleId"> & Pick<MappingRule, "mappingRuleId">,
      ) => unwrap(unassignRoleMappingRule(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: queryKeys.roles.mappingRules(variables.roleId),
        }),
    }),
  assignGroup: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Role, "roleId"> & Pick<Group, "groupId">) =>
        unwrap(assignRoleGroup(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: queryKeys.roles.groups(variables.roleId),
        }),
    }),
  unassignGroup: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Role, "roleId"> & Pick<Group, "groupId">) =>
        unwrap(unassignRoleGroup(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: queryKeys.roles.groups(variables.roleId),
        }),
    }),
  assignClient: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (
        body: Pick<Role, "roleId"> & Pick<TenantClient, "clientId">,
      ) => unwrap(assignRoleClient(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: queryKeys.roles.clients(variables.roleId),
        }),
    }),
  unassignClient: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (
        body: Pick<Role, "roleId"> & Pick<TenantClient, "clientId">,
      ) => unwrap(unassignRoleClient(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: queryKeys.roles.clients(variables.roleId),
        }),
    }),
};
