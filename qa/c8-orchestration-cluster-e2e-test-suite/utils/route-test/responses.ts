/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {readFileSync} from 'node:fs';
import {resolve, isAbsolute} from 'node:path';
import {ResponseEntry, ResponsesFile, RouteContext} from './types';

// Allow overriding the responses file path via env var for test flexibility.
// If not provided, fall back to the generated responses.json location.
const RESPONSES_FILE_ENV = process.env.ROUTE_TEST_RESPONSES_FILE;
// Support both CJS (__dirname) and ESM (import.meta.url) execution contexts.
const __here = typeof __dirname !== 'undefined' ? __dirname : process.cwd();
const DEFAULT_RESPONSES_PATH = resolve(
  __here,
  '../../response-required-extractor/output/responses.json',
);
const RESPONSES_PATH = (() => {
  if (!RESPONSES_FILE_ENV) return DEFAULT_RESPONSES_PATH;
  return isAbsolute(RESPONSES_FILE_ENV)
    ? RESPONSES_FILE_ENV
    : resolve(process.cwd(), RESPONSES_FILE_ENV);
})();
let responseIndex: Map<string, ResponseEntry[]> | null = null;

export function loadResponses(): Map<string, ResponseEntry[]> {
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

export function pickRoute(
  path: string,
  method?: string,
  status?: string,
): RouteContext {
  const idx = loadResponses();
  const entries = idx.get(path) || [];
  if (entries.length === 0) {
    throw new Error(
      `No OpenAPI response spec entries found for path ${path}. Ensure responses.json was generated and the path is correct.`,
    );
  }
  let filtered = entries;
  if (method)
    filtered = filtered.filter(
      (e) => e.method.toLowerCase() === method.toLowerCase(),
    );
  if (status) filtered = filtered.filter((e) => e.status === status);
  if (method || status) {
    if (filtered.length === 0) {
      const available = entries
        .map((e) => `${e.method} ${e.status}`)
        .sort()
        .join(', ');
      throw new Error(
        `No matching spec entry for ${path} with method=${method || '*'} status=${status || '*'}.\nAvailable combinations: ${available}`,
      );
    }
  }
  const chosen =
    filtered.find((e) => e.status === '200' && e.method === 'GET') ||
    filtered.find((e) => e.status === '200') ||
    filtered.find((e) => e.status === '201') ||
    filtered[0];
  const required = chosen.schema.required || [];
  const optional = chosen.schema.optional || [];
  return {
    route: path,
    method: chosen.method,
    status: chosen.status,
    requiredFieldNames: required.map((f) => f.name),
    requiredFields: required,
    optionalFields: optional,
  };
}
