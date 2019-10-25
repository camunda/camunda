/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get, post, put, del} from 'request';

export async function loadEntities() {
  const response = await get('api/entities');
  return await response.json();
}

export async function addUser(collection, id, type, role) {
  return await post(`api/collection/${collection}/role`, {identity: {id, type}, role});
}

export async function editUser(collection, id, role) {
  return await put(`api/collection/${collection}/role/${id}`, {role});
}

export async function removeUser(collection, id) {
  return await del(`api/collection/${collection}/role/${id}`);
}

export async function loadAlerts(collection) {
  const response = await get(`api/collection/${collection}/alerts`);
  return await response.json();
}

export async function addAlert(alert) {
  return await post(`api/alert`, alert);
}

export async function editAlert(id, alert) {
  return await put(`api/alert/${id}`, alert);
}

export async function removeAlert(id) {
  return await del(`api/alert/${id}`);
}

export async function copyEntity(type, id, name, collectionId) {
  const query = {name};

  if (collectionId || collectionId === null) {
    query.collectionId = collectionId;
  }

  const response = await post(`api/${type}/${id}/copy`, undefined, {query});
  const json = await response.json();

  return json.id;
}

export async function searchIdentities(terms) {
  const response = await get(`api/identity/search`, {terms});

  return await response.json();
}
