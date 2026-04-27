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
  grantUserResourceAuthorization,
  getLast24HoursRange,
  StatisticsJobItem,
} from '@requestHelpers';
import {cleanupUsers} from 'utils/usersCleanup';
import {defaultAssertionOptions} from 'utils/constants';

test.describe.parallel('Job Statistics By Type API Tests', () => {
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

  test('Get Job Statistics By Type - success', async ({request}) => {
    let item: StatisticsJobItem;
    let jobType: string = 'uninitialized';

    await test.step('General Search', async () => {
      await expect(async () => {
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
        const items = responseBody.items as StatisticsJobItem[];
        for (const receivedItem of items) {
          if (receivedItem.jobType != '') {
            expect(receivedItem.jobType).toBeDefined();
            item = receivedItem;
            jobType = receivedItem.jobType;
            break;
          }
        }
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Search with jobType', async () => {
      const extendedSearchRes = await request.post(
        buildUrl('/jobs/statistics/by-types'),
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
          path: '/jobs/statistics/by-types',
          method: 'POST',
          status: '200',
        },
        extendedSearchRes,
      );
      const extendedResponseBody = await extendedSearchRes.json();
      const extendedItem = extendedResponseBody.items[0];
      if (!extendedItem || !extendedItem.jobType) {
        test.info().annotations.push({
          type: 'blocked',
          description:
            'No job statistics data available to verify the response with jobType filter',
        });
        return;
      }

      expect(extendedItem).toBeDefined();
      expect(extendedItem.jobType).toBe(jobType);
      expect(extendedItem.created.count).toBe(item.created.count);
      expect(extendedItem.completed.count).toBe(item.completed.count);
      expect(extendedItem.failed.count).toBe(item.failed.count);
      expect(extendedItem.workers).toBe(item.workers);
    });
  });

  test('Get Job Statistics By Type With Wrong Filter Parameter - Bad Request', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/jobs/statistics/by-types'), {
      headers: jsonHeaders(),
      data: {
        filter: {
          from: fromDate,
          to: toDate,
          type: 'someJobType',
        },
      },
    });
    await assertBadRequest(
      res,
      'Request property [filter.type] cannot be parsed',
    );
  });

  test('Get Job Statistics By Type With Missing Required From Filter Parameter - Bad Request', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/jobs/statistics/by-types'), {
      headers: jsonHeaders(),
      data: {
        filter: {
          to: toDate,
          jobType: 'someJobType',
        },
      },
    });
    await assertBadRequest(res, 'from must not be null');
  });

  test('Get Job Statistics By Type With Not Existing Job Type - Success Empty Result', async ({
    request,
  }) => {
    const jobType = 'someNotExistingJobType';
    const res = await request.post(buildUrl('/jobs/statistics/by-types'), {
      headers: jsonHeaders(),
      data: {
        filter: {
          from: fromDate,
          to: toDate,
          jobType,
        },
      },
    });
    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/jobs/statistics/by-types',
        method: 'POST',
        status: '200',
      },
      res,
    );
    const body = await res.json();
    expect(body.items).toHaveLength(0);
    expect(body.page.totalItems).toBe(0);
  });

  test('Get Job Statistics By Type - Unauthorized', async ({request}) => {
    const res = await request.post(buildUrl('/jobs/statistics/by-types'), {
      headers: {},
      data: {
        filter: {
          from: fromDate,
          to: toDate,
        },
      },
    });
    await assertUnauthorizedRequest(res);
  });

  test('Get Job Statistics By Type - Forbidden, Empty Result, 200', async ({
    request,
  }) => {
    const token = encode(
      `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
    );
    const res = await request.post(buildUrl('/jobs/statistics/by-types'), {
      headers: jsonHeaders(token), // overrides default demo:demo
      data: {
        filter: {
          from: fromDate,
          to: toDate,
        },
      },
    });
    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/jobs/statistics/by-types',
        method: 'POST',
        status: '200',
      },
      res,
    );
    const body = await res.json();
    expect(body.items).toHaveLength(0);
    expect(body.page.totalItems).toBe(0);
  });
});
