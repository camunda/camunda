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
  assignTenantClient,
  assignTenantGroup,
  assignTenantMappingRule,
  assignTenantRole,
  createTenant,
  deleteTenant,
  unassignTenantClient,
  unassignTenantGroup,
  unassignTenantMappingRule,
  unassignTenantRole,
  updateTenant,
} from ".";

export const tenantMutations = {
  create: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: CreateTenantRequestBody) =>
        unwrap(createTenant(body)(getApiBaseUrl())),
      meta: { skipErrorNotification: true },
      onSuccess: () =>
        qc.invalidateQueries({ queryKey: queryKeys.tenants.all }),
    }),
  update: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: UpdateTenantRequestBody & Pick<Tenant, "tenantId">) =>
        unwrap(updateTenant(body)(getApiBaseUrl())),
      meta: { skipErrorNotification: true },
      onSuccess: () =>
        qc.invalidateQueries({ queryKey: queryKeys.tenants.all }),
    }),
  delete: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Tenant, "tenantId">) =>
        unwrap(deleteTenant(body)(getApiBaseUrl())),
      onSuccess: () =>
        qc.invalidateQueries({ queryKey: queryKeys.tenants.all }),
    }),
  assignGroup: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Tenant, "tenantId"> & Pick<Group, "groupId">) =>
        unwrap(assignTenantGroup(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: ["tenants", "groups", variables.tenantId],
        }),
    }),
  unassignGroup: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Tenant, "tenantId"> & Pick<Group, "groupId">) =>
        unwrap(unassignTenantGroup(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: ["tenants", "groups", variables.tenantId],
        }),
    }),
  assignRole: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Tenant, "tenantId"> & Pick<Role, "roleId">) =>
        unwrap(assignTenantRole(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: ["tenants", "roles", variables.tenantId],
        }),
    }),
  unassignRole: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Tenant, "tenantId"> & Pick<Role, "roleId">) =>
        unwrap(unassignTenantRole(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: ["tenants", "roles", variables.tenantId],
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
