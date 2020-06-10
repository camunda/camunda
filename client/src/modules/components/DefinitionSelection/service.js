/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get, post} from 'request';

export async function loadDefinitions(type, collectionId, excludeEventProcesses = false) {
  const params = {excludeEventProcesses};
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

export async function loadTenants(type, collectionId, key, versions) {
  const params = {versions};
  if (collectionId) {
    params.filterByCollectionScope = collectionId;
  }

  const response = await post(`api/definition/${type}/${key}/_resolveTenantsForVersions`, params);

  return await response.json();
}
