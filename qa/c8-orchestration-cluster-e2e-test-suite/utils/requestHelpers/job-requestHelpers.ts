/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {APIRequestContext} from 'playwright-core';
import {assertStatusCode, buildUrl, jsonHeaders} from '../http';
import {expect} from '@playwright/test';
import {JSONDoc} from '@camunda8/sdk/dist/zeebe/types';
import {cancelProcessInstance, createInstances, deploy} from '../zeebeClient';
import {defaultAssertionOptions} from '../constants';

export async function activateJobToObtainAValidJobKey(
  request: APIRequestContext,
  jobType: string,
): Promise<number> {
  const activateRes = await request.post(buildUrl('/jobs/activation'), {
    headers: jsonHeaders(),
    data: {
      type: jobType,
      timeout: 10000,
      maxJobsToActivate: 1,
    },
  });
  await assertStatusCode(activateRes, 200);
  const activateJson = await activateRes.json();
  expect(activateJson.jobs.length).toBe(1);
  return activateJson.jobs[0].jobKey;
}

export async function searchJobKey(
  request: APIRequestContext,
  processInstanceKey: string,
): Promise<number> {
  const result: Record<string, number> = {};
  await expect(async () => {
    const searchRes = await request.post(buildUrl('/jobs/search'), {
      headers: jsonHeaders(),
      data: {
        filter: {
          processInstanceKey: processInstanceKey,
        },
      },
    });
    await assertStatusCode(searchRes, 200);
    const searchJson = await searchRes.json();
    expect(searchJson.items.length).toBeGreaterThan(0);
    result.jobKey = searchJson.items[0].jobKey;
  }).toPass(defaultAssertionOptions);
  return result.jobKey;
}

export async function completeJob(
  request: APIRequestContext,
  jobKey: number,
): Promise<void> {
  const completeRes = await request.post(
    buildUrl(`/jobs/${jobKey}/completion`),
    {
      headers: jsonHeaders(),
    },
  );
  await assertStatusCode(completeRes, 204);
}

export function setupProcessInstanceForTests(
  processFileName: string,
  processName?: string,
  variables?: JSONDoc,
) {
  const state: Record<string, unknown> = {};

  return {
    state,
    beforeAll: async () => {
      await deploy([`./resources/${processFileName}.bpmn`]);
    },
    beforeEach: async () => {
      const processInstance = await createInstances(
        processName ? processName : processFileName,
        1,
        1,
        variables,
      );
      state['processInstanceKey'] = processInstance[0].processInstanceKey;
    },
    afterEach: async () => {
      if (!state['processCompleted']) {
        await cancelProcessInstance(state['processInstanceKey'] as string);
      }
    },
  };
}
