/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {DecisionInstanceFilters} from 'modules/utils/filter';
import {VisibleFilters} from './visibleFilters';

type OptionalFilter = keyof Pick<
  DecisionInstanceFilters,
  'decisionInstanceIds' | 'processInstanceId' | 'evaluationDate'
>;

const optionalFilters: Array<OptionalFilter> = [
  'decisionInstanceIds',
  'processInstanceId',
  'evaluationDate',
];

const decisionInstancesVisibleFiltersStore = new VisibleFilters<OptionalFilter>(
  optionalFilters
);

export {decisionInstancesVisibleFiltersStore};
export type {OptionalFilter};
