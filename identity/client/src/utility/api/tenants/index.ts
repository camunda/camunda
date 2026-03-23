/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  MappingRule,
  Tenant,
  Group,
  Role,
  QueryTenantsRequestBody,
  QueryTenantsResponseBody,
  CreateTenantRequestBody,
  UpdateTenantRequestBody,
  QueryGroupsByTenantRequestBody,
  QueryGroupsByTenantResponseBody,
  QueryRolesByTenantRequestBody,
  QueryRolesByTenantResponseBody,
  QueryMappingRulesByTenantRequestBody,
  QueryMappingRulesByTenantResponseBody,
  QueryClientsByTenantRequestBody,
  QueryClientsByTenantResponseBody,
} from "@camunda/camunda-api-zod-schemas/8.9";
import {
  ApiDefinition,
  apiPost,
  apiPatch,
  apiDelete,
  apiPut,
} from "src/utility/api/request";

export const TENANTS_ENDPOINT = "/tenants";

export const searchTenant: ApiDefinition<
  QueryTenantsResponseBody,
  QueryTenantsRequestBody
> = (params) => apiPost(`${TENANTS_ENDPOINT}/search`, params);

export const getTenantDetails: ApiDefinition<
  QueryTenantsResponseBody,
  Partial<Pick<Tenant, "tenantId" | "name">>
> = ({ tenantId, name }) =>
  apiPost(`${TENANTS_ENDPOINT}/search`, {
    filter: { ...(tenantId && { tenantId }), ...(name && { name }) },
  });

export const createTenant: ApiDefinition<undefined, CreateTenantRequestBody> = (
  tenant,
) => apiPost(TENANTS_ENDPOINT, tenant);

export const updateTenant: ApiDefinition<
  undefined,
  UpdateTenantRequestBody & Pick<Tenant, "tenantId">
> = ({ tenantId, name, description }) =>
  apiPatch(`${TENANTS_ENDPOINT}/${encodeURIComponent(tenantId)}`, {
    name,
    description,
  });

export const deleteTenant: ApiDefinition<
  undefined,
  Pick<Tenant, "tenantId">
> = ({ tenantId }) =>
  apiDelete(`${TENANTS_ENDPOINT}/${encodeURIComponent(tenantId)}`);

// ----------------- Groups within a Tenant -----------------

export const getGroupsByTenantId: ApiDefinition<
  QueryGroupsByTenantResponseBody,
  QueryGroupsByTenantRequestBody & Pick<Tenant, "tenantId">
> = ({ tenantId, ...body }) =>
  apiPost(
    `${TENANTS_ENDPOINT}/${encodeURIComponent(tenantId)}/groups/search`,
    body,
  );

export const assignTenantGroup: ApiDefinition<
  undefined,
  Pick<Tenant, "tenantId"> & Pick<Group, "groupId">
> = ({ tenantId, groupId }) => {
  return apiPut(
    `${TENANTS_ENDPOINT}/${encodeURIComponent(tenantId)}/groups/${encodeURIComponent(groupId)}`,
  );
};

export const unassignTenantGroup: ApiDefinition<
  undefined,
  Pick<Tenant, "tenantId"> & Pick<Group, "groupId">
> = ({ tenantId, groupId }) =>
  apiDelete(
    `${TENANTS_ENDPOINT}/${encodeURIComponent(tenantId)}/groups/${encodeURIComponent(groupId)}`,
  );

// ----------------- Roles within a Tenant -----------------

export const getRolesByTenantId: ApiDefinition<
  QueryRolesByTenantResponseBody,
  QueryRolesByTenantRequestBody & Pick<Tenant, "tenantId">
> = ({ tenantId, ...body }) =>
  apiPost(
    `${TENANTS_ENDPOINT}/${encodeURIComponent(tenantId)}/roles/search`,
    body,
  );

export const assignTenantRole: ApiDefinition<
  undefined,
  Pick<Tenant, "tenantId"> & Pick<Role, "roleId">
> = ({ tenantId, roleId }) => {
  return apiPut(
    `${TENANTS_ENDPOINT}/${encodeURIComponent(tenantId)}/roles/${encodeURIComponent(roleId)}`,
  );
};

export const unassignTenantRole: ApiDefinition<
  undefined,
  Pick<Tenant, "tenantId"> & Pick<Role, "roleId">
> = ({ tenantId, roleId }) =>
  apiDelete(
    `${TENANTS_ENDPOINT}/${encodeURIComponent(tenantId)}/roles/${encodeURIComponent(roleId)}`,
  );

// ----------------- Mapping rules within a Tenant -----------------

export const getMappingRulesByTenantId: ApiDefinition<
  QueryMappingRulesByTenantResponseBody,
  QueryMappingRulesByTenantRequestBody & Pick<Tenant, "tenantId">
> = (params) => {
  const { tenantId, ...body } = params;
  return apiPost(
    `${TENANTS_ENDPOINT}/${encodeURIComponent(tenantId)}/mapping-rules/search`,
    body,
  );
};

export const assignTenantMappingRule: ApiDefinition<
  undefined,
  Pick<Tenant, "tenantId"> & Pick<MappingRule, "mappingRuleId">
> = ({ tenantId, mappingRuleId }) => {
  return apiPut(
    `${TENANTS_ENDPOINT}/${encodeURIComponent(tenantId)}/mapping-rules/${encodeURIComponent(mappingRuleId)}`,
  );
};

export const unassignTenantMappingRule: ApiDefinition<
  undefined,
  Pick<Tenant, "tenantId"> & Pick<MappingRule, "mappingRuleId">
> = ({ tenantId, mappingRuleId }) =>
  apiDelete(
    `${TENANTS_ENDPOINT}/${encodeURIComponent(tenantId)}/mapping-rules/${encodeURIComponent(mappingRuleId)}`,
  );

export const getClientsByTenantId: ApiDefinition<
  QueryClientsByTenantResponseBody,
  QueryClientsByTenantRequestBody & Pick<Tenant, "tenantId">
> = ({ tenantId, ...body }) =>
  apiPost(
    `${TENANTS_ENDPOINT}/${encodeURIComponent(tenantId)}/clients/search`,
    body,
  );

export const assignTenantClient: ApiDefinition<
  undefined,
  Pick<Tenant, "tenantId"> & { clientId: string }
> = ({ tenantId, clientId, ...body }) => {
  return apiPut(
    `${TENANTS_ENDPOINT}/${encodeURIComponent(tenantId)}/clients/${encodeURIComponent(clientId)}`,
    body,
  );
};

export const unassignTenantClient: ApiDefinition<
  undefined,
  Pick<Tenant, "tenantId"> & { clientId: string }
> = ({ tenantId, clientId }) =>
  apiDelete(
    `${TENANTS_ENDPOINT}/${encodeURIComponent(tenantId)}/clients/${encodeURIComponent(clientId)}`,
  );
