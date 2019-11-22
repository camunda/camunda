/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get, post, put, del} from 'request';

export async function loadProcesses() {
  const response = await get('/api/eventBasedProcess');
  return await response.json();
}

export async function loadProcess(id) {
  const response = await get('/api/eventBasedProcess/' + id);
  return await response.json();
}

export async function removeProcess(id) {
  return await del(`api/eventBasedProcess/${id}`);
}

export async function createProcess(name, xml, mappings) {
  const response = await post('/api/eventBasedProcess', {name, xml, mappings});
  const json = await response.json();

  return json.id;
}

export async function updateProcess(id, name, xml, mappings) {
  return await put('/api/eventBasedProcess/' + id, {name, xml, mappings});
}

export async function loadEvents(searchTerm) {
  const query = {};
  if (searchTerm) {
    query.searchTerm = searchTerm;
  }

  const response = await get('/api/event/count', query);
  return await response.json();
}
