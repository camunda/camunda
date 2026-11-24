/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {del, get, post, put} from 'request';

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
