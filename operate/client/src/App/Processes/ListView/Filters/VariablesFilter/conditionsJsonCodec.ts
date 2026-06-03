/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {queryProcessInstancesRequestBodySchema} from '@camunda/camunda-api-zod-schemas/8.10';
import type {DraftCondition} from './constants';

const ApiVariableEntrySchema =
  queryProcessInstancesRequestBodySchema.shape.filter
    .unwrap()
    .shape.variables.unwrap()
    .def.element.extend({
      name: z.string().min(1, 'Variable name is required'),
    })
    .strict();

const ApiVariablesSchema = z.array(ApiVariableEntrySchema);

const apiVariablesJsonSchema = z.toJSONSchema(ApiVariablesSchema);

type ParseResult =
  | {ok: true; conditions: DraftCondition[]}
  | {ok: false; error: string; kind: 'syntax' | 'validation'};

const isPlaceholderRow = (c: DraftCondition): boolean =>
  c.name === '' && c.value === '';

const serializeConditions = (conditions: DraftCondition[]): string => {
  const meaningful = conditions.filter((c) => !isPlaceholderRow(c));
  return JSON.stringify(meaningful.map(toApiEntry), null, 2);
};

const toApiEntry = (condition: DraftCondition): unknown => {
  switch (condition.operator) {
    case 'equals':
      return {name: condition.name, value: condition.value};
    case 'notEqual':
      return {name: condition.name, value: {$neq: condition.value}};
    case 'contains':
      return {name: condition.name, value: {$like: `*${condition.value}*`}};
    case 'oneOf':
      return {
        name: condition.name,
        value: {$in: parseOneOfValues(condition.value)},
      };
    case 'exists':
      return {name: condition.name, value: {$exists: true}};
    case 'doesNotExist':
      return {name: condition.name, value: {$exists: false}};
  }
};

const parseOneOfValues = (raw: string): unknown[] => {
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (Array.isArray(parsed)) {
      return parsed;
    }
  } catch {
    // fall through to comma-split
  }
  return raw
    .split(',')
    .map((v) => v.trim())
    .filter(Boolean);
};

const parseConditionsJson = (text: string): ParseResult => {
  let raw: unknown;
  try {
    raw = JSON.parse(text.trim() || '[]');
  } catch {
    return {ok: false, error: 'Invalid JSON syntax', kind: 'syntax'};
  }

  const unknownOperatorErrors = detectUnknownOperators(raw);
  if (unknownOperatorErrors.length > 0) {
    return {
      ok: false,
      error: unknownOperatorErrors.join('; '),
      kind: 'validation',
    };
  }

  const result = ApiVariablesSchema.safeParse(raw);
  if (!result.success) {
    return {
      ok: false,
      error: formatZodError(result.error),
      kind: 'validation',
    };
  }

  const conditions: DraftCondition[] = [];
  const errors: string[] = [];

  for (let i = 0; i < result.data.length; i++) {
    const mapped = fromApiEntry(result.data[i]!);
    if (mapped.ok) {
      conditions.push(mapped.condition);
    } else {
      errors.push(`Condition #${i + 1}: ${mapped.error}`);
    }
  }

  if (errors.length > 0) {
    return {ok: false, error: errors.join('; '), kind: 'validation'};
  }

  return {ok: true, conditions};
};

