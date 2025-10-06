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
  assertEqualsForKeys,
  assertPaginatedRequest,
  assertRequiredFields,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
  paginatedResponseFields,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {
  jobResponseFields,
  jobSearchItemResponseFields,
  jobSearchPageResponseRequiredFields,
} from '../../../../utils/beans/requestBeans';

test.describe.parallel('Job API Tests', () => {
  const state: Record<string, unknown> = {};

  test.beforeAll(async () => {
    await deploy(['./resources/processWithThreeParallelTasks.bpmn']);
    const processInstance = await createInstances(
      'processWithThreeParallelTasks',
      1,
      1,
    );
    state['processInstanceKey'] = processInstance[0].processInstanceKey;
    state['processDefinitionKey'] = processInstance[0].processDefinitionKey;
    state['processDefinitionVersion'] =
      processInstance[0].processDefinitionVersion;
    state['tenantId'] = processInstance[0].tenantId;
    state['processDefinitionId'] = processInstance[0].processDefinitionId;
    state['variables'] = processInstance[0].variables;
  });

  test.afterAll(async () => {
    await cancelProcessInstance(state['processInstanceKey'] as string);
  });

  test('Activate Jobs - only required fields', async ({request}) => {
    const expectedJobFields: Record<string, unknown> = {
      type: 'someTask',
      processDefinitionId: 'processWithThreeParallelTasks',
      processDefinitionVersion: state['processDefinitionVersion'],
      elementId: 'Activity_0r0cymb',
      customHeaders: {},
      worker: '',
      retries: 3,
      variables: state['variables'],
      tenantId: state['tenantId'],
      processDefinitionKey: state['processDefinitionKey'] as string,
      kind: 'BPMN_ELEMENT',
      listenerEventType: 'UNSPECIFIED',
    };

    const res = await request.post(buildUrl('/jobs/activation'), {
      headers: jsonHeaders(),
      data: {
        type: 'someTask',
        timeout: 10000,
        maxJobsToActivate: 1,
      },
    });

    await assertStatusCode(res, 200);
    const json = await res.json();
    expect(json.jobs.length).toBe(1);
    assertRequiredFields(json.jobs[0], jobResponseFields);
    const filteredFields = filterOutDynamicFields(jobResponseFields);
    assertEqualsForKeys(json.jobs[0], expectedJobFields, filteredFields);
  });

  function filterOutDynamicFields(fields: string[]) {
    return fields.filter(
      (field) =>
        field !== 'deadline' &&
        field !== 'jobKey' &&
        field !== 'elementInstanceKey' &&
        field !== 'processInstanceKey',
    );
  }

  test('Activate Jobs - unauthorized', async ({request}) => {
    const res = await request.post(buildUrl('/jobs/activation'), {
      data: {
        type: 'task',
        timeout: 10000,
        maxJobsToActivate: 1,
      },
    });
    await assertUnauthorizedRequest(res);
  });

  test('Activate Jobs - invalid type', async ({request}) => {
    const res = await request.post(buildUrl('/jobs/activation'), {
      headers: jsonHeaders(),
      data: {
        type: 1000,
        timeout: 10000,
        maxJobsToActivate: 1,
      },
    });
    await assertBadRequest(res, 'Request property [type] cannot be parsed');
  });

  test('Activate Jobs - invalid timeout', async ({request}) => {
    const res = await request.post(buildUrl('/jobs/activation'), {
      headers: jsonHeaders(),
      data: {
        type: 'task',
        maxJobsToActivate: 1,
      },
    });
    await assertBadRequest(res, 'No timeout provided.', 'INVALID_ARGUMENT');
  });

  test('Activate Jobs - invalid maxJobsToActivate', async ({request}) => {
    const res = await request.post(buildUrl('/jobs/activation'), {
      headers: jsonHeaders(),
      data: {
        type: 'task',
        timeout: 10000,
      },
    });
    await assertBadRequest(
      res,
      'No maxJobsToActivate provided.',
      'INVALID_ARGUMENT',
    );
  });

  test.describe.parallel('Job Search API Tests', () => {
    test('Search Jobs - no criteria', async ({request}) => {
      await expect(async () => {
        const res = await request.post(buildUrl('/jobs/search'), {
          headers: jsonHeaders(),
          data: {},
        });

        expect(res.status()).toBe(200);
        await assertPaginatedRequest(res, {
          itemLengthGreaterThan: 3,
          totalItemGreaterThan: 3,
        });

        const json = await res.json();
        assertRequiredFields(json, paginatedResponseFields);
        assertRequiredFields(json.page, jobSearchPageResponseRequiredFields);
        assertRequiredFields(json.items[0], jobSearchItemResponseFields);
      }).toPass(defaultAssertionOptions);
    });

    test("Search Jobs - sorted by field 'type'", async ({request}) => {
      await expect(async () => {
        const res = await request.post(buildUrl('/jobs/search'), {
          headers: jsonHeaders(),
          data: {
            sort: [
              {
                field: 'type',
              },
            ],
            filter: {
              processInstanceKey: {
                $eq: state['processInstanceKey'],
              },
            },
          },
        });

        await assertStatusCode(res, 200);
        const json = await res.json();
        assertRequiredFields(json, paginatedResponseFields);
        assertRequiredFields(json.page, jobSearchPageResponseRequiredFields);
        const actualTypeList = json.items.map(
          (item: {type: string}) => item.type,
        );
        const expectedTypeList = [...actualTypeList].sort();
        expect(actualTypeList).toEqual(expectedTypeList);
        assertRequiredFields(json.items[0], jobSearchItemResponseFields);
      }).toPass(defaultAssertionOptions);
    });

    test('Search Jobs - Unauthorized', async ({request}) => {
      await expect(async () => {
        const res = await request.post(buildUrl('/jobs/search'), {
          data: {},
        });

        await assertUnauthorizedRequest(res);
      }).toPass(defaultAssertionOptions);
    });

    test('Search Jobs - invalid request', async ({request}) => {
      const res = await request.post(buildUrl('/jobs/search'), {
        headers: jsonHeaders(),
        data: {
          sort: [
            {
              field: 123,
            },
          ],
        },
      });

      await assertBadRequest(
        res,
        "Unexpected value '123' for enum field 'field'. Use any of the following values: [deadline, deniedReason, elementId, elementInstanceKey, endTime, errorCode, errorMessage, hasFailedWithRetriesLeft, isDenied, jobKey, kind, listenerEventType, processDefinitionId, processDefinitionKey, processInstanceKey, retries, state, tenantId, type, worker]",
      );
    });
  });
});
