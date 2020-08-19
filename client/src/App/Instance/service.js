/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {STATE} from 'modules/constants';
import * as api from 'modules/api/instances';

export function isRunningInstance(instance) {
  return instance.state === STATE.ACTIVE || instance.state === STATE.INCIDENT;
}

export async function fetchIncidents(instance) {
  return instance.state === 'INCIDENT'
    ? await api.fetchWorkflowInstanceIncidents(instance.id)
    : null;
}

// Subscription Handlers;

export function storeResponse({response, state}, callback) {
  if (state === 'LOADED') {
    response && callback(response);
  }
}
