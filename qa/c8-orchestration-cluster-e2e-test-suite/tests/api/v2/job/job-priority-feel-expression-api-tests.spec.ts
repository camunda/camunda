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
import {defaultAssertionOptions} from '../../../../utils/constants';

test.describe.parallel('Job Priority FEEL Expression API Tests', () => {
  const runSuffix = randomUUID().slice(0, 8);
  const processId = `processWithFeelJobPriority-${runSuffix}`;
  const jobType = `feelPriorityJobType-${runSuffix}`;
  const processInstanceKeysToCancel: string[] = [];

  test.beforeAll(async () => {
    await deployWithSubstitutions(
      './resources/processWithFeelJobPriority.bpmn',
      {
        processWithFeelJobPriority: processId,
        feelPriorityJobType: jobType,
      },
    );
  });

  test.afterAll(async () => {
    for (const processInstanceKey of processInstanceKeysToCancel) {
      await cancelProcessInstance(processInstanceKey);
    }
  });

  test('Job priority resolves per instance from the FEEL expression variable', async ({
    request,
  }) => {
    const [instanceWithHighPriority] = await createInstances(processId, 1, 1, {
      priorityVar: 77,
    });
    const [instanceWithLowPriority] = await createInstances(processId, 1, 1, {
      priorityVar: 15,
    });
    processInstanceKeysToCancel.push(
      String(instanceWithHighPriority.processInstanceKey),
      String(instanceWithLowPriority.processInstanceKey),
    );

    await expect(async () => {
      const res = await request.post(buildUrl('/jobs/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: {
              $eq: instanceWithHighPriority.processInstanceKey,
            },
          },
        },
      });
      await assertStatusCode(res, 200);
      const json = await res.json();
      expect(json.items).toHaveLength(1);
      expect(json.items[0].priority).toBe(77);
    }).toPass(defaultAssertionOptions);

    await expect(async () => {
      const res = await request.post(buildUrl('/jobs/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: {
              $eq: instanceWithLowPriority.processInstanceKey,
            },
          },
        },
      });
      await assertStatusCode(res, 200);
      const json = await res.json();
      expect(json.items).toHaveLength(1);
      expect(json.items[0].priority).toBe(15);
    }).toPass(defaultAssertionOptions);
  });

  test('Job priority FEEL expression referencing a missing variable raises an incident and creates no job', async ({
    request,
  }) => {
    const [instance] = await createInstances(processId, 1, 1);
    processInstanceKeysToCancel.push(String(instance.processInstanceKey));

    await expect(async () => {
      const res = await request.post(buildUrl('/incidents/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: instance.processInstanceKey,
          },
        },
      });
      await assertStatusCode(res, 200);
      const json = await res.json();
      expect(json.items).toHaveLength(1);
      expect(json.items[0].errorType).toBe('EXTRACT_VALUE_ERROR');
      expect(json.items[0].errorMessage).toContain('priorityVar');
      expect(json.items[0].elementId).toBe('task');
      expect(json.items[0].jobKey).toBeNull();
    }).toPass(defaultAssertionOptions);

    await expect(async () => {
      const jobSearchRes = await request.post(buildUrl('/jobs/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: {$eq: instance.processInstanceKey},
            type: {$eq: jobType},
          },
        },
      });
      await assertStatusCode(jobSearchRes, 200);
      const jobSearchJson = await jobSearchRes.json();
      expect(jobSearchJson.items).toHaveLength(0);
    }).toPass(defaultAssertionOptions);
  });
});
