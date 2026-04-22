/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {APIRequestContext} from 'playwright-core';
import {expect} from '@playwright/test';
import {assertStatusCode, buildUrl, jsonHeaders} from '../http';
import {defaultAssertionOptions} from '../constants';

export interface VariableRecord {
  variableKey: string;
  name: string;
  value: string;
  processInstanceKey: string;
  scopeKey: string;
  tenantId?: string;
  isTruncated?: boolean;
}

export function isRootScope(variable: VariableRecord): boolean {
  return variable.scopeKey === variable.processInstanceKey;
}

export function isLocalScope(variable: VariableRecord): boolean {
  return variable.scopeKey !== variable.processInstanceKey;
}

export function getRootScopeVariables(
  variables: VariableRecord[],
): VariableRecord[] {
  return variables.filter(isRootScope);
}

export function getLocalScopeVariables(
  variables: VariableRecord[],
): VariableRecord[] {
  return variables.filter(isLocalScope);
}

export async function getAllProcessInstanceVariables(
  request: APIRequestContext,
  processInstanceKey: string,
  minCount: number = 1,
): Promise<VariableRecord[]> {
  const state: {variables: VariableRecord[]} = {variables: []};

  await expect(async () => {
    const res = await request.post(buildUrl('/variables/search'), {
      headers: jsonHeaders(),
      data: {
        page: {from: 0, limit: 100},
        filter: {processInstanceKey},
      },
    });
    await assertStatusCode(res, 200);
    const json = await res.json();
    const items: VariableRecord[] = json.items ?? [];
    if (minCount > 0) {
      expect(items.length).toBeGreaterThanOrEqual(minCount);
    }
    state.variables = items;
  }).toPass(defaultAssertionOptions);

  return state.variables;
}

export async function assertNoVariablesForProcessInstance(
  request: APIRequestContext,
  processInstanceKey: string,
  timeoutMs: number = 15_000,
): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, timeoutMs));
  const res = await request.post(buildUrl('/variables/search'), {
    headers: jsonHeaders(),
    data: {
      page: {from: 0, limit: 10},
      filter: {processInstanceKey},
    },
  });
  await assertStatusCode(res, 200);
  const json = await res.json();
  expect(json.items).toHaveLength(0);
}

export function getVariablesByName(
  variables: VariableRecord[],
  name: string,
): VariableRecord[] {
  return variables.filter((v) => v.name === name);
}

export function getVariablesByPatterns(
  variables: VariableRecord[],
  patterns: (string | RegExp)[],
): VariableRecord[] {
  return variables.filter((v) =>
    patterns.some((p) =>
      typeof p === 'string' ? v.name === p : p.test(v.name),
    ),
  );
}