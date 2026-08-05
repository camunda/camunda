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

interface Opts {
  onlyOperations?: Set<string>;
  capPerOperation?: number;
}

function collectQueryParams(op: OperationModel): ParameterModel[] {
  return op.parameters.filter((p) => p.in === 'query');
}

/**
 * Valid placeholder values for the operation's *required* query parameters, so the violation
 * under test is the only thing wrong with the request. Optional parameters are deliberately
 * left out: a dummy value for one of them can fail its own validation, which would make the
 * expected 400 mean something other than the violation the scenario claims to exercise.
 */
function buildRequiredQueryParamMap(
  op: OperationModel,
): Record<string, string> {
  const q: Record<string, string> = {};
  for (const p of collectQueryParams(op)) {
    if (!p.required) continue;
    q[p.name] = validParamValue(p.schema, p.name);
  }
  return q;
}

export function generateParamMissing(
  ops: OperationModel[],
  opts: Opts,
): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId))
      continue;
    let produced = 0;
    for (const p of op.parameters) {
      if (!p.required) continue;
      if (p.in === 'path') continue; // can't "omit" path param without changing path shape
      if (opts.capPerOperation && produced >= opts.capPerOperation) break;
      let query: Record<string, string> | undefined;
      if (p.in === 'query') {
        const allQ = buildRequiredQueryParamMap(op);
        delete allQ[p.name];
        query = Object.keys(allQ).length ? allQ : undefined;
      }
      out.push({
        id: makeId([op.operationId, 'paramMissing', p.in, p.name]),
        operationId: op.operationId,
        method: op.method,
        path: op.path,
        type: 'param-missing',
        target: `${p.in}.${p.name}`,
        params: buildPathParams(op.path),
        query,
        expectedStatus: 400,
        description: `Missing required ${p.in} parameter ${p.name}`,
        headersAuth: true,
        source: p.in,
      });
      produced++;
    }
  }
  return out;
}

export function generateParamTypeMismatch(
  ops: OperationModel[],
  opts: Opts,
): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId))
      continue;
    let produced = 0;
    for (const p of op.parameters) {
      if (!p.schema || !p.schema.type) continue;
      if (p.in === 'path') continue; // path params often strictly string serialized
      if (opts.capPerOperation && produced >= opts.capPerOperation) break;
      // Skip string parameters that can't have a type mismatch: any string is a valid value
      // unless an enum or a format with its own lexical space narrows it down.
      if (p.schema.type === 'string' && !p.schema.enum && !hasTypedFormat(p))
        continue;
      const wrong = wrongTypeValue(p.schema.type);
      if (wrong === undefined) continue;
      // Start with all required query params (so we don't unintentionally create identical empty queries)
      let query: Record<string, string> | undefined;
      if (p.in === 'query') {
        const allQ = buildRequiredQueryParamMap(op);
        // Overwrite the specific param with wrong typed value (stringified to keep buildUrl logic simple)
        if (p.schema?.type === 'boolean') {
          allQ[p.name] = 'notBoolean';
        } else if (
          p.schema?.type === 'integer' ||
          p.schema?.type === 'number'
        ) {
          allQ[p.name] = 'NaNValue';
        } else if (p.schema?.type === 'string') {
          // If we reached here we have format/enum; provide a clearly invalid token
          allQ[p.name] = '__INVALID_STRING__';
        } else if (p.schema?.type === 'array') {
          allQ[p.name] = 'notArray';
        } else if (p.schema?.type === 'object') {
          allQ[p.name] = 'notObject';
        }
        query = allQ;
      }
      out.push({
        id: makeId([op.operationId, 'paramType', p.in, p.name]),
        operationId: op.operationId,
        method: op.method,
        path: op.path,
        type: 'param-type-mismatch',
        target: `${p.in}.${p.name}`,
        params: buildPathParams(op.path),
        query,
        expectedStatus: 400,
        description: `Type mismatch for ${p.in} parameter ${p.name}`,
        headersAuth: true,
        source: p.in,
      });
      produced++;
    }
  }
  return out;
}

export function generateParamEnumViolation(
  ops: OperationModel[],
  opts: Opts,
): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId))
      continue;
    let produced = 0;
    for (const p of op.parameters) {
      const e = p.schema?.enum;
      if (!Array.isArray(e) || !e.length) continue;
      if (p.in === 'path') continue;
      if (opts.capPerOperation && produced >= opts.capPerOperation) break;
      let invalid = '__INVALID_ENUM__';
      if (typeof e[0] === 'string') {
        invalid = e[0] + '_X';
      }
      out.push({
        id: makeId([op.operationId, 'paramEnum', p.in, p.name]),
        operationId: op.operationId,
        method: op.method,
        path: op.path,
        type: 'param-enum-violation',
        target: `${p.in}.${p.name}`,
        params: buildPathParams(op.path),
        query:
          p.in === 'query'
            ? {...buildRequiredQueryParamMap(op), [p.name]: String(invalid)}
            : undefined,
        expectedStatus: 400,
        description: `Enum violation for ${p.in} parameter ${p.name}`,
        headersAuth: true,
        source: p.in,
      });
      produced++;
    }
  }
  return out;
}

/**
 * Standard formats the gateway binds to a typed Java value, so a malformed string is rejected on
 * type grounds alone.
 *
 * The spec also uses `format` for semantic-type aliases (`DocumentId`, `TenantId`, `JobKey`, ...,
 * always alongside `x-semantic-type`). Those name the domain concept, not a lexical space: what a
 * value must look like is expressed separately as `pattern`/`minLength`/`maxLength`, which
 * generateParamConstraintViolations already covers. Treating an alias as a type would claim a
 * mismatch for a value the endpoint has no reason to reject.
 */
const TYPED_STRING_FORMATS = new Set([
  'date',
  'date-time',
  'time',
  'duration',
  'uuid',
]);

function hasTypedFormat(p: ParameterModel): boolean {
  return (
    typeof p.schema?.format === 'string' &&
    TYPED_STRING_FORMATS.has(p.schema.format)
  );
}

function wrongTypeValue(type: string): any {
  switch (type) {
    case 'integer':
    case 'number':
      return 'NaNValue';
    case 'boolean':
      return 'notBoolean';
    case 'string':
      return 12345; // number instead of string
    case 'array':
      return 'notArray';
    case 'object':
      return 'notObject';
    default:
      return undefined;
  }
}

/** Valid placeholders for the path template's `{token}`s only - query values belong in `query`. */
function buildPathParams(path: string): Record<string, string> | undefined {
  const m = path.match(/\{([^}]+)}/g);
  if (!m) return undefined;
  const params: Record<string, string> = {};
  for (const token of m) params[token.slice(1, -1)] = '1'; // default valid numeric-like placeholder
  return params;
}
