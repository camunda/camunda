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
  confirmThenInvalidate,
  insertIntoListCaches,
  removeFromListCaches,
  syncSubTable,
  updateInListCaches,
} from "../optimisticListUpdates";
import {
  assignRoleClient,
  assignRoleGroup,
  assignRoleMappingRule,
  createRole,
  deleteRole,
  getClientsByRoleId,
  getGroupsByRoleId,
  getMappingRulesByRoleId,
  searchRoles,
  unassignRoleClient,
  unassignRoleGroup,
  unassignRoleMappingRule,
  updateRole,
} from ".";

const roleMappingRules = (roleId: string) => ({
  searchKey: queryKeys.roles.mappingRules(roleId),
  search: () =>
    unwrap(
      getMappingRulesByRoleId({ roleId, page: { limit: 1 } })(getApiBaseUrl()),
    ),
});

const roleGroups = (roleId: string) => ({
  searchKey: ["enrichedGroups", "roles"] as const,
  scope: { roleId },
  invalidateKeys: [["enrichedGroups", "roles"], queryKeys.roles.groups(roleId)],
  search: () =>
    unwrap(getGroupsByRoleId({ roleId, page: { limit: 1 } })(getApiBaseUrl())),
});

const roleClients = (roleId: string) => ({
  searchKey: queryKeys.roles.clients(roleId),
  search: () =>
    unwrap(getClientsByRoleId({ roleId, page: { limit: 1 } })(getApiBaseUrl())),
});

export const roleMutations = {
  create: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Role) => unwrap(createRole(body)(getApiBaseUrl())),
      meta: { skipErrorNotification: true },
      onSuccess: async (_data, role) => {
        await insertIntoListCaches(
          qc,
          queryKeys.roles.search(),
          role,
          (item) => item.roleId === role.roleId,
        );
        void confirmThenInvalidate(
          qc,
          queryKeys.roles.all,
          () =>
            unwrap(
              searchRoles({
                filter: { roleId: role.roleId },
                page: { limit: 1 },
              })(getApiBaseUrl()),
            ),
          (items) => items.some((item) => item.roleId === role.roleId),
        );
      },
    }),
  update: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Role) => unwrap(updateRole(body)(getApiBaseUrl())),
      meta: { skipErrorNotification: true },
      onSuccess: async (_data, role) => {
        await updateInListCaches<Role>(
          qc,
          queryKeys.roles.search(),
          (item) => item.roleId === role.roleId,
          { name: role.name, description: role.description },
        );
        void qc.invalidateQueries({
          queryKey: queryKeys.roles.detail(role.roleId),
        });
        void confirmThenInvalidate(
          qc,
          queryKeys.roles.all,
          () =>
            unwrap(
              searchRoles({
                filter: { roleId: role.roleId },
                page: { limit: 1 },
              })(getApiBaseUrl()),
            ),
          (items) =>
            items.some(
              (item) =>
                item.roleId === role.roleId &&
                item.name === role.name &&
                (item.description ?? "") === (role.description ?? ""),
            ),
        );
      },
    }),
  delete: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Role, "roleId">) =>
        unwrap(deleteRole(body)(getApiBaseUrl())),
      onSuccess: async (_data, { roleId }) => {
        await removeFromListCaches<Role>(
          qc,
          queryKeys.roles.search(),
          (item) => item.roleId === roleId,
        );
        void confirmThenInvalidate(
          qc,
          queryKeys.roles.all,
          () =>
            unwrap(
              searchRoles({ filter: { roleId }, page: { limit: 1 } })(
                getApiBaseUrl(),
              ),
            ),
          (items) => items.every((item) => item.roleId !== roleId),
        );
      },
    }),
  assignMappingRule: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (
        body: Pick<Role, "roleId"> &
          Pick<MappingRule, "mappingRuleId"> & {
            mappingRule?: MappingRule;
          },
      ) => unwrap(assignRoleMappingRule(body)(getApiBaseUrl())),
      onSuccess: (_data, { roleId, mappingRuleId, mappingRule }) =>
        syncSubTable(qc, roleMappingRules(roleId), {
          type: "assign",
          item: mappingRule,
          matches: (item) => item.mappingRuleId === mappingRuleId,
        }),
    }),
  unassignMappingRule: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (
        body: Pick<Role, "roleId"> & Pick<MappingRule, "mappingRuleId">,
      ) => unwrap(unassignRoleMappingRule(body)(getApiBaseUrl())),
      onSuccess: (_data, { roleId, mappingRuleId }) =>
        syncSubTable(qc, roleMappingRules(roleId), {
          type: "unassign",
          matches: (item) => item.mappingRuleId === mappingRuleId,
        }),
    }),
  assignGroup: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Role, "roleId"> & Pick<Group, "groupId">) =>
        unwrap(assignRoleGroup(body)(getApiBaseUrl())),
      onSuccess: (_data, { roleId, groupId }) =>
        syncSubTable(qc, roleGroups(roleId), {
          type: "assign",
          item: { groupId },
          matches: (item) => item.groupId === groupId,
        }),
    }),
  unassignGroup: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Role, "roleId"> & Pick<Group, "groupId">) =>
        unwrap(unassignRoleGroup(body)(getApiBaseUrl())),
      onSuccess: (_data, { roleId, groupId }) =>
        syncSubTable(qc, roleGroups(roleId), {
          type: "unassign",
          matches: (item) => item.groupId === groupId,
        }),
    }),
  assignClient: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (
        body: Pick<Role, "roleId"> & Pick<TenantClient, "clientId">,
      ) => unwrap(assignRoleClient(body)(getApiBaseUrl())),
      onSuccess: (_data, { roleId, clientId }) =>
        syncSubTable(qc, roleClients(roleId), {
          type: "assign",
          item: { clientId },
          matches: (item) => item.clientId === clientId,
        }),
    }),
  unassignClient: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (
        body: Pick<Role, "roleId"> & Pick<TenantClient, "clientId">,
      ) => unwrap(unassignRoleClient(body)(getApiBaseUrl())),
      onSuccess: (_data, { roleId, clientId }) =>
        syncSubTable(qc, roleClients(roleId), {
          type: "unassign",
          matches: (item) => item.clientId === clientId,
        }),
    }),
};
