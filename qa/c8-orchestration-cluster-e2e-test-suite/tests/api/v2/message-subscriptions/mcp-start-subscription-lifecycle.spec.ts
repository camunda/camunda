/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '@playwright/test';
import {buildUrl, jsonHeaders, assertStatusCode} from '../../../../utils/http';
import {validateResponse} from '../../../../json-body-assertions';
import {deploy} from '../../../../utils/zeebeClient';

const subscriptionAssertionOptions = {
  intervals: [5_000, 10_000, 15_000],
  timeout: 30_000,
};

async function expectStartSubscriptionCount(
  request: import('@playwright/test').APIRequestContext,
  processDefinitionId: string,
  expectedCount: number,
) {
  await expect(async () => {
    const res = await request.post(buildUrl('/message-subscriptions/search'), {
      headers: jsonHeaders(),
      data: {
        filter: {processDefinitionId, messageSubscriptionType: 'START_EVENT'},
      },
    });
    await assertStatusCode(res, 200);
    await validateResponse(
      {path: '/message-subscriptions/search', method: 'POST', status: '200'},
      res,
    );
    const json = await res.json();
    expect(json.page.totalItems).toBe(expectedCount);
  }).toPass(subscriptionAssertionOptions);
}

test.describe('MCP Start-Subscription Lifecycle API Tests', () => {
  test('SC-LIF-01 — history-deleting a definition removes its MESSAGE_START_EVENT_SUBSCRIPTION', async ({
    request,
  }) => {
    const deployment = await deploy([
      './resources/mcp-process-lifecycle-a.bpmn',
    ]);
    const resourceKey = deployment.processes[0].processDefinitionKey;

    await expectStartSubscriptionCount(request, 'mcpLifecycleProcessA', 1);

    const deletion = await request.post(
      buildUrl('/resources/{resourceKey}/deletion', {resourceKey}),
      {
        headers: jsonHeaders(),
        data: {deleteHistory: true},
      },
    );
    await assertStatusCode(deletion, 200);
    await validateResponse(
      {
        path: '/resources/{resourceKey}/deletion',
        method: 'POST',
        status: '200',
      },
      deletion,
    );

    await expectStartSubscriptionCount(request, 'mcpLifecycleProcessA', 0);
  });

  test("SC-LIF-02 — deleting one definition does not affect another definition's start subscription", async ({
    request,
  }) => {
    const [deploymentA] = await Promise.all([
      deploy(['./resources/mcp-process-lifecycle-a.bpmn']),
      deploy(['./resources/mcp-process-lifecycle-b.bpmn']),
    ]);
    const resourceKeyA = deploymentA.processes[0].processDefinitionKey;

    await expectStartSubscriptionCount(request, 'mcpLifecycleProcessA', 1);
    await expectStartSubscriptionCount(request, 'mcpLifecycleProcessB', 1);

    const deletion = await request.post(
      buildUrl('/resources/{resourceKey}/deletion', {
        resourceKey: resourceKeyA,
      }),
      {
        headers: jsonHeaders(),
        data: {deleteHistory: true},
      },
    );
    await assertStatusCode(deletion, 200);

    await expectStartSubscriptionCount(request, 'mcpLifecycleProcessA', 0);
    await expectStartSubscriptionCount(request, 'mcpLifecycleProcessB', 1);
  });
});
