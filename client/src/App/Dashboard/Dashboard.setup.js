/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  createInstancesByWorkflow,
  createIncidentsByError
} from 'modules/testUtils';

export const fetchError = {data: [], error: 'fetchError'};
export const emptyData = {data: [], error: null};
export const incidentsByError = {data: createIncidentsByError()};
export const instancesByWorkflow = {data: createInstancesByWorkflow()};
export const mockCountStore = {running: 120, active: 20, withIncidents: 100};
