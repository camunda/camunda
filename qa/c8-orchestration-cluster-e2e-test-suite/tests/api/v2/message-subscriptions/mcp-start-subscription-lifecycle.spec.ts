/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '@playwright/test';
import {buildUrl, jsonHeaders, assertStatusCode} from '../../../../utils/http';
import {validateResponse} from '../../../../json-body-assertions';
import {deploy} from '../../../../utils/zeebeClient';
import {
  expectStartSubscriptionPresent,
  expectStartSubscriptionAbsent,
} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */
test.describe('MCP Start-Subscription Lifecycle API Tests', () => {
  test('SC-LIF-01 — history-deleting a definition removes its MESSAGE_START_EVENT_SUBSCRIPTION', async ({
    request,
  }) => {
    const deployment = await deploy([
      './resources/mcp-process-lifecycle-a.bpmn',
    ]);
    const resourceKey = deployment.processes[0].processDefinitionKey;

    await expectStartSubscriptionPresent(
      request,
      'mcpLifecycleProcessA',
      resourceKey,
    );

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

    await expectStartSubscriptionAbsent(
      request,
      'mcpLifecycleProcessA',
      resourceKey,
    );
  });

  test("SC-LIF-02 — deleting one definition does not affect another definition's start subscription", async ({
    request,
  }) => {
    const [deploymentA, deploymentB] = await Promise.all([
      deploy(['./resources/mcp-process-lifecycle-a.bpmn']),
      deploy(['./resources/mcp-process-lifecycle-b.bpmn']),
    ]);
    const resourceKeyA = deploymentA.processes[0].processDefinitionKey;
    const resourceKeyB = deploymentB.processes[0].processDefinitionKey;

    await expectStartSubscriptionPresent(
      request,
      'mcpLifecycleProcessA',
      resourceKeyA,
    );
    await expectStartSubscriptionPresent(
      request,
      'mcpLifecycleProcessB',
      resourceKeyB,
    );

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
    await validateResponse(
      {
        path: '/resources/{resourceKey}/deletion',
        method: 'POST',
        status: '200',
      },
      deletion,
    );

    await expectStartSubscriptionAbsent(
      request,
      'mcpLifecycleProcessA',
      resourceKeyA,
    );
    await expectStartSubscriptionPresent(
      request,
      'mcpLifecycleProcessB',
      resourceKeyB,
    );
  });
});
