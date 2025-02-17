/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {get, post, put} from 'request';
import {track} from 'tracking';
import {createEventName} from './entityService.tsx';
export {deleteEntity} from './entityService.tsx';
import { getFullURL } from '../api.ts';

export async function loadEntity(type, id, query) {
  const response = await get(getFullURL(`api/${type}/` + id), query);
  const json = await response.json();
  track(createEventName('view', type), {
    entityId: id,
  });

  return await json;
}

export async function copyReport(id) {
  const response = await post(getFullURL(`api/report/${id}/copy`));
  const json = await response.json();
  return json.id;
}

export async function updateEntity(type, id, data, options = {}) {
  const response = await put(getFullURL(`api/${type}/${id}`), data, options);
  track(createEventName('update', type), {entityId: id});
  return response;
}

export async function loadReports(collection) {
  let url = getFullURL('api/report');
  if (collection) {
    url = getFullURL(`api/collection/${collection}/reports`);
  }
  const response = await get(url);
  return await response.json();
}
