/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {get, post, put, del} from 'request';

export async function loadCollectionEntities(id, sortBy, sortOrder) {
  const params = {};
  if (sortBy && sortOrder) {
    params.sortBy = sortBy;
    params.sortOrder = sortOrder;
  }

  const response = await get(`api/collection/${id}/entities`, params);
  return await response.json();
}

export async function getUsers(collection) {
  const response = await get(`api/collection/${collection}/role`);
  return await response.json();
}

export async function addUser(collection, roles) {
  return await post(`api/collection/${collection}/role`, roles);
}

export async function editUser(collection, id, role) {
  return await put(`api/collection/${collection}/role/${id}`, {role});
}

export async function removeUser(collection, id) {
  return await del(`api/collection/${collection}/role/${id}`);
}

export async function getSources(collection) {
  const response = await get(`api/collection/${collection}/scope`);
  return await response.json();
}

export async function editSource(collection, scopeId, tenants, force = false) {
  return await put(
    `api/collection/${collection}/scope/${scopeId}`,
    {tenants},
    {query: {force}}
  );
}

export async function removeSource(collection, scopeId) {
  return await del(`api/collection/${collection}/scope/${scopeId}?force=true`);
}

export async function checkDeleteSourceConflicts(collection, scopeId) {
  const response = await get(
    `api/collection/${collection}/scope/${scopeId}/delete-conflicts`
  );
  return await response.json();
}

export async function importEntity(json, collectionId) {
  const query = {};
  if (collectionId) {
    query.collectionId = collectionId;
  }
  return await post('api/import', json, {query});
}

export async function removeEntities(entities, collectionId) {
  const query = {};
  if (collectionId) {
    query.collectionId = collectionId;
  }

  return await post(`api/entities/delete`, formatRequest(entities));
}

export async function checkConflicts(entities, collectionId) {
  const query = {};
  if (collectionId) {
    query.collectionId = collectionId;
  }

  const response = await post(`api/entities/delete-conflicts`, formatRequest(entities));

  return await response.json();
}

export async function removeAlerts(alerts) {
  return await post('api/alert/delete', getIds(alerts));
}

export async function removeSources(collectionId, scopes) {
  return await post(`api/collection/${collectionId}/scope/delete`, getIds(scopes));
}

export async function checkSourcesConflicts(collectionId, scopes) {
  const response = await post(
    `api/collection/${collectionId}/scope/delete-conflicts`,
    getIds(scopes)
  );

  return await response.json();
}

export async function removeUsers(collectionId, users) {
  return await post(`api/collection/${collectionId}/roles/delete`, getIds(users));
}

function getIds(entities) {
  return entities.map(({id}) => id);
}

function formatRequest(entities) {
  return entities.reduce(
    (entitesMap, entity) => {
      entitesMap[entity.entityType + 's'].push(entity.id);
      return entitesMap;
    },
    {reports: [], dashboards: [], collections: []}
  );
}
