/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {VariableFilterOperator} from 'modules/stores/variableFilter';

type OperatorConfig = {
  id: VariableFilterOperator;
  label: string;
  requiresValue: boolean;
};

const VARIABLE_FILTER_OPERATORS: OperatorConfig[] = [
  {id: 'equals', label: 'equals', requiresValue: true},
  {id: 'notEqual', label: 'not equal', requiresValue: true},
  {id: 'contains', label: 'contains', requiresValue: true},
  {id: 'oneOf', label: 'is one of', requiresValue: true},
  {id: 'exists', label: 'exists', requiresValue: false},
  {id: 'doesNotExist', label: 'does not exist', requiresValue: false},
];

type DraftCondition = {
  id: string;
  name: string;
  operator: VariableFilterOperator;
  value: string;
};

export {VARIABLE_FILTER_OPERATORS, type DraftCondition};
