/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {get, post} from 'request';

export async function loadDefinitions(type, collectionId, camundaEventImportedOnly = false) {
  const params = {camundaEventImportedOnly};
  if (collectionId) {
    params.filterByCollectionScope = collectionId;
  }

  const response = await get(`api/definition/${type}/keys`, params);

  return await response.json();
}

export async function loadVersions(type, collectionId, key) {
  const params = {};
  if (collectionId) {
    params.filterByCollectionScope = collectionId;
  }

  const response = await get(`api/definition/${type}/${key}/versions`, params);

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
