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
  accessToken: Buffer.from(`demo:demo`).toString('base64'),
};

export const paginatedResponseFields: string[] = ['items', 'page'];

export function authHeaders(token?: string): Record<string, string> {
  const h: Record<string, string> = {};
  if (token) h.Authorization = `Basic ${credentials.accessToken}`;
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
    expect(has(obj, f)).toBe(true);
    const v = (obj as Record<string, unknown>)[f];
    expect(v).toBeDefined();
  }
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

export function jsonHeaders(): Record<string, string> {
  return {
    'Content-Type': 'application/json',
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
  let url = `${base}/${version}${pathTemplate}`.replace(
    /\{(\w+)\}/g,
    (_, k) => {
      const v = params?.[k];
      return v == null ? '__MISSING_PARAM__' : String(v);
    },
  );
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
  state: Record<string, any>,
) {
  try {
    const ct = resp.headers()['content-type'] || '';
    if (!ct.includes('application/json')) return;
    const data = await resp.json();

    const consider = (o: any) => {
      if (o && typeof o === 'object') {
        for (const [k, v] of Object.entries(o)) {
          if (
            /(Id|Key|username|documentId|clientId|groupId|roleId|name|mappingRuleId|contentHash|description|claimName|claimValue)$/.test(
              k,
            )
          ) {
            state[k] = v;
          }
        }
      }
    };
    Array.isArray(data) ? data.forEach(consider) : consider(data);

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
