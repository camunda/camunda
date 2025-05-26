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
import { Mapping } from "src/utility/api/mappings";

export const TENANTS_ENDPOINT = "/tenants";

export type Tenant = EntityData & {
  tenantKey: string;
  tenantId: string;
  name: string;
  description: string;
};

export const searchTenant: ApiDefinition<SearchResponse<Tenant>> = () =>
  apiPost(`${TENANTS_ENDPOINT}/search`);

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
> = ({ tenantId }) => apiPost(`${TENANTS_ENDPOINT}/${tenantId}/groups/search`);

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
> = ({ tenantId }) => apiPost(`${TENANTS_ENDPOINT}/${tenantId}/roles/search`);

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

// ----------------- Mappings within a Tenant -----------------

export type GetTenantMappingsParams = {
  tenantId: string;
};
export const getMappingsByTenantId: ApiDefinition<
  SearchResponse<Mapping>,
  GetTenantMappingsParams
> = ({ tenantId }) =>
  apiPost(`${TENANTS_ENDPOINT}/${tenantId}/mapping-rules/search`);

type AssignTenantMappingParams = GetTenantMappingsParams & {
  mappingId: string;
};
export const assignTenantMapping: ApiDefinition<
  undefined,
  AssignTenantMappingParams
> = ({ tenantId, mappingId }) => {
  return apiPut(`${TENANTS_ENDPOINT}/${tenantId}/mapping-rules/${mappingId}`);
};

type UnassignTenantMappingParams = AssignTenantMappingParams;
export const unassignTenantMapping: ApiDefinition<
  undefined,
  UnassignTenantMappingParams
> = ({ tenantId, mappingId }) =>
  apiDelete(`${TENANTS_ENDPOINT}/${tenantId}/mapping-rules/${mappingId}`);

type GetTenantClientsParams = {
  tenantId: string;
};

export type Client = {
  clientId: string;
};

export const getClientsByTenantId: ApiDefinition<
  SearchResponse<Client>,
  GetTenantClientsParams
> = ({ tenantId }) => apiPost(`${TENANTS_ENDPOINT}/${tenantId}/clients/search`);

type AssignTenantClientParams = GetTenantClientsParams & Client;
export const assignTenantClient: ApiDefinition<
  undefined,
  AssignTenantClientParams
> = ({ tenantId, clientId }) => {
  return apiPut(`${TENANTS_ENDPOINT}/${tenantId}/clients/${clientId}`);
};

type UnassignTenantClientParams = AssignTenantClientParams;
export const unassignTenantClient: ApiDefinition<
  undefined,
  UnassignTenantClientParams
> = ({ tenantId, clientId }) =>
  apiDelete(`${TENANTS_ENDPOINT}/${tenantId}/clients/${clientId}`);
