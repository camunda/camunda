/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {DecisionInstanceFilters} from 'modules/utils/filter';
import {VisibleFilters} from './visibleFilters';

const optionalFilters: Array<keyof DecisionInstanceFilters> = [
  'decisionInstanceId',
  'processInstanceId',
  'evaluationDate',
];

const decisionInstancesVisibleFiltersStore = new VisibleFilters(
  optionalFilters
);

export {decisionInstancesVisibleFiltersStore};
