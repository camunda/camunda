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
 * When `AJB_ALLOW_EXTRA_FIELDS=true` is set in the environment, `[EXTRA]`
 * validation errors are silently filtered out. This allows running an older
 * test suite against a newer server that returns additional fields not yet
 * declared in `responses.json`.
 *
 * All other error categories (`[MISSING]`, `[TYPE]`, `[ENUM]`) still fail.
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
// Forward-compatibility: filter [EXTRA] errors
// ---------------------------------------------------------------------------

const _allowExtraFields = process.env.AJB_ALLOW_EXTRA_FIELDS === 'true';

// Derive types from the generated functions so they stay in sync with
// assert-json-body upgrades without manual maintenance.
type ValidateOptions = Parameters<typeof _generatedValidateResponseShape>[2];
type ValidationResult = ReturnType<typeof _generatedValidateResponseShape>;

function _filterExtraFieldErrors(result: ValidationResult): ValidationResult {
  if (!_allowExtraFields || result.ok || !result.errors) return result;

  const remaining = result.errors.filter(
    (e: string) => !e.startsWith('[EXTRA]'),
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
  if (!_allowExtraFields) {
    return _generatedValidateResponseShape(spec, body, options);
  }

  // Call without throwing so we can filter [EXTRA] errors first.
  const result = _generatedValidateResponseShape(spec, body, {
    ...options,
    throw: false,
  }) as ValidationResult;

  const filtered = _filterExtraFieldErrors(result);
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
  if (!_allowExtraFields) {
    return _generatedValidateResponse(spec, response, options);
  }

  // Delegate body parsing and status checks to the base implementation
  // (handles empty bodies, non-JSON responses, etc.) but suppress throws
  // so we can filter [EXTRA] errors before deciding whether to throw.
  const result = (await _generatedValidateResponse(spec, response, {
    ...options,
    throw: false,
  })) as ValidationResult;

  const filtered = _filterExtraFieldErrors(result);
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
