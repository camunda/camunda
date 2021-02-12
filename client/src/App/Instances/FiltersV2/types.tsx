/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

type FieldsType =
  | 'workflow'
  | 'workflowVersion'
  | 'ids'
  | 'errorMessage'
  | 'startDate'
  | 'endDate'
  | 'flowNodeId'
  | 'variableName'
  | 'variableValue'
  | 'operationId'
  | 'active'
  | 'incidents'
  | 'completed'
  | 'canceled';

type ValidatedFields =
  | 'ids'
  | 'startDate'
  | 'endDate'
  | 'variableName'
  | 'variableValue'
  | 'operationId';

type FiltersType = {
  [key in FieldsType]?: string;
};

type Errors = {
  [key in ValidatedFields]?: string;
};

type VariablePair = Pick<FiltersType, 'variableName' | 'variableValue'>;

export type {FieldsType, FiltersType, Errors, VariablePair};
