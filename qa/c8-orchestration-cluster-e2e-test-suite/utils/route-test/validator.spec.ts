/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Tests for route-test validator using a sample override responses file.
 */
import {test, expect} from '@playwright/test';
import type {RouteContext} from './types';
import {resolve} from 'node:path';

// Point the loader to the fixture file
let pickRoute: (path: string, method?: string, status?: string) => RouteContext;
let validateResponseShape: (ctx: RouteContext, body: unknown) => void;

test.beforeAll(async () => {
  // Tests are executed with CWD = this package directory, so we can resolve directly.
  process.env.ROUTE_TEST_RESPONSES_FILE = resolve(
    process.cwd(),
    '__fixtures__/sample-responses.json',
  );
  ({pickRoute} = await import('./responses'));
  ({validateResponseShape} = await import('./validator'));
});

test.describe('route-test validator (positive cases)', () => {
  test('validates flat createProcessInstance response', () => {
    const ctx = pickRoute('/process-instance/create', 'POST', '200');
    const body: Record<string, unknown> = {
      processInstanceKey: 123,
      bpmnProcessId: 'proc',
      version: 1,
      tenantId: 't1',
    };
    expect(() => validateResponseShape(ctx, body)).not.toThrow();
  });

  test('validates searchProcessInstances with child array', () => {
    const ctx = pickRoute('/process-instance/search', 'POST', '200');
    const body: Record<string, unknown> = {
      items: [
        {processInstanceKey: 1, bpmnProcessId: 'p1'},
        {processInstanceKey: 2, bpmnProcessId: 'p2', tenantId: 't2'},
      ],
      count: 2,
    };
    expect(() => validateResponseShape(ctx, body)).not.toThrow();
  });

  test('validates object with optional fields present & absent', () => {
    const ctx = pickRoute('/mixed/optional', 'GET', '200');
    const body: Record<string, unknown> = {
      id: 'abc',
      state: 'ACTIVE',
      description: 'desc',
      extra: {flag: true},
      // priority omitted intentionally (optional)
    };
    expect(() => validateResponseShape(ctx, body)).not.toThrow();
  });
});

test.describe('route-test validator (negative cases)', () => {
  test('fails when required field missing', () => {
    const ctx = pickRoute('/process-instance/create', 'POST', '200');
    const body: Record<string, unknown> = {
      // processInstanceKey missing
      bpmnProcessId: 'proc',
      version: 1,
    };
    expect(() => validateResponseShape(ctx, body)).toThrow(
      /MISSING.*processInstanceKey/i,
    );
  });

  test('fails when required field wrong type', () => {
    const ctx = pickRoute('/process-instance/create', 'POST', '200');
    const body: Record<string, unknown> = {
      processInstanceKey: 'not-a-number', // wrong
      bpmnProcessId: 'proc',
      version: 1,
    };
    expect(() => validateResponseShape(ctx, body)).toThrow(
      /processInstanceKey/,
    );
  });

  test('fails when optional field wrong type (if present)', () => {
    const ctx = pickRoute('/mixed/optional', 'GET', '200');
    const body: Record<string, unknown> = {
      id: 'abc',
      state: 'DONE',
      description: 42, // wrong type
      priority: 10,
      extra: {flag: true},
    };
    expect(() => validateResponseShape(ctx, body)).toThrow(/description/);
  });

  test('fails for undeclared additional properties', () => {
    const ctx = pickRoute('/process-instance/create', 'POST', '200');
    const body: Record<string, unknown> = {
      processInstanceKey: 1,
      bpmnProcessId: 'p',
      version: 1,
      unexpected: 'extra', // undeclared
    };
    expect(() => validateResponseShape(ctx, body)).toThrow(/EXTRA/);
  });
});
