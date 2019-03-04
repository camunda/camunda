/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {parseFilterForRequest} from 'modules/utils/filter';
import {FILTER_SELECTION} from 'modules/constants';

// keys for values that fallback to the localState
export const localStateKeys = [
  'filter',
  'filterCount',
  'selectionCount',
  'instancesInSelectionsCount'
];

// keys for values that fallback to the api
export const apiKeys = ['runningInstancesCount', 'incidentsCount'];

export const filtersMap = {
  incidentsCount: parseFilterForRequest(FILTER_SELECTION.incidents),
  runningInstancesCount: parseFilterForRequest(FILTER_SELECTION.running)
};
