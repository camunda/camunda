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

type ProcessInstanceStatisticsByVersionResponse = {
  processDefinitionId: string;
  processDefinitionKey: string;
  processDefinitionName: string;
  tenantId: string;
  processDefinitionVersion: number;
  activeInstancesWithIncidentCount: number;
  activeInstancesWithoutIncidentCount: number;
};

test.describe
  .parallel('Get process instance statistics by version API Tests', () => {
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
  const processDefinitionId: string = 'processWithMultipleVersions';

  test.beforeAll(async ({request}) => {
    await test.step('Setup - Deploy process and create instances', async () => {
      await deploy(['./resources/processWithMultipleVersions_v_1.bpmn']);
      const processInstanceV1 = await createSingleInstance(
        processDefinitionId,
        1,
      );
      const processInstanceKeyV1 = processInstanceV1.processInstanceKey;

      await deploy(['./resources/processWithMultipleVersions_v_2.bpmn']);
      const processInstanceV2 = await createSingleInstance(
        processDefinitionId,
        2,
      );
      const processInstanceKeyV2 = processInstanceV2.processInstanceKey;

      processInstanceKeys.push(processInstanceKeyV1);
      processInstanceKeys.push(processInstanceKeyV2);
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

  test('Get process instance statistics by version - Success', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl(
          `/process-definitions/statistics/process-instances-by-version`,
        ),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              processDefinitionId,
            },
          },
        },
      );
      await assertStatusCode(res, 200);
      const body = await res.json();
      validateResponse(
        {
          path: '/process-definitions/statistics/process-instances-by-version',
          method: 'POST',
          status: '200',
        },
        res,
      );
      expect(body.page.totalItems).toBeGreaterThanOrEqual(2);
      body.items.forEach(
        (element: ProcessInstanceStatisticsByVersionResponse) => {
          expect(element.processDefinitionId).toBe(processDefinitionId);
          expect(
            element.activeInstancesWithoutIncidentCount,
          ).toBeGreaterThanOrEqual(1);
        },
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Get process instance statistics by version, sorting - Success', async ({
    request,
  }) => {
    const sort = [
      //Skipped due to bug 50976: https://github.com/camunda/camunda/issues/50976
      // {field: "processDefinitionId", order: "ASC"},
      // {field: "processDefinitionKey", order: "ASC"},
      // {field: "processDefinitionName", order: "ASC"},
      // {field: "processDefinitionVersion", order: "ASC"},
      {field: 'activeInstancesWithIncidentCount', order: 'ASC'},
      {field: 'activeInstancesWithoutIncidentCount', order: 'ASC'},
    ];
    await expect(async () => {
      for (const sortOption of sort) {
        const res = await request.post(
          buildUrl(
            `/process-definitions/statistics/process-instances-by-version`,
          ),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                processDefinitionId,
              },
              sort: [sortOption],
            },
          },
        );
        await assertStatusCode(res, 200);
        const body = await res.json();
        validateResponse(
          {
            path: '/process-definitions/statistics/process-instances-by-version',
            method: 'POST',
            status: '200',
          },
          res,
        );
        const field =
          sortOption.field as keyof ProcessInstanceStatisticsByVersionResponse;
        const fieldValues: string[] = body.items.map(
          (item: ProcessInstanceStatisticsByVersionResponse) =>
            item[field] as string,
        );
        const sortedfieldValues = [...fieldValues].sort();
        expect(fieldValues).toEqual(sortedfieldValues);
        expect(body.page.totalItems).toBeGreaterThanOrEqual(2);
        body.items.forEach(
          (element: ProcessInstanceStatisticsByVersionResponse) => {
            expect(element.processDefinitionId).toBe(processDefinitionId);
            expect(
              element.activeInstancesWithoutIncidentCount,
            ).toBeGreaterThanOrEqual(1);
          },
        );
      }
    }).toPass(defaultAssertionOptions);
  });

  test('Get process instance statistics by version without processDefinitionId - Invalid Argument', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl(`/process-definitions/statistics/process-instances-by-version`),
      {
        headers: jsonHeaders(),
        data: {},
      },
    );
    await assertInvalidArgument(res, 400, 'No filter provided.');
  });

  test('Get process instance statistics by version with non-existing field - Bad Request', async ({
    request,
  }) => {
    const notExistingField = {meow: 'meow'};
    const res = await request.post(
      buildUrl(`/process-definitions/statistics/process-instances-by-version`),
      {
        headers: jsonHeaders(),
        data: {
          filter: {
            processDefinitionId,
            ...notExistingField,
          },
        },
      },
    );
    await assertBadRequest(
      res,
      'Request property [filter.meow] cannot be parsed',
    );
  });

  test('Get process instance statistics by version with non-existing processDefinitionId - Success, empty result', async ({
    request,
  }) => {
    const nonExistingProcessDefinitionId = 'nonExistingProcessDefinitionId';
    const res = await request.post(
      buildUrl(`/process-definitions/statistics/process-instances-by-version`),
      {
        headers: jsonHeaders(),
        data: {
          filter: {
            processDefinitionId: nonExistingProcessDefinitionId,
          },
        },
      },
    );
    await assertStatusCode(res, 200);
    const body = await res.json();
    validateResponse(
      {
        path: '/process-definitions/statistics/process-instances-by-version',
        method: 'POST',
        status: '200',
      },
      res,
    );
    expect(body.page.totalItems).toBe(0);
    expect(body.items.length).toEqual(0);
  });

  test('Get process instance statistics by version without authorization - Unauthorized', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl(`/process-definitions/statistics/process-instances-by-version`),
      {
        headers: {},
        data: {
          filter: {
            processDefinitionId,
          },
        },
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Get process instance statistics by version with user without proper authorization - Forbidden, 200, empty result', async ({
    request,
  }) => {
    const token = encode(
      `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
    );
    const res = await request.post(
      buildUrl(`/process-definitions/statistics/process-instances-by-version`),
      {
        headers: jsonHeaders(token), // overrides default demo:demo
        data: {
          filter: {
            processDefinitionId,
          },
        },
      },
    );
    await assertStatusCode(res, 200);
    const body = await res.json();
    validateResponse(
      {
        path: '/process-definitions/statistics/process-instances-by-version',
        method: 'POST',
        status: '200',
      },
      res,
    );
    expect(body.page.totalItems).toBe(0);
    expect(body.items.length).toEqual(0);
  });
});
