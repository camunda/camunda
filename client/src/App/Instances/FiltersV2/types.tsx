/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {FiltersType} from 'modules/utils/filter';

type ValidatedFields =
  | 'ids'
  | 'startDate'
  | 'endDate'
  | 'variableName'
  | 'variableValue'
  | 'operationId';

type Errors = {
  [key in ValidatedFields]?: string;
};

type VariablePair = Pick<FiltersType, 'variableName' | 'variableValue'>;

export type {FiltersType, Errors, VariablePair};
