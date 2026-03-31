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
  assertConflictRequest,
  assertStatusCode,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {cancelProcessInstance, deploy} from '../../../../utils/zeebeClient';
import {
  defaultAssertionOptions,
  generateUniqueId,
} from '../../../../utils/constants';

const PROCESS_INSTANCE_ENDPOINT = '/process-instances';
const LONG_RUNNING_PROCESS_ID = 'process_with_task_listener';
const INSTANT_PROCESS_ID = 'process_instance_api_test';
const SECOND_PROCESS_ID = 'user_task_api_test_process';

function uniqueBusinessId(prefix = 'biz'): string {
  return `${prefix}-${generateUniqueId()}`;
}

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Business ID - Core API Behavior', () => {
  test.beforeAll(async () => {
    await deploy([
      './resources/process_with_task_listener.bpmn',
      './resources/process_instance_api_test.bpmn',
      './resources/user_task_api_test_process.bpmn',
    ]);
  });

  test('Start process instance with Business ID - success', async ({
    request,
  }) => {
    const businessId = uniqueBusinessId('start-with-id');

    const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
      headers: jsonHeaders(),
      data: {processDefinitionId: LONG_RUNNING_PROCESS_ID, businessId},
    });

    await assertStatusCode(res, 200);
    const json = await res.json();
    expect(json.processInstanceKey).toBeDefined();
    expect(json.businessId).toBe(businessId);
    await cancelProcessInstance(json.processInstanceKey);
  });

  test('Start process instance without Business ID - success', async ({
    request,
  }) => {
    const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
      headers: jsonHeaders(),
      data: {processDefinitionId: LONG_RUNNING_PROCESS_ID},
    });

    await assertStatusCode(res, 200);
    const json = await res.json();
    expect(json.processInstanceKey).toBeDefined();
    expect(json.businessId == null).toBe(true);
    await cancelProcessInstance(json.processInstanceKey);
  });

  test('Start process instance with Business ID exceeding 256 characters - bad request', async ({
    request,
  }) => {
    const businessId = 'a'.repeat(257);

    const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
      headers: jsonHeaders(),
      data: {processDefinitionId: LONG_RUNNING_PROCESS_ID, businessId},
    });

    await assertBadRequest(res, /businessId|256/i, 'INVALID_ARGUMENT');
  });

  test('Start process instance with Business ID of exactly 256 characters - success', async ({
    request,
  }) => {
    const businessId = 'a'.repeat(256);

    const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
      headers: jsonHeaders(),
      data: {processDefinitionId: LONG_RUNNING_PROCESS_ID, businessId},
    });

    await assertStatusCode(res, 200);
    const json = await res.json();
    expect(json.businessId).toBe(businessId);
    await cancelProcessInstance(json.processInstanceKey);
  });
});

