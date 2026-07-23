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
import {validateResponse} from '../../json-body-assertions';
import {createSingleInstance, deploy} from '../zeebeClient';

export const WAIT_STATES_SEARCH_ENDPOINT =
  '/element-instances/wait-states/search';

export interface WaitStateItem {
  rootProcessInstanceKey: string;
  processInstanceKey: string;
  elementInstanceKey: string;
  elementId: string;
  elementType: string;
  bpmnProcessId: string;
  tenantId: string;
  // waitStateType lives inside `details`, alongside per-type fields
  // (jobKey, messageName, etc.) — confirmed against a live response, not
  // a top-level field.
  details: {waitStateType: string; [key: string]: unknown};
  [key: string]: unknown;
}

export interface WaitStateSearchResponse {
  items: WaitStateItem[];
  page: {
    totalItems: number;
    hasMoreTotalItems?: boolean;
    startCursor?: string | null;
    endCursor?: string | null;
  };
}

/**
 * Polls `wait-states/search` until it returns a 200 with a validated shape.
 * Does not assert on item count — callers assert that themselves, since some
 * negative cases (e.g. flag disabled, unknown key) expect zero items.
 */
export async function searchWaitStatesByFilter(
  request: APIRequestContext,
  filter: Record<string, unknown>,
  options: {page?: Record<string, unknown>; auth?: string} = {},
): Promise<WaitStateSearchResponse> {
  const result: Record<string, unknown> = {};
  await expect(async () => {
    const res = await request.post(buildUrl(WAIT_STATES_SEARCH_ENDPOINT), {
      headers: jsonHeaders(options.auth),
      data: {filter, ...(options.page ? {page: options.page} : {})},
    });
    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: WAIT_STATES_SEARCH_ENDPOINT,
        method: 'POST',
        status: '200',
      },
      res,
    );
    result.body = await res.json();
  }).toPass(defaultAssertionOptions);
  return result.body as WaitStateSearchResponse;
}

/**
 * Waits until `wait-states/search` reports at least one row for the given
 * process instance — absorbs exporter/indexing lag between instance
 * creation and the wait-state row becoming searchable.
 */
export async function waitForWaitState(
  request: APIRequestContext,
  processInstanceKey: string,
): Promise<WaitStateItem> {
  const result: Record<string, unknown> = {};
  await expect(async () => {
    const body = await searchWaitStatesByFilter(request, {processInstanceKey});
    expect(
      body.items.length,
      `Received JSON: ${JSON.stringify(body)}`,
    ).toBeGreaterThan(0);
    result.item = body.items[0];
  }).toPass(defaultAssertionOptions);
  return result.item as WaitStateItem;
}

/** JOB wait state: a service task whose type is never claimed by a worker. */
export async function createProcessInstanceWaitingOnJob(): Promise<{
  processInstanceKey: string;
}> {
  await deploy(['./resources/simpleServiceTaskProcess.bpmn']);
  return createSingleInstance('simpleServiceTaskProcess', 1);
}

/** MESSAGE wait state: a receive task with a literal correlation key. */
export async function createProcessInstanceWaitingOnMessage(): Promise<{
  processInstanceKey: string;
}> {
  await deploy(['./resources/messageCatchEvent1.bpmn']);
  return createSingleInstance('messageCatchEvent1', 1);
}

/** SIGNAL wait state: an intermediate catch event subscribed to Signal_220k2ur. */
export async function createProcessInstanceWaitingOnSignal(): Promise<{
  processInstanceKey: string;
}> {
  await deploy(['./resources/signal_broadcast_test_process.bpmn']);
  return createSingleInstance('signal_broadcast_test_process', 1, {
    orderId: '123',
  });
}

/** USER_TASK wait state. */
export async function createProcessInstanceWaitingOnUserTask(): Promise<{
  processInstanceKey: string;
}> {
  await deploy(['./resources/user_task_api_test_process.bpmn']);
  return createSingleInstance('user_task_api_test_process', 1);
}

/**
 * TIMER wait state. `duration` is an ISO-8601 duration (e.g. `PT2S` for a
 * short-lived timer that resolves on its own, `PT1H` for one that stays
 * waiting for the duration of a test).
 */
export async function createProcessInstanceWaitingOnTimer(
  duration: string,
): Promise<{processInstanceKey: string}> {
  await deploy(['./resources/timerIntermediateCatchEvent.bpmn']);
  return createSingleInstance('timerIntermediateCatchEventProcess', 1, {
    duration,
  });
}

/**
 * CONDITIONAL wait state. Starts with `x=1` (condition `x > 10` unmet).
 * Resolve by calling `setVariables(processInstanceKey, {x: 42})` — the same
 * mechanism dev's own WaitStateConditionalIT uses to re-trigger evaluation.
 */
export async function createProcessInstanceWaitingOnCondition(): Promise<{
  processInstanceKey: string;
}> {
  await deploy(['./resources/conditionalIntermediateCatchEvent.bpmn']);
  return createSingleInstance('conditionalIntermediateCatchEventProcess', 1, {
    x: 1,
  });
}

/**
 * High-cardinality multi-instance JOB wait state, all on the same
 * elementId, for cap-behavior testing. Never claimed by a worker — the
 * `highCardinalityWaitTask` job type is dedicated to this resource.
 */
export async function createProcessInstanceWithManyWaitingTokens(
  tokenCount: number,
): Promise<{processInstanceKey: string}> {
  await deploy(['./resources/highCardinalityMultiInstance.bpmn']);
  return createSingleInstance('highCardinalityMultiInstance', 1, {
    items: Array.from({length: tokenCount}, (_, i) => i),
  });
}
