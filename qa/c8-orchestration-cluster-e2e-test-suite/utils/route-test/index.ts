/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/** Route-aware Playwright test utilities providing declarative response shape
 * assertions sourced from the OpenAPI-derived responses.json.
 *
 * Recommended usage in a spec file:
 *
 *   import { routeTest as test, expect } from '../utils/test-route/route-test';
 *
 *   test.describe('jobs search', () => {
 *     test.use({ routePath: '/jobs/search' });
 *
 *     test('shape', async ({ request, expectRouteShape, routeCtx }) => {
 *       const res = await request.get(routeCtx.route);
 *       const body = await res.json();
 *       expectRouteShape(body); // validates required top-level fields
 *     });
 *   });
 */
import {test as base, expect} from '@playwright/test';
import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';

// ---------------- Types -----------------
export interface ResponseFieldSpec {
  name: string;
  type: string;
  children?: {required: ResponseFieldSpec[]; optional: ResponseFieldSpec[]};
  enumValues?: string[];
  underlyingPrimitive?: string;
  rawRefName?: string;
  wrapper?: boolean;
}
interface ResponseEntry {
  path: string;
  method: string;
  status: string;
  schema: {required: ResponseFieldSpec[]; optional: ResponseFieldSpec[]};
}
interface ResponsesFile {
  responses: ResponseEntry[];
}

export interface RouteContext {
  route: string;
  requiredFieldNames: string[];
  requiredFields: ResponseFieldSpec[];
  optionalFields: ResponseFieldSpec[];
}

// ------------- Loading / indexing --------------
const RESPONSES_PATH = resolve(
  __dirname,
  '../../response-required-extractor/output/responses.json',
);
let responseIndex: Map<string, ResponseEntry[]> | null = null;

function loadResponses(): Map<string, ResponseEntry[]> {
  if (responseIndex) return responseIndex;
  try {
    const raw = readFileSync(RESPONSES_PATH, 'utf8');
    const parsed: ResponsesFile = JSON.parse(raw);
    const map = new Map<string, ResponseEntry[]>();
    for (const entry of parsed.responses) {
      const list = map.get(entry.path) || [];
      list.push(entry);
      map.set(entry.path, list);
    }
    responseIndex = map;
  } catch {
    responseIndex = new Map();
  }
  return responseIndex;
}

function pickRoute(path: string): RouteContext {
  const idx = loadResponses();
  const entries = idx.get(path) || [];
  if (entries.length === 0) {
    return {
      route: path,
      requiredFieldNames: [],
      requiredFields: [],
      optionalFields: [],
    };
  }
  const chosen =
    entries.find((e) => e.status === '200' && e.method === 'GET') ||
    entries.find((e) => e.status === '200') ||
    entries.find((e) => e.status === '201') ||
    entries[0];
  const required = chosen.schema.required || [];
  const optional = chosen.schema.optional || [];
  return {
    route: path,
    requiredFieldNames: required.map((f) => f.name),
    requiredFields: required,
    optionalFields: optional,
  };
}

