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
  assignGroupClient,
  assignGroupMappingRule,
  assignGroupRole,
  createGroup,
  deleteGroup,
  getClientsByGroupId,
  getMappingRulesByGroupId,
  searchRolesByGroupId,
  searchGroups,
  unassignGroupClient,
  unassignGroupMappingRule,
  unassignGroupRole,
  updateGroup,
} from ".";

const groupRoles = (groupId: string) => ({
  searchKey: queryKeys.groups.roles(groupId),
  search: () =>
    unwrap(
      searchRolesByGroupId({ groupId, page: { limit: 1 } })(getApiBaseUrl()),
    ),
});

const groupMappingRules = (groupId: string) => ({
  searchKey: queryKeys.groups.mappingRules(groupId),
  search: () =>
    unwrap(
      getMappingRulesByGroupId({ groupId, page: { limit: 1 } })(
        getApiBaseUrl(),
      ),
    ),
});

const groupClients = (groupId: string) => ({
  searchKey: queryKeys.groups.clients(groupId),
  search: () =>
    unwrap(
      getClientsByGroupId({ groupId, page: { limit: 1 } })(getApiBaseUrl()),
    ),
});

const searchGroupById = (groupId: string) =>
  unwrap(
    searchGroups({ filter: { groupId }, page: { limit: 1 } })(getApiBaseUrl()),
  );

export const groupMutations = {
  create: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Group) => unwrap(createGroup(body)(getApiBaseUrl())),
      meta: { skipErrorNotification: true },
      onSuccess: async (_data, group) => {
        await insertIntoListCaches(
          qc,
          queryKeys.groups.search(),
          group,
          (item) => item.groupId === group.groupId,
        );
        void confirmThenInvalidate(
          qc,
          queryKeys.groups.all,
          () => searchGroupById(group.groupId),
          (items) => items.some((item) => item.groupId === group.groupId),
        );
      },
    }),
  update: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Group) => unwrap(updateGroup(body)(getApiBaseUrl())),
      meta: { skipErrorNotification: true },
      onSuccess: async (_data, group) => {
        await updateInListCaches<Group>(
          qc,
          queryKeys.groups.search(),
          (item) => item.groupId === group.groupId,
          { name: group.name, description: group.description },
        );
        void qc.invalidateQueries({
          queryKey: queryKeys.groups.detail(group.groupId),
        });
        void confirmThenInvalidate(
          qc,
          queryKeys.groups.all,
          () => searchGroupById(group.groupId),
          (items) =>
            items.some(
              (item) =>
                item.groupId === group.groupId &&
                item.name === group.name &&
                (item.description ?? "") === (group.description ?? ""),
            ),
        );
      },
    }),
  delete: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Group, "groupId">) =>
        unwrap(deleteGroup(body)(getApiBaseUrl())),
      onSuccess: async (_data, { groupId }) => {
        await removeFromListCaches<Group>(
          qc,
          queryKeys.groups.search(),
          (item) => item.groupId === groupId,
        );
        void confirmThenInvalidate(
          qc,
          queryKeys.groups.all,
          () => searchGroupById(groupId),
          (items) => items.every((item) => item.groupId !== groupId),
        );
      },
    }),
  assignRole: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (
        body: Pick<Group, "groupId"> & Pick<Role, "roleId"> & { role?: Role },
      ) => unwrap(assignGroupRole(body)(getApiBaseUrl())),
      onSuccess: (_data, { groupId, roleId, role }) =>
        syncSubTable(qc, groupRoles(groupId), {
          type: "assign",
          item: role,
          matches: (item) => item.roleId === roleId,
        }),
    }),
  unassignRole: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Group, "groupId"> & Pick<Role, "roleId">) =>
        unwrap(unassignGroupRole(body)(getApiBaseUrl())),
      onSuccess: (_data, { groupId, roleId }) =>
        syncSubTable(qc, groupRoles(groupId), {
          type: "unassign",
          matches: (item) => item.roleId === roleId,
        }),
    }),
  assignMappingRule: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (
        body: Pick<Group, "groupId"> &
          Pick<MappingRule, "mappingRuleId"> & {
            mappingRule?: MappingRule;
          },
      ) => unwrap(assignGroupMappingRule(body)(getApiBaseUrl())),
      onSuccess: (_data, { groupId, mappingRuleId, mappingRule }) =>
        syncSubTable(qc, groupMappingRules(groupId), {
          type: "assign",
          item: mappingRule,
          matches: (item) => item.mappingRuleId === mappingRuleId,
        }),
    }),
  unassignMappingRule: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (
        body: Pick<Group, "groupId"> & Pick<MappingRule, "mappingRuleId">,
      ) => unwrap(unassignGroupMappingRule(body)(getApiBaseUrl())),
      onSuccess: (_data, { groupId, mappingRuleId }) =>
        syncSubTable(qc, groupMappingRules(groupId), {
          type: "unassign",
          matches: (item) => item.mappingRuleId === mappingRuleId,
        }),
    }),
  assignClient: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (
        body: Pick<Group, "groupId"> & Pick<TenantClient, "clientId">,
      ) => unwrap(assignGroupClient(body)(getApiBaseUrl())),
      onSuccess: (_data, { groupId, clientId }) =>
        syncSubTable(qc, groupClients(groupId), {
          type: "assign",
          item: { clientId },
          matches: (item) => item.clientId === clientId,
        }),
    }),
  unassignClient: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (
        body: Pick<Group, "groupId"> & Pick<TenantClient, "clientId">,
      ) => unwrap(unassignGroupClient(body)(getApiBaseUrl())),
      onSuccess: (_data, { groupId, clientId }) =>
        syncSubTable(qc, groupClients(groupId), {
          type: "unassign",
          matches: (item) => item.clientId === clientId,
        }),
    }),
};
