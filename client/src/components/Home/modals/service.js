/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {t} from 'translation';

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

export {
  getDefinitionsWithTenants,
  getTenantsWithDefinitions,
  formatTenants,
  getDefinitionTenants,
} from './service.ts';
