/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {waitForAssertion} from '../../../../utils/waitForAssertion';
import {
  assertNotFoundRequest,
  assertRequiredFields,
  assertInvalidArgument,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
  encode,
  assertEqualsForKeys,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {validateResponse} from '../../../../json-body-assertions';
import {
  usageMetricsGetResponseRequiredFields,
  userRequiredFields,
  roleRequiredFields,
} from '../../../../utils/beans/requestBeans';
import {generateUniqueId} from '../../../../utils/constants';
import {cleanupUsers} from '../../../../utils/usersCleanup';
import {cleanupRoles} from '../../../../utils/rolesCleanup';

const USAGE_METRICS_GET_ENDPOINT = '/system/usage-metrics';
const CREATE_USER_ENDPOINT = '/users';
const uid = generateUniqueId();
const LIMITED_USER = {
  username: `metrics-limited-${uid}`,
  password: 'metrics-limited',
  name: `name-metrics-limited`,
  email: `email-metrics-limited@example.com`,
};
const LIMITED_ROLE = {
  roleId: `role-limited-${uid}`,
  name: `Usage Metrics Limited Role`,
  description: 'Usage metrics API test role',
};
const LIMITED_ROLE_AUTHORIZATION = {
  ownerId: LIMITED_ROLE.roleId,
  ownerType: 'ROLE',
  resourceId: '*',
  resourceType: 'DECISION_DEFINITION',
  permissionTypes: ['READ_DECISION_DEFINITION'],
};

test.describe('Get usage metrics API Tests', () => {
  test('Get Usage Metrics Success', async ({request}) => {
    const startOfTodayLocal = new Date();
    startOfTodayLocal.setHours(0, 0, 0, 0);
    const isoLocalMidnight = startOfTodayLocal.toISOString();

    const endOfTodayLocal = new Date();
    endOfTodayLocal.setHours(23, 59, 59, 999);
    const isoLocalEndOfDay = endOfTodayLocal.toISOString();
    await expect(async () => {
      const res = await request.get(
        buildUrl(
          USAGE_METRICS_GET_ENDPOINT,
          {},
          {startTime: isoLocalMidnight, endTime: isoLocalEndOfDay},
        ),
        {
          headers: jsonHeaders(),
        },
      );
      await assertStatusCode(res, 200);

      await validateResponse(
        {
          path: USAGE_METRICS_GET_ENDPOINT,
          method: 'GET',
          status: '200',
        },
        res,
      );

      const body = await res.json();
      assertRequiredFields(body, usageMetricsGetResponseRequiredFields);
      expect(body.activeTenants).toBeGreaterThan(0);
      expect(body.processInstances).toBeGreaterThan(0);
      expect(body.decisionInstances).toBeGreaterThan(0);
      expect(body.assignees).toBeGreaterThan(0);
    }).toPass(defaultAssertionOptions);
  });

  test('Get Usage Metrics - Invalid date format', async ({request}) => {
    const invalidStartDate = 'meow';
    const invalidEndDate = 'meow';
    const expectedDetail =
      "The provided startTime 'meow' cannot be parsed as a date according to RFC 3339, section 5.6. The provided endTime 'meow' cannot be parsed as a date according to RFC 3339, section 5.6.";
    await expect(async () => {
      const res = await request.get(
        buildUrl(
          USAGE_METRICS_GET_ENDPOINT,
          {},
          {startTime: invalidStartDate, endTime: invalidEndDate},
        ),
        {
          headers: jsonHeaders(),
        },
      );

      await assertInvalidArgument(res, 400, expectedDetail);
    }).toPass(defaultAssertionOptions);
  });

  test('Get Usage Metrics - Unauthorized', async ({request}) => {
    const startOfTodayLocal = new Date();
    startOfTodayLocal.setHours(0, 0, 0, 0);
    const isoLocalMidnight = startOfTodayLocal.toISOString();

    const endOfTodayLocal = new Date();
    endOfTodayLocal.setHours(23, 59, 59, 999);
    const isoLocalEndOfDay = endOfTodayLocal.toISOString();
    await expect(async () => {
      const res = await request.get(
        buildUrl(
          USAGE_METRICS_GET_ENDPOINT,
          {},
          {startTime: isoLocalMidnight, endTime: isoLocalEndOfDay},
        ),
        {
          headers: {},
        },
      );
      await assertUnauthorizedRequest(res);
    }).toPass(defaultAssertionOptions);
  });
});

test.describe('Get Usage Metrics API Tests - User with no permission', () => {
  let limitedAuthorizationKey: string;

  test.beforeAll(async ({request}) => {
    await test.step('Setup - Create limited role', async () => {
      const res = await request.post(buildUrl('/roles'), {
        headers: jsonHeaders(),
        data: LIMITED_ROLE,
      });

      expect(res.status()).toBe(201);
      const json = await res.json();
      assertRequiredFields(json, roleRequiredFields);
      assertEqualsForKeys(json, LIMITED_ROLE, roleRequiredFields);
    });

    await test.step('Setup - create limited authorization for limited role', async () => {
      const authRes = await request.post(buildUrl('/authorizations'), {
        headers: jsonHeaders(),
        data: LIMITED_ROLE_AUTHORIZATION,
      });
      expect(authRes.status()).toBe(201);
      const authBody = await authRes.json();
      assertRequiredFields(authBody, ['authorizationKey']);
      limitedAuthorizationKey = authBody.authorizationKey;
    });

    await test.step('Setup - Create limited user', async () => {
      const res = await request.post(buildUrl(CREATE_USER_ENDPOINT), {
        headers: jsonHeaders(),
        data: LIMITED_USER,
      });

      expect(res.status()).toBe(201);
      const body = await res.json();
      assertRequiredFields(body, userRequiredFields);
      assertEqualsForKeys(body, LIMITED_USER, userRequiredFields);
    });

    await test.step('Setup - Assign limited role to limited user', async () => {
      const path = {
        userId: LIMITED_USER.username,
        roleId: LIMITED_ROLE.roleId,
      };

      await expect(async () => {
        const res = await request.put(
          buildUrl('/roles/{roleId}/users/{userId}', path),
          {headers: jsonHeaders()},
        );
        await assertStatusCode(res, 204);
      }).toPass(defaultAssertionOptions);
    });
  });

  test.afterAll(async ({request}) => {
    console.log('Starting teardown...');

    await test.step('Teardown - Delete limited role and verify deletion', async () => {
      await cleanupRoles(request, [LIMITED_ROLE.roleId]);
    });

    await test.step('Teardown - Delete limited user and verify deletion', async () => {
      await cleanupUsers(request, [LIMITED_USER.username]);
    });
  });

  test('Get Usage Metrics - User with no granted authorization', async ({
    request,
  }) => {
    const startOfTodayLocal = new Date();
    startOfTodayLocal.setHours(0, 0, 0, 0);
    const isoLocalMidnight = startOfTodayLocal.toISOString();

    const endOfTodayLocal = new Date();
    endOfTodayLocal.setHours(23, 59, 59, 999);
    const isoLocalEndOfDay = endOfTodayLocal.toISOString();

    const token = encode(`${LIMITED_USER.username}:${LIMITED_USER.password}`);

    await expect(async () => {
      const res = await request.get(
        buildUrl(
          USAGE_METRICS_GET_ENDPOINT,
          {},
          {startTime: isoLocalMidnight, endTime: isoLocalEndOfDay},
        ),
        {
          headers: jsonHeaders(token), // overrides default demo:demo
        },
      );
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.activeTenants).toBe(0);
      expect(body.processInstances).toBe(0);
      expect(body.decisionInstances).toBe(0);
      expect(body.assignees).toBe(0);
    }).toPass(defaultAssertionOptions);
  });
});
