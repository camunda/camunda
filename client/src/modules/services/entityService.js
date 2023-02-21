/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {del, get, post, put} from 'request';
import {track} from 'tracking';

export async function loadEntity(type, id, query) {
  const response = await get(`api/${type}/` + id, query);

  return await response.json();
}

export async function createEntity(type, initialValues = {}, context) {
  const response = await post('api/' + type, initialValues);
  const json = await response.json();
  track(createEventName('create', type), {entityId: json.id, context});
  return json.id;
}

export async function copyReport(id) {
  const response = await post(`api/report/${id}/copy`);
  const json = await response.json();
  return json.id;
}

export async function updateEntity(type, id, data, options = {}) {
  const response = await put(`api/${type}/${id}`, data, options);
  track(createEventName('update', type), {entityId: id});
  return response;
}

export async function deleteEntity(type, id) {
  const response = await del(`api/${type}/${id}`, {force: true});
  track(createEventName('delete', type), {entityId: id});
  return response;
}

export async function loadReports(collection) {
  let url = 'api/report';
  if (collection) {
    url = `api/collection/${collection}/reports`;
  }
  const response = await get(url);
  return await response.json();
}

function createEventName(action, entityType) {
  return action + entityType.charAt(0).toUpperCase() + entityType.slice(1);
}
