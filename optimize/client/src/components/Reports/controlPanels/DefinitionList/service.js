/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {get, post} from 'request';

export async function loadTenants(type, definitions, collectionId) {
  const payload = {definitions};
  if (collectionId) {
    payload.filterByCollectionScope = collectionId;
  }

  const response = await post(
    `api/definition/${type}/_resolveTenantsForVersions`,
    payload
  );

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

export {updateVariables} from './service.ts';
