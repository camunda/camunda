/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {defaultAssertionOptions} from '../constants';
import {APIRequestContext} from 'playwright-core';
import {assertStatusCode, buildUrl, jsonHeaders} from '../http';
import {expect} from '@playwright/test';
import {SearchElementInstancesResponse} from '@camunda8/sdk/dist/c8/lib/C8Dto';
import {validateResponse} from 'json-body-assertions';

export async function resolveAdHocSubProcessInstanceKey(
  request: APIRequestContext,
  processInstanceKey: string,
): Promise<string> {
  const result = await searchElementInstanceByFilter(request, {
    processInstanceKey,
    elementId: 'AdHoc_Subprocess',
  });

  return result.body.items[0].elementInstanceKey;
}

export function createFilter(
  filterKey: string,
  filterValue: string,
  state: Record<string, unknown>,
): {key: string; value: unknown} {
  if (filterValue === '') {
    if (filterKey === 'processDefinitionKey')
      // Use value from state
      return {key: filterKey, value: state.processDefinitionKey};
    else if (filterKey === 'processInstanceKey')
      return {key: filterKey, value: state.processInstanceKey};
    else throw new Error('Unsupported filter key for empty value');
  } else return {key: filterKey, value: filterValue};
}

export async function searchActiveElementInstance(
  request: APIRequestContext,
  processInstanceKey: string,
) {
  return (
    await searchElementInstanceByFilter(request, {
      processInstanceKey: processInstanceKey,
      state: 'ACTIVE',
    })
  ).body.items[0].elementInstanceKey;
}

export async function searchElementInstanceByElementIdAndState(
  request: APIRequestContext,
  processInstanceKey: string,
  elementId: string,
  state: string,
) {
  return (
    await searchElementInstanceByFilter(request, {
      processInstanceKey: processInstanceKey,
      elementId: elementId,
      state: state,
    })
  ).body.items[0].elementInstanceKey;
}

export async function searchElementInstanceByProcessInstance(
  request: APIRequestContext,
  processInstanceKey: string,
) {
  return searchElementInstanceByFilter(request, {
    processInstanceKey: processInstanceKey,
  });
}

async function searchElementInstanceByFilter(
  request: APIRequestContext,
  filter: Record<string, string>,
) {
  const result: Record<string, SearchElementInstancesResponse> = {};
  await expect(async () => {
    const res = await request.post(buildUrl('/element-instances/search'), {
      headers: jsonHeaders(),
      data: {
        filter: filter,
      },
    });
    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/element-instances/search',
        method: 'POST',
        status: '200',
      },
      res,
    );
    const body = await res.json();
    expect(body.items.length, `Received JSON: ${JSON.stringify(body)}`).toBe(1);
    Object.keys(filter).forEach((filterKey) => {
      expect(body.items[0][filterKey]).toBe(filter[filterKey]);
    });
    result.body = body;
  }).toPass({
    ...defaultAssertionOptions,
    timeout: 60_000,
  });
  return result;
}

/** Start times of an element's instances, oldest first. */
export async function elementInstanceStartTimes(
  request: APIRequestContext,
  processInstanceKey: string,
  elementId: string,
): Promise<number[]> {
  const res = await request.post(buildUrl('/element-instances/search'), {
    headers: jsonHeaders(),
    data: {filter: {processInstanceKey, elementId}, page: {limit: 50}},
  });
  await assertStatusCode(res, 200);
  const items: Array<{startDate: string}> = (await res.json()).items ?? [];
  return items
    .map((item) => new Date(item.startDate).getTime())
    .sort((a, b) => a - b);
}

/**
 * The start times after `since`, once there are at least `expected` of them.
 *
 * Anchored on a time, not a count: the export lag is unbounded, so a count
 * cannot say which side of an event its elements fell on.
 */
export async function expectElementInstancesStartedAfter(
  request: APIRequestContext,
  processInstanceKey: string,
  elementId: string,
  since: number,
  expected: number,
  assertionOptions = defaultAssertionOptions,
): Promise<number[]> {
  let after: number[] = [];
  await expect(async () => {
    after = (
      await elementInstanceStartTimes(request, processInstanceKey, elementId)
    ).filter((startedAt) => startedAt > since);
    expect(after.length).toBeGreaterThanOrEqual(expected);
  }).toPass(assertionOptions);
  return after;
}
