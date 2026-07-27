/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {randomUUID} from 'crypto';
import {
  cancelProcessInstance,
  createInstances,
  deployWithSubstitutions,
} from '../../../../utils/zeebeClient';
import {
  assertBadRequest,
  assertEqualsForKeys,
  assertPaginatedRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {
  validateResponse,
  validateResponseShape,
} from '../../../../json-body-assertions';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {jobResponseFields} from '../../../../utils/beans/requestBeans';

test.describe.parallel('Job API Tests', () => {
  const state: Record<string, unknown> = {};
  const runSuffix = randomUUID().slice(0, 8);
  const processId = `processWithThreeParallelTasks-${runSuffix}`;
  const someTaskType = `someTask-${runSuffix}`;

  test.beforeAll(async () => {
    await deployWithSubstitutions(
      './resources/processWithThreeParallelTasks.bpmn',
      {
        processWithThreeParallelTasks: processId,
        someTask: someTaskType,
      },
    );
    const processInstance = await createInstances(processId, 1, 1);
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
      type: someTaskType,
      processDefinitionId: processId,
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
        type: someTaskType,
        timeout: 10000,
        maxJobsToActivate: 1,
      },
    });

    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/jobs/activation',
        method: 'POST',
        status: '200',
      },
      res,
    );
    const json = await res.json();
    expect(json.jobs).toHaveLength(1);
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

        await assertStatusCode(res, 200);
        await assertPaginatedRequest(res, {
          itemLengthGreaterThan: 3,
          totalItemGreaterThan: 3,
        });

        const json = await res.json();
        validateResponseShape(
          {
            path: '/jobs/search',
            method: 'POST',
            status: '200',
          },
          json,
        );
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
        await validateResponse(
          {
            path: '/jobs/search',
            method: 'POST',
            status: '200',
          },
          res,
        );
        const json = await res.json();
        const actualTypeList = json.items.map(
          (item: {type: string}) => item.type,
        );
        const expectedTypeList = [...actualTypeList].sort();
        expect(actualTypeList).toEqual(expectedTypeList);
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
        "Unexpected value '123' for enum field 'field'. Use any of the following values: [deadline, deniedReason, elementId, elementInstanceKey, endTime, errorCode, errorMessage, hasFailedWithRetriesLeft, isDenied, jobKey, kind, listenerEventType, priority, processDefinitionId, processDefinitionKey, processInstanceKey, retries, state, tenantId, type, worker]",
      );
    });
  });
});

test.describe.parallel('Job Priority API Tests', () => {
  const state: Record<string, unknown> = {};
  const runSuffix = randomUUID().slice(0, 8);
  const processId = `processWithJobPriority-${runSuffix}`;
  const priorityJobType = `priorityJobType-${runSuffix}`;

  // Priorities are defined statically per task in resources/processWithJobPriority.bpmn.
  // All three tasks share one job type so priority is the only thing that can
  // distinguish the resulting jobs from each other.
  const priorityByElementId: Record<string, number> = {
    Activity_PriorityHigh: 90,
    Activity_PriorityMid: 50,
    Activity_PriorityLow: 10,
  };

  test.beforeAll(async () => {
    await deployWithSubstitutions('./resources/processWithJobPriority.bpmn', {
      processWithJobPriority: processId,
      priorityJobType: priorityJobType,
    });
    const processInstance = await createInstances(processId, 1, 1);
    state['processInstanceKey'] = processInstance[0].processInstanceKey;
  });

  test.afterAll(async () => {
    await cancelProcessInstance(state['processInstanceKey'] as string);
  });

  test('Search Jobs - priority field matches the value defined at deploy time', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/jobs/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: {$eq: state['processInstanceKey']},
          },
        },
      });

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/jobs/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      expect(json.items).toHaveLength(3);
      const actualPriorityByElementId = Object.fromEntries(
        json.items.map((item: {elementId: string; priority: number}) => [
          item.elementId,
          item.priority,
        ]),
      );
      expect(actualPriorityByElementId).toEqual(priorityByElementId);
    }).toPass(defaultAssertionOptions);
  });

  test("Search Jobs - sorted by field 'priority' descending returns the highest priority job first", async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/jobs/search'), {
        headers: jsonHeaders(),
        data: {
          sort: [
            {
              field: 'priority',
              order: 'DESC',
            },
          ],
          filter: {
            processInstanceKey: {$eq: state['processInstanceKey']},
          },
        },
      });

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/jobs/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      const actualPriorityList = json.items.map(
        (item: {priority: number}) => item.priority,
      );
      expect(actualPriorityList).toEqual([90, 50, 10]);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Jobs - filter priority $gte 50 returns only the mid and high priority jobs', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/jobs/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: {$eq: state['processInstanceKey']},
            priority: {$gte: 50},
          },
        },
      });

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/jobs/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      const actualPriorities = json.items
        .map((item: {priority: number}) => item.priority)
        .sort((a: number, b: number) => a - b);
      expect(actualPriorities).toEqual([50, 90]);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Jobs - filter priority $lt 50 returns only the low priority job', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/jobs/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: {$eq: state['processInstanceKey']},
            priority: {$lt: 50},
          },
        },
      });

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/jobs/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      expect(json.items).toHaveLength(1);
      expect(json.items[0].priority).toBe(10);
    }).toPass(defaultAssertionOptions);
  });
});

test.describe.parallel('Activate Jobs API Tests - priority order', () => {
  const runSuffix = randomUUID().slice(0, 8);
  const processId = `processWithJobPriority-${runSuffix}`;
  const priorityJobType = `priorityJobType-${runSuffix}`;
  const processInstanceKeysToCancel: string[] = [];

  test.beforeAll(async () => {
    await deployWithSubstitutions('./resources/processWithJobPriority.bpmn', {
      processWithJobPriority: processId,
      priorityJobType: priorityJobType,
    });
  });

  test.afterAll(async () => {
    for (const processInstanceKey of processInstanceKeysToCancel) {
      await cancelProcessInstance(processInstanceKey);
    }
  });

  test('Activate Jobs - jobs of the same type are activated in descending priority order', async ({
    request,
  }) => {
    const [processInstance] = await createInstances(processId, 1, 1);
    processInstanceKeysToCancel.push(
      String(processInstance.processInstanceKey),
    );

    await expect(async () => {
      const res = await request.post(buildUrl('/jobs/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: {$eq: processInstance.processInstanceKey},
          },
        },
      });
      await assertStatusCode(res, 200);
      const json = await res.json();
      expect(json.items).toHaveLength(3);
    }).toPass(defaultAssertionOptions);

    const activatedPriorities: number[] = [];
    for (let i = 0; i < 3; i++) {
      const res = await request.post(buildUrl('/jobs/activation'), {
        headers: jsonHeaders(),
        data: {
          type: priorityJobType,
          timeout: 10000,
          maxJobsToActivate: 1,
        },
      });

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/jobs/activation',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      expect(json.jobs).toHaveLength(1);
      expect(json.jobs[0].priority).not.toBeUndefined();
      activatedPriorities.push(json.jobs[0].priority);
    }

    expect(activatedPriorities).toEqual([90, 50, 10]);
  });
});
