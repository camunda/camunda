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
  jsonHeaders,
  encode,
} from '../../../../utils/http';
import {validateResponse} from '../../../../json-body-assertions';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {cleanupUsers} from 'utils/usersCleanup';
import {grantUserResourceAuthorization} from 'utils/requestHelpers/authorization-requestHelpers';
import {createUser} from 'utils/requestHelpers/user-requestHelpers';

test.describe.parallel('Get process instance statistics API Tests', () => {
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
  let processInstanceKeys: string[] = [];

  test.beforeAll(async ({request}) => {
    await test.step('Setup - Deploy process and create instances', async () => {
      await deploy([
        './resources/calledProcess.bpmn',
        './resources/IncidentProcess.bpmn',
      ]);

      const incidentInstance = await createSingleInstance('IncidentProcess', 1);
      const calledInstance = await createSingleInstance('CalledProcess', 1);

      processInstanceKeys.push(incidentInstance.processInstanceKey);
      processInstanceKeys.push(calledInstance.processInstanceKey);
    });

    await test.step('Setup - Create test user with Resource Authorization and user for granting Authorization', async () => {
      userWithResourcesAuthorizationToSendRequest = await createUser(request);
      await grantUserResourceAuthorization(
        request,
        userWithResourcesAuthorizationToSendRequest,
      );
    });
  });

  test.afterAll(async ({request}) => {
    await test.step('Cleanup - Delete test users', async () => {
      await cleanupUsers(request, [
        userWithResourcesAuthorizationToSendRequest.username,
      ]);
    });

    await test.step('Cleanup - Cancel process instances', async () => {
      for (const processInstanceKey of processInstanceKeys) {
        await cancelProcessInstance(processInstanceKey);
      }
    });

    await test.step('Cleanup - Clean array', async () => {
      processInstanceKeys = [];
    });
  });

  test('Get process instance statistics - Success', async ({request}) => {
    const res = await request.post(
      buildUrl(`/process-definitions/statistics/process-instances`),
      {
        headers: jsonHeaders(),
      },
    );
    await assertStatusCode(res, 200);
    const body = await res.json();
    await validateResponse(
      {
        path: '/process-definitions/statistics/process-instances',
        method: 'POST',
        status: '200',
      },
      res,
    );
    expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
  });

  test('Get process instance statistics sort by activeInstancesWithIncidentCount - Success', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl(`/process-definitions/statistics/process-instances`),
      {
        headers: jsonHeaders(),
        data: {
          sort: [
            {
              field: 'activeInstancesWithIncidentCount',
              order: 'DESC',
            },
          ],
        },
      },
    );
    await assertStatusCode(res, 200);
    const body = await res.json();
    await validateResponse(
      {
        path: '/process-definitions/statistics/process-instances',
        method: 'POST',
        status: '200',
      },
      res,
    );
    expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
    const actualActiveInstancesWithIncidentCountList = body.items.map(
      (item: {activeInstancesWithIncidentCount: string}) =>
        item.activeInstancesWithIncidentCount,
    );
    const expectedActiveInstancesWithIncidentCountList = [
      ...actualActiveInstancesWithIncidentCountList,
    ].sort().reverse();
    expect(actualActiveInstancesWithIncidentCountList).toEqual(
      expectedActiveInstancesWithIncidentCountList,
    );
  });

  test('Get process instance statistics sort by activeInstancesWithoutIncidentCount - Success', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl(`/process-definitions/statistics/process-instances`),
      {
        headers: jsonHeaders(),
        data: {
          sort: [
            {
              field: 'activeInstancesWithoutIncidentCount',
              order: 'ASC',
            },
          ],
        },
      },
    );
    await assertStatusCode(res, 200);
    const body = await res.json();
    await validateResponse(
      {
        path: '/process-definitions/statistics/process-instances',
        method: 'POST',
        status: '200',
      },
      res,
    );
    expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
    const actualActiveInstancesWithoutIncidentCountList = body.items.map(
      (item: {activeInstancesWithoutIncidentCount: number}) =>
        item.activeInstancesWithoutIncidentCount,
    );
    const expectedActiveInstancesWithoutIncidentCountList = [
      ...actualActiveInstancesWithoutIncidentCountList,
    ].sort((a, b) => a - b);
    expect(actualActiveInstancesWithoutIncidentCountList).toEqual(
      expectedActiveInstancesWithoutIncidentCountList,
    );
  });

  //Skipped due to bug 50945: https://github.com/camunda/camunda/issues/50945
  test.skip('Get process instance statistics sort by processDefinitionId - Success', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl(`/process-definitions/statistics/process-instances`),
      {
        headers: jsonHeaders(),
        data: {
          sort: [
            {
              field: 'processDefinitionId',
              order: 'ASC',
            },
          ],
        },
      },
    );
    await assertStatusCode(res, 200);
    const body = await res.json();
    await validateResponse(
      {
        path: '/process-definitions/statistics/process-instances',
        method: 'POST',
        status: '200',
      },
      res,
    );
    expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
    const actualProcessDefinitionIdList = body.items.map(
      (item: {ProcessDefinitionId: string}) => item.ProcessDefinitionId,
    );
    const expectedProcessDefinitionIdList = [
      ...actualProcessDefinitionIdList,
    ].sort();
    expect(actualProcessDefinitionIdList).toEqual(
      expectedProcessDefinitionIdList,
    );
  });

  test('Get process instance statistics sort by not existing field - Bad Request', async ({
    request,
  }) => {
    const notExistingField = 'meow';
    const res = await request.post(
      buildUrl(`/process-definitions/statistics/process-instances`),
      {
        headers: jsonHeaders(),
        data: {
          sort: [
            {
              field: notExistingField,
              order: 'DESC',
            },
          ],
        },
      },
    );
    await assertBadRequest(
      res,
      `Unexpected value '${notExistingField}' for enum field 'field'.`,
    );
  });

  test('Get process instance statistics with page limit 1 - Success', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl(`/process-definitions/statistics/process-instances`),
        {
          headers: jsonHeaders(),
          data: {
            page: {
              from: 0,
              limit: 1,
            },
          },
        },
      );
      await assertStatusCode(res, 200);
      const body = await res.json();
      await validateResponse(
        {
          path: '/process-definitions/statistics/process-instances',
          method: 'POST',
          status: '200',
        },
        res,
      );
      expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
      expect(body.items.length).toEqual(1);
    }).toPass(defaultAssertionOptions);
  });

  test('Get process instance statistics with page limit -1 - Bad Request', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl(`/process-definitions/statistics/process-instances`),
      {
        headers: jsonHeaders(),
        data: {
          page: {
            from: 0,
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
  });

  test('Get process instance statistics - Unauthorized', async ({request}) => {
    const res = await request.post(
      buildUrl(`/process-definitions/statistics/process-instances`),
      {
        headers: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Get process instance statistics with user without proper authorization - Forbidden, 200, empty result', async ({
    request,
  }) => {
    const token = encode(
      `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
    );
    const res = await request.post(
      buildUrl(`/process-definitions/statistics/process-instances`),
      {
        headers: jsonHeaders(token), // overrides default demo:demo
      },
    );
    await assertStatusCode(res, 200);
    const body = await res.json();
    await validateResponse(
      {
        path: '/process-definitions/statistics/process-instances',
        method: 'POST',
        status: '200',
      },
      res,
    );
    expect(body.page.totalItems).toEqual(0);
    expect(body.items.length).toEqual(0);
    expect(body.page.hasMoreTotalItems).toBe(false);
  });
});
