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
import {createSingleInstance} from '../zeebeClient';

export async function createProcessInstanceAndRetrieveTimeStamp(
  request: APIRequestContext,
  processDefinitionId: string,
) {
  const instance = await createSingleInstance(processDefinitionId, 1);
  const processInstanceKeyToGet = instance.processInstanceKey;
  let startDate = '';
  let endDate = '';

  await expect(async () => {
    const res = await request.get(
      buildUrl(`/process-instances/${processInstanceKeyToGet}`),
      {
        headers: jsonHeaders(),
      },
    );

    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/process-instances/{processInstanceKey}',
        method: 'GET',
        status: '200',
      },
      res,
    );

    const body = await res.json();
    expect(body.processInstanceKey).toBe(processInstanceKeyToGet);
    expect(body.processDefinitionId).toBe(instance.processDefinitionId);
    expect(body.state).toBe('COMPLETED');
    startDate = body.startDate;
    endDate = body.endDate;
  }).toPass(defaultAssertionOptions);

  return {startDate: startDate, endDate: endDate};
}
