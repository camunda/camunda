/*
 * Copyright © Camunda Services GmbH
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

type AssignTenantGroupParams = GetTenantGroupsParams & { groupKey: string };
export const assignTenantGroup: ApiDefinition<
  undefined,
  AssignTenantGroupParams
> = ({ tenantId, groupKey }) => {
  return apiPut(`${TENANTS_ENDPOINT}/${tenantId}/groups/${groupKey}`);
};

type UnassignTenantGroupParams = AssignTenantGroupParams;
export const unassignTenantGroup: ApiDefinition<
  undefined,
  UnassignTenantGroupParams
> = ({ tenantId, groupKey }) =>
  apiDelete(`${TENANTS_ENDPOINT}/${tenantId}/groups/${groupKey}`);

// ----------------- Roles within a Tenant -----------------

export type GetTenantRolesParams = {
  tenantId: string;
};
export const getRolesByTenantId: ApiDefinition<
  SearchResponse<Role>,
  GetTenantRolesParams
> = ({ tenantId }) => apiPost(`${TENANTS_ENDPOINT}/${tenantId}/roles/search`);

type AssignTenantRoleParams = GetTenantRolesParams & { roleKey: string };
export const assignTenantRole: ApiDefinition<
  undefined,
  AssignTenantRoleParams
> = ({ tenantId, roleKey }) => {
  return apiPut(`${TENANTS_ENDPOINT}/${tenantId}/roles/${roleKey}`);
};

type UnassignTenantRoleParams = AssignTenantRoleParams;
export const unassignTenantRole: ApiDefinition<
  undefined,
  UnassignTenantRoleParams
> = ({ tenantId, roleKey }) =>
  apiDelete(`${TENANTS_ENDPOINT}/${tenantId}/roles/${roleKey}`);

// ----------------- Mappings within a Tenant -----------------

export type GetTenantMappingsParams = {
  tenantId: string;
};
export const getMappingsByTenantId: ApiDefinition<
  SearchResponse<Mapping>,
  GetTenantMappingsParams
> = ({ tenantId }) =>
  apiPost(`${TENANTS_ENDPOINT}/${tenantId}/mapping-rules/search`);

type AssignTenantMappingParams = GetTenantMappingsParams & { id: string };
export const assignTenantMapping: ApiDefinition<
  undefined,
  AssignTenantMappingParams
> = ({ tenantId, id }) => {
  return apiPut(`${TENANTS_ENDPOINT}/${tenantId}/mapping-rules/${id}`);
};

type UnassignTenantMappingParams = AssignTenantMappingParams;
export const unassignTenantMapping: ApiDefinition<
  undefined,
  UnassignTenantMappingParams
> = ({ tenantId, id }) =>
  apiDelete(`${TENANTS_ENDPOINT}/${tenantId}/mapping-rules/${id}`);
