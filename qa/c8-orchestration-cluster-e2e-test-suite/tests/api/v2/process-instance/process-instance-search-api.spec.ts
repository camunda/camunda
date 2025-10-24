/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {cancelProcessInstance, deploy} from '../../../../utils/zeebeClient';
import {
  assertBadRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {validateResponse} from '../../../../json-body-assertions';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Get Process instance Tests', () => {
  test.beforeAll(async () => {
    await deploy([
      './resources/process_with_task_listener.bpmn',
      './resources/user_task_api_test_process.bpmn',
    ]);
  });

  test('Search Process Instances - Success', async ({request}) => {
    const localState: Record<string, unknown> = {};
    await test.step('Create a process instance to search for', async () => {
      const res = await request.post(buildUrl('/process-instances'), {
        headers: jsonHeaders(),
        data: {
          processDefinitionId: 'process_with_task_listener',
        },
      });
      await assertStatusCode(res, 200);
      const json = await res.json();
      localState.processInstanceKey = json.processInstanceKey;
    });
    await test.step('Search Process Instances', async () => {
      await expect(async () => {
        const res = await request.post(buildUrl('/process-instances/search'), {
          headers: jsonHeaders(),
          data: {},
        });
        await assertStatusCode(res, 200);
        const json = await res.json();
        await validateResponse(
          {
            path: '/process-instances/search',
            method: 'POST',
            status: '200',
          },
          res,
        );
        expect(json.page.totalItems).toBeGreaterThan(1);
      }).toPass(defaultAssertionOptions);
    });
    await cancelProcessInstance(localState.processInstanceKey as string);
  });

  test('Search Process Instance With Filter - Success', async ({request}) => {
    const localState: Record<string, unknown> = {};
    await test.step('Create a process instance to filter for', async () => {
      const res = await request.post(buildUrl('/process-instances'), {
        headers: jsonHeaders(),
        data: {
          processDefinitionId: 'process_with_task_listener',
        },
      });
      await assertStatusCode(res, 200);
      const json = await res.json();
      localState.processInstanceKey = json.processInstanceKey;
    });

    await test.step('Search Process Instances With Filter', async () => {
      await expect(async () => {
        const res = await request.post(buildUrl('/process-instances/search'), {
          headers: jsonHeaders(),
          data: {
            filter: {
              processInstanceKey: localState.processInstanceKey,
            },
          },
        });
        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/process-instances/search',
            method: 'POST',
            status: '200',
          },
          res,
        );
        const json = await res.json();
        expect(json.page).toBeDefined();
        expect(json.page.totalItems).toBeDefined();
        expect(json.items).toBeDefined();
        expect(json.page.totalItems).toBe(1);
        expect(json.items.length).toBe(1);
        expect(json.items[0].processDefinitionId).toBe(
          'process_with_task_listener',
        );
      }).toPass(defaultAssertionOptions);
    });
    await cancelProcessInstance(localState.processInstanceKey as string);
  });

  test('Search Process Instance With Multiple Filters - Success', async ({
    request,
  }) => {
    const localState: Record<string, unknown> = {};
    await test.step('Create two process instances to filter for', async () => {
      const res = await request.post(buildUrl('/process-instances'), {
        headers: jsonHeaders(),
        data: {
          processDefinitionId: 'process_with_task_listener',
          variables: {
            firstname: 'john',
            lastname: 'doe',
          },
          tags: ['example'],
        },
      });
      await assertStatusCode(res, 200);
      const json = await res.json();
      localState.processInstanceKey1 = json.processInstanceKey;

      const res2 = await request.post(buildUrl('/process-instances'), {
        headers: jsonHeaders(),
        data: {
          processDefinitionId: 'user_task_api_test_process',
          variables: {
            firstname: 'jane',
            lastname: 'doe',
          },
          tags: ['example'],
        },
      });
      await assertStatusCode(res2, 200);
      const json2 = await res2.json();
      localState.processInstanceKey2 = json2.processInstanceKey;
    });

    await test.step('Search Process Instances With Multiple Filters', async () => {
      await expect(async () => {
        const res = await request.post(buildUrl('/process-instances/search'), {
          headers: jsonHeaders(),
          data: {
            sort: [{field: 'processDefinitionId', order: 'asc'}],
            filter: {
              processInstanceKey: {
                $in: [
                  localState.processInstanceKey1,
                  localState.processInstanceKey2,
                ],
              },
              state: 'ACTIVE',
              tags: ['example'],
            },
          },
        });
        await assertStatusCode(res, 200);
        const json = await res.json();
        expect(json.page.totalItems).toBe(2);
        expect(json.items.length).toBe(2);
        expect(json.items[0].processDefinitionId).toBe(
          'process_with_task_listener',
        );
        expect(json.items[1].processDefinitionId).toBe(
          'user_task_api_test_process',
        );
      }).toPass(defaultAssertionOptions);
    });

    await cancelProcessInstance(localState.processInstanceKey1 as string);
    await cancelProcessInstance(localState.processInstanceKey2 as string);
  });

  test('Search Process Instances - Unauthorized', async ({request}) => {
    const res = await request.post(buildUrl('/process-instances/search'), {
      data: {},
    });
    await assertUnauthorizedRequest(res);
  });

  test('Search Process Instances - Bad Request - Invalid Payload', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/process-instances/search'), {
      headers: jsonHeaders(),
      data: {
        filter: {
          processInstanceKey: {invalidOperator: 123}, // Invalid operator
        },
      },
    });
    await assertBadRequest(
      res,
      'Request property [filter.processInstanceKey.invalidOperator] cannot be parsed',
    );
  });

  test('Search Process Instances - No Items Found', async ({request}) => {
    const res = await request.post(buildUrl('/process-instances/search'), {
      headers: jsonHeaders(),
      data: {
        filter: {
          processDefinitionName: '9999999999', // Assuming no process has this name
        },
      },
    });
    await assertStatusCode(res, 200);
    const json = await res.json();
    expect(json.page.totalItems).toBe(0);
    expect(json.items.length).toBe(0);
  });
});
