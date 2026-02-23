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
import {validateResponse} from '../../../../json-body-assertions';
import {defaultAssertionOptions} from '../../../../utils/constants';

/*
 * Test Suite for Element Instance Search API
 * These test cases are not executed in parallel to avoid interference between tests
 */
test.describe('Element Instance Search API', () => {
  const resourceId = 'element_instance_api_tests';
  const state: Record<string, unknown> = {};
  const processInstanceKeys: string[] = [];
  let execCount = 0;

  test.beforeAll(async () => {
    console.log(`${++execCount} - Deploying and creating instances for tests`);
    await deploy([`./resources/${resourceId}.bpmn`]);
    await createInstances(resourceId, 1, 1).then((instances) => {
      console.log(instances[0].processInstanceKey);
      state.processInstanceKey = instances[0].processInstanceKey;
      state.processDefinitionKey = instances[0].processDefinitionKey;
      processInstanceKeys.push(instances[0].processInstanceKey);
      console.log(`State: ${JSON.stringify(state)}`);
    });
  });

  test.afterAll(async () => {
    for (const instance of processInstanceKeys) {
      await cancelProcessInstance(instance as string);
    }
  });

  test('Search Element Instances - Success', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/element-instances/search'), {
        headers: jsonHeaders(),
        data: {}, // No filter find all
      });
      await assertStatusCode(res, 200);
      const body = await res.json();
      await validateResponse(
        {
          path: '/element-instances/search',
          method: 'POST',
          status: '200',
        },
        res,
      );

      expect(body.page.totalItems).toBeGreaterThanOrEqual(2);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Element Instances - Page Limit 1', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/element-instances/search'), {
        headers: jsonHeaders(),
        data: {
          page: {
            limit: 1,
          },
        },
      });
      await assertStatusCode(res, 200);
      const body = await res.json();

      expect(body.page.totalItems).toBeGreaterThan(1);
      expect(body.items.length).toBe(1);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Element Instances - Sort by state ASC', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/element-instances/search'), {
        headers: jsonHeaders(),
        data: {
          sort: [
            {
              field: 'state',
              // default is ASC
            },
          ],
        },
      });
      await assertStatusCode(res, 200);
      const body = await res.json();
      const states = body.items.map(
        (item: Record<string, unknown>) => item.state,
      );
      const sortedStates = [...states].sort();
      expect(states).toEqual(sortedStates);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Element Instances - filter by processDefinitionId', async ({
    request,
  }) => {
    const processDefinitionIdToSearch = resourceId;
    const expectedTotal = 2;
    await expect(async () => {
      const res = await request.post(buildUrl('/element-instances/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processDefinitionId: processDefinitionIdToSearch,
          },
        },
      });
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.page.totalItems).toBe(expectedTotal);
      expect(body.items.length).toBe(expectedTotal);
      body.items.forEach((item: Record<string, unknown>) => {
        expect(item.processDefinitionId).toBe(processDefinitionIdToSearch);
      });
    }).toPass(defaultAssertionOptions);
  });

  test('Search Element Instances - filter by elementName', async ({
    request,
  }) => {
    const elementNameToSearch = 'Decision If Human Needed';
    const expectedTotal = 1;
    await expect(async () => {
      const res = await request.post(buildUrl('/element-instances/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            elementName: elementNameToSearch,
          },
        },
      });
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.page.totalItems).toBe(expectedTotal);
      expect(body.items.length).toBe(expectedTotal);
      body.items.forEach((item: Record<string, unknown>) => {
        expect(item.elementName).toBe(elementNameToSearch);
      });
    }).toPass(defaultAssertionOptions);
  });

  test('Search Element Instances - filter by type', async ({request}) => {
    const typeToSearch = 'EXCLUSIVE_GATEWAY';
    const expectedTotal = 1;
    await expect(async () => {
      const res = await request.post(buildUrl('/element-instances/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            type: typeToSearch,
          },
        },
      });
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThanOrEqual(expectedTotal);
      expect(body.items.length).toBeGreaterThanOrEqual(expectedTotal);
      body.items.forEach((item: Record<string, unknown>) => {
        expect(item.type).toBe(typeToSearch);
      });
    }).toPass(defaultAssertionOptions);
  });

  test('Search Element Instances - filter by processDefinitionKey', async ({
    request,
  }) => {
    const processDefinitionKeyToSearch = state.processDefinitionKey as string;
    const expectedTotal = 2;
    await expect(async () => {
      const res = await request.post(buildUrl('/element-instances/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processDefinitionKey: processDefinitionKeyToSearch,
          },
        },
      });
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.page.totalItems).toBe(expectedTotal);
      expect(body.items.length).toBe(expectedTotal);
      body.items.forEach((item: Record<string, unknown>) => {
        expect(item.processDefinitionKey).toBe(processDefinitionKeyToSearch);
      });
    }).toPass(defaultAssertionOptions);
  });

  test('Search Element Instances - filter by processInstanceKey', async ({
    request,
  }) => {
    const processInstanceKeyToSearch = state.processInstanceKey as string;
    const expectedTotal = 2;
    await expect(async () => {
      const res = await request.post(buildUrl('/element-instances/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: processInstanceKeyToSearch,
          },
        },
      });
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.page.totalItems).toBe(expectedTotal);
      expect(body.items.length).toBe(expectedTotal);
      body.items.forEach((item: Record<string, unknown>) => {
        expect(item.processInstanceKey).toBe(processInstanceKeyToSearch);
      });
    }).toPass(defaultAssertionOptions);
  });

  test(`Search Element Instances - Multiple Filters`, async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/element-instances/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processDefinitionKey: state.processDefinitionKey,
            state: 'COMPLETED',
          },
        },
      });
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.page.totalItems).toBe(1);
      expect(body.items.length).toBe(1);
      expect(body.items[0]['processDefinitionKey']).toBe(
        state.processDefinitionKey,
      );
      expect(body.items[0]['state']).toBe('COMPLETED');
      expect(body.items[0]['elementId']).toBe('ElementInstance_StartEvent');
    }).toPass(defaultAssertionOptions);
  });

  test('Search Element Instances - Invalid Filter', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/element-instances/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            invalidField: 'someValue',
          },
        },
      });
      await assertBadRequest(
        res,
        'Request property [filter.invalidField] cannot be parsed',
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Element Instances - Invalid Sort Field', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/element-instances/search'), {
        headers: jsonHeaders(),
        data: {
          sort: [
            {
              // Missing field
              order: 'DESC',
            },
          ],
        },
      });
      await assertBadRequest(
        res,
        'Sort field must not be null.',
        'INVALID_ARGUMENT',
      );
    }).toPass(defaultAssertionOptions);
  });

  //Skipped due to bug 39372: https://github.com/camunda/camunda/issues/39372
  test.skip('Search Element Instances - - with invalid pagination parameters', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/element-instances/search'), {
        headers: jsonHeaders(),
        data: {
          page: {
            limit: -1,
          },
        },
      });
      await assertBadRequest(res, 'Sort field must not be null.');
    }).toPass(defaultAssertionOptions);
  });

  test('Search Element Instances - Unauthorized', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/element-instances/search'), {
        // No auth headers
        data: {},
      });
      await assertUnauthorizedRequest(res);
    }).toPass(defaultAssertionOptions);
  });
});
