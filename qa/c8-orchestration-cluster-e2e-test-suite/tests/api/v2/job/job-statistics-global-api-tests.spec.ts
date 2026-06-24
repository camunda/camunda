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
  encode,
  jsonHeaders,
} from '../../../../utils/http';
import {validateResponse} from '../../../../json-body-assertions';
import {
  createUser,
  getLast24HoursRange,
  grantUserResourceAuthorization,
} from '@requestHelpers';
import {cleanupUsers} from 'utils/usersCleanup';
import {defaultAssertionOptions} from 'utils/constants';

test.describe.parallel('Job Statistics Global API Tests', () => {
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
  const {fromDate, toDate} = getLast24HoursRange();

  test.beforeAll(async ({request}) => {
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
  });

  test('Get Job Statistics - success', async ({request}) => {
    await expect(async () => {
      const res = await request.get(
        buildUrl('/jobs/statistics/global', undefined, {
          from: fromDate,
          to: toDate,
        }),
        {
          headers: jsonHeaders(),
        },
      );
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/jobs/statistics/global',
          method: 'GET',
          status: '200',
        },
        res,
      );
      const body = await res.json();
      expect(body.created.count).toBeGreaterThanOrEqual(1);
      expect(body.completed.count).toBeGreaterThanOrEqual(0);
      expect(body.failed.count).toBeGreaterThanOrEqual(0);
      expect(body.isIncomplete).toBe(false);
    }).toPass(defaultAssertionOptions);
  });

  test('Get Job Statistics with jobtype - success', async ({request}) => {
    const jobType = 'someNotExistingJobType';
    const res = await request.get(
      buildUrl('/jobs/statistics/global', undefined, {
        from: fromDate,
        to: toDate,
        jobType: jobType,
      }),
      {
        headers: jsonHeaders(),
      },
    );
    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/jobs/statistics/global',
        method: 'GET',
        status: '200',
      },
      res,
    );
    const body = await res.json();
    expect(body.created.count).toBe(0);
    expect(body.completed.count).toBe(0);
    expect(body.failed.count).toBe(0);
    expect(body.isIncomplete).toBe(false);
  });

  test('Get Job Statistics no from parameter - Bad Request', async ({
    request,
  }) => {
    const jobType = 'someNotExistingJobType';
    const res = await request.get(
      buildUrl('/jobs/statistics/global', undefined, {
        to: toDate,
        jobType: jobType,
      }),
      {
        headers: jsonHeaders(),
      },
    );
    await assertBadRequest(res, "Required parameter 'from' is not present.");
  });

  test('Get Job Statistics no to parameter - Bad Request', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/jobs/statistics/global', undefined, {from: fromDate}),
      {
        headers: jsonHeaders(),
      },
    );
    await assertBadRequest(res, "Required parameter 'to' is not present.");
  });

  test('Get Job Statistics - Unauthorized', async ({request}) => {
    const res = await request.get(
      buildUrl('/jobs/statistics/global', undefined, {
        from: fromDate,
        to: toDate,
      }),
      {
        headers: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Get Job Statistics - Forbidden, Empty Result, 200', async ({
    request,
  }) => {
    const token = encode(
      `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
    );
    const res = await request.get(
      buildUrl('/jobs/statistics/global', undefined, {
        from: fromDate,
        to: toDate,
      }),
      {
        headers: jsonHeaders(token), // overrides default demo:demo
      },
    );
    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/jobs/statistics/global',
        method: 'GET',
        status: '200',
      },
      res,
    );
    const body = await res.json();
    expect(body.created.count).toBe(0);
    expect(body.completed.count).toBe(0);
    expect(body.failed.count).toBe(0);
    expect(body.isIncomplete).toBe(false);
  });
});
