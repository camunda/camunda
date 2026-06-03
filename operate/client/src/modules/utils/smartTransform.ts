/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {VariableFilterOperator} from 'modules/stores/variableFilter';

const STRUCTURAL_CHARS_RE = /[{}[\]"]/;

const parseScalar = (input: string): unknown => {
  try {
    return JSON.parse(input);
  } catch {
    return input;
  }
};

const smartTransformValue = (raw: string): unknown => {
  const trimmed = raw
    .trim()
    .replace(/^,+|,+$/g, '')
    .trim();
  if (trimmed === '') {
    return '';
  }

  try {
    return JSON.parse(trimmed);
  } catch {
    // Not whole-string JSON. Try comma-split for unquoted lists.
    if (trimmed.includes(',') && !STRUCTURAL_CHARS_RE.test(trimmed)) {
      return trimmed
        .split(',')
        .map((part) => part.trim())
        .filter((part) => part !== '')
        .map(parseScalar);
    }
    // Auto-quote bare strings (no structural JSON characters).
    if (!STRUCTURAL_CHARS_RE.test(trimmed)) {
      return trimmed;
    }
    throw new Error(`Invalid value: ${raw}`);
  }
};

const toStringFilterProperty = (
  operator: VariableFilterOperator,
  value: unknown,
): Record<string, unknown> => {
  switch (operator) {
    case 'equals':
      return {$eq: JSON.stringify(value)};
    case 'notEqual':
      return {$neq: JSON.stringify(value)};
    case 'contains':
      // $like is a glob pattern: `*` matches any chars, `?` matches one.
      // User-typed wildcards pass through unchanged (backend-documented).
      return {$like: `*${String(value)}*`};
    case 'oneOf': {
      const arr = Array.isArray(value) ? value : [value];
      return {$in: arr.map((v) => JSON.stringify(v))};
    }
    case 'exists':
      return {$exists: true};
    case 'doesNotExist':
      return {$exists: false};
  }
};

const operatorTakesValue = (operator: VariableFilterOperator): boolean =>
  operator !== 'exists' && operator !== 'doesNotExist';

export {smartTransformValue, toStringFilterProperty, operatorTakesValue};
