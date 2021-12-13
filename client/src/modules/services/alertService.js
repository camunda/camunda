/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get, post, put, del} from 'request';

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
