/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {STATE} from 'modules/constants';

/**
 * @returns a boolean showing if the current instance has an incident
 * @param {*} instance object with full instance data
 */
export const isWithIncident = instance => {
  return instance.state === STATE.INCIDENT;
};

/**
 * @returns a boolean showing if the current instance is running.
 * @param {*} instance object with full instance data
 */
export const isRunning = instance => {
  return instance.state === STATE.ACTIVE || instance.state === STATE.INCIDENT;
};
