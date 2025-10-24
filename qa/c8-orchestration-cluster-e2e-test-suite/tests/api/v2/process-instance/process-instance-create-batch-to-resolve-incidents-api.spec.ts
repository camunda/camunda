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
import {
  cancelProcessInstance,
  createInstances,
  deploy,
} from '../../../../utils/zeebeClient';
import {validateResponseShape} from '../../../../json-body-assertions';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {
  failJob,
  searchJobKeysForProcessInstance,
  verifyIncidentsForProcessInstance,
} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */
test.describe
  .parallel('Create Process Instance Batch to Resolve Incidents Tests', () => {
  test.beforeAll(async () => {
    await deploy(['./resources/processWithThreeParallelTasks.bpmn']);
  });

  test('Create a Batch Operation to Resolve Process Instance Incidents - Unauthorized', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-instances/incident-resolution'),
      {
        data: {
          processInstanceKeys: [2251799813685249, 2251799813685250],
        },
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Create a Batch Operation to Resolve Incidents - Success', async ({
    request,
  }) => {
    const localState: Record<string, string> = {
      processInstanceKey: '',
    };

    await test.step('Create a process instance that will generate incidents', async () => {
      const processInstances = await createInstances(
        'processWithThreeParallelTasks',
        1,
        1,
      );
      localState.processInstanceKey = processInstances[0].processInstanceKey;
    });

    await test.step('Generate incidents by failing jobs and throwing errors', async () => {
      const processInstanceKey = localState.processInstanceKey;
      const foundJobKeys = await searchJobKeysForProcessInstance(
        request,
        processInstanceKey,
      );
      await failJob(request, foundJobKeys[0]);
      await failJob(request, foundJobKeys[1]);
      await failJob(request, foundJobKeys[2]);
    });

    await test.step('Verify that the process instance has incidents', async () => {
      const processInstanceKey = localState.processInstanceKey;
      await verifyIncidentsForProcessInstance(request, processInstanceKey, 3);
    });

    await test.step('Create Batch Operation to Resolve Incidents - Success', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/process-instances/incident-resolution'),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                processInstanceKey: localState.processInstanceKey,
              },
            },
          },
        );
        await assertStatusCode(res, 200);
        const json = await res.json();
        validateResponseShape(
          {
            path: '/process-instances/incident-resolution',
            method: 'POST',
            status: '200',
          },
          json,
        );
        expect(json.batchOperationType).toBe('RESOLVE_INCIDENT');
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Verify that the incidents are resolved', async () => {
      const processInstanceKey = localState.processInstanceKey;
      await verifyIncidentsForProcessInstance(request, processInstanceKey, 0);
    });

    await cancelProcessInstance(localState.processInstanceKey);
  });

  test('Create a Batch Operation to Resolve Incidents With No Filter - Bad Request', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-instances/incident-resolution'),
      {
        headers: jsonHeaders(),
        data: {
          // No filter or processInstanceKeys provided
        },
      },
    );
    await assertBadRequest(res, 'No filter provided.', 'INVALID_ARGUMENT');
  });

  test('Create a Batch Operation to Resolve Incidents - With Invalid Filter - Bad Request', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-instances/incident-resolution'),
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

  test('Create a Batch Operation to Resolve Incidents - With Multiple Filters', async ({
    request,
  }) => {
    const localState: Record<string, string> = {
      processInstanceKey1: '',
      processInstanceKey2: '',
    };

    await test.step('Create Process Instances to generate incidents', async () => {
      const instances = await createInstances(
        'processWithThreeParallelTasks',
        1,
        1,
      );
      localState.processInstanceKey1 = instances[0].processInstanceKey;
      const instances2 = await createInstances(
        'processWithThreeParallelTasks',
        1,
        1,
        {example: 1},
      );
      localState.processInstanceKey2 = instances2[0].processInstanceKey;
    });

    await test.step('Generate incidents by failing jobs and throwing errors', async () => {
      const foundJobKeys = await searchJobKeysForProcessInstance(
        request,
        localState.processInstanceKey1,
      );
      await failJob(request, foundJobKeys[0]);
      const foundJobKeys2 = await searchJobKeysForProcessInstance(
        request,
        localState.processInstanceKey2,
      );
      await failJob(request, foundJobKeys2[0]);
    });

    await test.step('Verify that the process instances have incidents', async () => {
      await verifyIncidentsForProcessInstance(
        request,
        localState.processInstanceKey1,
        1,
      );

      await verifyIncidentsForProcessInstance(
        request,
        localState.processInstanceKey2,
        1,
      );
    });

    await test.step('Resolve incidents for processInstanceKey1 with errorMessage filter', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/process-instances/incident-resolution'),
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
            },
          },
        );
        await assertStatusCode(res, 200);
        const json = await res.json();
        expect(json.batchOperationType).toBe('RESOLVE_INCIDENT');
      }).toPass(defaultAssertionOptions);
    });
    await test.step('Verify that the process instances have no incidents', async () => {
      await verifyIncidentsForProcessInstance(
        request,
        localState.processInstanceKey1,
        1,
      );

      await verifyIncidentsForProcessInstance(
        request,
        localState.processInstanceKey2,
        0,
      );
    });

    await cancelProcessInstance(localState.processInstanceKey1);
    await cancelProcessInstance(localState.processInstanceKey2);
  });

  test('Create a Batch Operation to Resolve Incidents - With Or Filters', async ({
    request,
  }) => {
    const localState: Record<string, string> = {
      processInstanceKey1: '',
      processInstanceKey2: '',
    };

    await test.step('Create Process Instances to generate incidents', async () => {
      const instances = await createInstances(
        'processWithThreeParallelTasks',
        1,
        1,
      );
      localState.processInstanceKey1 = instances[0].processInstanceKey;
      const instances2 = await createInstances(
        'processWithThreeParallelTasks',
        1,
        1,
        {example: 1},
      );
      localState.processInstanceKey2 = instances2[0].processInstanceKey;
    });

    await test.step('Generate incidents by failing jobs and throwing errors', async () => {
      const foundJobKeys = await searchJobKeysForProcessInstance(
        request,
        localState.processInstanceKey1,
      );
      await failJob(request, foundJobKeys[0]);
      const foundJobKeys2 = await searchJobKeysForProcessInstance(
        request,
        localState.processInstanceKey2,
      );
      await failJob(request, foundJobKeys2[0], 0, 'TEST_FAILURE');
    });

    await test.step('Verify that the process instances have incidents', async () => {
      await verifyIncidentsForProcessInstance(
        request,
        localState.processInstanceKey1,
        1,
      );

      await verifyIncidentsForProcessInstance(
        request,
        localState.processInstanceKey2,
        1,
      );
    });

    await test.step('Resolve incidents for processInstanceKey1 with errorMessage filter', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/process-instances/incident-resolution'),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                $or: [
                  {processInstanceKey: localState.processInstanceKey1},
                  {errorMessage: 'TEST_FAILURE'},
                ],
              },
            },
          },
        );
        await assertStatusCode(res, 200);
        const json = await res.json();
        expect(json.batchOperationType).toBe('RESOLVE_INCIDENT');
      }).toPass(defaultAssertionOptions);
    });
    await test.step('Verify that the process instances have no incidents', async () => {
      await verifyIncidentsForProcessInstance(
        request,
        localState.processInstanceKey1,
        0,
      );

      await verifyIncidentsForProcessInstance(
        request,
        localState.processInstanceKey2,
        0,
      );
    });

    await cancelProcessInstance(localState.processInstanceKey1);
    await cancelProcessInstance(localState.processInstanceKey2);
  });
});
