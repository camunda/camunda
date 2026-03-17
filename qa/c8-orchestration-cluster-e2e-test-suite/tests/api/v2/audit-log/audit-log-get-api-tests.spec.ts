/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '@playwright/test';
import {
  jsonHeaders,
  buildUrl,
  assertUnauthorizedRequest,
  assertStatusCode,
  encode,
  assertForbiddenRequest,
  assertNotFoundRequest,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {validateResponse} from 'json-body-assertions';
import {cleanupUsers} from 'utils/usersCleanup';
import {grantUserResourceAuthorization} from 'utils/requestHelpers/authorization-requestHelpers';
import {createUser} from 'utils/requestHelpers/user-requestHelpers';
import {type AuditLog} from '@requestHelpers';

test.describe.parallel('Get Audit Logs API Tests', () => {
  test('Get Audit Logs Success', async ({request}) => {
    let expectedAuditLog: AuditLog;

    await test.step('Search audit logs to get an audit log key', async () => {
      await expect(async () => {
        const res = await request.post(buildUrl('/audit-logs/search'), {
          headers: jsonHeaders(),
        });

        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/audit-logs/search',
            method: 'POST',
            status: '200',
          },
          res,
        );
        const responseBody = await res.json();
        const items = Array.isArray(responseBody.items)
          ? responseBody.items
          : [];
        expectedAuditLog = items[0] as AuditLog;
        expect(expectedAuditLog).toBeDefined();
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Get Audit Logs', async () => {
      const auditLogKey = expectedAuditLog.auditLogKey;
      await expect(async () => {
        const res = await request.get(buildUrl(`/audit-logs/${auditLogKey}`), {
          headers: jsonHeaders(),
        });

        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/audit-logs/{auditLogKey}',
            method: 'GET',
            status: '200',
          },
          res,
        );
        const responseBody = await res.json();
        expect(responseBody).toEqual(expectedAuditLog);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Get Audit Logs - Not Found', async ({request}) => {
    const invalidAuditLogKey = 'meowmeowInvalidAuditLogKey';
    const res = await request.get(
      buildUrl(`/audit-logs/${invalidAuditLogKey}`),
      {
        headers: jsonHeaders(),
      },
    );

    await assertNotFoundRequest(
      res,
      `Audit log with id '${invalidAuditLogKey}' not found`,
    );
  });

  test('Get Audit Logs - Unauthorized', async ({request}) => {
    const res = await request.get(buildUrl('/audit-logs/someAuditLogKey'), {
      headers: {},
    });
    await assertUnauthorizedRequest(res);
  });

  test('Get Audit Logs - Forbidden', async ({request}) => {
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

    await test.step('Setup - Create test user with Resource Authorization', async () => {
      userWithResourcesAuthorizationToSendRequest = await createUser(request);
      await grantUserResourceAuthorization(
        request,
        userWithResourcesAuthorizationToSendRequest,
      );
    });

    let expectedAuditLog: AuditLog;

    await test.step('Search audit logs to get an audit log key', async () => {
      await expect(async () => {
        const res = await request.post(buildUrl('/audit-logs/search'), {
          headers: jsonHeaders(),
        });

        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/audit-logs/search',
            method: 'POST',
            status: '200',
          },
          res,
        );
        const responseBody = await res.json();
        expect(responseBody.items?.length).toBeGreaterThan(0);
        expectedAuditLog = responseBody.items[0] as AuditLog;
      }).toPass(defaultAssertionOptions);
    });

    const token = encode(
      `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
    );

    await test.step('Get Audit Logs with user having only Resource Authorization - Expect Forbidden', async () => {
      const auditLogKey = expectedAuditLog.auditLogKey;
      await expect(async () => {
        const res = await request.get(buildUrl(`/audit-logs/${auditLogKey}`), {
          headers: jsonHeaders(token), // overrides default demo:demo
          data: {},
        });
        await assertForbiddenRequest(res, 'Unauthorized to perform');
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Cleanup', async () => {
      await cleanupUsers(request, [
        userWithResourcesAuthorizationToSendRequest.username,
      ]);
    });
  });
});
