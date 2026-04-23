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

test.describe.parallel('EL Header Cross-Task Isolation Tests', () => {
  const suffix = randomUUID().slice(0, 8);

  const callActivityParentProcessId = `el-header-call-activity-parent-${suffix}`;
  const callActivityChildProcessId = `el-header-call-activity-child-${suffix}`;
  const elCallActivityStartType = `el-call-activity-start-${suffix}`;
  const elChildStartType = `el-child-start-${suffix}`;
  const childServiceJobType = `child-service-job-${suffix}`;

  test.beforeAll(async () => {
    // Child must be deployed before parent so the calledElement reference resolves
    await deployWithSubstitutions(
      './resources/el-header-call-activity-child.bpmn',
      {
        'el-header-call-activity-child': callActivityChildProcessId,
        'el-child-start': elChildStartType,
        'child-service-job': childServiceJobType,
      },
    );
    await deployWithSubstitutions(
      './resources/el-header-call-activity-parent.bpmn',
      {
        'el-header-call-activity-parent': callActivityParentProcessId,
        'el-header-call-activity-child': callActivityChildProcessId,
        'el-call-activity-start': elCallActivityStartType,
      },
    );
  });

  test('As a developer, I can verify that EL headers defined on a Call Activity do not appear in the EL jobs of the called process', async ({
    request,
  }) => {
    const instances = await createInstances(callActivityParentProcessId, 1, 1);
    const piKey = String(instances[0].processInstanceKey);

    try {
      // Parent Call Activity start EL: parent-scoped headers
      const caJob = await activateJobAndGetHeaders(
        request,
        elCallActivityStartType,
      );
      expect(caJob.customHeaders).toMatchObject({
        scope: 'parent',
        env: 'production',
      });
      await completeJob(request, caJob.jobKey);

      // Child process service task start EL: child-scoped headers only — no bleed from parent
      const childJob = await activateJobAndGetHeaders(
        request,
        elChildStartType,
      );
      expect(childJob.customHeaders).toMatchObject({
        scope: 'child',
        env: 'sandbox',
      });
      expect(childJob.customHeaders['scope']).not.toBe('parent');
      expect(childJob.customHeaders['env']).not.toBe('production');
      await completeJob(request, childJob.jobKey);

      // Complete the child service task to let the child process finish
      const childServiceJob = await activateJobAndGetHeaders(
        request,
        childServiceJobType,
      );
      await completeJob(request, childServiceJob.jobKey);
    } finally {
      await cancelProcessInstance(piKey);
    }
  });
});
