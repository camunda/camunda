/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Operators for variable value filtering.
 *
 * These map to the v2 API StringFilterProperty operators:
 * - equals → {$eq: value}
 * - notEqual → {$neq: value}
 * - contains → {$like: "*value*"} (auto-wrap wildcards)
 * - oneOf → {$in: [values]} (always array, even single value)
 * - exists → {$exists: true}
 * - doesNotExist → {$exists: false}
 *
 * TODO: Replace mock data with actual API call to POST /v2/process-instances/search
 * Expected request format:
 * {
 *   "filter": {
 *     "variables": [
 *       { "name": "variableName", "value": { "$eq": "exactValue" } },
 *       { "name": "variableName", "value": { "$like": "*contains*" } },
 *       { "name": "variableName", "value": { "$in": ["val1", "val2"] } },
 *       { "name": "variableName", "value": { "$exists": true } }
 *     ]
 *   }
 * }
 */
export type VariableFilterOperator =
  | 'equals'
  | 'notEqual'
  | 'contains'
  | 'oneOf'
  | 'exists'
  | 'doesNotExist';

export const VARIABLE_FILTER_OPERATORS: Array<{
  id: VariableFilterOperator;
  label: string;
  requiresValue: boolean;
}> = [
  {id: 'equals', label: 'equals', requiresValue: true},
  {id: 'notEqual', label: 'not equal', requiresValue: true},
  {id: 'contains', label: 'contains', requiresValue: true},
  {id: 'oneOf', label: 'one of', requiresValue: true},
  {id: 'exists', label: 'exists', requiresValue: false},
  {id: 'doesNotExist', label: 'does not exist', requiresValue: false},
];

export interface VariableFilterCondition {
  id: string;
  name: string;
  operator: VariableFilterOperator;
  value: string;
}

/**
 * Mock variable names for the prototype.
 * TODO: Replace with actual API call to fetch process variable names
 * from GET /v2/variables/search or similar endpoint.
 */
export const MOCK_VARIABLE_NAMES = [
  'customer_id',
  'order_id',
  'status',
  'total_amount',
  'created_date',
  'email',
  'priority',
  'assigned_to',
  'department',
  'region',
];
