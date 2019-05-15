/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post, put, get, del} from 'request';

export async function loadEntity(type, id) {
  const response = await get(`api/${type}/` + id);

  return await response.json();
}

export async function loadEntities(api, sortBy, numResults) {
  const url = `api/${api}`;

  const params = {};
  if (sortBy) {
    params['orderBy'] = sortBy;
  }

  if (numResults) {
    params['numResults'] = numResults;
  }

  const response = await get(url, params);
  return await response.json();
}

export async function createEntity(type, initialValues, options = {}) {
  const response = await post(`api/${type}/`, options);
  const json = await response.json();

  if (initialValues) {
    await updateEntity(type, json.id, initialValues);
  }

  return json.id;
}

export async function updateEntity(type, id, data, options = {}) {
  return await put(`api/${type}/${id}`, data, options);
}

export async function deleteEntity(type, id) {
  return await del(`api/${type}/${id}`, {force: true});
}

export async function addEntityToCollection(entityId, collectionId) {
  return await post(`api/collection/${collectionId}/entity`, {entityId});
}

export async function removeEntityFromCollection(entityId, collectionId) {
  return await del(`api/collection/${collectionId}/entity/${entityId}`);
}
