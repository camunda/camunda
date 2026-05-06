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
  StatisticsJobItem,
} from '@requestHelpers';
import {cleanupUsers} from 'utils/usersCleanup';

test.describe
  .parallel('Get time-series metrics for a job type API Tests', () => {
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

  test('Get time-series metrics for a job type - success', async ({
    request,
  }) => {
    let item: StatisticsJobItem;
    let jobType: string = 'uninitialized';
    const resolution = 'PT1M';

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
      const items: StatisticsJobItem[] = responseBody.items;
      for (const statisticsJob of items) {
        if (statisticsJob.failed.count > 0) {
          item = statisticsJob;
          jobType = item.jobType;
          break;
        }
      }

      if (jobType === 'uninitialized') {
        test.info().annotations.push({
          type: 'blocked',
          description:
            'No job statistics data available to verify the response with jobType filter',
        });
        return;
      }
    });

    await test.step('Get time-series metrics with jobType', async () => {
      const extendedSearchRes = await request.post(
        buildUrl('/jobs/statistics/time-series'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              from: fromDate,
              to: toDate,
              jobType,
              resolution,
            },
          },
        },
      );
      await assertStatusCode(extendedSearchRes, 200);
      await validateResponse(
        {
          path: '/jobs/statistics/time-series',
          method: 'POST',
          status: '200',
        },
        extendedSearchRes,
      );
      const extendedResponseBody = await extendedSearchRes.json();
      const extendedItem = extendedResponseBody.items[0];
      expect(extendedItem.created.count).toBe(item.created.count);
      expect(extendedItem.completed.count).toBe(item.completed.count);
      expect(extendedItem.failed.count).toBe(item.failed.count);
    });
  });

  test('Get time-series metrics for a job type - Unauthorized', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/jobs/statistics/time-series'), {
      headers: {},
      data: {
        filter: {
          from: fromDate,
          to: toDate,
          jobType: 'someNotExistingJobType',
          resolution: 'PT1M',
        },
      },
    });
    await assertUnauthorizedRequest(res);
  });

  test('Get time-series metrics for a not existing job type - Success, empty result', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/jobs/statistics/time-series'), {
      headers: jsonHeaders(),
      data: {
        filter: {
          from: fromDate,
          to: toDate,
          jobType: 'someNotExistingJobType',
          resolution: 'PT1M',
        },
      },
    });
    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/jobs/statistics/time-series',
        method: 'POST',
        status: '200',
      },
      res,
    );
    const responseBody = await res.json();
    expect(responseBody.items).toHaveLength(0);
  });

  test('Get time-series metrics for a job type with no jobtype parameter - Bad Request', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/jobs/statistics/time-series'), {
      headers: jsonHeaders(),
      data: {
        filter: {
          from: fromDate,
          to: toDate,
          resolution: 'PT1M',
        },
      },
    });
    await assertInvalidArgument(res, 400, 'No jobType provided.');
  });

  test('Get time-series metrics for a job type with no resolution parameter - Success', async ({
    request,
  }) => {
    let item: StatisticsJobItem;
    let jobType: string = 'uninitialized';

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
      const items: StatisticsJobItem[] = responseBody.items;
      for (const statisticsJob of items) {
        if (statisticsJob.failed.count > 0) {
          item = statisticsJob;
          jobType = item.jobType;
          break;
        }
      }

      if (jobType === 'uninitialized') {
        test.info().annotations.push({
          type: 'blocked',
          description:
            'No job statistics data available to verify the response with jobType filter',
        });
        return;
      }
    });

    await test.step('Get time-series metrics with jobType', async () => {
      const extendedSearchRes = await request.post(
        buildUrl('/jobs/statistics/time-series'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              from: fromDate,
              to: toDate,
              jobType,
            },
          },
        },
      );
      await assertStatusCode(extendedSearchRes, 200);
      await validateResponse(
        {
          path: '/jobs/statistics/time-series',
          method: 'POST',
          status: '200',
        },
        extendedSearchRes,
      );
      const extendedResponseBody = await extendedSearchRes.json();
      const extendedItem = extendedResponseBody.items[0];
      expect(extendedItem.created.count).toBe(item.created.count);
      expect(extendedItem.completed.count).toBe(item.completed.count);
      expect(extendedItem.failed.count).toBe(item.failed.count);
    });
  });

  test('Get time-series metrics for a job type - Forbidden, empty result', async ({
    request,
  }) => {
    const token = encode(
      `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
    );
    const res = await request.post(buildUrl('/jobs/statistics/time-series'), {
      headers: jsonHeaders(token), // overrides default demo:demo
      data: {
        filter: {
          from: fromDate,
          to: toDate,
          jobType: 'someNotExistingJobType',
          resolution: 'PT1M',
        },
      },
    });
    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/jobs/statistics/time-series',
        method: 'POST',
        status: '200',
      },
      res,
    );
    const responseBody = await res.json();
    expect(responseBody.items).toHaveLength(0);
  });
});
