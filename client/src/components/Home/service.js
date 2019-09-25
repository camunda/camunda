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
