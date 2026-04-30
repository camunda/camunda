/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {
  assertInvalidArgument,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  encode,
  jsonHeaders,
} from '../../../../utils/http';
import {validateResponse} from '../../../../json-body-assertions';
import {
  createUser,
  grantUserResourceAuthorization,
  getLast24HoursRange,
} from '@requestHelpers';
import {cleanupUsers} from 'utils/usersCleanup';

test.describe.parallel('Job Statistics By Worker API Tests', () => {
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
  const expectedJobType = 'incidentGenerator';

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

  test('Get Job Statistics By Worker - success', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/jobs/statistics/by-workers'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            from: fromDate,
            to: toDate,
            jobType: expectedJobType,
          },
        },
      });
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/jobs/statistics/by-workers',
          method: 'POST',
          status: '200',
        },
        res,
      );

      const body = await res.json();
      expect(body.items.length).toBeGreaterThanOrEqual(1);
      expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
      const item = body.items[0];
      expect(item.worker).toBeDefined();
      expect(item.created.count).toBeGreaterThanOrEqual(0);
      expect(item.completed.count).toBeGreaterThanOrEqual(0);
      expect(item.failed.count).toBeGreaterThanOrEqual(1);
    }).toPass({
      intervals: [5_000, 10_000, 15_000],
      timeout: 90_000,
    });
  });

  test('Get Job Statistics By Worker with non existing job type - success, empty result', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/jobs/statistics/by-workers'), {
      headers: jsonHeaders(),
      data: {
        filter: {
          from: fromDate,
          to: toDate,
          jobType: 'someNotExistingJobType',
        },
      },
    });
    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/jobs/statistics/by-workers',
        method: 'POST',
        status: '200',
      },
      res,
    );

    const body = await res.json();
    expect(body.items).toHaveLength(0);
    expect(body.page.totalItems).toBe(0);
    expect(body.page.hasMoreTotalItems).toBe(false);
  });

  test('Get Job Statistics By Worker no jobType parameter - Bad Request', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/jobs/statistics/by-workers'), {
      headers: jsonHeaders(),
      data: {
        filter: {
          from: fromDate,
          to: toDate,
        },
      },
    });
    await assertInvalidArgument(res, 400, 'No jobType provided.');
  });

  test('Get Job Statistics By Worker - Unauthorized', async ({request}) => {
    const res = await request.post(buildUrl('/jobs/statistics/by-workers'), {
      headers: {},
      data: {
        filter: {
          from: fromDate,
          to: toDate,
          jobType: 'someNotExistingJobType',
        },
      },
    });
    await assertUnauthorizedRequest(res);
  });

  test('Get Job Statistics By Worker  - Forbidden, empty result', async ({
    request,
  }) => {
    const token = encode(
      `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
    );
    const res = await request.post(buildUrl('/jobs/statistics/by-workers'), {
      headers: jsonHeaders(token), // overrides default demo:demo
      data: {
        filter: {
          from: fromDate,
          to: toDate,
          jobType: 'someNotExistingJobType',
        },
      },
    });
    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/jobs/statistics/by-workers',
        method: 'POST',
        status: '200',
      },
      res,
    );
    const body = await res.json();
    expect(body.items).toHaveLength(0);
    expect(body.page.totalItems).toBe(0);
    expect(body.page.hasMoreTotalItems).toBe(false);
  });
});
