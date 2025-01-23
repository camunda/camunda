/*
 * Copyright © Camunda Services GmbH
 */
import {
  ApiDefinition,
  apiPost,
  apiPatch,
  apiDelete,
} from "src/utility/api/request";
import { SearchResponse } from "src/utility/api";
import { EntityData } from "src/components/entityList/EntityList";

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
