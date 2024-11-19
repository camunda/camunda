/*
 * Copyright © Camunda Services GmbH
 */

import { ApiDefinition, apiPost } from "src/utility/api/request";
import { SearchResponse } from "src/utility/api";
import { EntityData } from "src/components/entityList/EntityList";

export const TENANTS_ENDPOINT = "/tenants";

export type Tenant = EntityData & {
  id: string;
  tenantId: string;
  name: string;
  permissions: string[];
};

export const searchTenant: ApiDefinition<SearchResponse<Tenant>> = () =>
  apiPost(`${TENANTS_ENDPOINT}/search`);

type GetTenantParams = {
  tenantId?: string;
  name?: string;
};

export const getTenantDetails: ApiDefinition<
  SearchResponse<Tenant>,
  GetTenantParams
> = ({ tenantId, name }) =>
  apiPost(`${TENANTS_ENDPOINT}/search`, {
    filter: { ...(tenantId && { tenantId }), ...(name && { name }) },
  });

type CreateTenantParams = Omit<Tenant, "id" | "permissions">;

export const createTenant: ApiDefinition<undefined, CreateTenantParams> = (
  tenant,
) => apiPost(TENANTS_ENDPOINT, tenant);
