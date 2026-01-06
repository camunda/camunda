/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {
  cancelProcessInstance,
  createInstances,
  deploy,
} from '../../../../utils/zeebeClient';
import {
  assertBadRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {APIRequestContext} from 'playwright-core';
import {JSONDoc} from '@camunda8/sdk/dist/zeebe/types.js';
import {expectBatchState, findUserTask} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */
test.describe.serial('Create Process Instance Batch to Migrate Tests', () => {
  const instanceKeys: string[] = [];
  test.beforeAll(async () => {
    await deploy([
      './resources/test_migration_process_v1.bpmn',
      './resources/test_migration_process_v2.bpmn',
    ]);
  });

  test.afterAll(async () => {
    for (const key of instanceKeys) {
      await cancelProcessInstance(key).catch(() => {
        // ignore if already completed
      });
    }
  });

  test('Create a Batch Operation to Migrate Process Instances - Unauthorized', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/process-instances/migration'), {
      data: {
        processInstanceKeys: [2251799813685249, 2251799813685250],
        migrationPlan: {
          targetProcessDefinitionKey: '2251799813738499',
          mappingInstructions: [
            {
              sourceElementId: 'test_migration_api_user_task',
              targetElementId: 'do_something_else',
            },
          ],
        },
      },
    });

    await assertUnauthorizedRequest(res);
  });

  test('Create a Batch Operation to Migrate Process Instances - With No Filter - Bad Request', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/process-instances/migration'), {
      headers: jsonHeaders(),
      data: {
        migrationPlan: {
          targetProcessDefinitionKey: '2251799813738499',
          mappingInstructions: [
            {
              sourceElementId: 'test_migration_api_user_task',
              targetElementId: 'do_something_else',
            },
          ],
        },
      },
    });

    await assertBadRequest(res, 'No filter provided.', 'INVALID_ARGUMENT');
  });

  test('Create a Batch Operation to Migrate Process Instances - With No Migration Instructions - Bad Request', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/process-instances/migration'), {
      headers: jsonHeaders(),
      data: {
        filter: {
          hasIncident: true,
        },
      },
    });

    await assertBadRequest(
      res,
      'No migrationPlan provided.',
      'INVALID_ARGUMENT',
    );
  });

  test('Create a Batch Operation to Migrate Process Instances - With Invalid Filter - Bad Request', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/process-instances/migration'), {
      headers: jsonHeaders(),
      data: {
        filter: {
          invalidField: 'invalidValue',
        },
        migrationPlan: {
          targetProcessDefinitionKey: '2251799813738499',
          mappingInstructions: [
            {
              sourceElementId: 'test_migration_api_user_task',
              targetElementId: 'do_something_else',
            },
          ],
        },
      },
    });
    await assertBadRequest(
      res,
      'Request property [filter.invalidField] cannot be parsed',
    );
  });

  test('Create a Batch Operation to Migrate Process Instances - Success', async ({
    request,
  }) => {
    const localState = await prepareTestCases(request);

    await test.step('Create batch migration operation', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/process-instances/migration'),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                processInstanceKey: {
                  $in: [
                    localState.processInstanceKey1,
                    localState.processInstanceKey2,
                  ],
                },
              },
              migrationPlan: {
                targetProcessDefinitionKey:
                  localState.targetProcessDefinitionKey,
                mappingInstructions: [
                  {
                    sourceElementId: 'test_migration_api_user_task',
                    targetElementId: 'do_something_else',
                  },
                ],
              },
            },
          },
        );
        await assertStatusCode(res, 200);
        const json = await res.json();
        localState.batchOperationKey = json.batchOperationKey;
        expect(json.batchOperationType).toBe('MIGRATE_PROCESS_INSTANCE');
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Wait for migration to complete', async () => {
      await expectBatchState(
        request,
        localState.batchOperationKey,
        'COMPLETED',
      );
    });

    await test.step('Verify both instances migrated to target task', async () => {
      await verifyBothInstancesAreAtElementId(
        request,
        localState.processInstanceKey1,
        localState.processInstanceKey2,
        'do_something_else',
      );
    });
  });

  test('Create a Batch Operation to Migrate Process Instances - With Multiple Filters', async ({
    request,
  }) => {
    const localState = await prepareTestCases(request, undefined, {
      migrateMe: true,
    });

    await test.step('Migrate only instance with specific variable', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/process-instances/migration'),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                processInstanceKey: localState.processInstanceKey2,
                variables: [
                  {
                    name: 'migrateMe',
                    value: 'true',
                  },
                ],
              },
              migrationPlan: {
                targetProcessDefinitionKey:
                  localState.targetProcessDefinitionKey,
                mappingInstructions: [
                  {
                    sourceElementId: 'test_migration_api_user_task',
                    targetElementId: 'do_something_else',
                  },
                ],
              },
            },
          },
        );
        await assertStatusCode(res, 200);
        const json = await res.json();
        localState.batchOperationKey = json.batchOperationKey;
        expect(json.batchOperationType).toBe('MIGRATE_PROCESS_INSTANCE');
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Wait for migration to complete', async () => {
      await expectBatchState(
        request,
        localState.batchOperationKey,
        'COMPLETED',
      );
    });

    await test.step('Verify only second instance migrated', async () => {
      await findUserTask(
        request,
        localState.processInstanceKey1,
        'CREATED',
        'test_migration_api_user_task',
      );

      await findUserTask(
        request,
        localState.processInstanceKey2,
        'CREATED',
        'do_something_else',
      );
    });
  });

  test('Create a Batch Operation to Migrate Process Instances - With Or Filters', async ({
    request,
  }) => {
    const localState = await prepareTestCases(
      request,
      {type: 'A'},
      {
        type: 'B',
      },
    );

    await test.step('Migrate instances using OR filters', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/process-instances/migration'),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                $or: [
                  {
                    processInstanceKey: localState.processInstanceKey1,
                  },
                  {
                    processInstanceKey: localState.processInstanceKey2,
                  },
                ],
              },
              migrationPlan: {
                targetProcessDefinitionKey:
                  localState.targetProcessDefinitionKey,
                mappingInstructions: [
                  {
                    sourceElementId: 'test_migration_api_user_task',
                    targetElementId: 'do_something_else',
                  },
                ],
              },
            },
          },
        );
        await assertStatusCode(res, 200);
        const json = await res.json();
        localState.batchOperationKey = json.batchOperationKey;
        expect(json.batchOperationType).toBe('MIGRATE_PROCESS_INSTANCE');
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Wait for migration to complete', async () => {
      await expectBatchState(
        request,
        localState.batchOperationKey,
        'COMPLETED',
      );
    });

    await test.step('Verify both instances migrated', async () => {
      await verifyBothInstancesAreAtElementId(
        request,
        localState.processInstanceKey1,
        localState.processInstanceKey2,
        'do_something_else',
      );
    });
  });

  const verifyBothInstancesAreAtElementId = async (
    request: APIRequestContext,
    processInstanceKey1: string,
    processInstanceKey2: string,
    elementId: string,
  ) => {
    await findUserTask(request, processInstanceKey1, 'CREATED', elementId);

    await findUserTask(request, processInstanceKey2, 'CREATED', elementId);
  };

  const prepareTestCases = async (
    request: APIRequestContext,
    variableProcess1?: JSONDoc,
    variableProcess2?: JSONDoc,
  ) => {
    const localState: Record<string, string> = {
      processInstanceKey1: '',
      processInstanceKey2: '',
      targetProcessDefinitionKey: '',
    };
    await test.step('Create two process instances of version 1', async () => {
      const instances1 = await createInstances(
        'test_migration_process',
        1,
        1,
        variableProcess1,
      );
      localState.processInstanceKey1 = instances1[0].processInstanceKey;
      instanceKeys.push(instances1[0].processInstanceKey);

      const instances2 = await createInstances(
        'test_migration_process',
        1,
        1,
        variableProcess2,
      );
      localState.processInstanceKey2 = instances2[0].processInstanceKey;
      instanceKeys.push(instances2[0].processInstanceKey);
    });

    await test.step('Verify both instances are at the source task', async () => {
      await verifyBothInstancesAreAtElementId(
        request,
        localState.processInstanceKey1,
        localState.processInstanceKey2,
        'test_migration_api_user_task',
      );
    });

    await test.step('Deploy version 2 and get target process definition key', async () => {
      await deploy(['./resources/test_migration_process_v2.bpmn']);
      const instances = await createInstances(
        'test_migration_process_v2',
        1,
        1,
      );
      localState.targetProcessDefinitionKey = instances[0].processDefinitionKey;
      instanceKeys.push(instances[0].processInstanceKey);
    });
    return localState;
  };
});
