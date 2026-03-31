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
  deploy,
} from '../../../../utils/zeebeClient';
import {
  assertBadRequest,
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

test.describe.parallel('Get Process Instance Statistics By Definition API Tests', () => {
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

  test('Get Process Instance Statistics By Definition - Success', async ({
    request,
  }) => {
    let errorHashCode: number;
    const expectedProcessDefinitionId = 'MultipleErrorTypesProcess';

    await test.step('Create process instance with incidents', async () => {
      await test.step('Start a process instance that will have an incident', async () => {
        const firstLocalState: Record<string, unknown> = {};
        await createTwoDifferentIncidentsInOneProcess(firstLocalState, request);
        processInstanceKeys.push(firstLocalState['processInstanceKey'] as string);

        const secondLocalState: Record<string, unknown> = {};
        await createTwoDifferentIncidentsInOneProcess(secondLocalState, request);
        processInstanceKeys.push(secondLocalState['processInstanceKey'] as string);
      });

      const processInstanceKeyToSearch =
        processInstanceKeys[processInstanceKeys.length - 1];

      await test.step('Verify that the process instance has incidents', async () => {
        await verifyIncidentsForProcessInstance(
          request,
          processInstanceKeyToSearch,
          2,
        );
      });
    });

    const expectedErrorMessage =
      "Expected to evaluate decision 'MeowDM', but no decision found for id 'MeowDM'";

    await test.step('Get process instance statistics by error to get errorHashCode', async () => {
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
          (item: {errorMessage: string}) =>
            item.errorMessage === expectedErrorMessage,
        );
        expect(matchingItem).toBeDefined();
        errorHashCode = matchingItem.errorHashCode;
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Get process instance statistics by definition with errorHashCode filter', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl(`/incidents/statistics/process-instances-by-definition`),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                errorHashCode,
              },
            },
          },
        );
        await assertStatusCode(res, 200);
        const responseBody = await res.json();
        await validateResponse(
          {
            path: '/incidents/statistics/process-instances-by-definition',
            method: 'POST',
            status: '200',
          },
          res,
        );
        expect(responseBody.page.totalItems).toBeGreaterThanOrEqual(1);
        const matchingItem = responseBody.items.find(
          (item: {processDefinitionId: string}) =>
            item.processDefinitionId.includes(expectedProcessDefinitionId),
        );
        expect(matchingItem).toBeDefined();
        expect(
          matchingItem.activeInstancesWithErrorCount,
        ).toBeGreaterThanOrEqual(2);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Get Process Instance Statistics By Definition - Unauthorized', async ({
    request,
  }) => {
    const errorHashCode = 123456789;

    await test.step('Get process instance statistics by definition without authorization', async () => {
      const res = await request.post(
        buildUrl(`/incidents/statistics/process-instances-by-definition`),
        {
          headers: {},
          data: {
            filter: {
              errorHashCode,
            },
          },
        },
      );
      await assertUnauthorizedRequest(res);
    });
  });

  test('Get Process Instance Statistics By Definition - Bad Request', async ({
    request,
  }) => {
    const errorHashCode = 'meow';
    const res = await request.post(
      buildUrl(`/incidents/statistics/process-instances-by-definition`),
      {
        headers: jsonHeaders(),
        data: {
          filter: {
            errorHashCode,
          },
        },
      },
    );
    await assertBadRequest(
      res,
      'Request property [filter.errorHashCode] cannot be parsed',
    );
  });

  test('Get Process Instance Statistics By Definition - Forbidden, empty result, 200', async ({
    request,
  }) => {
    await test.step('Get Process Instance Statistics By Definition with user that has Resource Authorization - Forbidden, empty result, 200', async () => {
      const token = encode(
        `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
      );
      const errorHashCode = 123456789;
      const res = await request.post(
        buildUrl(`/incidents/statistics/process-instances-by-definition`),
        {
          headers: jsonHeaders(token),
          data: {
            filter: {
              errorHashCode,
            },
          },
        },
      );

      await assertStatusCode(res, 200);
      const responseBody = await res.json();
      await validateResponse(
        {
          path: '/incidents/statistics/process-instances-by-definition',
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