test.describe.parallel('Business ID - Uniqueness Enforcement', () => {
  test.beforeAll(async () => {
    await deploy([
      './resources/process_with_task_listener.bpmn',
      './resources/process_instance_api_test.bpmn',
      './resources/user_task_api_test_process.bpmn',
      './resources/test_migration_process_v1.bpmn',
      './resources/test_migration_process_v2.bpmn',
    ]);
  });

  test('Start process instance with Business ID when uniqueness enabled - success', async ({
    request,
  }) => {
    const businessId = uniqueBusinessId('toggle-on');

    const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
      headers: jsonHeaders(),
      data: {processDefinitionId: LONG_RUNNING_PROCESS_ID, businessId},
    });

    await assertStatusCode(res, 200);
    const json = await res.json();
    expect(json.processInstanceKey).toBeDefined();
    expect(json.businessId).toBe(businessId);
    await cancelProcessInstance(json.processInstanceKey);
  });

  test('Duplicate root instance with same Business ID - conflict', async ({
    request,
  }) => {
    const businessId = uniqueBusinessId('duplicate');
    const localState: Record<string, unknown> = {};

    await test.step('Start first process instance with businessId', async () => {
      const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
        headers: jsonHeaders(),
        data: {processDefinitionId: LONG_RUNNING_PROCESS_ID, businessId},
      });
      await assertStatusCode(res, 200);
      localState['processInstanceKey'] = (await res.json()).processInstanceKey;
    });

    await test.step('Attempt to start a second instance with the same businessId', async () => {
      await expect(async () => {
        const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
          headers: jsonHeaders(),
          data: {
            processDefinitionId: LONG_RUNNING_PROCESS_ID,
            businessId,
          },
        });
        await assertConflictRequest(res);
      }).toPass(defaultAssertionOptions);
    });

    await cancelProcessInstance(localState['processInstanceKey'] as string);
  });

  test('Business ID reuse after instance completes - success', async ({
    request,
  }) => {
    const businessId = uniqueBusinessId('reuse-complete');

    await test.step('Start and complete first instance', async () => {
      const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          processDefinitionId: INSTANT_PROCESS_ID,
          businessId,
          awaitCompletion: true,
        },
      });
      await assertStatusCode(res, 200);
    });

    await test.step('Start second instance with same businessId after first completes — expect success', async () => {
      await expect(async () => {
        const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
          headers: jsonHeaders(),
          data: {
            processDefinitionId: INSTANT_PROCESS_ID,
            businessId,
            awaitCompletion: true,
          },
        });
        await assertStatusCode(res, 200);
        const json = await res.json();
        expect(json.businessId).toBe(businessId);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Business ID reuse after cancellation - success', async ({request}) => {
    const businessId = uniqueBusinessId('reuse-cancel');

    await test.step('Start instance and cancel it', async () => {
      const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
        headers: jsonHeaders(),
        data: {processDefinitionId: LONG_RUNNING_PROCESS_ID, businessId},
      });
      await assertStatusCode(res, 200);
      await cancelProcessInstance((await res.json()).processInstanceKey);
    });

    await test.step('Start new instance with same businessId after cancellation — expect success', async () => {
      await expect(async () => {
        const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
          headers: jsonHeaders(),
          data: {processDefinitionId: LONG_RUNNING_PROCESS_ID, businessId},
        });
        await assertStatusCode(res, 200);
        const json = await res.json();
        expect(json.businessId).toBe(businessId);
        await cancelProcessInstance(json.processInstanceKey);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Same Business ID across different process definitions - success', async ({
    request,
  }) => {
    const businessId = uniqueBusinessId('cross-proc');
    const instances: string[] = [];

    await test.step('Start instance on process definition 1 with businessId', async () => {
      const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          processDefinitionId: LONG_RUNNING_PROCESS_ID,
          businessId,
        },
      });
      await assertStatusCode(res, 200);
      instances.push((await res.json()).processInstanceKey);
    });

    await test.step('Start instance on a DIFFERENT process definition with same businessId — expect success', async () => {
      const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
        headers: jsonHeaders(),
        data: {processDefinitionId: SECOND_PROCESS_ID, businessId},
      });
      await assertStatusCode(res, 200);
      const json = await res.json();
      expect(json.businessId).toBe(businessId);
      instances.push(json.processInstanceKey);
    });

    for (const key of instances) {
      await cancelProcessInstance(key);
    }
  });

  test('New start with same Business ID while instance is running - conflict', async ({
    request,
  }) => {
    const businessId = uniqueBusinessId('running-blocks');
    const localState: Record<string, unknown> = {};

    await test.step('Start a long-running instance with businessId', async () => {
      const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          processDefinitionId: LONG_RUNNING_PROCESS_ID,
          businessId,
        },
      });
      await assertStatusCode(res, 200);
      localState['processInstanceKey'] = (await res.json()).processInstanceKey;
    });

    await test.step('Confirm the existing instance is ACTIVE', async () => {
      await expect(async () => {
        const res = await request.get(
          buildUrl(`/process-instances/${localState['processInstanceKey']}`),
          {headers: jsonHeaders()},
        );
        await assertStatusCode(res, 200);
        const json = await res.json();
        expect(json.state).toBe('ACTIVE');
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Attempt to start another instance while first is ACTIVE — expect conflict', async () => {
      const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          processDefinitionId: LONG_RUNNING_PROCESS_ID,
          businessId,
        },
      });
      await assertConflictRequest(res);
    });

    await cancelProcessInstance(localState['processInstanceKey'] as string);
  });

  test('Business ID retained after process migration - success', async ({
    request,
  }) => {
    const businessId = uniqueBusinessId('migration');
    const localState: Record<string, string> = {
      processInstanceKey: '',
      targetProcessDefinitionKey: '',
    };

    await test.step('Start process instance with businessId on v1', async () => {
      const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          processDefinitionId: 'test_migration_process',
          businessId,
        },
      });
      await assertStatusCode(res, 200);
      localState['processInstanceKey'] = (await res.json()).processInstanceKey;
    });

    await test.step('Deploy v2 and resolve its processDefinitionKey', async () => {
      await deploy(['./resources/test_migration_process_v2.bpmn']);
      const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
        headers: jsonHeaders(),
        data: {processDefinitionId: 'test_migration_process_v2'},
      });
      await assertStatusCode(res, 200);
      const json = await res.json();
      localState['targetProcessDefinitionKey'] = json.processDefinitionKey;
      await cancelProcessInstance(json.processInstanceKey);
    });

    await test.step('Migrate process instance to v2', async () => {
      const res = await request.post(
        buildUrl(
          `/process-instances/${localState['processInstanceKey']}/migration`,
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
            targetProcessDefinitionKey:
              localState['targetProcessDefinitionKey'],
          },
        },
      );
      await assertStatusCode(res, 204);
    });

    await test.step('GET migrated instance — businessId must be retained', async () => {
      await expect(async () => {
        const res = await request.get(
          buildUrl(`/process-instances/${localState['processInstanceKey']}`),
          {headers: jsonHeaders()},
        );
        await assertStatusCode(res, 200);
        const json = await res.json();
        expect(json.businessId).toBe(businessId);
      }).toPass(defaultAssertionOptions);
    });

    await cancelProcessInstance(localState['processInstanceKey']);
  });
});