const fromApiEntry = (
  entry: z.infer<typeof ApiVariableEntrySchema>,
): {ok: true; condition: DraftCondition} | {ok: false; error: string} => {
  const {name, value} = entry;

  if (typeof value === 'string') {
    return {ok: true, condition: {name, operator: 'equals', value}};
  }

  if (Array.isArray(value)) {
    return {ok: false, error: 'Value must be a string or an operator object'};
  }

  const entries = Object.entries(value).filter(([, v]) => v !== undefined);
  if (entries.length === 0) {
    return {ok: false, error: 'No operator specified in value'};
  }
  if (entries.length > 1) {
    return {
      ok: false,
      error: 'Multiple operators in a single condition are not supported',
    };
  }

  const [op, val] = entries[0]!;

  switch (op) {
    case '$eq':
      if (typeof val !== 'string') {
        return {ok: false, error: "'$eq' must be a string"};
      }
      return {ok: true, condition: {name, operator: 'equals', value: val}};
    case '$neq':
      if (typeof val !== 'string') {
        return {ok: false, error: "'$neq' must be a string"};
      }
      return {ok: true, condition: {name, operator: 'notEqual', value: val}};
    case '$like':
      if (typeof val !== 'string') {
        return {ok: false, error: "'$like' must be a string"};
      }
      return {
        ok: true,
        condition: {
          name,
          operator: 'contains',
          value: val.replace(/^\*/, '').replace(/\*$/, ''),
        },
      };
    case '$in':
      if (!Array.isArray(val)) {
        return {ok: false, error: "'$in' must be an array"};
      }
      return {
        ok: true,
        condition: {
          name,
          operator: 'oneOf',
          value: JSON.stringify(val),
        },
      };
    case '$exists':
      if (typeof val !== 'boolean') {
        return {ok: false, error: "'$exists' must be a boolean"};
      }
      return {
        ok: true,
        condition: {
          name,
          operator: val ? 'exists' : 'doesNotExist',
          value: '',
        },
      };
    default:
      return {ok: false, error: `Unsupported operator '${op}'`};
  }
};

const KNOWN_OPERATORS = new Set([
  '$eq',
  '$neq',
  '$exists',
  '$in',
  '$notIn',
  '$like',
]);

const detectUnknownOperators = (raw: unknown): string[] => {
  if (!Array.isArray(raw)) {
    return [];
  }
  const errors: string[] = [];
  raw.forEach((entry, i) => {
    if (typeof entry !== 'object' || entry === null || Array.isArray(entry)) {
      return;
    }
    const value = (entry as Record<string, unknown>)['value'];
    if (typeof value !== 'object' || value === null || Array.isArray(value)) {
      return;
    }
    for (const key of Object.keys(value)) {
      if (key.startsWith('$') && !KNOWN_OPERATORS.has(key)) {
        errors.push(`Condition #${i + 1}: Unsupported operator '${key}'`);
      }
    }
  });
  return errors;
};

const formatZodError = (error: z.ZodError): string => {
  return error.issues
    .map((issue) => {
      const [index, ...rest] = issue.path;
      const prefix =
        typeof index === 'number' ? `Condition #${index + 1}: ` : '';
      const suffix =
        rest.length > 0 ? ` (at ${rest.map(String).join('.')})` : '';
      return `${prefix}${issue.message}${suffix}`;
    })
    .join('; ');
};

type ConditionRange = {
  startLine: number;
  startColumn: number;
  endLine: number;
  endColumn: number;
};

const isEscaped = (text: string, pos: number): boolean => {
  let backslashes = 0;
  for (let j = pos - 1; j >= 0 && text[j] === '\\'; j--) {
    backslashes++;
  }
  return backslashes % 2 !== 0;
};

const findConditionRanges = (text: string): ConditionRange[] => {
  const ranges: ConditionRange[] = [];
  let depth = 0;
  let line = 1;
  let column = 1;
  let inString = false;
  let startLine = 1;
  let startColumn = 1;

  for (let i = 0; i < text.length; i++) {
    const ch = text[i]!;
    if (ch === '\n') {
      line++;
      column = 1;
      continue;
    }

    if (ch === '"' && !isEscaped(text, i)) {
      inString = !inString;
      column++;
      continue;
    }
    if (inString) {
      column++;
      continue;
    }

    if (ch === '{') {
      depth++;
      if (depth === 1) {
        startLine = line;
        startColumn = column;
      }
    } else if (ch === '}') {
      if (depth === 1) {
        ranges.push({
          startLine,
          startColumn,
          endLine: line,
          endColumn: column + 1,
        });
      }
      depth--;
    }
    column++;
  }

  return ranges;
};

export {
  serializeConditions,
  parseConditionsJson,
  findConditionRanges,
  apiVariablesJsonSchema,
  type ParseResult,
};
