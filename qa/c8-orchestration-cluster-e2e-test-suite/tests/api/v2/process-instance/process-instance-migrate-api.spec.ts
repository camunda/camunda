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
  assertInvalidState,
  assertNotFoundRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';

/* eslint-disable playwright/expect-expect */
test.describe.serial('Test process instance migrate API', () => {
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

  test('Process instance migrate - success', async ({request}) => {
    const localState: Record<string, string> = {
      processInstanceKey: '',
      processDefinitionKey: '',
    };
    await test.step('Create process instance of version 1', async () => {
      await createInstances('test_migration_process', 1, 1).then((instance) => {
        localState.processInstanceKey = instance[0].processInstanceKey;
        instanceKeys.push(instance[0].processInstanceKey);
      });
    });

    await test.step('Deploy version 2 of the process and create a process', async () => {
      await deploy(['./resources/test_migration_process_v2.bpmn']);
      await createInstances('test_migration_process_v2', 1, 1).then(
        (instance) => {
          localState.processDefinitionKey = instance[0].processDefinitionKey;
          instanceKeys.push(instance[0].processInstanceKey);
        },
      );
    });

    await test.step('Migrate process instance to version 2', async () => {
      const res = await request.post(
        buildUrl(
          `/process-instances/${localState.processInstanceKey}/migration`,
        ),
        {
          headers: jsonHeaders(),
          data: {
            mappingInstructions: [
              {
                sourceElementId: 'test_migration_api_user_task',
                targetElementId: 'do_something_else',
              },
            ],
            targetProcessDefinitionKey: localState.processDefinitionKey,
          },
        },
      );
      await assertStatusCode(res, 204);
    });

    await test.step('Check if migrated process instance is at the target task', async () => {
      await expect(async () => {
        const res = await request.post(buildUrl('/user-tasks/search'), {
          headers: jsonHeaders(),
          data: {
            filter: {
              processInstanceKey: localState.processInstanceKey,
            },
          },
        });
        await assertStatusCode(res, 200);
        const body = await res.json();
        expect(body).toHaveProperty('items');
        expect(body.items.length).toBe(1);
        expect(body.items[0].elementId).toBe('do_something_else');
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Process instance migrate - 409 Invalid State', async ({request}) => {
    const localState: Record<string, string> = {
      processInstanceKey: '',
      processDefinitionKey: '',
    };
    await test.step('Create process-instance of version 1', async () => {
      await createInstances('test_migration_process', 1, 1).then((instance) => {
        localState.processInstanceKey = instance[0].processInstanceKey;
        instanceKeys.push(instance[0].processInstanceKey);
      });
    });

    await test.step('Deploy version 2 of the process and create a process', async () => {
      await deploy(['./resources/test_migration_process_v2.bpmn']);
      await createInstances('test_migration_process_v2', 1, 1).then(
        (instance) => {
          localState.processDefinitionKey = instance[0].processDefinitionKey;
          instanceKeys.push(instance[0].processInstanceKey);
        },
      );
    });

    await test.step('Migrate process instance to version 2 to wrong task type', async () => {
      const res = await request.post(
        buildUrl(
          `/process-instances/${localState.processInstanceKey}/migration`,
        ),
        {
          headers: jsonHeaders(),
          data: {
            mappingInstructions: [
              {
                sourceElementId: 'test_migration_api_user_task',
                targetElementId: 'decide_what_to_do',
              },
            ],
            targetProcessDefinitionKey: localState.processDefinitionKey,
          },
        },
      );
      await assertInvalidState(res, 409);
    });
  });

  test('Process instance migrate - 400 Bad Request - invalid path parameter', async ({
    request,
  }) => {
    const invalidProcessInstanceKey = 'invalidKey';
    const res = await request.post(
      buildUrl(`/process-instances/${invalidProcessInstanceKey}/migration`),
      {
        headers: jsonHeaders(),
        data: {},
      },
    );
    await assertBadRequest(
      res,
      "Failed to convert 'processInstanceKey' with value: 'invalidKey'",
    );
  });

  test('Process instance migrate - 400 Invalid Argument - Missing targetProcessDefinitionKey', async ({
    request,
  }) => {
    const processInstanceKey = 2251799813738499;
    const res = await request.post(
      buildUrl(`/process-instances/${processInstanceKey}/migration`),
      {
        headers: jsonHeaders(),
        data: {
          // Missing targetProcessDefinitionKey
          mappingInstructions: [
            {
              targetElementId: 'foo',
              sourceElementId: 'bar',
            },
          ],
        },
      },
    );
    await assertBadRequest(
      res,
      'No targetProcessDefinitionKey provided.',
      'INVALID_ARGUMENT',
    );
  });

  test('Process instance migrate - 400 Bad Request - Missing mappingInstructions', async ({
    request,
  }) => {
    const processInstanceKey = 2251799813738499;
    const res = await request.post(
      buildUrl(`/process-instances/${processInstanceKey}/migration`),
      {
        headers: jsonHeaders(),
        data: {
          targetProcessDefinitionKey: '2251799813738499',
          mappingInstructions: 'first this then that', // Invalid type
        },
      },
    );
    await assertBadRequest(
      res,
      'Request property [mappingInstructions] cannot be parsed',
    );
  });

  test('Process instance migrate - Unauthorized', async ({request}) => {
    const processInstanceKey = 2251799813704885;
    const res = await request.post(
      buildUrl(`/process-instances/${processInstanceKey}/migration`),
      {
        // No auth headers
        headers: {
          'Content-Type': 'application/json',
        },
        data: {
          targetProcessDefinitionKey: '2251799813738499',
          mappingInstructions: [
            {
              sourceElementId: 'test_migration_api_user_task',
              targetElementId: 'do_something_else',
            },
          ],
        },
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Create Process instance migrate - 404 Not Found - non existing process instance', async ({
    request,
  }) => {
    const nonExistingProcessInstanceKey = 2251799813738499;
    const res = await request.post(
      buildUrl(`/process-instances/${nonExistingProcessInstanceKey}/migration`),
      {
        headers: jsonHeaders(),
        data: {
          targetProcessDefinitionKey: '2251799813738499',
          mappingInstructions: [
            {
              sourceElementId: 'test_migration_api_user_task',
              targetElementId: 'do_something_else',
            },
          ],
        },
      },
    );
    await assertNotFoundRequest(
      res,
      `Command 'MIGRATE' rejected with code 'NOT_FOUND': Expected to migrate process instance but no process instance found with key '2251799813738499'`,
    );
  });
});
