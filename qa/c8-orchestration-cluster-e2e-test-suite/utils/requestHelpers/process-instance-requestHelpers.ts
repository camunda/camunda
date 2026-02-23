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
import {cancelProcessInstance} from '../zeebeClient';
import {sleep} from '../sleep';

export async function getProcessDefinitionKey(
  request: APIRequestContext,
  processDefinitionId: string,
) {
  const res = await request.post(buildUrl('/process-instances'), {
    headers: jsonHeaders(),
    data: {
      processDefinitionId: processDefinitionId,
    },
  });
  await assertStatusCode(res, 200);
  const json = await res.json();
  await cancelProcessInstance(json.processInstanceKey);
  return json.processDefinitionKey;
}

export async function createCancellationBatch(
  request: APIRequestContext,
  numberOfInstances = 3,
  processDefinitionId = 'batch_cancellation_process',
): Promise<string> {
  const processInstanceKeys: string[] = [];
  for (let i = 0; i < numberOfInstances; i++) {
    const startRes = await request.post(buildUrl('/process-instances'), {
      headers: jsonHeaders(),
      data: {
        processDefinitionId: processDefinitionId,
      },
    });
    await assertStatusCode(startRes, 200);
    const startJson = await startRes.json();
    processInstanceKeys.push(String(startJson.processInstanceKey));
  }

  await sleep(7_000);

  const result: Record<string, string> = {};
  await expect(async () => {
    const batchRes = await request.post(
      buildUrl('/process-instances/cancellation'),
      {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: {
              $in: processInstanceKeys,
            },
          },
        },
      },
    );
    await assertStatusCode(batchRes, 200);
    const json = await batchRes.json();
    expect(json).toHaveProperty('batchOperationKey');
    result.batchKey = json.batchOperationKey;
  }).toPass(defaultAssertionOptions);

  return result.batchKey;
}

export async function searchJobKeysForProcessInstance(
  request: APIRequestContext,
  processInstanceKey: string,
) {
  const localState: Record<string, unknown> = {};
  await expect(async () => {
    const res = await request.post(buildUrl('/jobs/search'), {
      headers: jsonHeaders(),
      data: {
        filter: {
          processInstanceKey: processInstanceKey,
        },
      },
    });

    await assertStatusCode(res, 200);
    const json = await res.json();
    expect(json.page.totalItems).toBeGreaterThan(0);
    localState['jobKeys'] = json.items.map(
      (job: {jobKey: number}) => job.jobKey,
    );
  }).toPass(defaultAssertionOptions);
  return localState['jobKeys'] as Array<string>;
}

export async function failJob(
  request: APIRequestContext,
  jobKey: string,
  retries = 0,
  errorMessage = 'Simulated failure',
) {
  const failRes = await request.post(buildUrl(`/jobs/${jobKey}/failure`), {
    headers: jsonHeaders(),
    data: {
      retries: retries,
      errorMessage: errorMessage,
    },
  });
  await assertStatusCode(failRes, 204);
}

export async function throwErrorForJob(
  request: APIRequestContext,
  jobKey: string,
  errorCode: string,
  errorMessage = 'Simulated error',
) {
  const throwRes = await request.post(buildUrl(`/jobs/${jobKey}/error`), {
    headers: jsonHeaders(),
    data: {
      errorCode: errorCode,
      errorMessage: errorMessage,
    },
  });
  await assertStatusCode(throwRes, 204);
}

export async function verifyIncidentsForProcessInstance(
  request: APIRequestContext,
  processInstanceKey: string,
  expectedIncidentCount: number,
) {
  return await expect(async () => {
    const res = await request.post(
      buildUrl(`/process-instances/${processInstanceKey}/incidents/search`),
      {
        headers: jsonHeaders(),
        data: {},
      },
    );
    await assertStatusCode(res, 200);
    const json = await res.json();
    expect(
      json.page.totalItems,
      `Unexpected number of incident items. Found: ${JSON.stringify(json)}`,
    ).toBe(expectedIncidentCount);
  }).toPass(defaultAssertionOptions);
}

export async function expectProcessInstanceCanBeFound(
  request: APIRequestContext,
  processInstanceKey: string,
) {
  await expect(async () => {
    const statusRes = await request.get(
      buildUrl(`/process-instances/${processInstanceKey}`),
      {
        headers: jsonHeaders(),
      },
    );
    await assertStatusCode(statusRes, 200);
    const json = await statusRes.json();
    expect(json.processInstanceKey).toBe(processInstanceKey);
  }).toPass({
    intervals: [5_000, 10_000, 15_000, 25_000, 35_000],
    timeout: 180_000,
  });
}
