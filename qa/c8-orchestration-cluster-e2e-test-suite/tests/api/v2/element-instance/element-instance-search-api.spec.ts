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
import {createFilter} from '@requestHelpers';
import {filterCases} from '../../../../utils/beans/element-instance-requestBeans';

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

  filterCases(resourceId).forEach(({filterKey, filterValue, expectedTotal}) => {
    test(`Search Element Instances - Filter by ${filterKey} - Success`, async ({
      request,
    }) => {
      await expect(async () => {
        const filter: Record<string, unknown> = {};
        const {key, value} = createFilter(filterKey, filterValue, state);
        filter[key] = value;
        const res = await request.post(buildUrl('/element-instances/search'), {
          headers: jsonHeaders(),
          data: {
            filter: filter,
          },
        });
        await assertStatusCode(res, 200);
        const body = await res.json();
        expect(body.page.totalItems).toBe(expectedTotal);
        expect(body.items.length).toBe(expectedTotal);
        for (const item of body.items) {
          expect(item[key]).toBe(value);
        }
      }).toPass(defaultAssertionOptions);
    });
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
  test.skip('Search Element Instances - with invalid pagination parameters', async ({
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
