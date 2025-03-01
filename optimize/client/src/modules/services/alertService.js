/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {del, get, post, put} from 'request';
import {track} from 'tracking';

export async function loadAlerts(collection) {
  const response = await get(`api/collection/${collection}/alerts`);
  return await response.json();
}

export async function addAlert(alert) {
  const response = await post(`api/alert`, alert);
  const json = await response.json();
  track('createAlert', {entityId: json.id});
  return response;
}

export async function editAlert(id, alert) {
  const response = await put(`api/alert/${id}`, alert);
  track('updateAlert', {entityId: id});
  return response;
}

export async function removeAlert(id) {
  const response = await del(`api/alert/${id}`);
  track('deleteAlert', {entityId: id});
  return response;
}
