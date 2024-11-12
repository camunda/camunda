/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {get} from 'request';
import {Definition, Tenant} from 'types';
import {formatters, UNAUTHORIZED_TENANT_ID} from 'services';
import {t} from 'translation';

const {formatTenantName} = formatters;

export type TenantWithDefinitions = Tenant & {definitions: Definition};

export type DefinitionWithTenants = Omit<Definition, 'key'> & {tenants: Tenant[]; key: string};

export async function getDefinitionsWithTenants(): Promise<DefinitionWithTenants[]> {
  const response = await get('api/definition');

  return await response.json();
}

export async function getTenantsWithDefinitions(): Promise<TenantWithDefinitions[]> {
  const response = await get('api/definition/_groupByTenant');

  return await response.json();
}

export async function getDefinitionTenants(
  defintionKey: string,
  defintionType: string
): Promise<DefinitionWithTenants> {
  const response = await get(`api/definition/${defintionType}/${defintionKey}`);

  return await response.json();
}

export function formatTenants(tenants: Tenant[], selectedTenants: Tenant[]) {
  return tenants.map(({id, name}, index) => {
    if (id === UNAUTHORIZED_TENANT_ID) {
      return {
        id: index,
        label: t('home.sources.unauthorizedTenant'),
        checked: true,
        disabled: true,
      };
    }

    return {
      id,
      label: formatTenantName({id, name}),
      checked: selectedTenants.some((tenant) => tenant.id === id),
    };
  });
}
