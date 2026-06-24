/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {APIRequestContext} from 'playwright-core';
import {expect, test} from '@playwright/test';
import {assertStatusCode, buildUrl, jsonHeaders} from '../http';
import {defaultAssertionOptions} from '../constants';
import {validateResponse} from '../../json-body-assertions';
import {createInstances} from '../zeebeClient';

export async function searchVariableByNameAndProcessInstanceKey(
  request: APIRequestContext,
  {
    processInstanceKey,
    name,
  }: {
    processInstanceKey: string;
    name: string;
  },
) {
  const localState: Record<string, unknown> = {};

  await expect(async () => {
    const res = await request.post(buildUrl('/variables/search'), {
      headers: jsonHeaders(),
      data: {
        page: {
          from: 0,
          limit: 10,
        },
        filter: {
          processInstanceKey,
          name,
        },
      },
    });

    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/variables/search',
        method: 'POST',
        status: '200',
      },
      res,
    );

    const json = await res.json();
    expect(json.items.length).toBeGreaterThan(0);
    localState['variable'] = json.items[0];
  }).toPass(defaultAssertionOptions);

  return localState['variable'] as {
    variableKey: string;
    name: string;
    processInstanceKey: string;
    value: string;
    tenantId?: string;
    scopeKey?: string;
    isTruncated?: boolean;
  };
}

export async function setupVariableTest(
  localState: Record<string, unknown>,
  request: APIRequestContext,
) {
  await test.step('Create process instance', async () => {
    const instances = await createInstances('process_with_variables', 1, 1);
    localState['processInstanceKey'] = instances[0].processInstanceKey;
  });

  await test.step('Search variable to get variableKey', async () => {
    const variable = await searchVariableByNameAndProcessInstanceKey(request, {
      processInstanceKey: localState['processInstanceKey'] as string,
      name: 'customerId',
    });
    localState['variableKey'] = variable.variableKey;
  });
}
