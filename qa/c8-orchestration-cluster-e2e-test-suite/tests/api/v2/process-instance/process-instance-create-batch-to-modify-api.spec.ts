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

import {findUserTask} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Create Process Instance Batch to Modify Tests', () => {
  test.beforeAll(async () => {
    await deploy([
      './resources/process_with_two_user_tasks.bpmn',
      './resources/process_with_task_listener.bpmn',
    ]);
  });

  test('Create a Batch Operation to Modify Process Instances - Unauthorized', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-instances/modification'),
      {
        data: {
          processInstanceKeys: [2251799813685249, 2251799813685250],
          modification: {
            activateInstructions: [
              {
                elementId: 'second_task',
              },
            ],
          },
        },
      },
    );

    await assertUnauthorizedRequest(res);
  });

  test('Create a Batch Operation to Modify Process Instances - With No Filter - Bad Request', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-instances/modification'),
      {
        headers: jsonHeaders(),
        data: {
          moveInstructions: [
            {
              sourceElementId: 'first_task',
              targetElementId: 'second_task',
            },
          ],
        },
      },
    );

    await assertBadRequest(res, 'No filter provided.', 'INVALID_ARGUMENT');
  });

  test('Create a Batch Operation to Modify Process Instances - With No Instructions - Bad Request', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-instances/modification'),
      {
        headers: jsonHeaders(),
        data: {
          filter: {
            hasIncident: true,
          },
        },
      },
    );

    await assertBadRequest(
      res,
      'No moveInstructions provided.',
      'INVALID_ARGUMENT',
    );
  });

  test('Create a Batch Operation to Modify Process Instances - With Invalid Filter - Bad Request', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-instances/modification'),
      {
        headers: jsonHeaders(),
        data: {
          filter: {
            invalidField: 'invalidValue',
          },
          moveInstructions: [
            {sourceElementId: 'first_task', targetElementId: 'second_task'},
          ],
        },
      },
    );
    await assertBadRequest(
      res,
      'Request property [filter.invalidField] cannot be parsed',
    );
  });

  test('Create a Batch Operation to Modify Process Instances - With Multiple Filters', async ({
    request,
  }) => {
    const localState: Record<string, string> = {
      processInstanceKey1: '',
      processInstanceKey2: '',
    };

    await test.step('Create two process instances', async () => {
      const instances = await createInstances(
        'process_with_two_user_tasks',
        1,
        1,
      );
      localState.processInstanceKey1 = instances[0].processInstanceKey;

      const instances2 = await createInstances(
        'process_with_two_user_tasks',
        1,
        1,
        {
          example: 1,
        },
      );
      localState.processInstanceKey2 = instances2[0].processInstanceKey;
    });

    await test.step('Verify first tasks active', async () => {
      await findUserTask(
        request,
        localState.processInstanceKey1,
        'CREATED',
        'first_task',
      );

      await findUserTask(
        request,
        localState.processInstanceKey2,
        'CREATED',
        'first_task',
      );
    });

    await test.step('Modify only processInstanceKey2 via variables filter', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/process-instances/modification'),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                processInstanceKey: localState.processInstanceKey2,
                variables: [
                  {
                    name: 'example',
                    value: '1',
                  },
                ],
              },
              moveInstructions: [
                {
                  sourceElementId: 'first_task',
                  targetElementId: 'second_task',
                },
              ],
            },
          },
        );
        await assertStatusCode(res, 200);
        const json = await res.json();
        expect(json.batchOperationType).toBe('MODIFY_PROCESS_INSTANCE');
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Verify only second instance modified', async () => {
      await findUserTask(
        request,
        localState.processInstanceKey1,
        'CREATED',
        'first_task',
      );

      await expect(async () => {
        const res2 = await request.post(buildUrl('/user-tasks/search'), {
          headers: jsonHeaders(),
          data: {
            filter: {
              processInstanceKey: localState.processInstanceKey2,
            },
          },
        });
        await assertStatusCode(res2, 200);
        const body2 = await res2.json();
        const secondTask = body2.items.find(
          (i: {elementId?: string; state?: string}) =>
            i.elementId === 'second_task',
        );
        const firstTask2 = body2.items.find(
          (i: {elementId?: string; state?: string}) =>
            i.elementId === 'first_task',
        );
        expect(secondTask).toBeDefined();
        expect(firstTask2!.state).toBe('CANCELED');
      }).toPass(defaultAssertionOptions);
    });

    await cancelProcessInstance(localState.processInstanceKey1);
    await cancelProcessInstance(localState.processInstanceKey2);
  });

  test('Create a Batch Operation to Modify Process Instances - With Or Filters', async ({
    request,
  }) => {
    const localState: Record<string, string> = {
      processInstanceKey1: '',
      processInstanceKey2: '',
    };

    await test.step('Create two process instances with a different set of variables', async () => {
      const instances = await createInstances(
        'process_with_two_user_tasks',
        1,
        1,
      );
      localState.processInstanceKey1 = instances[0].processInstanceKey;

      const instances2 = await createInstances(
        'process_with_two_user_tasks',
        1,
        1,
        {
          example: 1,
        },
      );
      localState.processInstanceKey2 = instances2[0].processInstanceKey;
    });

    await test.step('Verify first tasks active', async () => {
      await findUserTask(
        request,
        localState.processInstanceKey1,
        'CREATED',
        'first_task',
      );

      await findUserTask(
        request,
        localState.processInstanceKey2,
        'CREATED',
        'first_task',
      );
    });

    await test.step('Modify instances using OR filters', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/process-instances/modification'),
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
              moveInstructions: [
                {
                  sourceElementId: 'first_task',
                  targetElementId: 'second_task',
                },
              ],
            },
          },
        );
        await assertStatusCode(res, 200);
        const json = await res.json();
        expect(json.batchOperationType).toBe('MODIFY_PROCESS_INSTANCE');
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Verify both instances modified', async () => {
      await expect(async () => {
        const res1 = await request.post(buildUrl('/user-tasks/search'), {
          headers: jsonHeaders(),
          data: {
            filter: {
              processInstanceKey: localState.processInstanceKey1,
            },
          },
        });
        await assertStatusCode(res1, 200);
        const body1 = await res1.json();
        const firstTask = body1.items.find(
          (i: {elementId?: string; state?: string}) =>
            i.elementId === 'first_task',
        );
        const secondTask = body1.items.find(
          (i: {elementId?: string; state?: string}) =>
            i.elementId === 'second_task',
        );
        expect(firstTask).toBeDefined();
        expect(secondTask).toBeDefined();
        expect(firstTask!.state).toBe('CANCELED');
        expect(secondTask!.state).toBe('CREATED');
      }).toPass(defaultAssertionOptions);

      await expect(async () => {
        const res2 = await request.post(buildUrl('/user-tasks/search'), {
          headers: jsonHeaders(),
          data: {
            filter: {
              processInstanceKey: localState.processInstanceKey2,
            },
          },
        });
        await assertStatusCode(res2, 200);
        const body2 = await res2.json();
        const firstTask2 = body2.items.find(
          (i: {elementId?: string; state?: string}) =>
            i.elementId === 'first_task',
        );
        const secondTask2 = body2.items.find(
          (i: {elementId?: string; state?: string}) =>
            i.elementId === 'second_task',
        );
        expect(firstTask2).toBeDefined();
        expect(secondTask2).toBeDefined();
        expect(firstTask2!.state).toBe('CANCELED');
        expect(secondTask2!.state).toBe('CREATED');
      }).toPass(defaultAssertionOptions);
    });

    await cancelProcessInstance(localState.processInstanceKey1);
    await cancelProcessInstance(localState.processInstanceKey2);
  });
});
