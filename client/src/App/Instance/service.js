/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {STATE} from 'modules/constants';

export function isRunningInstance(instance) {
  return instance.state === STATE.ACTIVE || instance.state === STATE.INCIDENT;
}

// Subscription Handlers;

export function storeResponse({response, state}, callback) {
  if (state === 'LOADED') {
    response && callback(response);
  }
}
