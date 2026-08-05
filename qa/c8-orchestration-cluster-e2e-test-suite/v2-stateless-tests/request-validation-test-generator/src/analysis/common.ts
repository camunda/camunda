/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {OperationModel} from '../model/types.js';

export function firstResourceSegment(path: string): string {
  // strip leading slash then split after /v1|/v2 if present
  const cleaned = path.startsWith('/') ? path.slice(1) : path;
  const segs = cleaned.split('/');
  if (segs[0] === 'v1' || segs[0] === 'v2') return segs[1] || 'root';
  return segs[0] || 'root';
}

export function makeId(parts: string[]): string {
  return parts
    .filter(Boolean)
    .join('__')
    .replace(/[^a-zA-Z0-9_]+/g, '_');
}

export function genPlaceholder(schema: any): any {
  if (!schema) return 'x';
  if (schema.enum && schema.enum.length) return schema.enum[0];
  switch (schema.type) {
    case 'string':
      return 'x';
    case 'integer':
    case 'number':
      return 1;
    case 'boolean':
      return true;
    case 'array':
      return [];
    case 'object':
      return {}; // shallow for now
    default:
      return 'x';
  }
}

/**
 * A parameter that reads as the upper end of a range, so its placeholder has to sort after the
 * lower end's. Endpoints taking a range typically reject `end <= start`, and two parameters filled
 * with the same instant would trip that check - turning an unrelated 400 into a scenario that looks
 * like it exercised its own violation.
 *
 * Nothing in the schema says "this is the end of a range", so the name is the only signal.
 */
function isRangeEnd(name: string): boolean {
  return /^(to|end)([A-Z_]|$)/.test(name) || /(To|Before)$/.test(name);
}

/**
 * A valid, URL-serialized value for a path or query parameter schema.
 *
 * Used to fill in every parameter a scenario is *not* attacking, so the response can only be a
 * rejection of the violation under test. Formats matter here: a required `date-time` parameter
 * filled with a placeholder like `x` is rejected on its own, and the scenario would then pass
 * without ever exercising the violation it claims to.
 */
export function validParamValue(schema: any, name = ''): string {
  if (!schema) return 'x';
  // Follow one level of allOf, which is how the spec layers constraints onto semantic types.
  const parts: any[] = [
    schema,
    ...(Array.isArray(schema.allOf) ? schema.allOf : []),
  ];
  const pick = (key: string) =>
    parts.map((p) => p?.[key]).find((v) => v !== undefined);
  const enumValues = pick('enum');
  if (Array.isArray(enumValues) && enumValues.length)
    return String(enumValues[0]);
  const rangeEnd = isRangeEnd(name);
  switch (pick('format')) {
    case 'date-time':
      return rangeEnd ? '2030-01-01T00:00:00.000Z' : '2020-01-01T00:00:00.000Z';
    case 'date':
      return rangeEnd ? '2030-01-01' : '2020-01-01';
    case 'time':
      return rangeEnd ? '23:59:59Z' : '00:00:00Z';
    case 'duration':
      return 'PT1S';
    case 'uuid':
      return '00000000-0000-0000-0000-000000000000';
    default:
      break;
  }
  const type = pick('type');
  if (type === 'integer' || type === 'number') return '1';
  if (type === 'boolean') return 'true';
  // Keys and other numeric-only strings: the pattern is the only thing that says so.
  if (pick('pattern') === '^-?[0-9]+$') return '1';
  const minLength = pick('minLength');
  if (typeof minLength === 'number' && minLength > 1)
    return 'a'.repeat(minLength);
  return 'x';
}

export function deriveOperationKey(op: OperationModel): string {
  return op.operationId || `${op.method}_${op.path}`;
}
