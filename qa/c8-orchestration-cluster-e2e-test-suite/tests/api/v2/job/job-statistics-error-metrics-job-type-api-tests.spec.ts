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
  buildUrl,
  encode,
  jsonHeaders,
} from '../../../../utils/http';
import {validateResponse} from '../../../../json-body-assertions';
import {
  createUser,
  grantUserResourceAuthorization,
  getLast24HoursRange,
  StatisticsJobItem,
} from '@requestHelpers';
import {cleanupUsers} from 'utils/usersCleanup';

test.describe.parallel('Get error metrics for a job type API Tests', () => {
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

  test('Get error metrics for a job type - success', async ({request}) => {
    let failedItems: string[] = [];

    await test.step('General Search', async () => {
      const generalSearchRes = await request.post(
        buildUrl('/jobs/statistics/by-types'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              from: fromDate,
              to: toDate,
            },
          },
        },
      );
      await assertStatusCode(generalSearchRes, 200);
      await validateResponse(
        {
          path: '/jobs/statistics/by-types',
          method: 'POST',
          status: '200',
        },
        generalSearchRes,
      );
      const responseBody = await generalSearchRes.json();
      const receivedItems: StatisticsJobItem[] = responseBody.items;
      for (const item of receivedItems) {
        if (item.failed.count != 0) {
          failedItems.push(item.jobType);
        }
      }

      if (failedItems.length === 0) {
        test.info().annotations.push({
          type: 'blocked',
          description:
            'No failed jobs in the last 24 hours to verify the response with jobType filter',
        });
        return;
      }
    });

    await test.step('Get error metrics for a job type without error code and without errorMessage', async () => {
      for (const statisticsJob of failedItems) {
        const extendedSearchRes = await request.post(
          buildUrl('/jobs/statistics/errors'),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                from: fromDate,
                to: toDate,
                jobType: statisticsJob,
              },
            },
          },
        );
        await assertStatusCode(extendedSearchRes, 200);
        await validateResponse(
          {
            path: '/jobs/statistics/errors',
            method: 'POST',
            status: '200',
          },
          extendedSearchRes,
        );
      }
    });
  });

  test('Get error metrics for a job type without jobType - Bad Request', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/jobs/statistics/errors'), {
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

  test('Get error metrics for a job type - Forbidden, empty result', async ({
    request,
  }) => {
    const token = encode(
      `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
    );
    const res = await request.post(buildUrl('/jobs/statistics/errors'), {
      headers: jsonHeaders(token), // overrides default demo:demo
      data: {
        filter: {
          from: fromDate,
          to: toDate,
          jobType: 'meowJobType',
        },
      },
    });

    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/jobs/statistics/errors',
        method: 'POST',
        status: '200',
      },
      res,
    );
    const responseBody = await res.json();
    expect(responseBody.items).toHaveLength(0);
  });
});
