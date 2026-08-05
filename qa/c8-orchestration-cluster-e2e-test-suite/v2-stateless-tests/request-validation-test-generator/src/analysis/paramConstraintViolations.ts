/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  OperationModel,
  ValidationScenario,
  ParameterModel,
} from '../model/types.js';
import {makeId, validParamValue} from './common.js';
import {buildGuaranteedPatternMismatch} from '../util/patternMismatch.js';

interface Opts {
  onlyOperations?: Set<string>;
  capPerOperation?: number;
}

interface ResolvedParamSchema {
  schema: any;
  pattern?: string;
  minLength?: number;
  maxLength?: number;
  enumValues?: any[];
  type?: string;
}

// Very small resolver: follows allOf chains to merge top-level constraints.
function resolveParamSchema(
  p: ParameterModel,
): ResolvedParamSchema | undefined {
  const schema: any = p.schema;
  if (!schema) return undefined;
  const out: ResolvedParamSchema = {schema};
  function merge(s: unknown) {
    if (!s || typeof s !== 'object') return;
    const obj = s as Record<string, unknown>;
    if (typeof obj.pattern === 'string' && out.pattern === undefined)
      out.pattern = obj.pattern;
    if (typeof obj.minLength === 'number' && out.minLength === undefined)
      out.minLength = obj.minLength;
    if (typeof obj.maxLength === 'number' && out.maxLength === undefined)
      out.maxLength = obj.maxLength;
    if (Array.isArray(obj.enum) && !out.enumValues)
      out.enumValues = obj.enum.slice();
    if (typeof obj.type === 'string' && !out.type) out.type = obj.type;
  }
  // Direct
  merge(schema);
  // allOf chain
  if (Array.isArray(schema.allOf)) {
    for (const part of schema.allOf) merge(part);
  }
  return out;
}

function buildViolations(
  p: ParameterModel,
  r: ResolvedParamSchema,
): {kind: string; invalid: string}[] {
  const out: {kind: string; invalid: string}[] = [];
  // Pattern violation
  if (r.pattern) {
    const invalid = buildGuaranteedPatternMismatch(r.pattern, {
      pathSegmentSafe: p.in === 'path',
    });
    if (invalid) out.push({kind: 'pattern', invalid});
  }
  // Length violations
  if (typeof r.minLength === 'number' && r.minLength > 0) {
    const tooShort = ''.padEnd(Math.max(0, r.minLength - 1), '');
    out.push({kind: 'length-min', invalid: tooShort});
  }
  if (typeof r.maxLength === 'number') {
    const tooLong = 'a'.repeat(r.maxLength + 10);
    out.push({kind: 'length-max', invalid: tooLong});
  }
  // Enum violation (only if enum present)
  if (r.enumValues && r.enumValues.length) {
    let inval = String(r.enumValues[0]) + '_X';
    if (r.pattern === '^-?[0-9]+$') inval = '9999999999999999999999999'; // excessively long number string
    out.push({kind: 'enum', invalid: inval});
  }
  return out;
}

/**
 * Values for the path template's `{token}`s only. Overrides that name no token are ignored - a
 * query parameter passed here would be silently dropped by buildUrl instead of sent, so query
 * values are kept in a separate map and handed to buildUrl's `query` argument.
 */
function buildPathParams(
  path: string,
  overrides: Record<string, string>,
): Record<string, string> | undefined {
  const m = path.match(/\{([^}]+)}/g);
  if (!m) return undefined;
  const params: Record<string, string> = {};
  for (const token of m) params[token.slice(1, -1)] = 'x';
  for (const [k, v] of Object.entries(overrides)) {
    if (k in params) params[k] = v;
  }
  return params;
}

/**
 * Valid placeholder values for the operation's parameters in one location, so the violation under
 * test is the only thing wrong with the request. Optional query parameters are skipped: a dummy
 * value for one of them can fail its own validation, which would make the expected 400 mean
 * something other than the violation the scenario claims to exercise.
 */
function buildValidValues(
  op: OperationModel,
  where: 'path' | 'query',
): Record<string, string> {
  const out: Record<string, string> = {};
  for (const p of op.parameters) {
    if (p.in !== where) continue;
    if (where === 'query' && !p.required) continue;
    out[p.name] = validParamValue(p.schema, p.name);
  }
  return out;
}

export function generateParamConstraintViolations(
  ops: OperationModel[],
  opts: Opts,
): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId))
      continue;
    let produced = 0;
    for (const p of op.parameters) {
      if (p.in !== 'path' && p.in !== 'query') continue; // focus path+query first
      const resolved = resolveParamSchema(p);
      if (!resolved) continue;
      const violations = buildViolations(p, resolved);
      if (!violations.length) continue;
      // Use valid placeholders for all params first
      const validPath = buildValidValues(op, 'path');
      const validQuery = buildValidValues(op, 'query');
      for (const v of violations) {
        if (opts.capPerOperation && produced >= opts.capPerOperation) break;
        const params = buildPathParams(
          op.path,
          p.in === 'path' ? {...validPath, [p.name]: v.invalid} : validPath,
        );
        const queryMap =
          p.in === 'query' ? {...validQuery, [p.name]: v.invalid} : validQuery;
        out.push({
          id: makeId([op.operationId, 'paramConstraint', p.in, p.name, v.kind]),
          operationId: op.operationId,
          method: op.method,
          path: op.path,
          // Temporary cast until ScenarioKind union is extended
          type: 'param-constraint-violation' as unknown as ValidationScenario['type'],
          target: `${p.in}.${p.name}`,
          params,
          query: Object.keys(queryMap).length ? queryMap : undefined,
          expectedStatus: 400,
          description: `${p.in === 'path' ? 'Path' : 'Query'} parameter ${p.name} ${v.kind} constraint violation`,
          headersAuth: true,
          source: p.in,
          // Additional metadata for emitter/title building

          constraintKind: v.kind as any,

          constraintOrigin: 'param' as any,
        });
        produced++;
      }
    }
  }
  return out;
}

// Local pattern mismatch helper removed in favor of shared util.
