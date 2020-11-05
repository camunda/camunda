/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  createInstancesByWorkflow,
  createIncidentsByError,
} from 'modules/testUtils';

export const fetchError = {data: [], error: 'fetchError'};
export const emptyData = {data: [], error: null};
// @ts-expect-error ts-migrate(2554) FIXME: Expected 1 arguments, but got 0.
export const incidentsByError = {data: createIncidentsByError()};
// @ts-expect-error ts-migrate(2554) FIXME: Expected 1 arguments, but got 0.
export const instancesByWorkflow = {data: createInstancesByWorkflow()};
