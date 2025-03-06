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

export type DeleteTenantParams = UpdateTenantParams;

export const updateTenant: ApiDefinition<undefined, UpdateTenantParams> = ({
  tenantId,
  name,
  description,
}) => apiPatch(`${TENANTS_ENDPOINT}/${tenantId}`, { name, description });

export const deleteTenant: ApiDefinition<undefined, { tenantId: string }> = ({
  tenantId,
}) => apiDelete(`${TENANTS_ENDPOINT}/${tenantId}`);

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
