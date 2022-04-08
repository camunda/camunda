/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

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
