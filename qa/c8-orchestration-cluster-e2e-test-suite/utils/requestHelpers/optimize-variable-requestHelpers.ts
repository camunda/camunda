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

/**
 * Represents a variable record returned by the /v2/variables/search endpoint.
 * The scopeKey field is used to determine whether a variable is root-scope
 * (scopeKey === processInstanceKey) or local-scope (scopeKey !== processInstanceKey).
 *
 * Root scope  = variable.scopeKey === variable.processInstanceKey
 * Local scope = variable.scopeKey !== variable.processInstanceKey
 */
export interface VariableRecord {
  variableKey: string;
  name: string;
  value: string;
  processInstanceKey: string;
  scopeKey: string;
  tenantId?: string;
  isTruncated?: boolean;
}

/**
 * Returns true when a variable was created at the process instance (root) scope.
 * Zeebe sets scopeKey === processInstanceKey for variables defined at the root level.
 */
export function isRootScope(variable: VariableRecord): boolean {
  return variable.scopeKey === variable.processInstanceKey;
}

/**
 * Returns true when a variable was created at a child scope (e.g. service task,
 * sub-process, call activity). Zeebe sets scopeKey to the element instance key,
 * which differs from the process instance key.
 */
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

/**
 * Polls /v2/variables/search until at least `minCount` variables are present
 * for the given process instance, then returns all found records.
 *
 * Use minCount=0 and the caller is responsible for asserting absence of variables
 * after an appropriate stabilisation wait.
 */
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

/**
 * Waits the full `timeoutMs` then performs a single assertion that no variables
 * exist for the given process instance.  Using a stabilisation wait avoids
 * false positives caused by async export lag.
 */
export async function assertNoVariablesForProcessInstance(
  request: APIRequestContext,
  processInstanceKey: string,
  timeoutMs: number = 15_000,
): Promise<void> {
  // Wait the full stabilisation period before asserting absence, so that any
  // async export/import lag has time to produce variables if they were going to.
  // A toPass that exits on the first empty poll would be a false positive.
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
/**
 * Returns all variables for the given process instance that match the supplied name.
 */
export function getVariablesByName(
  variables: VariableRecord[],
  name: string,
): VariableRecord[] {
  return variables.filter((v) => v.name === name);
}

/**
 * Returns all variables for the given process instance whose names match at least
 * one of the supplied patterns (exact string or regex).
 */
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
