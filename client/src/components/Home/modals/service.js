/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get} from 'request';
import {t} from 'translation';

export async function searchIdentities(terms) {
  const response = await get(`api/identity/search`, {terms});

  return await response.json();
}

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
  return tenants.map(({id, name}) => ({
    id,
    label: name,
    checked: selectedTenants.some(tenant => tenant.id === id)
  }));
}

export function formatDefinitions(definitions, selectedDefinitions) {
  return definitions.map(({key, name, type}) => ({
    id: key,
    label: formatDefintionName({name, type}),
    checked: selectedDefinitions.some(def => def.key === key)
  }));
}

export function formatDefintionName({name, type}) {
  const typeLabel = type === 'process' ? t('home.sources.process') : t('home.sources.decision');

  return name + ' (' + typeLabel + ')';
}
