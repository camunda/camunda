/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {VariableFilterOperator} from 'modules/stores/variableFilter';
import type {ProcessInstanceVariableValueFilter} from '@camunda/camunda-api-zod-schemas/8.10';

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
    if (trimmed.includes(',') && !STRUCTURAL_CHARS_RE.test(trimmed)) {
      return trimmed
        .split(',')
        .map((part) => part.trim())
        .filter((part) => part !== '')
        .map(parseScalar);
    }
    if (!STRUCTURAL_CHARS_RE.test(trimmed)) {
      return trimmed;
    }
    throw new Error(`Invalid value: ${raw}`);
  }
};

const toStringFilterProperty = (
  operator: VariableFilterOperator,
  value: unknown,
): ProcessInstanceVariableValueFilter => {
  switch (operator) {
    case 'equals':
      return {$eq: JSON.stringify(value)};
    case 'notEqual':
      return {$neq: JSON.stringify(value)};
    case 'contains':
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

export {smartTransformValue, toStringFilterProperty};
