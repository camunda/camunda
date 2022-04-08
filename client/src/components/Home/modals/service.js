/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {get} from 'request';
import {t} from 'translation';
import {formatters} from 'services';
const {formatTenantName} = formatters;

export async function getDefinitionTenants(defintionKey, defintionType) {
  const response = await get(`api/definition/${defintionType}/${defintionKey}`);

  return await response.json();
}

export async function getDefinitionsWithTenants() {
  const response = await get('api/definition');

  return await response.json();
}

export async function getTenantsWithDefinitions() {
  const response = await get('api/definition/_groupByTenant');

  return await response.json();
}

export function formatTenants(tenants, selectedTenants) {
  return tenants.map(({id, name}, index) => {
    if (id === '__unauthorizedTenantId__') {
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

export function formatDefinitions(definitions, selectedDefinitions) {
  return definitions.map((def) => ({
    id: def.key,
    label: formatDefintionName(def),
    checked: selectedDefinitions.some(({key}) => key === def.key),
  }));
}

export function formatDefintionName({key, name, type}) {
  const typeLabel = type === 'process' ? t('home.sources.process') : t('home.sources.decision');

  return (name || key) + ' (' + typeLabel + ')';
}
