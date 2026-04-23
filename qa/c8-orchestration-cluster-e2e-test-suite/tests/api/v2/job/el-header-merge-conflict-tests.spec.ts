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

test.describe.parallel('EL Header Merge and Conflict Tests', () => {
  const suffix = randomUUID().slice(0, 8);

  const conflictProcessId = `el-header-conflict-${suffix}`;
  const elConflictListenerType = `el-conflict-listener-${suffix}`;
  const conflictMainTaskType = `conflict-main-task-${suffix}`;

  test.beforeAll(async () => {
    await deployWithSubstitutions('./resources/el-header-conflict.bpmn', {
      'el-header-conflict': conflictProcessId,
      'el-conflict-listener': elConflictListenerType,
      'conflict-main-task': conflictMainTaskType,
    });
  });

  test('As a developer, I can verify that when a header key is defined on both the base element and the EL, the EL value takes precedence', async ({
    request,
  }) => {
    const instances = await createInstances(conflictProcessId, 1, 1);
    const piKey = String(instances[0].processInstanceKey);

    try {
      const elJob = await activateJobAndGetHeaders(
        request,
        elConflictListenerType,
      );
      // EL overrides base "config"; base-only and EL-only headers are preserved
      expect(elJob.customHeaders).toMatchObject({
        config: 'overriddenValue',
        onlyBase: 'fromBase',
        onlyEl: 'fromEL',
      });
      // Original base value must not appear anywhere in the headers
      expect(Object.values(elJob.customHeaders)).not.toContain('defaultValue');
      await completeJob(request, elJob.jobKey);

      const mainJob = await activateJobAndGetHeaders(
        request,
        conflictMainTaskType,
      );
      await completeJob(request, mainJob.jobKey);
    } finally {
      await cancelProcessInstance(piKey);
    }
  });
});
