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
import {
  cancelProcessInstance,
  createInstances,
  deploy,
  deployWithSubstitutions,
} from '../zeebeClient';
import {defaultAssertionOptions} from '../constants';
import {validateResponse} from 'json-body-assertions';

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
  await validateResponse(
    {
      path: '/jobs/activation',
      method: 'POST',
      status: '200',
    },
    activateRes,
  );
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
    await validateResponse(
      {
        path: '/jobs/search',
        method: 'POST',
        status: '200',
      },
      searchRes,
    );
    const searchJson = await searchRes.json();
    expect(searchJson.items.length).toBeGreaterThan(0);
    result.jobKey = searchJson.items[0].jobKey;
  }).toPass(defaultAssertionOptions);
  return result.jobKey;
}

export interface ActivatedJob {
  jobKey: number;
  customHeaders: JSONDoc;
}

export interface ActivatedJobWithVars {
  jobKey: number;
  processInstanceKey: number;
  variables: Record<string, unknown>;
}

export async function activateJobAndGetHeaders(
  request: APIRequestContext,
  jobType: string,
): Promise<ActivatedJob> {
  const result: ActivatedJob = {jobKey: 0, customHeaders: {}};
  await expect(async () => {
    const res = await request.post(buildUrl('/jobs/activation'), {
      headers: jsonHeaders(),
      data: {type: jobType, timeout: 5000, maxJobsToActivate: 1},
    });
    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/jobs/activation',
        method: 'POST',
        status: '200',
      },
      res,
    );
    const json = await res.json();
    expect(json.jobs).toHaveLength(1);
    result.jobKey = json.jobs[0].jobKey;
    result.customHeaders = json.jobs[0].customHeaders;
  }).toPass(defaultAssertionOptions);
  return result;
}

export async function completeJob(
  request: APIRequestContext,
  jobKey: number,
  variables?: Record<string, unknown>,
): Promise<void> {
  const completeRes = await request.post(
    buildUrl(`/jobs/${jobKey}/completion`),
    {
      headers: jsonHeaders(),
      ...(variables !== undefined && {data: {variables}}),
    },
  );
  await assertStatusCode(completeRes, 204);
}

/**
 * Activates jobs of a given type and returns those belonging to the given
 * process instance. Optionally fetches specific variables per job.
 *
 * NOTE: /jobs/activation has no processInstanceKey filter — results are
 * filtered client-side using the processInstanceKey field on each activated job.
 */
export async function activateJobsByType(
  request: APIRequestContext,
  jobType: string,
  processInstanceKey: string,
  fetchVariables: string[] = [],
  maxJobs = 10,
): Promise<ActivatedJobWithVars[]> {
  const res = await request.post(buildUrl('/jobs/activation'), {
    headers: jsonHeaders(),
    data: {
      type: jobType,
      maxJobsToActivate: maxJobs,
      timeout: 10_000,
      ...(fetchVariables.length > 0 && {fetchVariable: fetchVariables}),
    },
  });
  await assertStatusCode(res, 200);
  const jobs: Array<{
    jobKey: number;
    processInstanceKey: number;
    variables: Record<string, unknown>;
  }> = (await res.json()).jobs ?? [];
  return jobs
    .filter((j) => String(j.processInstanceKey) === processInstanceKey)
    .map((j) => ({
      jobKey: j.jobKey,
      processInstanceKey: j.processInstanceKey,
      variables: j.variables ?? {},
    }));
}

export function setupProcessInstanceForTests(
  processFileName: string,
  options?: {
    processName?: string;
    variables?: JSONDoc;
    substitutions?: Record<string, string>;
  },
) {
  const {processName, variables, substitutions} = options ?? {};
  const state: Record<string, unknown> = {};

  return {
    state,
    beforeAll: async () => {
      if (substitutions) {
        await deployWithSubstitutions(
          `./resources/${processFileName}.bpmn`,
          substitutions,
        );
      } else {
        await deploy([`./resources/${processFileName}.bpmn`]);
      }
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

/**
 * Searches for jobs by type and returns their count. Asserts that
 * /v2/jobs/search returns HTTP 200 (never 500).
 */
export async function countJobsByType(
  request: APIRequestContext,
  processInstanceKey: string,
  type: string,
): Promise<number> {
  const res = await request.post(buildUrl('/jobs/search'), {
    headers: jsonHeaders(),
    data: {filter: {processInstanceKey, type}, page: {limit: 100}},
  });
  await assertStatusCode(res, 200);
  return (await res.json()).items.length;
}

export async function expectJobsByType(
  request: APIRequestContext,
  processInstanceKey: string,
  type: string,
  expected: number,
  assertionOptions: {
    intervals?: number[];
    timeout?: number;
  } = defaultAssertionOptions,
): Promise<void> {
  await expect(async () => {
    expect(await countJobsByType(request, processInstanceKey, type)).toBe(
      expected,
    );
  }).toPass(assertionOptions);
}

export function getLast24HoursRange() {
  const fromDate = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(); // 24 hours ago
  const toDate = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(); // 24 hours from now
  return {
    fromDate,
    toDate,
  };
}

export interface StatisticsJobItem {
  jobType: string;
  created: {
    count: number;
    lastUpdatedAt: string;
  };
  completed: {
    count: number;
    lastUpdatedAt: string;
  };
  failed: {
    count: number;
    lastUpdatedAt: string;
  };
  workers: number;
}
