/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

const processInstancesVisibleFiltersStore = new VisibleFilters<OptionalFilter>(
  optionalFilters
);

export {processInstancesVisibleFiltersStore};
export type {OptionalFilter};
