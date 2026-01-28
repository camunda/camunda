/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, APIResponse} from '@playwright/test';

export type Credentials = {
  baseUrl: string;
  accessToken: string;
};

export const credentials: Credentials = {
  baseUrl: 'http://localhost:8080',
  accessToken: encode(`demo:demo`),
};

export function encode(auth: string) {
  return Buffer.from(auth).toString('base64');
}

export const paginatedResponseFields: string[] = ['items', 'page'];

export function authHeaders(token?: string): Record<string, string> {
  const h: Record<string, string> = {};
  if (token) h.Authorization = `Basic ${token}`;
  return h;
}

function has(obj: unknown, prop: string): boolean {
  return (
    obj != null && Object.prototype.hasOwnProperty.call(obj as object, prop)
  );
}

export function assertRequiredFields(obj: unknown, required: string[]): void {
  expect(obj).toBeTruthy();
  for (const f of required) {
    if (!has(obj, f)) {
      console.error('❌ Missing required field:', f);
      console.error('Full response object:', JSON.stringify(obj, null, 2));
    }
    expect(has(obj, f)).toBe(true);
    const v = (obj as Record<string, unknown>)[f];
    expect(v).toBeDefined();
  }
}

export async function assertStatusCode(
  response: APIResponse,
  expectedStatusCode: number,
  logMessage: string = 'Unexpected status code:',
) {
  if (response.status() !== expectedStatusCode) {
    const body = await response.text().catch(() => '<no-body>');
    console.error(logMessage, response.status(), body);
  }
  expect(response.status()).toBe(expectedStatusCode);
}

export async function assertUnauthorizedRequest(response: APIResponse) {
  await assertStatusCode(response, 401);
  const json = await response.json();
  assertRequiredFields(json, ['detail', 'title']);
  expect(json.title).toBe('Unauthorized');
}

export async function assertUnsupportedMediaTypeRequest(response: APIResponse) {
  await assertStatusCode(response, 415);
  const json = await response.json();
  expect(json.title).toContain('Unsupported Media Type');
}

export async function assertBadRequest(
  response: APIResponse,
  detail: string | RegExp,
  title = 'Bad Request',
) {
  await assertStatusCode(response, 400);
  const json = await response.json();
  assertRequiredFields(json, ['detail', 'title']);
  expect(json.title).toBe(title);
  expect(json.detail).toMatch(detail);
}

export async function assertPaginatedRequest(
  response: APIResponse,
  options: {
    totalItemGreaterThan?: number;
    itemLengthGreaterThan?: number;
    totalItemsEqualTo?: number;
    itemsLengthEqualTo?: number;
  },
) {
  await assertStatusCode(response, 200);
  const json = await response.json();
  assertRequiredFields(json, paginatedResponseFields);
  if (options.totalItemGreaterThan !== undefined) {
    expect(json.page.totalItems).toBeGreaterThan(
      options.totalItemGreaterThan as number,
    );
  }
  if (options.itemLengthGreaterThan !== undefined) {
    expect(json.items.length).toBeGreaterThan(
      options.itemLengthGreaterThan as number,
    );
  }
  if (options.totalItemsEqualTo !== undefined) {
    expect(json.page.totalItems).toBe(options.totalItemsEqualTo as number);
  }
  if (options.itemsLengthEqualTo !== undefined) {
    expect(json.items.length).toBe(options.itemsLengthEqualTo as number);
  }
}

export async function assertForbiddenRequest(
  response: APIResponse,
  detail: string,
) {
  await assertStatusCode(response, 403);
  const json = await response.json();
  assertRequiredFields(json, ['detail', 'title']);
  expect(json.title).toBe('FORBIDDEN');
  expect(json.detail).toContain(detail);
}

export async function assertConflictRequest(
  response: APIResponse,
  detail?: string,
) {
  await assertStatusCode(response, 409);
  const json = await response.json();
  assertRequiredFields(json, ['title', 'detail']);
  expect(json.title).toBe('ALREADY_EXISTS');
  if (detail) {
    expect(json.detail).toContain(detail);
  }
}

export async function assertInvalidState(
  response: APIResponse,
  expectedStatusCode: number = 409,
) {
  await assertStatusCode(response, expectedStatusCode);
  const json = await response.json();
  assertRequiredFields(json, ['detail', 'title']);
  expect(json.title).toBe('INVALID_STATE');
}
export async function assertNotFoundRequest(
  response: APIResponse,
  detail: string,
) {
  await assertStatusCode(response, 404);
  const json = await response.json();
  assertRequiredFields(json, ['detail', 'title']);
  expect(json.title).toBe('NOT_FOUND');
  expect(json.detail).toContain(detail);
}

