/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  ApiDefinition,
  apiPost,
  apiPatch,
  apiDelete,
  apiPut,
} from "src/utility/api/request";
import { SearchResponse } from "src/utility/api";
import { EntityData } from "src/components/entityList/EntityList";
import { Group } from "src/utility/api/groups";
import { Role } from "src/utility/api/roles";
import { MappingRule } from "src/utility/api/mapping-rules";
import { PageSearchParams } from "../hooks/usePagination";

export const TENANTS_ENDPOINT = "/tenants";

export type Tenant = EntityData & {
  tenantKey: string;
  tenantId: string;
  name: string;
  description: string;
};

export const searchTenant: ApiDefinition<
  SearchResponse<Tenant>,
  PageSearchParams
> = (params) => apiPost(`${TENANTS_ENDPOINT}/search`, params);

type GetTenantParams = {
  tenantId?: string;
  name?: string;
  description?: string;
};
export const getTenantDetails: ApiDefinition<
  SearchResponse<Tenant>,
  GetTenantParams
> = ({ tenantId, name }) =>
  apiPost(`${TENANTS_ENDPOINT}/search`, {
    filter: { ...(tenantId && { tenantId }), ...(name && { name }) },
  });

type CreateTenantParams = Omit<Tenant, "tenantKey">;
export const createTenant: ApiDefinition<undefined, CreateTenantParams> = (
  tenant,
) => apiPost(TENANTS_ENDPOINT, tenant);

export type UpdateTenantParams = {
  tenantId: string;
  name: string;
  description: string;
};
export const updateTenant: ApiDefinition<undefined, UpdateTenantParams> = ({
  tenantId,
  name,
  description,
}) => apiPatch(`${TENANTS_ENDPOINT}/${tenantId}`, { name, description });

export type DeleteTenantParams = UpdateTenantParams;
export const deleteTenant: ApiDefinition<undefined, { tenantId: string }> = ({
  tenantId,
}) => apiDelete(`${TENANTS_ENDPOINT}/${tenantId}`);

// ----------------- Groups within a Tenant -----------------

export type GetTenantGroupsParams = {
  tenantId: string;
};
export const getGroupsByTenantId: ApiDefinition<
  SearchResponse<Group>,
  GetTenantGroupsParams
> = ({ tenantId, ...body }) =>
  apiPost(`${TENANTS_ENDPOINT}/${tenantId}/groups/search`, body);

type AssignTenantGroupParams = GetTenantGroupsParams & { groupId: string };
export const assignTenantGroup: ApiDefinition<
  undefined,
  AssignTenantGroupParams
> = ({ tenantId, groupId }) => {
  return apiPut(`${TENANTS_ENDPOINT}/${tenantId}/groups/${groupId}`);
};

type UnassignTenantGroupParams = AssignTenantGroupParams;
export const unassignTenantGroup: ApiDefinition<
  undefined,
  UnassignTenantGroupParams
> = ({ tenantId, groupId }) =>
  apiDelete(`${TENANTS_ENDPOINT}/${tenantId}/groups/${groupId}`);

// ----------------- Roles within a Tenant -----------------

export type GetTenantRolesParams = {
  tenantId: string;
};
export const getRolesByTenantId: ApiDefinition<
  SearchResponse<Role>,
  GetTenantRolesParams
> = ({ tenantId, ...body }) =>
  apiPost(`${TENANTS_ENDPOINT}/${tenantId}/roles/search`, body);

type AssignTenantRoleParams = GetTenantRolesParams & { roleId: string };
export const assignTenantRole: ApiDefinition<
  undefined,
  AssignTenantRoleParams
> = ({ tenantId, roleId }) => {
  return apiPut(`${TENANTS_ENDPOINT}/${tenantId}/roles/${roleId}`);
};

type UnassignTenantRoleParams = AssignTenantRoleParams;
export const unassignTenantRole: ApiDefinition<
  undefined,
  UnassignTenantRoleParams
> = ({ tenantId, roleId }) =>
  apiDelete(`${TENANTS_ENDPOINT}/${tenantId}/roles/${roleId}`);

// ----------------- Mapping rules within a Tenant -----------------

export type GetTenantMappingRulesParams = {
  tenantId: string;
};
export const getMappingRulesByTenantId: ApiDefinition<
  SearchResponse<MappingRule>,
  GetTenantMappingRulesParams
> = (params) => {
  const { tenantId, ...body } = params;
  return apiPost(`${TENANTS_ENDPOINT}/${tenantId}/mapping-rules/search`, body);
};

type AssignTenantMappingRuleParams = GetTenantMappingRulesParams & {
  mappingRuleId: string;
};
export const assignTenantMappingRule: ApiDefinition<
  undefined,
  AssignTenantMappingRuleParams
> = ({ tenantId, mappingRuleId }) => {
  return apiPut(
    `${TENANTS_ENDPOINT}/${tenantId}/mapping-rules/${mappingRuleId}`,
  );
};

type UnassignTenantMappingParams = AssignTenantMappingRuleParams;
export const unassignTenantMappingRule: ApiDefinition<
  undefined,
  UnassignTenantMappingParams
> = ({ tenantId, mappingRuleId }) =>
  apiDelete(`${TENANTS_ENDPOINT}/${tenantId}/mapping-rules/${mappingRuleId}`);

type GetTenantClientsParams = {
  tenantId: string;
};

export type Client = {
  clientId: string;
};

export const getClientsByTenantId: ApiDefinition<
  SearchResponse<Client>,
  GetTenantClientsParams
> = ({ tenantId, ...body }) =>
  apiPost(`${TENANTS_ENDPOINT}/${tenantId}/clients/search`, body);

type AssignTenantClientParams = GetTenantClientsParams & Client;
export const assignTenantClient: ApiDefinition<
  undefined,
  AssignTenantClientParams
> = ({ tenantId, clientId, ...body }) => {
  return apiPut(`${TENANTS_ENDPOINT}/${tenantId}/clients/${clientId}`, body);
};

type UnassignTenantClientParams = AssignTenantClientParams;
export const unassignTenantClient: ApiDefinition<
  undefined,
  UnassignTenantClientParams
> = ({ tenantId, clientId }) =>
  apiDelete(`${TENANTS_ENDPOINT}/${tenantId}/clients/${clientId}`);
