/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {VisibleFilters} from './visibleFilters';

type OptionalFilter =
  | 'variable'
  | 'ids'
  | 'parentInstanceId'
  | 'operationId'
  | 'errorMessage'
  | 'startDate'
  | 'endDate';

const optionalFilters: Array<OptionalFilter> = [
  'variable',
  'ids',
  'parentInstanceId',
  'operationId',
  'errorMessage',
  'startDate',
  'endDate',
];

const processInstancesVisibleFiltersStore = new VisibleFilters(optionalFilters);

export {processInstancesVisibleFiltersStore};
