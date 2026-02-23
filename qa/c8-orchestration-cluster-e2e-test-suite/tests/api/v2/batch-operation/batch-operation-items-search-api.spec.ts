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
  createSingleInstance,
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
import {validateResponseShape} from '../../../../json-body-assertions';
import {validateResponse} from '../../../../json-body-assertions';
import {
  createSingleIncidentProcessInstance,
  expectBatchState,
  verifyIncidentsForProcessInstance,
  expectProcessInstanceCanBeFound,
} from '@requestHelpers';
import {sleep} from 'utils/sleep';
import {waitForAssertion} from 'utils/waitForAssertion';

const SEARCH_BATCH_OPERATION_ITEMS_PATH = '/batch-operation-items/search';

test.describe.parallel('Batch Operation Items Search API Tests', () => {
  const processInstanceKeys: string[] = [];

  test.beforeAll(async () => {
    await deploy([
      './resources/singleIncidentProcess.bpmn',
      './resources/process_with_task_listener.bpmn',
      './resources/processWithAnError.bpmn',
    ]);
  });

  test.afterAll(async () => {
    for (const processInstanceKey of processInstanceKeys) {
      try {
        await cancelProcessInstance(processInstanceKey);
      } catch (error) {
        console.warn(
          `Failed to cancel process instance with key ${processInstanceKey}: ${error}`,
        );
      }
    }
  });

  test('Search Batch Operation Items - by itemKey [incident key] - Success', async ({
    request,
  }) => {
    const localState: Record<string, unknown> = {};
    await createSingleIncidentProcessInstance(localState, request);
    const incidentKeys = localState['incidentKeys'] as string[];
    const incidentKey = incidentKeys[0];
    const processInstanceKeyToResolveIncident =
      localState.processInstanceKey as string;
    processInstanceKeys.push(processInstanceKeyToResolveIncident);

    await test.step('Verify that the process instance has incidents', async () => {
      await verifyIncidentsForProcessInstance(
        request,
        processInstanceKeyToResolveIncident,
        1,
      );
    });

    await sleep(10000);

    await test.step('Poll process instance can be found', async () => {
      await expectProcessInstanceCanBeFound(
        request,
        processInstanceKeyToResolveIncident,
      );
    });

    await test.step('Create Batch Operation to Resolve Incidents', async () => {
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

    await test.step('Search Batch Operation Items', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(async () => {
            const res = await request.post(
              buildUrl(SEARCH_BATCH_OPERATION_ITEMS_PATH),
              {
                headers: jsonHeaders(),
                data: {
                  filter: {
                    itemKey: incidentKey,
                  },
                },
              },
            );

            await assertStatusCode(res, 200);
            await validateResponse(
              {
                path: SEARCH_BATCH_OPERATION_ITEMS_PATH,
                method: 'POST',
                status: '200',
              },
              res,
            );

            const body = await res.json();
            expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
            expect(body.items.length).toEqual(1);

            const item = body.items[0];
            expect(item.itemKey).toBe(incidentKey);
            expect(item.operationType).toBe('RESOLVE_INCIDENT');
            expect(item.state).toBeDefined();
            expect(item.processInstanceKey).toBe(
              processInstanceKeyToResolveIncident,
            );
          }).toPass(defaultAssertionOptions);
        },
        onFailure: async () => {},
      });
    });
  });

  test('Search Batch Operation Items - by process instance key with single canceled process instance - Success', async ({
    request,
  }) => {
    let processInstanceKeyToCancel: string;
    let batchOperationKey: string;
    await test.step('Create process instance to cancel', async () => {
      processInstanceKeyToCancel = (
        await createSingleInstance('processWithAnError', 1)
      ).processInstanceKey;
      console.log(
        'Created process instance with key:',
        processInstanceKeyToCancel,
      );
    });

    await test.step('Poll process instance can be found', async () => {
      await expectProcessInstanceCanBeFound(
        request,
        processInstanceKeyToCancel,
      );
    });

    await test.step('Create a Batch Operation to Cancel Process Instance - Success', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/process-instances/cancellation'),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                processInstanceKey: processInstanceKeyToCancel,
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
        batchOperationKey = json.batchOperationKey;
        console.log('Created batch operation with key:', batchOperationKey);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Poll batch status', async () => {
      await expectBatchState(request, batchOperationKey, 'COMPLETED');
    });

    await test.step('Search Batch Operation Items - by process instance key', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(async () => {
            const res = await request.post(
              buildUrl(SEARCH_BATCH_OPERATION_ITEMS_PATH),
              {
                headers: jsonHeaders(),
                data: {
                  filter: {
                    processInstanceKey: processInstanceKeyToCancel,
                  },
                },
              },
            );

            await assertStatusCode(res, 200);
            await validateResponse(
              {
                path: SEARCH_BATCH_OPERATION_ITEMS_PATH,
                method: 'POST',
                status: '200',
              },
              res,
            );

            const body = await res.json();
            expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
            expect(body.items.length).toEqual(1);

            const item = body.items[0];
            expect(item.itemKey).toBe(processInstanceKeyToCancel);
            expect(item.batchOperationKey).toBe(batchOperationKey);
            expect(item.operationType).toBe('CANCEL_PROCESS_INSTANCE');
            expect(item.state).toBeDefined();
            expect(item.processInstanceKey).toBe(processInstanceKeyToCancel);
          }).toPass(defaultAssertionOptions);
        },
        onFailure: async () => {},
      });
    });
  });

  test('Search Batch Operation Items - by itemKey [process instance key] with multiple canceled process instance - Success', async ({
    request,
  }) => {
    const localState: Record<string, string[]> = {processInstanceKeys: []};
    let processInstanceKey1: string;
    let processInstanceKey2: string;
    let batchOperationKey: string;
    await test.step('Create multiple process instances to cancel', async () => {
      const processInstances = await createInstances(
        'process_with_task_listener',
        1,
        3,
      );
      for (const processInstance of processInstances) {
        localState['processInstanceKeys'] = [
          ...(localState['processInstanceKeys'] as string[]),
          processInstance.processInstanceKey,
        ];
      }
      processInstanceKey1 = localState['processInstanceKeys']![0];
      processInstanceKey2 = localState['processInstanceKeys']![1];
    });

    await test.step('Poll process instances can be found', async () => {
      await expectProcessInstanceCanBeFound(request, processInstanceKey1);
      await expectProcessInstanceCanBeFound(request, processInstanceKey2);
    });

    await test.step('Create a Batch Operation to Cancel Process Instances', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/process-instances/cancellation'),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                $or: [
                  ...localState['processInstanceKeys'].map((key) => ({
                    processInstanceKey: key,
                  })),
                ],
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
        batchOperationKey = json.batchOperationKey;
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Search Batch Operation Items - by multiple process instance key as itemKey', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl(SEARCH_BATCH_OPERATION_ITEMS_PATH),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                itemKey: {
                  $in: [processInstanceKey1, processInstanceKey2],
                },
              },
            },
          },
        );

        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: SEARCH_BATCH_OPERATION_ITEMS_PATH,
            method: 'POST',
            status: '200',
          },
          res,
        );

        const body = await res.json();
        expect(body.page.totalItems).toEqual(2);
        expect(body.items.length).toEqual(2);

        const item = body.items[0];
        expect([processInstanceKey1, processInstanceKey2]).toContain(
          item.itemKey,
        );
        expect(item.batchOperationKey).toBe(batchOperationKey);
        expect(item.operationType).toBe('CANCEL_PROCESS_INSTANCE');
        expect(item.state).toBeDefined();
        expect([processInstanceKey1, processInstanceKey2]).toContain(
          item.processInstanceKey,
        );
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Search Batch Operation Items - by operationType - Success', async ({
    request,
  }) => {
    const localState: Record<string, unknown> = {};
    await createSingleIncidentProcessInstance(localState, request);
    const processInstanceKey = localState.processInstanceKey as string;
    let processInstanceKeyToCancel: string;
    let batchOperationKey: string;
    processInstanceKeys.push(processInstanceKey);

    await test.step('Verify that the process instance has incidents', async () => {
      await verifyIncidentsForProcessInstance(request, processInstanceKey, 1);
    });

    await test.step('Poll process instance can be found', async () => {
      await expectProcessInstanceCanBeFound(request, processInstanceKey);
    });

    await test.step('Create Batch Operation to Resolve Incidents', async () => {
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

    await test.step('Create process instance to cancel', async () => {
      processInstanceKeyToCancel = (
        await createSingleInstance('processWithAnError', 1)
      ).processInstanceKey;
      console.log(
        'Created process instance with key:',
        processInstanceKeyToCancel,
      );
    });

    await test.step('Poll process instance can be found', async () => {
      await expectProcessInstanceCanBeFound(
        request,
        processInstanceKeyToCancel,
      );
    });

    await test.step('Create a Batch Operation to Cancel Process Instance - Success', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/process-instances/cancellation'),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                processInstanceKey: processInstanceKeyToCancel,
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
        batchOperationKey = json.batchOperationKey;
        console.log('Created batch operation with key:', batchOperationKey);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Poll batch status', async () => {
      await expectBatchState(request, batchOperationKey, 'COMPLETED');
    });

    await test.step('Search Batch Operation Items - by operation type CANCEL_PROCESS_INSTANCE', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl(SEARCH_BATCH_OPERATION_ITEMS_PATH),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                operationType: 'CANCEL_PROCESS_INSTANCE',
              },
            },
          },
        );

        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: SEARCH_BATCH_OPERATION_ITEMS_PATH,
            method: 'POST',
            status: '200',
          },
          res,
        );

        const body = await res.json();

        expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
        expect(body.items.length).toBeGreaterThanOrEqual(1);

        const item = body.items[0];
        expect(item.operationType).toBe('CANCEL_PROCESS_INSTANCE');
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Search Batch Operation Items - by operation type RESOLVE_INCIDENT', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl(SEARCH_BATCH_OPERATION_ITEMS_PATH),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                operationType: 'RESOLVE_INCIDENT',
              },
            },
          },
        );

        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: SEARCH_BATCH_OPERATION_ITEMS_PATH,
            method: 'POST',
            status: '200',
          },
          res,
        );

        const body = await res.json();

        expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
        expect(body.items.length).toBeGreaterThanOrEqual(1);

        const item = body.items[0];
        expect(item.operationType).toBe('RESOLVE_INCIDENT');
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Search Batch Operation Items - Unauthorized Request', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl(SEARCH_BATCH_OPERATION_ITEMS_PATH),
      {
        data: {
          filter: {
            operationType: 'RESOLVE_INCIDENT',
          },
        },
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Search Batch Operation Items - Empty Result', async ({request}) => {
    const notExistingProcessInstanceKey = '9999999999999999';
    await expect(async () => {
      const res = await request.post(
        buildUrl(SEARCH_BATCH_OPERATION_ITEMS_PATH),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              processInstanceKey: notExistingProcessInstanceKey,
            },
          },
        },
      );

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: SEARCH_BATCH_OPERATION_ITEMS_PATH,
          method: 'POST',
          status: '200',
        },
        res,
      );

      const body = await res.json();
      expect(body.page.totalItems).toBe(0);
      expect(body.items.length).toBe(0);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Batch Operation Items - Bad Request - invalid filter field', async ({
    request,
  }) => {
    const invalidFilterValue = 'meow';
    await expect(async () => {
      const res = await request.post(
        buildUrl(SEARCH_BATCH_OPERATION_ITEMS_PATH),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              meow: invalidFilterValue,
            },
          },
        },
      );

      await assertBadRequest(
        res,
        'Request property [filter.meow] cannot be parsed',
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Batch Operation Items - Bad Request - invalid state filter value', async ({
    request,
  }) => {
    const invalidFilterValue = 'meow';
    await expect(async () => {
      const res = await request.post(
        buildUrl(SEARCH_BATCH_OPERATION_ITEMS_PATH),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              state: invalidFilterValue,
            },
          },
        },
      );

      await assertBadRequest(
        res,
        "Unexpected value 'meow' for enum field 'state'. Use any of the following values: [ACTIVE, COMPLETED, CANCELED, FAILED]",
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Batch Operation Items - Bad Request - invalid itemKey filter value', async ({
    request,
  }) => {
    const invalidFilterValue = 'meow';
    await expect(async () => {
      const res = await request.post(
        buildUrl(SEARCH_BATCH_OPERATION_ITEMS_PATH),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              itemKey: invalidFilterValue,
            },
          },
        },
      );

      await assertBadRequest(res, 'For input string: \"meow\"');
    }).toPass(defaultAssertionOptions);
  });
});
