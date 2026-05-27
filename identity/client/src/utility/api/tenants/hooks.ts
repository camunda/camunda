/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  CreateTenantRequestBody,
  Group,
  MappingRule,
  QueryClientsByTenantRequestBody,
  QueryGroupsByTenantRequestBody,
  QueryMappingRulesByTenantRequestBody,
  QueryRolesByTenantRequestBody,
  QueryTenantsRequestBody,
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
  getClientsByTenantId,
  getGroupsByTenantId,
  getMappingRulesByTenantId,
  getRolesByTenantId,
  getTenantDetails,
  searchTenant,
  unassignTenantClient,
  unassignTenantGroup,
  unassignTenantMappingRule,
  unassignTenantRole,
  updateTenant,
} from ".";

export const useSearchTenants = (
  params: QueryTenantsRequestBody,
  options?: { enabled?: boolean },
) =>
  useQuery({
    queryKey: queryKeys.tenants.search(params),
    queryFn: () => unwrap(searchTenant(params)(getApiBaseUrl())),
    enabled: options?.enabled,
  });

export const useTenantDetails = (
  params: Partial<Pick<Tenant, "tenantId" | "name">>,
) =>
  useQuery({
    queryKey: queryKeys.tenants.detail(params.tenantId ?? params.name ?? ""),
    queryFn: () => unwrap(getTenantDetails(params)(getApiBaseUrl())),
    enabled: !!(params.tenantId || params.name),
  });

export const useCreateTenant = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateTenantRequestBody) =>
      unwrap(createTenant(body)(getApiBaseUrl())),
    meta: { skipErrorNotification: true },
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.tenants.all }),
  });
};

export const useUpdateTenant = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateTenantRequestBody & Pick<Tenant, "tenantId">) =>
      unwrap(updateTenant(body)(getApiBaseUrl())),
    meta: { skipErrorNotification: true },
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.tenants.all }),
  });
};

export const useDeleteTenant = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<Tenant, "tenantId">) =>
      unwrap(deleteTenant(body)(getApiBaseUrl())),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.tenants.all }),
  });
};

// ----------------- Groups within a Tenant -----------------

export const useTenantGroups = (
  tenantId: string,
  params?: QueryGroupsByTenantRequestBody,
) =>
  useQuery({
    queryKey: queryKeys.tenants.groups(tenantId, params),
    queryFn: () =>
      unwrap(getGroupsByTenantId({ tenantId, ...params })(getApiBaseUrl())),
    enabled: !!tenantId,
  });

export const useAssignTenantGroup = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<Tenant, "tenantId"> & Pick<Group, "groupId">) =>
      unwrap(assignTenantGroup(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["tenants", "groups", variables.tenantId],
      }),
  });
};

export const useUnassignTenantGroup = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<Tenant, "tenantId"> & Pick<Group, "groupId">) =>
      unwrap(unassignTenantGroup(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["tenants", "groups", variables.tenantId],
      }),
  });
};

// ----------------- Roles within a Tenant -----------------

export const useTenantRoles = (
  tenantId: string,
  params?: QueryRolesByTenantRequestBody,
) =>
  useQuery({
    queryKey: queryKeys.tenants.roles(tenantId, params),
    queryFn: () =>
      unwrap(getRolesByTenantId({ tenantId, ...params })(getApiBaseUrl())),
    enabled: !!tenantId,
  });

export const useAssignTenantRole = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<Tenant, "tenantId"> & Pick<Role, "roleId">) =>
      unwrap(assignTenantRole(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["tenants", "roles", variables.tenantId],
      }),
  });
};

export const useUnassignTenantRole = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<Tenant, "tenantId"> & Pick<Role, "roleId">) =>
      unwrap(unassignTenantRole(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["tenants", "roles", variables.tenantId],
      }),
  });
};

// ----------------- Mapping rules within a Tenant -----------------

export const useTenantMappingRules = (
  tenantId: string,
  params?: QueryMappingRulesByTenantRequestBody,
) =>
  useQuery({
    queryKey: queryKeys.tenants.mappingRules(tenantId, params),
    queryFn: () =>
      unwrap(
        getMappingRulesByTenantId({ tenantId, ...params })(getApiBaseUrl()),
      ),
    enabled: !!tenantId,
  });

export const useAssignTenantMappingRule = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (
      body: Pick<Tenant, "tenantId"> & Pick<MappingRule, "mappingRuleId">,
    ) => unwrap(assignTenantMappingRule(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["tenants", "mappingRules", variables.tenantId],
      }),
  });
};

export const useUnassignTenantMappingRule = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (
      body: Pick<Tenant, "tenantId"> & Pick<MappingRule, "mappingRuleId">,
    ) => unwrap(unassignTenantMappingRule(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["tenants", "mappingRules", variables.tenantId],
      }),
  });
};

// ----------------- Clients within a Tenant -----------------

export const useTenantClients = (
  tenantId: string,
  params?: QueryClientsByTenantRequestBody,
) =>
  useQuery({
    queryKey: queryKeys.tenants.clients(tenantId, params),
    queryFn: () =>
      unwrap(getClientsByTenantId({ tenantId, ...params })(getApiBaseUrl())),
    enabled: !!tenantId,
  });

export const useAssignTenantClient = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<Tenant, "tenantId"> & { clientId: string }) =>
      unwrap(assignTenantClient(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["tenants", "clients", variables.tenantId],
      }),
  });
};

export const useUnassignTenantClient = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<Tenant, "tenantId"> & { clientId: string }) =>
      unwrap(unassignTenantClient(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["tenants", "clients", variables.tenantId],
      }),
  });
};
