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

export async function createEntity(type, initialValues = {}) {
  const response = await post('api/' + type, initialValues);
  const json = await response.json();

  return json.id;
}

export async function copyReport(id) {
  const response = await post(`/api/report/${id}/copy`);
  const json = await response.json();
  return json.id;
}

export async function updateEntity(type, id, data, options = {}) {
  return await put(`api/${type}/${id}`, data, options);
}

export async function deleteEntity(type, id) {
  return await del(`api/${type}/${id}`, {force: true});
}

export async function loadReports(collection) {
  let url = 'api/report';
  if (collection) {
    url = `api/collection/${collection}/reports`;
  }
  const response = await get(url);
  return await response.json();
}
