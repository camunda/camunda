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
