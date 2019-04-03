/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post, put, get} from 'request';

export async function loadEntity(api, numResults, sortBy) {
  const url = `api/${api}`;

  const params = {};
  if (numResults) {
    params['numResults'] = numResults;
  }

  if (sortBy) {
    params['orderBy'] = sortBy;
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

export async function updateEntity(type, id, data) {
  return await put(`api/${type}/${id}`, data);
}
