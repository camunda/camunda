/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get} from 'modules/request';

export const fetchInstancesByWorkflow = async () => {
  try {
    const response = await get('/api/incidents/byWorkflow');
    return {data: await response.json()};
  } catch (e) {
    return {error: e, data: []};
  }
};

export const fetchIncidentsByError = async () => {
  try {
    const response = await get('/api/incidents/byError');
    return {data: await response.json()};
  } catch (e) {
    return {error: e, data: []};
  }
};
