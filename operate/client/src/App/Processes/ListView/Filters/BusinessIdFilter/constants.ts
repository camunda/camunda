/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Operator labels and IDs for the Business ID filter.
 * Mirrors the variables-filter operator pattern from
 * `3124-operate-filter-by-multiple-variables` (with `is one of` removed,
 * since Business ID is a single value).
 *
 * Mapping to API operators (used by buildBusinessIdFilterValue):
 *   equals       → {$eq: value}
 *   notEqual     → {$neq: value}
 *   contains     → {$like: "*value*"} (auto-wrap)
 *   exists       → {$exists: true}
 *   doesNotExist → {$exists: false}
 */
export type BusinessIdFilterOperator =
  | 'equals'
  | 'notEqual'
  | 'contains'
  | 'exists'
  | 'doesNotExist';

export const BUSINESS_ID_FILTER_OPERATORS: Array<{
  id: BusinessIdFilterOperator;
  label: string;
  requiresValue: boolean;
}> = [
  {id: 'equals', label: 'equals', requiresValue: true},
  {id: 'notEqual', label: 'not equal', requiresValue: true},
  {id: 'contains', label: 'contains', requiresValue: true},
  {id: 'exists', label: 'exists', requiresValue: false},
  {id: 'doesNotExist', label: 'does not exist', requiresValue: false},
];

export const DEFAULT_BUSINESS_ID_OPERATOR: BusinessIdFilterOperator = 'equals';

/**
 * Returns the operator config for a given ID, falling back to the default
 * (`equals`) when the ID is unknown — used when parsing URL search params.
 */
export function resolveBusinessIdOperator(
  raw: string | undefined,
): BusinessIdFilterOperator {
  const match = BUSINESS_ID_FILTER_OPERATORS.find((op) => op.id === raw);
  return match ? match.id : DEFAULT_BUSINESS_ID_OPERATOR;
}

/**
 * Builds the API-shaped filter value for a given (operator, value) pair.
 * Returns `undefined` when the filter is inactive (empty value with a
 * value-required operator).
 *
 * TODO: At API integration time, the caller should pass the returned object
 * as `filter.businessId` in a POST /v2/process-instances/search request.
 */
export function buildBusinessIdFilterValue(
  operator: BusinessIdFilterOperator,
  value: string,
): Record<string, unknown> | undefined {
  switch (operator) {
    case 'equals':
      return value === '' ? undefined : {$eq: value};
    case 'notEqual':
      return value === '' ? undefined : {$neq: value};
    case 'contains':
      return value === '' ? undefined : {$like: `*${value}*`};
    case 'exists':
      return {$exists: true};
    case 'doesNotExist':
      return {$exists: false};
    default: {
      const _exhaustive: never = operator;
      return _exhaustive;
    }
  }
}
