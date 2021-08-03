/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import update from 'immutability-helper';
import equal from 'fast-deep-equal';

import {get, post} from 'request';

export function isDurationHeatmap({view, visualization, definitions}) {
  return (
    view &&
    (view.entity === 'flowNode' || view.entity === 'userTask') &&
    view.properties[0] === 'duration' &&
    visualization === 'heat' &&
    definitions?.[0].key &&
    definitions?.[0].versions?.length > 0
  );
}

export function isProcessInstanceDuration({view}) {
  return view && view.entity === 'processInstance' && view.properties[0] === 'duration';
}

export async function loadDefinitions(type, collectionId) {
  const params = {};
  if (collectionId) {
    params.filterByCollectionScope = collectionId;
  }

  const response = await get(`api/definition/${type}/keys`, params);

  return await response.json();
}

export async function loadTenants(type, definitions, collectionId) {
  const payload = {definitions};
  if (collectionId) {
    payload.filterByCollectionScope = collectionId;
  }

  const response = await post(`api/definition/${type}/_resolveTenantsForVersions`, payload);

  return await response.json();
}

export function addVariables(options, variables, payloadFormatter, filter = () => true) {
  return options.map((option) => {
    const subOptions = option.options;
    if (subOptions && typeof subOptions === 'string') {
      return {
        ...option,
        options: variables[subOptions]?.filter(filter).map(({id, name, type}) => {
          const value = id ? {id, name, type} : {name, type};
          return {
            key: subOptions + '_' + (id || name),
            label: name,
            data: payloadFormatter(subOptions, value),
          };
        }),
      };
    }
    return option;
  });
}

export function isDataEqual(prevProps, nextProps) {
  const prevData = excludeConfig(prevProps.report.data);
  const nextData = excludeConfig(nextProps.report.data);

  if (equal(prevData, nextData) && equal(prevProps.variables, nextProps.variables)) {
    return true;
  }

  return false;
}

function excludeConfig(data) {
  return update(data, {$unset: ['configuration']});
}
