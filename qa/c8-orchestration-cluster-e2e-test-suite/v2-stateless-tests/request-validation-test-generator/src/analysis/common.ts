/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {OperationModel} from '../model/types.js';

/**
 * The resource a path belongs to: `/v2/user-tasks/{userTaskKey}` yields `user-tasks`.
 *
 * A leading `v1` or `v2` segment is an API version marker rather than a resource, so it is stepped
 * over. Paths that carry nothing but a version fall back to `root`.
 *
 * Currently unused.
 */
export function firstResourceSegment(path: string): string {
  const cleaned = path.startsWith('/') ? path.slice(1) : path;
  const segs = cleaned.split('/');
  if (segs[0] === 'v1' || segs[0] === 'v2') return segs[1] || 'root';
  return segs[0] || 'root';
}

/**
 * A stable identifier for one generated scenario, assembled from its parts:
 * `makeId(['createUser', 'missing', 'name'])` yields `createUser__missing_name`.
 *
 * The emitter sorts scenarios by this id, so a regenerated suite keeps the same ordering and the
 * diff stays reviewable. It is also the fallback test title when nothing more readable is
 * available, which is why every character outside `[A-Za-z0-9_]` is collapsed to `_`. Empty parts
 * are dropped so an operation missing an `operationId` does not produce a leading separator.
 */
export function makeId(parts: string[]): string {
  return parts
    .filter(Boolean)
    .join('__')
    .replace(/[^a-zA-Z0-9_]+/g, '_');
}

/**
 * A minimal value of the type a request body property declares.
 *
 * Used to populate the properties a scenario is *not* attacking. A "required property missing"
 * scenario, for instance, fills in every other required property, so the only thing wrong with the
 * body is the omission under test - otherwise the endpoint could reject the request for an
 * unrelated reason and the scenario would pass without proving anything.
 *
 * The value only has to clear the type check, not be semantically plausible. Objects are
 * deliberately left empty rather than recursed into: nested required properties are the concern of
 * the deep-missing-required analysis, and filling them here would duplicate that logic.
 */
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
      return {};
    default:
      return 'x';
  }
}

/**
 * Whether a parameter name reads as the upper end of a range - `endDate`, `toKey`, `createdBefore`.
 *
 * Endpoints that take a range usually reject `end <= start`. Filling both ends with the same instant
 * would trip that check and return a 400 unrelated to the rule under test, which is the same trap
 * `validParamValue` describes. Upper ends therefore get a later value than lower ends.
 *
 * The schema has no field marking a parameter as the end of a range, so the name is the only signal
 * available.
 */
function isRangeEnd(name: string): boolean {
  return /^(to|end)([A-Z_]|$)/.test(name) || /(To|Before)$/.test(name);
}

/**
 * A valid value for a path or query parameter, already serialized as it would appear in a URL.
 *
 * Every generated scenario breaks exactly one rule and expects a 400 back. That only proves
 * anything if the rest of the request is valid, so this fills in each parameter the scenario is
 * *not* attacking with a value the schema accepts.
 *
 * Get this wrong and the test lies instead of failing. Say an endpoint takes a required
 * `startDate` of format `date-time`, and the scenario under test omits a *different* required
 * parameter. Filling `startDate` with a bare `x` also returns a 400 - for the wrong reason - so the
 * scenario goes green without ever checking that the omission is caught. That is why the branches
 * below follow `format`, `pattern` and `minLength` rather than returning one generic placeholder.
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
  // Camunda keys are declared as strings, so `type` alone does not reveal that they only accept
  // digits - the pattern is the sole signal, and a non-numeric value here would be rejected.
  if (pick('pattern') === '^-?[0-9]+$') return '1';
  // A one-character value is too short for a parameter with a minLength constraint, and would draw
  // a 400 of its own.
  const minLength = pick('minLength');
  if (typeof minLength === 'number' && minLength > 1)
    return 'a'.repeat(minLength);
  return 'x';
}

/**
 * A key that identifies an operation independently of where it sits in the spec, so scenarios
 * derived from the same operation can be correlated across generator runs.
 *
 * Prefers the spec's `operationId`; the `method_path` fallback covers operations that declare none.
 *
 * Currently unused.
 */
export function deriveOperationKey(op: OperationModel): string {
  return op.operationId || `${op.method}_${op.path}`;
}
