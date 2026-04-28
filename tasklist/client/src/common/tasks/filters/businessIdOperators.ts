/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Operator labels and IDs for the Business ID filter in tasklist.
 * Local mirror of the Operate constants under
 * `operate/client/src/App/Processes/ListView/Filters/BusinessIdFilter/constants.ts`.
 *
 * TODO: Wire up to the API once Business ID filtering is implemented backend-side.
 */
export type BusinessIdFilterOperator =
  | 'equals'
  | 'notEqual'
  | 'contains'
  | 'exists'
  | 'doesNotExist';

export const BUSINESS_ID_FILTER_OPERATORS: Array<{
  id: BusinessIdFilterOperator;
  i18nKey: string;
  requiresValue: boolean;
}> = [
  {id: 'equals', i18nKey: 'filterOperatorEquals', requiresValue: true},
  {id: 'notEqual', i18nKey: 'filterOperatorNotEqual', requiresValue: true},
  {id: 'contains', i18nKey: 'filterOperatorContains', requiresValue: true},
  {id: 'exists', i18nKey: 'filterOperatorExists', requiresValue: false},
  {
    id: 'doesNotExist',
    i18nKey: 'filterOperatorDoesNotExist',
    requiresValue: false,
  },
];

export const DEFAULT_BUSINESS_ID_OPERATOR: BusinessIdFilterOperator = 'equals';
