/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {appendFileSync, existsSync, mkdirSync} from 'node:fs';
import {RouteContext} from './types';

const RECORD_DIR = process.env.TEST_RESPONSE_BODY_RECORD_DIR;

function ensureRecordDir(): void {
  if (!RECORD_DIR) return;
  if (!existsSync(RECORD_DIR)) {
    try {
      mkdirSync(RECORD_DIR, {recursive: true});
    } catch {
      /* ignore */
    }
  }
}

function sanitizeForFile(name: string): string {
  return name.replace(/^\//, '').replace(/[^A-Za-z0-9._-]+/g, '_');
}

export function recordBody(opts: {
  routeCtx: RouteContext;
  body: unknown;
  testTitle?: string;
}): void {
  if (!RECORD_DIR) return;
  try {
    ensureRecordDir();
    const {routeCtx, body, testTitle} = opts;
    const fileBase = `${(routeCtx.method || 'ANY').toUpperCase()}_${
      routeCtx.status || 'ANY'
    }_${sanitizeForFile(routeCtx.route)}`;
    const file = `${RECORD_DIR}/${fileBase}.jsonl`;
    const present: string[] = [];
    const deepSet = new Set<string>();
    const isObj = (v: unknown): v is Record<string, unknown> =>
      !!v && typeof v === 'object' && !Array.isArray(v);
    const escape = (seg: string) =>
      seg.replace(/~/g, '~0').replace(/\//g, '~1');
    const addPath = (p: string) => {
      if (p) deepSet.add(p);
    };
    const walk = (val: unknown, base: string) => {
      if (isObj(val)) {
        for (const key of Object.keys(val)) {
          const ptr = `${base}/${escape(key)}`;
          addPath(ptr);
          walk((val as Record<string, unknown>)[key], ptr);
        }
      } else if (Array.isArray(val)) {
        for (let i = 0; i < val.length; i++) {
          const el = val[i];
          if (isObj(el) || Array.isArray(el)) {
            walk(el, `${base}/*`);
          }
        }
      }
    };
    if (isObj(body)) {
      for (const k of Object.keys(body)) present.push(k);
      walk(body, '');
    }
    const deepPresent = Array.from(deepSet).sort();
    const line = JSON.stringify({
      ts: new Date().toISOString(),
      route: routeCtx.route,
      method: routeCtx.method,
      status: routeCtx.status,
      test: testTitle,
      required: routeCtx.requiredFieldNames,
      present,
      deepPresent,
      body,
    });
    appendFileSync(file, line + '\n');
  } catch {
    /* ignore */
  }
}
