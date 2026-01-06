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

export async function searchElementInstanceByFilter(
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
    const body = await res.json();
    expect(body.items.length, `Received JSON: ${JSON.stringify(body)}`).toBe(1);
    Object.keys(filter).forEach((filterKey) => {
      expect(body.items[0][filterKey]).toBe(filter[filterKey]);
    });
    result.body = body;
  }).toPass(defaultAssertionOptions);
  return result;
}

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
    const body = await res.json();
    expect(body.items.length, `Received JSON: ${JSON.stringify(body)}`).toBe(1);
    Object.keys(filter).forEach((filterKey) => {
      expect(body.items[0][filterKey]).toBe(filter[filterKey]);
    });
    result.body = body;
  }).toPass(defaultAssertionOptions);
  return result;
}
