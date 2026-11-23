/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { mutationOptions, QueryClient } from "@tanstack/react-query";
import type {
  CreateTenantRequestBody,
  Group,
  MappingRule,
  Role,
  Tenant,
  UpdateTenantRequestBody,
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
  assignTenantClient,
  assignTenantGroup,
  assignTenantMappingRule,
  assignTenantRole,
  createTenant,
  deleteTenant,
  getGroupsByTenantId,
  getRolesByTenantId,
  searchTenant,
  unassignTenantClient,
  unassignTenantGroup,
  unassignTenantMappingRule,
  unassignTenantRole,
  updateTenant,
} from ".";

const tenantGroups = (tenantId: string) => ({
  searchKey: ["enrichedGroups", "tenants"] as const,
  scope: { tenantId },
  invalidateKeys: [
    ["enrichedGroups", "tenants"],
    queryKeys.tenants.groups(tenantId),
  ],
  search: () =>
    unwrap(
      getGroupsByTenantId({ tenantId, page: { limit: 1 } })(getApiBaseUrl()),
    ),
});

const tenantRoles = (tenantId: string) => ({
  searchKey: queryKeys.tenants.roles(tenantId),
  search: () =>
    unwrap(
      getRolesByTenantId({ tenantId, page: { limit: 1 } })(getApiBaseUrl()),
    ),
});

const searchTenantById = (tenantId: string) =>
  unwrap(
    searchTenant({ filter: { tenantId }, page: { limit: 1 } })(getApiBaseUrl()),
  );

export const tenantMutations = {
  create: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: CreateTenantRequestBody) =>
        unwrap(createTenant(body)(getApiBaseUrl())),
      meta: { skipErrorNotification: true },
      onSuccess: async (_data, tenant) => {
        await insertIntoListCaches(
          qc,
          queryKeys.tenants.search(),
          tenant,
          (item) => item.tenantId === tenant.tenantId,
        );
        void confirmThenInvalidate(
          qc,
          queryKeys.tenants.all,
          () => searchTenantById(tenant.tenantId),
          (items) => items.some((item) => item.tenantId === tenant.tenantId),
        );
      },
    }),
  update: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: UpdateTenantRequestBody & Pick<Tenant, "tenantId">) =>
        unwrap(updateTenant(body)(getApiBaseUrl())),
      meta: { skipErrorNotification: true },
      onSuccess: async (_data, tenant) => {
        await updateInListCaches<Tenant>(
          qc,
          queryKeys.tenants.search(),
          (item) => item.tenantId === tenant.tenantId,
          { name: tenant.name, description: tenant.description },
        );
        await updateInListCaches<Tenant>(
          qc,
          queryKeys.tenants.detail(tenant.tenantId),
          (item) => item.tenantId === tenant.tenantId,
          { name: tenant.name, description: tenant.description },
        );
        void confirmThenInvalidate(
          qc,
          queryKeys.tenants.all,
          () => searchTenantById(tenant.tenantId),
          (items) =>
            items.some(
              (item) =>
                item.tenantId === tenant.tenantId &&
                item.name === tenant.name &&
                (item.description ?? "") === (tenant.description ?? ""),
            ),
        );
      },
    }),
  delete: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Tenant, "tenantId">) =>
        unwrap(deleteTenant(body)(getApiBaseUrl())),
      onSuccess: async (_data, { tenantId }) => {
        await removeFromListCaches<Tenant>(
          qc,
          queryKeys.tenants.search(),
          (item) => item.tenantId === tenantId,
        );
        void confirmThenInvalidate(
          qc,
          queryKeys.tenants.all,
          () => searchTenantById(tenantId),
          (items) => items.every((item) => item.tenantId !== tenantId),
        );
      },
    }),
  assignGroup: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Tenant, "tenantId"> & Pick<Group, "groupId">) =>
        unwrap(assignTenantGroup(body)(getApiBaseUrl())),
      onSuccess: (_data, { tenantId, groupId }) =>
        syncSubTable(qc, tenantGroups(tenantId), {
          type: "assign",
          item: { groupId },
          matches: (item) => item.groupId === groupId,
        }),
    }),
  unassignGroup: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Tenant, "tenantId"> & Pick<Group, "groupId">) =>
        unwrap(unassignTenantGroup(body)(getApiBaseUrl())),
      onSuccess: (_data, { tenantId, groupId }) =>
        syncSubTable(qc, tenantGroups(tenantId), {
          type: "unassign",
          matches: (item) => item.groupId === groupId,
        }),
    }),
  assignRole: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (
        body: Pick<Tenant, "tenantId"> & Pick<Role, "roleId"> & { role?: Role },
      ) => unwrap(assignTenantRole(body)(getApiBaseUrl())),
      onSuccess: (_data, { tenantId, roleId, role }) =>
        syncSubTable(qc, tenantRoles(tenantId), {
          type: "assign",
          item: role,
          matches: (item) => item.roleId === roleId,
        }),
    }),
  unassignRole: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Tenant, "tenantId"> & Pick<Role, "roleId">) =>
        unwrap(unassignTenantRole(body)(getApiBaseUrl())),
      onSuccess: (_data, { tenantId, roleId }) =>
        syncSubTable(qc, tenantRoles(tenantId), {
          type: "unassign",
          matches: (item) => item.roleId === roleId,
        }),
    }),
  assignMappingRule: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (
        body: Pick<Tenant, "tenantId"> & Pick<MappingRule, "mappingRuleId">,
      ) => unwrap(assignTenantMappingRule(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: ["tenants", "mappingRules", variables.tenantId],
        }),
    }),
  unassignMappingRule: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (
        body: Pick<Tenant, "tenantId"> & Pick<MappingRule, "mappingRuleId">,
      ) => unwrap(unassignTenantMappingRule(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: ["tenants", "mappingRules", variables.tenantId],
        }),
    }),
  assignClient: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Tenant, "tenantId"> & { clientId: string }) =>
        unwrap(assignTenantClient(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: ["tenants", "clients", variables.tenantId],
        }),
    }),
  unassignClient: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Tenant, "tenantId"> & { clientId: string }) =>
        unwrap(unassignTenantClient(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: ["tenants", "clients", variables.tenantId],
        }),
    }),
};
