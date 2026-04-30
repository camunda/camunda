/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import type {APIRequestContext} from 'playwright-core';
import {
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
  userRequiredFields,
  roleRequiredFields,
} from '../../../../utils/beans/requestBeans';
import {generateUniqueId} from '../../../../utils/constants';
import {cleanupUsers} from '../../../../utils/usersCleanup';
import {cleanupRoles} from '../../../../utils/rolesCleanup';
import {deploy} from '../../../../utils/zeebeClient';

const USAGE_METRICS_GET_ENDPOINT = '/system/usage-metrics';
const CREATE_USER_ENDPOINT = '/users';
const USAGE_METRICS_PROCESS_DEFINITION_ID = 'clockApiTestProcess';
const USAGE_METRICS_EXPORT_TIMEOUT_MS = 6 * 60_000;
const USAGE_METRICS_WINDOW_START_OFFSET_MS = 60_000;
const USAGE_METRICS_WINDOW_END_OFFSET_MS = 15 * 60_000;
const EXPECTED_PROCESS_INSTANCE_INCREASE = 3;
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

type UsageMetricsResponseBody = {
  activeTenants: number;
  processInstances: number;
  decisionInstances: number;
  assignees: number;
};

function toIsoWithOffset(timestamp: string, offsetMs: number) {
  return new Date(new Date(timestamp).getTime() + offsetMs).toISOString();
}

function buildUsageMetricsWindow(referenceTimestamp: string) {
  return {
    startTime: toIsoWithOffset(
      referenceTimestamp,
      -USAGE_METRICS_WINDOW_START_OFFSET_MS,
    ),
    endTime: toIsoWithOffset(
      referenceTimestamp,
      USAGE_METRICS_WINDOW_END_OFFSET_MS,
    ),
  };
}

async function getUsageMetrics(
  request: APIRequestContext,
  startTime: string,
  endTime: string,
) {
  const res = await request.get(
    buildUrl(USAGE_METRICS_GET_ENDPOINT, {}, {startTime, endTime}),
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

  return (await res.json()) as UsageMetricsResponseBody;
}

async function createCompletedProcessInstance(request: APIRequestContext) {
  const createRes = await request.post(buildUrl('/process-instances'), {
    headers: jsonHeaders(),
    data: {
      processDefinitionId: USAGE_METRICS_PROCESS_DEFINITION_ID,
      awaitCompletion: true,
    },
  });

  await assertStatusCode(createRes, 200);
  await validateResponse(
    {
      path: '/process-instances',
      method: 'POST',
      status: '200',
    },
    createRes,
  );

  const createdInstance = await createRes.json();

  let completedInstance: Record<string, unknown> | undefined;
  await expect(async () => {
    const getRes = await request.get(
      buildUrl(`/process-instances/${createdInstance.processInstanceKey}`),
      {
        headers: jsonHeaders(),
      },
    );

    await assertStatusCode(getRes, 200);
    await validateResponse(
      {
        path: '/process-instances/{processInstanceKey}',
        method: 'GET',
        status: '200',
      },
      getRes,
    );

    completedInstance = await getRes.json();
    expect(completedInstance?.processDefinitionId).toBe(
      USAGE_METRICS_PROCESS_DEFINITION_ID,
    );
    expect(completedInstance?.state).toBe('COMPLETED');
    expect(completedInstance?.endDate).toBeTruthy();
  }).toPass(defaultAssertionOptions);

  return completedInstance as {
    endDate: string;
  };
}

test.describe.serial('Get usage metrics API Tests', () => {
  test.beforeAll(async () => {
    await deploy(['./resources/clock_api_test_process.bpmn']);
  });

  test('Get Usage Metrics Success', async ({request}) => {
    const calibrationInstance = await createCompletedProcessInstance(request);
    const {startTime, endTime} = buildUsageMetricsWindow(
      calibrationInstance.endDate,
    );
    const baselineMetrics = await getUsageMetrics(request, startTime, endTime);

    for (let i = 0; i < EXPECTED_PROCESS_INSTANCE_INCREASE; i++) {
      await createCompletedProcessInstance(request);
    }

    await expect(async () => {
      const body = await getUsageMetrics(request, startTime, endTime);
      expect(body.activeTenants).toBeGreaterThanOrEqual(0);
      expect(body.processInstances).toBeGreaterThanOrEqual(
        baselineMetrics.processInstances + EXPECTED_PROCESS_INSTANCE_INCREASE,
      );
      expect(body.decisionInstances).toBeGreaterThanOrEqual(0);
      expect(body.assignees).toBeGreaterThanOrEqual(0);
    }).toPass({
      intervals: [10_000, 15_000, 30_000],
      timeout: USAGE_METRICS_EXPORT_TIMEOUT_MS,
    });
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
  test.beforeAll(async ({request}) => {
    await test.step('Setup - Create limited role', async () => {
      const res = await request.post(buildUrl('/roles'), {
        headers: jsonHeaders(),
        data: LIMITED_ROLE,
      });

      await assertStatusCode(res, 201);
      await validateResponse(
        {
          path: '/roles',
          method: 'POST',
          status: '201',
        },
        res,
      );
      const json = await res.json();
      assertEqualsForKeys(json, LIMITED_ROLE, roleRequiredFields);
    });

    await test.step('Setup - create limited authorization for limited role', async () => {
      const authRes = await request.post(buildUrl('/authorizations'), {
        headers: jsonHeaders(),
        data: LIMITED_ROLE_AUTHORIZATION,
      });
      expect(authRes.status()).toBe(201);
      await validateResponse(
        {
          path: '/authorizations',
          method: 'POST',
          status: '201',
        },
        authRes,
      );
      await authRes.json();
    });

    await test.step('Setup - Create limited user', async () => {
      const res = await request.post(buildUrl(CREATE_USER_ENDPOINT), {
        headers: jsonHeaders(),
        data: LIMITED_USER,
      });

      await assertStatusCode(res, 201);
      await validateResponse(
        {
          path: CREATE_USER_ENDPOINT,
          method: 'POST',
          status: '201',
        },
        res,
      );
      const body = await res.json();
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

  //Skipped due to bug 43428: https://github.com/camunda/camunda/issues/43428
  test.skip('Get Usage Metrics - User with no granted authorization', async ({
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
      await assertUnauthorizedRequest(res);
    }).toPass(defaultAssertionOptions);
  });
});
