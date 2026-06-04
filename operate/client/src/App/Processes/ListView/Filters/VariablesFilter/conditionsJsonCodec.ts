/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {processInstanceVariableFilterSchema} from '@camunda/camunda-api-zod-schemas/8.10';
import type {DraftCondition} from './constants';

// Build on the official top-level schema (a `.strict()` object whose `value`
// field is a discriminated union of `.strict()` operator shapes). We only
// override `name` to add the UI-only "non-empty" constraint.
const ApiVariableEntrySchema = processInstanceVariableFilterSchema.extend({
  name: z.string().min(1, 'Variable name is required'),
});

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

// The schema guarantees `value` is `string | {$eq} | {$neq} | {$like} |
// {$in} | {$notIn} | {$exists}` with strict shape, so we can dispatch on the
// presence of a single operator key without re-validating types.
const fromApiEntry = (
  entry: z.infer<typeof ApiVariableEntrySchema>,
): {ok: true; condition: DraftCondition} | {ok: false; error: string} => {
  const {name, value} = entry;

  if (typeof value === 'string') {
    return {ok: true, condition: {name, operator: 'equals', value}};
  }
  if ('$eq' in value) {
    return {ok: true, condition: {name, operator: 'equals', value: value.$eq}};
  }
  if ('$neq' in value) {
    return {
      ok: true,
      condition: {name, operator: 'notEqual', value: value.$neq},
    };
  }
  if ('$like' in value) {
    return {
      ok: true,
      condition: {
        name,
        operator: 'contains',
        value: value.$like.replace(/^\*/, '').replace(/\*$/, ''),
      },
    };
  }
  if ('$in' in value) {
    return {
      ok: true,
      condition: {name, operator: 'oneOf', value: JSON.stringify(value.$in)},
    };
  }
  if ('$exists' in value) {
    return {
      ok: true,
      condition: {
        name,
        operator: value.$exists ? 'exists' : 'doesNotExist',
        value: '',
      },
    };
  }
  if ('$notIn' in value) {
    return {ok: false, error: "'$notIn' is not yet supported in the UI"};
  }
  // Unreachable: schema's discriminated union covers all cases.
  return {ok: false, error: 'Unsupported operator'};
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
