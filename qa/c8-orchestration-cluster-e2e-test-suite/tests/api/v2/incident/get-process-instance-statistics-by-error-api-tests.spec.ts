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
  createSingleInstance,
  deploy,
} from '../../../../utils/zeebeClient';
import {
  assertBadRequest,
  assertInvalidArgument,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  encode,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {validateResponse} from '../../../../json-body-assertions';
import {
  createTwoDifferentIncidentsInOneProcess,
  createUser,
  grantUserResourceAuthorization,
  verifyIncidentsForProcessInstance,
} from '@requestHelpers';
import {cleanupUsers} from 'utils/usersCleanup';

test.describe.parallel('Get Process Instance Statistics By Error API Tests', () => {
  let userWithResourcesAuthorizationToSendRequest: {
    username: string;
    name: string;
    email: string;
    password: string;
  } = {} as {
    username: string;
    name: string;
    email: string;
    password: string;
  };
  const processInstanceKeys: string[] = [];

  test.beforeAll(async ({request}) => {
    await deploy([
      './resources/processWithAnError.bpmn',
      './resources/MultipleErrorTypesProcess.bpmn',
      './resources/singleIncidentProcess.bpmn',
    ]);

    await test.step('Setup - Create test user with Resource Authorization and user for granting Authorization', async () => {
      userWithResourcesAuthorizationToSendRequest = await createUser(request);
      await grantUserResourceAuthorization(
        request,
        userWithResourcesAuthorizationToSendRequest,
      );
    });
  });

  test.afterAll(async ({request}) => {
    for (const processInstanceKey of processInstanceKeys) {
      try {
        await cancelProcessInstance(processInstanceKey);
      } catch (error) {
        console.warn(
          `Failed to cancel process instance with key ${processInstanceKey}: ${error}`,
        );
      }
    }

    await test.step('Cleanup - Delete test users', async () => {
      await cleanupUsers(request, [
        userWithResourcesAuthorizationToSendRequest.username,
      ]);
    });
  });

  test('Get Statistics For Process Instances with errors - Success', async ({
    request,
  }) => {
    const errorMessage =
      "Expected result of the expression 'goUp < 0' to be 'BOOLEAN', but was 'NULL'. The evaluation reported the following warnings:\n[NO_VARIABLE_FOUND] No variable found with name 'goUp'\n[NOT_COMPARABLE] Can't compare 'null' with '0'";
    let processInstanceKeyToSearch: string;

    await test.step('Start a process instance that will throw an error', async () => {
      const instance = await createSingleInstance('singleIncidentProcess', 1);
      processInstanceKeyToSearch = instance.processInstanceKey as string;
      console.log(
        `Started process instance with key: ${processInstanceKeyToSearch}`,
      );
      processInstanceKeys.push(processInstanceKeyToSearch);
    });

    await test.step('Verify that the process instance has incidents', async () => {
      await verifyIncidentsForProcessInstance(
        request,
        processInstanceKeyToSearch,
        1,
      );
    });

    await test.step('Get process instance statistics by error', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl(`/incidents/statistics/process-instances-by-error`),
          {
            headers: jsonHeaders(),
          },
        );
        await assertStatusCode(res, 200);
        const responseBody = await res.json();
        await validateResponse(
          {
            path: '/incidents/statistics/process-instances-by-error',
            method: 'POST',
            status: '200',
          },
          res,
        );
        expect(responseBody.page.totalItems).toBeGreaterThanOrEqual(1);
        const responseItems = responseBody.items;
        const matchingItem = responseItems.find(
          (item: {errorMessage: string}) => item.errorMessage === errorMessage,
        );
        expect(matchingItem).toBeDefined();
        expect(matchingItem.activeInstancesWithErrorCount).toBeGreaterThanOrEqual(
          1,
        );
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Get Process Instance Statistics By activeInstancesWithErrorCount sort ASC by error message', async ({
    request,
  }) => {
    let processInstanceKeyToSearch: string;

    await test.step('Start a process instance that will have an incident', async () => {
      const localState: Record<string, unknown> = {};
      await createTwoDifferentIncidentsInOneProcess(localState, request);
      processInstanceKeys.push(localState['processInstanceKey'] as string);

      const instance1 = await createSingleInstance('singleIncidentProcess', 1);
      processInstanceKeyToSearch = instance1.processInstanceKey as string;
      const instance2 = await createSingleInstance('singleIncidentProcess', 1);
      processInstanceKeys.push(instance2.processInstanceKey as string);
      processInstanceKeys.push(processInstanceKeyToSearch);
    });

    await test.step('Verify that the process instance has incidents', async () => {
      await verifyIncidentsForProcessInstance(
        request,
        processInstanceKeyToSearch,
        1,
      );
    });

    await test.step('Get process instance statistics by error sorted ASC by activeInstancesWithErrorCount', async () => {
      const res = await request.post(
        buildUrl(`/incidents/statistics/process-instances-by-error`),
        {
          headers: jsonHeaders(),
          data: {
            sort: [
              {
                field: 'activeInstancesWithErrorCount',
                order: 'ASC',
              },
            ],
          },
        },
      );
      await assertStatusCode(res, 200);
      const responseBody = await res.json();
      await validateResponse(
        {
          path: '/incidents/statistics/process-instances-by-error',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const responseItems = responseBody.items;
      const sortedItems = [...responseItems].sort(
        (a, b) =>
          a.activeInstancesWithErrorCount - b.activeInstancesWithErrorCount,
      );
      expect(responseItems).toEqual(sortedItems);
    });
  });

  test('Get Process Instance Statistics By Error with negative page limit - Bad Request', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl(`/incidents/statistics/process-instances-by-error`),
        {
          headers: jsonHeaders(),
          data: {
            page: {
              limit: -1,
            },
          },
        },
      );

      await assertInvalidArgument(
        res,
        400,
        "The value for page.limit is '-1' but must be a non-negative number.",
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Get Process Instance Statistics By Error Invalid Sort Field - Bad Request', async ({
    request,
  }) => {
    const invalidSortField = 'invalid';
    const res = await request.post(
      buildUrl(`/incidents/statistics/process-instances-by-error`),
      {
        headers: jsonHeaders(),
        data: {
          sort: [
            {
              field: invalidSortField,
              order: 'ASC',
            },
          ],
        },
      },
    );
    await assertBadRequest(
      res,
      `Unexpected value '${invalidSortField}' for enum field 'field'.`,
    );
  });

  test('Get Process Instance Statistics By Error - Unauthorized', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl(`/incidents/statistics/process-instances-by-error`),
      {
        headers: {},
        data: {},
      },
    );

    await assertUnauthorizedRequest(res);
  });

  test('Get Process Instance Statistics By Error - Forbidden, 200, empty result', async ({
    request,
  }) => {
    await test.step('Get Process Instance Statistics By Error with user that has Resource Authorization - Forbidden, empty result, 200', async () => {
      const token = encode(
        `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
      );
      const res = await request.post(
        buildUrl(`/incidents/statistics/process-instances-by-error`),
        {
          headers: jsonHeaders(token),
          data: {},
        },
      );

      await assertStatusCode(res, 200);
      const responseBody = await res.json();
      await validateResponse(
        {
          path: '/incidents/statistics/process-instances-by-error',
          method: 'POST',
          status: '200',
        },
        res,
      );
      expect(responseBody.items.length).toBe(0);
      expect(responseBody.page.hasMoreTotalItems).toBe(false);
      expect(responseBody.page.totalItems).toBe(0);
    });
  });
});