// -------------- Fixtures ----------------
export const routeTest = base.extend<{
  routePath: string;
  routeCtx: RouteContext;
  expectResponseShape: (body: unknown) => void;
}>({
  routePath: ['', {option: true}],
  routeCtx: async ({routePath}, use) => {
    const ctx = routePath
      ? pickRoute(routePath)
      : {
          route: routePath,
          requiredFieldNames: [],
          requiredFields: [],
          optionalFields: [],
        };
    await use(ctx);
  },
  expectResponseShape: async ({routeCtx}, use /*, testInfo */) => {
    // Deep validator: recursively ensure all required fields (including nested) exist
    // and basic type expectations are met. Optional fields, when present, are also type-checked
    // and their required descendants validated.
    const fn = (body: unknown): void => {
      if (!routeCtx.route || routeCtx.requiredFields.length === 0) return; // no spec available
      if (body === null || typeof body !== 'object' || Array.isArray(body)) {
        throw new Error(
          `Expected object response body to validate route required fields for ${routeCtx.route}`,
        );
      }

      interface ValidationOptions {
        arraySampleLimit: number; // safety to avoid huge array traversal cost
      }
      const options: ValidationOptions = {arraySampleLimit: 25};

      const errors: string[] = [];

      const isPrimitive = (t: string): t is 'string' | 'number' | 'boolean' =>
        t === 'string' || t === 'number' || t === 'boolean';

      const checkPrimitiveType = (val: unknown, expected: string): boolean => {
        return typeof val === expected;
      };

      const extractArrayInner = (typeStr: string): string | null => {
        const m = /^array<(.+)>$/.exec(typeStr.trim());
        return m ? m[1].trim() : null;
      };

      const shouldTreatAsEnum = (typeStr: string): boolean =>
        /Enum$/.test(typeStr);

      const shouldTreatAsObject = (spec: ResponseFieldSpec): boolean => {
        if (spec.children) return true;
        if (spec.type === 'object') return true;
        // Custom complex type names (capitalized) we treat as object for basic validation
        if (
          /^[A-Z][A-Za-z0-9]+$/.test(spec.type) &&
          !shouldTreatAsEnum(spec.type)
        )
          return true;
        return false;
      };

      const validateGroup = (
        obj: Record<string, unknown>,
        specs: ResponseFieldSpec[],
        path: string,
        mode: 'required' | 'optional-present',
      ) => {
        for (const spec of specs) {
          const fieldPath = path ? `${path}.${spec.name}` : spec.name;
          const exists = spec.name in obj;
          if (!exists) {
            if (mode === 'required') {
              errors.push(`Missing required field: ${fieldPath}`);
            }
            continue;
          }
          const value = (obj as Record<string, unknown>)[spec.name];
          if (value === null) {
            errors.push(`Field ${fieldPath} is null but expected ${spec.type}`);
            continue;
          }
          const inner = extractArrayInner(spec.type);
          if (inner) {
            if (!Array.isArray(value)) {
              errors.push(
                `Field ${fieldPath} expected array but got ${typeof value}`,
              );
            } else if (value.length > 0) {
              const sample = (value as unknown[]).slice(
                0,
                options.arraySampleLimit,
              );
              if (isPrimitive(inner)) {
                for (let i = 0; i < sample.length; i++) {
                  const el = sample[i];
                  if (typeof el !== inner) {
                    errors.push(
                      `Field ${fieldPath}[${i}] expected element type ${inner} but got ${typeof el}`,
                    );
                  }
                }
              } else if (spec.children) {
                for (let i = 0; i < sample.length; i++) {
                  const el = sample[i];
                  if (
                    el === null ||
                    typeof el !== 'object' ||
                    Array.isArray(el)
                  ) {
                    errors.push(
                      `Field ${fieldPath}[${i}] expected object element for ${inner} but got ${
                        Array.isArray(el) ? 'array' : typeof el
                      }`,
                    );
                    continue;
                  }
                  validateGroup(
                    el as Record<string, unknown>,
                    spec.children.required || [],
                    `${fieldPath}[${i}]`,
                    'required',
                  );
                  if (spec.children.optional?.length) {
                    validateGroup(
                      el as Record<string, unknown>,
                      spec.children.optional,
                      `${fieldPath}[${i}]`,
                      'optional-present',
                    );
                  }
                }
              }
            }
            continue;
          }
          // Wrapper handling: if wrapper flag set with underlyingPrimitive, treat as that primitive
          if (
            spec.wrapper &&
            spec.underlyingPrimitive &&
            isPrimitive(spec.underlyingPrimitive)
          ) {
            if (!checkPrimitiveType(value, spec.underlyingPrimitive)) {
              errors.push(
                `Field ${fieldPath} expected wrapper primitive ${spec.underlyingPrimitive} (wrapper ${spec.rawRefName || spec.type}) but got ${Array.isArray(value) ? 'array' : typeof value}`,
              );
            }
            // Enum constraint may still apply
            if (spec.enumValues && typeof value === 'string') {
              if (!spec.enumValues.includes(value)) {
                errors.push(
                  `Field ${fieldPath} value ${JSON.stringify(value)} not in enum set [${spec.enumValues.join(', ')}]`,
                );
              }
            }
            continue;
          }
          if (isPrimitive(spec.type)) {
            if (!checkPrimitiveType(value, spec.type)) {
              errors.push(
                `Field ${fieldPath} expected type ${spec.type} but got ${Array.isArray(value) ? 'array' : typeof value}`,
              );
            }
            if (spec.enumValues && typeof value === 'string') {
              if (!spec.enumValues.includes(value)) {
                errors.push(
                  `Field ${fieldPath} value ${JSON.stringify(value)} not in enum set [${spec.enumValues.join(', ')}]`,
                );
              }
            }
            continue;
          }
          if (shouldTreatAsEnum(spec.type)) {
            if (typeof value !== 'string') {
              errors.push(
                `Field ${fieldPath} expected enum(string) but got ${typeof value}`,
              );
            }
            if (spec.enumValues && typeof value === 'string') {
              if (!spec.enumValues.includes(value)) {
                errors.push(
                  `Field ${fieldPath} value ${JSON.stringify(value)} not in enum set [${spec.enumValues.join(', ')}]`,
                );
              }
            }
            continue;
          }
          if (shouldTreatAsObject(spec)) {
            if (typeof value !== 'object' || Array.isArray(value)) {
              errors.push(
                `Field ${fieldPath} expected object (${spec.type}) but got ${Array.isArray(value) ? 'array' : typeof value}`,
              );
              continue;
            }
            if (spec.children) {
              if (spec.children.required?.length) {
                validateGroup(
                  value as Record<string, unknown>,
                  spec.children.required,
                  fieldPath,
                  'required',
                );
              }
              if (spec.children.optional?.length) {
                validateGroup(
                  value as Record<string, unknown>,
                  spec.children.optional,
                  fieldPath,
                  'optional-present',
                );
              }
            }
            continue;
          }
        }
      };

      // Validate required first, then optional present fields
      validateGroup(
        body as Record<string, unknown>,
        routeCtx.requiredFields,
        '',
        'required',
      );
      if (routeCtx.optionalFields.length) {
        validateGroup(
          body as Record<string, unknown>,
          routeCtx.optionalFields,
          '',
          'optional-present',
        );
      }

      if (errors.length) {
        const preview = errors.slice(0, 15).join('\n');
        const extra =
          errors.length > 15 ? `\n...and ${errors.length - 15} more` : '';
        throw new Error(
          `Response shape errors for route ${routeCtx.route}:\n${preview}${extra}`,
        );
      }
    };
    await use(fn);
  },
});

// -------------- Helpers ----------------
// Tests should set route declaratively via: test.use({ routePath: '/path' }) inside a describe block
// or at top-level before the test definition.

export {expect};
