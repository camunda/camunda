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
const INTEGER_RE = /^-?(?:0|[1-9]\d*)$/;
// Matches strings first, so that digits inside of them are consumed and left alone.
const JSON_STRING_OR_NUMBER_RE =
  /"(?:[^"\\]|\\.)*"|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/g;
const UNSAFE_INTEGER_MARKER = '\0unsafe-integer:';

/**
 * Parses JSON like `JSON.parse`, but reads integers a double cannot represent
 * as `BigInt`. Variables can hold any 64-bit integer and are matched by their
 * exact JSON text, so a rounded literal would match the wrong variable values.
 */
const parseJson = (input: string): unknown => {
  const markedInput = input.replace(JSON_STRING_OR_NUMBER_RE, (token) =>
    INTEGER_RE.test(token) && !Number.isSafeInteger(Number(token))
      ? JSON.stringify(`${UNSAFE_INTEGER_MARKER}${token}`)
      : token,
  );

  return JSON.parse(markedInput, (_key, value: unknown) =>
    typeof value === 'string' && value.startsWith(UNSAFE_INTEGER_MARKER)
      ? BigInt(value.slice(UNSAFE_INTEGER_MARKER.length))
      : value,
  );
};

/** Encodes a value like `JSON.stringify`, but also supports `BigInt`. */
const encodeJson = (value: unknown): string => {
  if (typeof value === 'bigint') {
    return value.toString();
  }
  if (Array.isArray(value)) {
    return `[${value.map(encodeJson).join(',')}]`;
  }
  if (typeof value === 'object' && value !== null) {
    const members = Object.entries(value).map(
      ([key, member]) => `${JSON.stringify(key)}:${encodeJson(member)}`,
    );
    return `{${members.join(',')}}`;
  }
  return JSON.stringify(value);
};

const parseScalar = (input: string): unknown => {
  try {
    return parseJson(input);
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
    return parseJson(trimmed);
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
      return {$eq: encodeJson(value)};
    case 'notEqual':
      return {$neq: encodeJson(value)};
    case 'contains':
      return {$like: `*${String(value)}*`};
    case 'oneOf': {
      const arr = Array.isArray(value) ? value : [value];
      return {$in: arr.map(encodeJson)};
    }
    case 'exists':
      return {$exists: true};
    case 'doesNotExist':
      return {$exists: false};
  }
};

export {smartTransformValue, toStringFilterProperty};
