/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {randomUUID} from 'node:crypto';
import {
  assertNotFoundRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {
  cancelProcessInstance,
  createInstances,
  deploy,
} from '../../../../utils/zeebeClient';
import {
  activateJobWithLease,
  resolveAdHocSubProcessInstanceKey,
} from '@requestHelpers';
import {validateResponse} from '../../../../json-body-assertions';

// See agent-instance-api-tests.spec.ts for why the ad-hoc sub-process resource is
// reused to obtain an active element instance for agent-instance creation, and why
// CREATE must be attributed to a real job activation (jobKey + jobLeaseToken).
const PROCESS_DEFINITION_ID = 'AgentInstance_AdHocSubProcess_API_Test';
const AGENT_JOB_TYPE = 'agent-instance-api-test';
const NON_EXISTENT_KEY = '2251799813700002';
const CREATE_ENDPOINT = '/agent-instances';
const HISTORY_SEARCH_ENDPOINT =
  '/agent-instances/{agentInstanceKey}/history/search';

const state: {agentInstanceKey?: string; processInstanceKey?: string} = {};

/* eslint-disable playwright/expect-expect */
test.describe.serial('Agent Instance History Search API', () => {
  test.beforeAll(async ({request}) => {
    await test.step('Deploy ad-hoc sub-process resource', async () => {
      await deploy([
        './resources/agent_instance_ad_hoc_sub_process_api_test.bpmn',
      ]);
    });

    await test.step('Create an agent instance to search history for', async () => {
      const instances = await createInstances(PROCESS_DEFINITION_ID, 1, 1);
      state.processInstanceKey = instances[0].processInstanceKey as string;
      const elementInstanceKey = await resolveAdHocSubProcessInstanceKey(
        request,
        state.processInstanceKey,
      );
      const {jobKey, jobLeaseToken} = await activateJobWithLease(
        request,
        AGENT_JOB_TYPE,
      );

      const res = await request.post(buildUrl(CREATE_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          elementInstanceKey,
          jobKey,
          jobLeaseToken,
          history: [
            {
              historyItemId: randomUUID(),
              loopIteration: 1,
              role: 'CONFIGURATION',
              content: [{contentType: 'TEXT', text: 'configuration'}],
              producedAt: new Date().toISOString(),
              model: 'gpt-4o',
              provider: 'openai',
              systemPrompt: [
                {contentType: 'TEXT', text: 'You are a helpful assistant.'},
              ],
            },
          ],
        },
      });
      await assertStatusCode(res, 200);
      await validateResponse(
        {path: CREATE_ENDPOINT, method: 'POST', status: '200'},
        res,
      );
      state.agentInstanceKey = (await res.json()).agentInstanceKey as string;
    });
  });

  test.afterAll(async () => {
    if (state.processInstanceKey) {
      await cancelProcessInstance(state.processInstanceKey);
    }
  });

  test('Search history for an agent instance without USER items returns an empty page', async ({
    request,
  }) => {
    const agentInstanceKey = state.agentInstanceKey!;
    await expect(async () => {
      // CREATE always commits its own CONFIGURATION history item immediately, so a
      // freshly created agent is never without committed history — filter to a role
      // it hasn't produced yet (no USER turn has happened) to still exercise an
      // empty result page.
      const res = await request.post(
        buildUrl(HISTORY_SEARCH_ENDPOINT, {agentInstanceKey}),
        {headers: jsonHeaders(), data: {filter: {role: 'USER'}}},
      );
      await assertStatusCode(res, 200);
      await validateResponse(
        {path: HISTORY_SEARCH_ENDPOINT, method: 'POST', status: '200'},
        res,
      );
      const body = await res.json();
      expect(body.items).toEqual([]);
      expect(body.page.totalItems).toBe(0);
    }).toPass(defaultAssertionOptions);
  });

  test('Search history for an unknown agent instance returns 404', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl(HISTORY_SEARCH_ENDPOINT, {agentInstanceKey: NON_EXISTENT_KEY}),
      {headers: jsonHeaders(), data: {}},
    );
    await assertNotFoundRequest(res, NON_EXISTENT_KEY);
  });

  test('Search history without authentication returns 401', async ({
    request,
  }) => {
    const agentInstanceKey = state.agentInstanceKey!;
    const res = await request.post(
      buildUrl(HISTORY_SEARCH_ENDPOINT, {agentInstanceKey}),
      {headers: jsonHeaders(''), data: {}},
    );
    await assertUnauthorizedRequest(res);
  });
});
