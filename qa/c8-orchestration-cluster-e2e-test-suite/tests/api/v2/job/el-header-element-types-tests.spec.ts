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
import {
  activateJobAndGetHeaders,
  completeJob,
} from '../../../../utils/requestHelpers/job-requestHelpers';

test.describe.parallel('EL Header Element Types Tests', () => {
  const suffix = randomUUID().slice(0, 8);

  // E2E-09
  const gatewayProcessId = `el-header-gateway-${suffix}`;
  const elGwListenerType = `el-gw-listener-${suffix}`;
  const taskAType = `task-a-${suffix}`;

  // E2E-10
  const subprocessProcessId = `el-header-subprocess-${suffix}`;
  const elSubprocessStartType = `el-subprocess-start-${suffix}`;
  const elSubprocessEndType = `el-subprocess-end-${suffix}`;
  const innerTaskType = `inner-task-${suffix}`;

  test.beforeAll(async () => {
    await deployWithSubstitutions('./resources/el-header-gateway.bpmn', {
      'el-header-gateway': gatewayProcessId,
      'el-gw-listener': elGwListenerType,
      'task-a': taskAType,
    });
    await deployWithSubstitutions('./resources/el-header-subprocess.bpmn', {
      'el-header-subprocess': subprocessProcessId,
      'el-subprocess-start': elSubprocessStartType,
      'el-subprocess-end': elSubprocessEndType,
      'inner-task': innerTaskType,
    });
  });

  test('As a developer, I can define a start EL with headers on an Exclusive Gateway and verify the job worker receives them', async ({
    request,
  }) => {
    const instances = await createInstances(gatewayProcessId, 1, 1);
    const piKey = String(instances[0].processInstanceKey);

    try {
      const elJob = await activateJobAndGetHeaders(request, elGwListenerType);
      expect(elJob.customHeaders).toMatchObject({gatewayCtx: 'routing-phase'});
      await completeJob(request, elJob.jobKey);

      const downstreamJob = await activateJobAndGetHeaders(request, taskAType);
      await completeJob(request, downstreamJob.jobKey);
    } finally {
      await cancelProcessInstance(piKey);
    }
  });

  test('As a developer, I can define start and end EL headers on an Embedded Subprocess and verify both are delivered correctly', async ({
    request,
  }) => {
    const instances = await createInstances(subprocessProcessId, 1, 1);
    const piKey = String(instances[0].processInstanceKey);

    try {
      // Subprocess start EL
      const startJob = await activateJobAndGetHeaders(
        request,
        elSubprocessStartType,
      );
      expect(startJob.customHeaders).toMatchObject({scope: 'subprocess-init'});
      await completeJob(request, startJob.jobKey);

      // Inner task inside the subprocess
      const innerJob = await activateJobAndGetHeaders(request, innerTaskType);
      await completeJob(request, innerJob.jobKey);

      // Subprocess end EL
      const endJob = await activateJobAndGetHeaders(
        request,
        elSubprocessEndType,
      );
      expect(endJob.customHeaders).toMatchObject({scope: 'subprocess-cleanup'});
      await completeJob(request, endJob.jobKey);
    } finally {
      await cancelProcessInstance(piKey);
    }
  });
});
