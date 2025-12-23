/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {assertStatusCode, buildUrl, jsonHeaders} from '../../../../utils/http';
import {cancelProcessInstance, deploy} from '../../../../utils/zeebeClient';
import {validateResponseShape} from '../../../../json-body-assertions';
import {getProcessDefinitionKey} from '@requestHelpers';

const PROCESS_INSTANCE_ENDPOINT = '/process-instances';
test.describe.parallel('Process instance Tests', () => {
  test.beforeAll(async () => {
    await deploy(['./resources/process_with_task_listener.bpmn']);
  });

  test('Create Process Instance - Success', async ({request}) => {
    const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
      headers: jsonHeaders(),
      data: {
        processDefinitionId: 'process_with_task_listener',
      },
    });

    await assertStatusCode(res, 200);

    const json = await res.json();
    validateResponseShape(
      {
        path: PROCESS_INSTANCE_ENDPOINT,
        method: 'POST',
        status: '200',
      },
      json,
    );
    expect(json.processDefinitionId).toBe('process_with_task_listener');
    expect(json.processDefinitionVersion).toBeGreaterThan(0);
    expect(json.processInstanceKey).toBeDefined();
    expect(json.processDefinitionKey).toBeDefined();
    expect(json.tenantId).toBe('<default>');
    expect(json.variables).toEqual({});

    await cancelProcessInstance(json.processInstanceKey);
  });

  test('Create Process with Variables - Success', async ({request}) => {
    await deploy(['./resources/process_instance_api_test.bpmn']);
    const variables = {key1: 'value1', key2: 'value2'};
    const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
      headers: jsonHeaders(),
      data: {
        processDefinitionId: 'process_instance_api_test',
        variables,
        awaitCompletion: true,
      },
    });
    await assertStatusCode(res, 200);
    const json = await res.json();
    expect(json.variables).toEqual(variables);
  });

  test('Create Process with Tags - Success', async ({request}) => {
    const tags = ['tag1', 'tag2'];
    const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
      headers: jsonHeaders(),
      data: {
        processDefinitionId: 'process_with_task_listener',
        tags,
      },
    });
    await assertStatusCode(res, 200);
    const json = await res.json();
    expect(json.tags).toEqual(tags);
    await cancelProcessInstance(json.processInstanceKey);
  });

  test('Create Process Instance by Process Definition Key - Success', async ({
    request,
  }) => {
    const localState: Record<string, unknown> = {};
    await test.step('Create Process Instance by Process Definition Id to get the Key', async () => {
      const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          processDefinitionId: 'process_with_task_listener',
        },
      });
      await assertStatusCode(res, 200);
      const json = await res.json();
      localState['processDefinitionKey'] = json.processDefinitionKey;
      await cancelProcessInstance(json.processInstanceKey);
    });

    await test.step('Create Process Instance by Process Definition Key', async () => {
      const resByKey = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          processDefinitionKey: localState['processDefinitionKey'],
        },
      });
      await assertStatusCode(resByKey, 200);

      const jsonByKey = await resByKey.json();
      validateResponseShape(
        {
          path: PROCESS_INSTANCE_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        jsonByKey,
      );
      expect(jsonByKey.processDefinitionId).toBe('process_with_task_listener');
      expect(jsonByKey.processDefinitionVersion).toBeGreaterThan(0);
      expect(jsonByKey.processInstanceKey).toBeDefined();
      expect(jsonByKey.processDefinitionKey).toBe(
        localState['processDefinitionKey'],
      );
      expect(jsonByKey.tenantId).toBe('<default>');
      expect(jsonByKey.variables).toEqual({});

      await cancelProcessInstance(jsonByKey.processInstanceKey);
    });
  });

  test('Create Process Instance by Process Definition Key with Variables - Success', async ({
    request,
  }) => {
    await deploy(['./resources/process_instance_api_test.bpmn']);
    const localState: Record<string, unknown> = {};
    await test.step('Create Process Instance by Process Definition Id to get the Key', async () => {
      const res = await request.post(buildUrl('/process-instances'), {
        headers: jsonHeaders(),
        data: {
          processDefinitionId: 'process_instance_api_test',
        },
      });
      await assertStatusCode(res, 200);
      const json = await res.json();
      localState['processDefinitionKey'] = json.processDefinitionKey;
    });

    await test.step('Create Process Instance by Process Definition Key', async () => {
      const variables = {key1: 'value1', key2: 'value2'};
      const resByKey = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          processDefinitionKey: localState['processDefinitionKey'],
          variables,
          awaitCompletion: true,
        },
      });
      await assertStatusCode(resByKey, 200);
      const jsonByKey = await resByKey.json();
      expect(jsonByKey.variables).toEqual(variables);
    });
  });

  test('Create Process Instance by Process Definition Key with Tags - Success', async ({
    request,
  }) => {
    const localState: Record<string, unknown> = {};
    await test.step('Create Process Instance by Process Definition Id to get the Key', async () => {
      localState['processDefinitionKey'] = await getProcessDefinitionKey(
        request,
        'process_with_task_listener',
      );
    });

    await test.step('Create Process Instance by Process Definition Key', async () => {
      const resByKey = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          processDefinitionKey: localState['processDefinitionKey'] as string,
          tags: ['tag1', 'tag2'],
        },
      });
      await assertStatusCode(resByKey, 200);
      const jsonByKey = await resByKey.json();
      expect(jsonByKey.tags).toEqual(['tag1', 'tag2']);

      await cancelProcessInstance(jsonByKey.processInstanceKey);
    });
  });

  // eslint-disable-next-line playwright/expect-expect
  test('Create Process Instance with startInstruction', async ({request}) => {
    const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
      headers: jsonHeaders(),
      data: {
        processDefinitionId: 'process_with_task_listener',
        startInstructions: [
          {
            elementId: 'Activity_1xci2nh',
          },
        ],
      },
    });

    await assertStatusCode(res, 200);
    const json = await res.json();
    await cancelProcessInstance(json.processInstanceKey);
  });

  test('Create Process Instance - Failure - Missing process definition id and key', async ({
    request,
  }) => {
    const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
      headers: jsonHeaders(),
      data: {
        // Missing processDefinitionId and processDefinitionKey
      },
    });

    await assertStatusCode(res, 400);
    const json = await res.json();
    expect(json.title).toBe('Bad Request');
    expect(json.detail).toBe(
      'At least one of [processDefinitionId, processDefinitionKey] is required',
    );
  });

  test('Create Process Instance - Failure - Invalid process definition id', async ({
    request,
  }) => {
    const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
      headers: jsonHeaders(),
      data: {
        processDefinitionId: 'invalid_process_definition_id',
      },
    });
    await assertStatusCode(res, 404);
    const json = await res.json();
    expect(json.title).toBe('NOT_FOUND');
    expect(json.detail).toBe(
      "Command 'CREATE' rejected with code 'NOT_FOUND': Expected to find process definition with process ID 'invalid_process_definition_id', but none found",
    );
  });

  test('Create Process Instance - Failure - Invalid process definition Id', async ({
    request,
  }) => {
    const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
      headers: jsonHeaders(),
      data: {
        processDefinitionKey: 1234567890, // Invalid type, should be a string
      },
    });
    await assertStatusCode(res, 400);
    const json = await res.json();
    expect(json.title).toBe('Bad Request');
    expect(json.detail).toBe(
      'Request property [processDefinitionKey] cannot be parsed',
    );
  });
});
