/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Stable validation wrapper — NOT auto-generated, safe across regenerations.
 *
 * The auto-generated file lives in `./_generated/index.ts` (produced by
 * `npm run responses:regenerate` / `assert-json-body extract`). This file
 * re-exports its types and adds forward-compatibility support.
 *
 * ## Forward-compatibility mode
 *
 * When `AJB_ALLOW_EXTRA_FIELDS=true` is set in the environment the following
 * validation errors are silently filtered out so that an older test suite can
 * run against a newer server without false positives:
 *
 *  - `[EXTRA]` — the newer server returns fields not yet declared in the
 *    older `responses.json`.
 *  - `[TYPE] … but got null` — the newer server returns `null` for fields
 *    whose nullability was not yet declared in the older schema.
 *  - `[TYPE] … expected object but got string` — the newer server returns a
 *    JSON-serialized string where the older schema declares an object (common
 *    when a structured field is stringified across versions).
 *  - `...and N more` — the assert-json-body library truncates errors to 15
 *    and returns the rest as a summary line.  When all visible errors were
 *    tolerable, the hidden ones very likely are too, so the summary line
 *    is also filtered.
 *
 * All other error categories (`[MISSING]`, other `[TYPE]`, `[ENUM]`) still
 * fail as before.
 *
 * Usage: set `AJB_ALLOW_EXTRA_FIELDS: "true"` in the CI workflow `env` block
 * for forward-compatibility test runs.
 */

import {
  validateResponseShape as _generatedValidateResponseShape,
  validateResponse as _generatedValidateResponse,
} from './_generated/index.js';
import type {PlaywrightAPIResponse} from 'assert-json-body';

// Re-export all types and constants so callers keep working with the same import path.
export type {TypedRouteSpec} from './_generated/index.js';
export {RESPONSE_INDEX} from './_generated/index.js';

// ---------------------------------------------------------------------------
// Forward-compatibility: filter tolerable schema errors
// ---------------------------------------------------------------------------

const _forwardCompat = process.env.AJB_ALLOW_EXTRA_FIELDS === 'true';

// Derive types from the generated functions so they stay in sync with
// assert-json-body upgrades without manual maintenance.
type ValidateOptions = Parameters<typeof _generatedValidateResponseShape>[2];
type ValidationResult = ReturnType<typeof _generatedValidateResponseShape>;

/** Null-type pattern: `[TYPE] /some/path expected <type> but got null` */
const _nullTypeRe = /^\[TYPE\] .+ but got null$/;

/** Object-to-string pattern: `[TYPE] /some/path expected object but got string` */
const _objectToStringRe = /^\[TYPE\] .+ expected object but got string$/;

/**
 * Truncation summary from assert-json-body: `...and 35 more`.
 *
 * When `throw: false` is used, the library internally throws with at most
 * 15 error lines + a summary, then parses the message back into an array.
 * The summary leaks into the errors array as a plain string.  When every
 * visible error was tolerable, the summary is tolerable too.
 */
const _truncationSummaryRe = /^\.\.\.and \d+ more$/;

function _isTolerableForwardCompatError(e: string): boolean {
  // [EXTRA] — newer server added a field not in the older spec
  if (e.startsWith('[EXTRA]')) return true;
  // [TYPE] … got null — newer server returns null for a field whose
  // nullability is not yet declared in the older schema
  if (_nullTypeRe.test(e)) return true;
  // [TYPE] … expected object but got string — newer server returns a
  // JSON-serialized string where the older schema declares an object
  if (_objectToStringRe.test(e)) return true;
  // Truncation summary line from the library's internal error formatting
  if (_truncationSummaryRe.test(e)) return true;
  return false;
}

function _filterForwardCompatErrors(
  result: ValidationResult,
): ValidationResult {
  if (!_forwardCompat || result.ok || !result.errors) return result;

  const remaining = result.errors.filter(
    (e: string) => !_isTolerableForwardCompatError(e),
  );
  if (remaining.length === 0) {
    return {...result, ok: true, errors: undefined};
  }
  return {...result, errors: remaining};
}

// ---------------------------------------------------------------------------
// Public API — mirrors the generated signatures
// ---------------------------------------------------------------------------

import type {RoutePath, MethodFor, StatusFor} from './_generated/index.js';

export type {RoutePath, MethodFor, StatusFor};

export function validateResponseShape<
  P extends RoutePath,
  M extends MethodFor<P>,
  S extends StatusFor<P, M>,
>(
  spec: {path: P; method: M; status: S},
  body: unknown,
  options?: ValidateOptions,
) {
  if (!_forwardCompat) {
    return _generatedValidateResponseShape(spec, body, options);
  }

  // Call without throwing so we can filter tolerable errors first.
  const result = _generatedValidateResponseShape(spec, body, {
    ...options,
    throw: false,
  }) as ValidationResult;

  const filtered = _filterForwardCompatErrors(result);
  if (!filtered.ok) {
    const shouldThrow = options?.throw !== undefined ? options.throw : true;
    if (shouldThrow) {
      const preview = (filtered.errors ?? []).slice(0, 15).join('\n');
      const extra =
        (filtered.errors?.length ?? 0) > 15
          ? `\n...and ${(filtered.errors?.length ?? 0) - 15} more`
          : '';
      throw new Error(
        `Response shape errors for route ${spec.method || '*'} ${spec.status || '*'} ${spec.path}:\n${preview}${extra}`,
      );
    }
  }
  return filtered;
}

export async function validateResponse<
  P extends RoutePath,
  M extends MethodFor<P>,
  S extends StatusFor<P, M>,
>(
  spec: {path: P; method: M; status: S},
  response: PlaywrightAPIResponse,
  options?: ValidateOptions,
) {
  if (!_forwardCompat) {
    return _generatedValidateResponse(spec, response, options);
  }

  // Delegate body parsing and status checks to the base implementation
  // (handles empty bodies, non-JSON responses, etc.) but suppress throws
  // so we can filter tolerable errors before deciding whether to throw.
  const result = (await _generatedValidateResponse(spec, response, {
    ...options,
    throw: false,
  })) as ValidationResult;

  const filtered = _filterForwardCompatErrors(result);
  if (!filtered.ok) {
    const shouldThrow = options?.throw !== undefined ? options.throw : true;
    if (shouldThrow) {
      const preview = (filtered.errors ?? []).slice(0, 15).join('\n');
      const extra =
        (filtered.errors?.length ?? 0) > 15
          ? `\n...and ${(filtered.errors?.length ?? 0) - 15} more`
          : '';
      throw new Error(
        `Response shape errors for route ${spec.method || '*'} ${spec.status || '*'} ${spec.path}:\n${preview}${extra}`,
      );
    }
  }
  return filtered;
}
