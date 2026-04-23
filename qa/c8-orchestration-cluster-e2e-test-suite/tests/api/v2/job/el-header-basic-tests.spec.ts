/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {randomUUID} from 'crypto';
import {
  cancelProcessInstance,
  createInstances,
  deployWithSubstitutions,
} from '../../../../utils/zeebeClient';
import {assertStatusCode, buildUrl, jsonHeaders} from '../../../../utils/http';
import {
  activateJobAndGetHeaders,
  completeJob,
} from '../../../../utils/requestHelpers/job-requestHelpers';

test.describe.parallel('EL Header Basic Tests', () => {
  const suffix = randomUUID().slice(0, 8);

  // E2E-01
  const basicStartProcessId = `el-header-basic-start-${suffix}`;
  const elStartListenerType = `el-start-listener-${suffix}`;
  const processOrderType = `process-order-${suffix}`;

  // E2E-02
  const basicEndProcessId = `el-header-basic-end-${suffix}`;
  const elEndListenerType = `el-end-listener-${suffix}`;
  const processOrderEndType = `process-order-end-${suffix}`;

  test.beforeAll(async () => {
    await deployWithSubstitutions('./resources/el-header-basic-start.bpmn', {
      'el-header-basic-start': basicStartProcessId,
      'el-start-listener': elStartListenerType,
      'process-order': processOrderType,
    });
    await deployWithSubstitutions('./resources/el-header-basic-end.bpmn', {
      'el-header-basic-end': basicEndProcessId,
      'el-end-listener': elEndListenerType,
      'process-order-end': processOrderEndType,
    });
  });

  test('As a developer, I can define headers on a start EL and verify the job worker receives them', async ({
    request,
  }) => {
    const instances = await createInstances(basicStartProcessId, 1, 1);
    const piKey = String(instances[0].processInstanceKey);

    try {
      // Activate start EL job and assert its headers
      const elJob = await activateJobAndGetHeaders(
        request,
        elStartListenerType,
      );
      expect(elJob.customHeaders).toMatchObject({
        env: 'production',
        retryPolicy: 'fast',
      });

      // Main task must be blocked while start EL is still active (use search, not long-poll)
      const blockingRes = await request.post(buildUrl('/jobs/search'), {
        headers: jsonHeaders(),
        data: {filter: {processInstanceKey: piKey, type: processOrderType}},
      });
      await assertStatusCode(blockingRes, 200);
      expect((await blockingRes.json()).items).toHaveLength(0);

      // Complete EL job — main task should now be available
      await completeJob(request, elJob.jobKey);

      // Main task must have no EL headers (no leakage)
      const mainJob = await activateJobAndGetHeaders(request, processOrderType);
      expect(mainJob.customHeaders['env']).toBeUndefined();
      expect(mainJob.customHeaders['retryPolicy']).toBeUndefined();
      await completeJob(request, mainJob.jobKey);
    } finally {
      await cancelProcessInstance(piKey);
    }
  });

  test('As a developer, I can define headers on an end EL and verify they are delivered after the main task completes', async ({
    request,
  }) => {
    const instances = await createInstances(basicEndProcessId, 1, 1);
    const piKey = String(instances[0].processInstanceKey);

    try {
      // Complete the main task first
      const mainJob = await activateJobAndGetHeaders(
        request,
        processOrderEndType,
      );
      await completeJob(request, mainJob.jobKey);

      // End EL fires after main task — assert headers
      const elJob = await activateJobAndGetHeaders(request, elEndListenerType);
      expect(elJob.customHeaders).toMatchObject({
        phase: 'post-processing',
        audited: 'true',
      });
      await completeJob(request, elJob.jobKey);
    } finally {
      await cancelProcessInstance(piKey);
    }
  });
});