export async function assertInvalidArgument(
  response: APIResponse,
  expectedStatusCode: number = 400,
  detail: string,
) {
  await assertStatusCode(response, expectedStatusCode);
  const json = await response.json();
  assertRequiredFields(json, ['detail', 'title']);
  expect(json.title).toBe('INVALID_ARGUMENT');
  expect(json.detail).toContain(detail);
}

export function assertEqualsForKeys(
  obj: unknown,
  expected: Record<string, unknown>,
  keys: readonly string[],
): void {
  if (obj == null || typeof obj !== 'object') {
    throw new Error('Response is not an object');
  }
  const rec = obj as Record<string, unknown>;
  const hasOwn = Object.prototype.hasOwnProperty;

  const fmt = (v: unknown): string => {
    try {
      return JSON.stringify(v);
    } catch {
      return String(v);
    }
  };

  for (const k of keys) {
    if (!hasOwn.call(rec, k)) {
      throw new Error(`Missing key '${k}' on response object`);
    }
    if (!hasOwn.call(expected, k)) {
      throw new Error(`Missing key '${k}' in expected values`);
    }

    const actual = rec[k];
    const exp = expected[k];

    const bothObjects =
      actual !== null &&
      typeof actual === 'object' &&
      exp !== null &&
      typeof exp === 'object';

    const equal = bothObjects
      ? fmt(actual) === fmt(exp)
      : Object.is(actual, exp);

    if (!equal) {
      throw new Error(
        `Value mismatch for key '${k}': actual=${fmt(actual)} expected=${fmt(exp)}`,
      );
    }
  }
}

export function jsonHeaders(
  auth: string = credentials.accessToken,
): Record<string, string> {
  return {
    'Content-Type': 'application/json',
    ...authHeaders(auth),
  };
}

export function textXMLHeaders(
  auth: string = credentials.accessToken,
): Record<string, string> {
  return {
    'Content-Type': 'application/json',
    Accept: 'text/xml',
    ...authHeaders(auth),
  };
}

export function defaultHeaders(): Record<string, string> {
  return {
    Accept: 'application/json',
    ...authHeaders(credentials.accessToken),
  };
}

export function octetStreamHeaders(token?: string): Record<string, string> {
  return {Accept: 'application/octet-stream', ...authHeaders(token)};
}

export function buildUrl(
  pathTemplate: string, // e.g., "/tenants/{tenantId}"
  params?: Record<string, string | number | undefined>,
  query?: Record<string, string | number | undefined>,
): string {
  const version: string = 'v2';
  const base = credentials.baseUrl;
  let url = `${base}/${version}${pathTemplate}`.replace(/\{(\w+)}/g, (_, k) => {
    const v = params?.[k];
    return v == null ? '__MISSING_PARAM__' : String(v);
  });
  if (query) {
    const q = Object.entries(query)
      .filter(([, v]) => v !== undefined)
      .map(
        ([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`,
      )
      .join('&');
    if (q) url += (url.includes('?') ? '&' : '?') + q;
  }
  return url;
}

export async function extractAndStoreIds(
  resp: APIResponse,
  state: Record<string, unknown>,
) {
  try {
    const ct = resp.headers()['content-type'] || '';
    if (!ct.includes('application/json')) return;
    const data = await resp.json();

    const consider = (o: Record<string, unknown>) => {
      if (o && typeof o === 'object') {
        for (const [k, v] of Object.entries(o)) {
          if (
            /(Id|Key|username|documentId|clientId|groupId|roleId|name|mappingRuleId|contentHash|description|claimName|claimValue)$/.test(
              k,
            )
          ) {
            if (typeof v === 'string' || typeof v === 'number') {
              state[k] = v;
            }
          }
        }
      }
    };
    if (Array.isArray(data)) {
      data.forEach(consider);
    } else {
      consider(data);
    }
    const loc = resp.headers()['location'] || resp.headers()['Location'];
    if (loc) {
      const parts = String(loc).split('/').filter(Boolean);
      for (let i = 0; i < parts.length - 1; i++) {
        const seg = parts[i];
        const val = parts[i + 1];
        if (/^\w[\w-]*$/.test(val)) {
          const base = seg.replace(/s$/, '');
          const key = `${base}Id`;
          if (state[key] == null) state[key] = val;
        }
      }
    }
  } catch (error) {
    console.error(
      'Error occurred while parsing response to extract IDs' + error,
    );
  }
}
