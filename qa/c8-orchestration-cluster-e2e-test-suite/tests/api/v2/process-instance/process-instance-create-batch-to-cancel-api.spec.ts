/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {
  assertBadRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {createInstances, deploy} from '../../../../utils/zeebeClient';
import {validateResponseShape} from '../../../../json-body-assertions';
import {defaultAssertionOptions} from '../../../../utils/constants';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Create Process Instance Batch to Cancel Tests', () => {
  test.beforeAll(async () => {
    await deploy(['./resources/process_with_task_listener.bpmn']);
  });

  test('Create a Batch Operation to Cancel Process Instances - Unauthorized', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-instances/cancellation'),
      {
        data: {
          processInstanceKeys: [2251799813685249, 2251799813685250],
        },
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Create a Batch Operation to Cancel Process Instances - Success', async ({
    request,
  }) => {
    const localState: Record<string, string[]> = {processInstanceKeys: []};
    await test.step('Create multiple process instances to cancel', async () => {
      const processInstances = await createInstances(
        'process_with_task_listener',
        1,
        6,
      );
      for (const processInstance of processInstances) {
        localState['processInstanceKeys'] = [
          ...(localState['processInstanceKeys'] as string[]),
          processInstance.processInstanceKey,
        ];
      }
    });

    await test.step('Create a Batch Operation to Cancel Process Instances - Success', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/process-instances/cancellation'),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                $or: [
                  ...localState['processInstanceKeys'].map((key) => ({ processInstanceKey: key })),
                ]
              },
            },
          },
        );
        await assertStatusCode(res, 200);
        const json = await res.json();
        validateResponseShape(
          {
            path: '/process-instances/cancellation',
            method: 'POST',
            status: '200',
          },
          json,
        );
        expect(json.batchOperationType).toBe('CANCEL_PROCESS_INSTANCE');
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Create a Batch Operation to Cancel Process Instances With No Filter - Bad Request', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-instances/cancellation'),
      {
        headers: jsonHeaders(),
        data: {
          // No filter or processInstanceKeys provided
        },
      },
    );
    await assertBadRequest(res, 'No filter provided.', 'INVALID_ARGUMENT');
  });

  test('Create a Batch Operation to Cancel Process Instances - With Invalid Filter - Bad Request', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-instances/cancellation'),
      {
        headers: jsonHeaders(),
        data: {
          filter: {
            invalidField: 'invalidValue',
          },
        },
      },
    );
    await assertBadRequest(
      res,
      'Request property [filter.invalidField] cannot be parsed',
    );
  });

  test('Create a Batch Operation to Cancel Process Instances - With Multiple Filters', async ({
    request,
  }) => {
    const localState: Record<string, string> = {
      processInstanceKey: '',
      processDefinitionKey: '',
    };

    await test.step('Create Process Instances to cancel', async () => {
      await createInstances('process_with_task_listener', 1, 2).then(
        (instances) => {
          localState.processInstanceKey = instances[0].processInstanceKey;
          localState.processDefinitionKey = instances[1].processDefinitionKey;
        },
      );
    });

    await expect(async () => {
      const res = await request.post(
        buildUrl('/process-instances/cancellation'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              processInstanceKey: localState.processInstanceKey,
              processDefinitionKey: localState.processDefinitionKey,
            },
          },
        },
      );
      await assertStatusCode(res, 200);
      const json = await res.json();
      expect(json.batchOperationType).toBe('CANCEL_PROCESS_INSTANCE');
    }).toPass(defaultAssertionOptions);
  });
});
