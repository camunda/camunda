/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '@playwright/test';
import {
  buildUrl,
  jsonHeaders,
  assertRequiredFields,
  assertUnauthorizedRequest,
  assertEqualsForKeys,
  encode,
} from '../../../utils/http';
import {defaultAssertionOptions} from '../../../utils/constants';
import {
  createComponentAuthorization,
  createGroupAndStoreResponseFields,
  createRole,
  createTenant,
  createUsersAndStoreResponseFields,
} from '@requestHelpers';
import {
  authenticationRequiredFields,
  CREATE_COMPONENT_AUTHORIZATION,
  GET_CURRENT_USER_EXPECTED_BODY,
  TENANT_EXPECTED_BODY,
} from '../../../utils/beans/requestBeans';
import {cleanupUsers} from '../../../utils/usersCleanup';

test.describe.parallel('Authentication API Tests', () => {
  const state: Record<string, unknown> = {};
  const createdUserIds: string[] = [];

  test.beforeAll(async ({request}) => {
    await createUsersAndStoreResponseFields(request, 2, state);

    createdUserIds.push(
      ...(Object.values(state).filter(
        (value) => typeof value === 'string' && value.startsWith('user'),
      ) as string[]),
    );
  });

  test.afterAll(async ({request}) => {
    await cleanupUsers(request, createdUserIds);
  });

  test('Get Current User', async ({request}) => {
    await expect(async () => {
      const res = await request.get(buildUrl('/authentication/me'), {
        headers: jsonHeaders(),
        data: {},
      });
      const expectedBody = GET_CURRENT_USER_EXPECTED_BODY(
        'demo',
        'Demo',
        'demo@example.com',
        {
          authorizedComponents: ['*'],
          tenants: [
            {
              name: 'Default',
              tenantId: '<default>',
              description: '',
            },
          ],
          roles: ['admin'],
        },
      );

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, authenticationRequiredFields);
      assertEqualsForKeys(json, expectedBody, authenticationRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  // eslint-disable-next-line playwright/expect-expect
  test('Get Current User Unauthorized', async ({request}) => {
    const res = await request.get(buildUrl('/authentication/me'), {
      headers: {},
      data: {},
    });
    await assertUnauthorizedRequest(res);
  });

  test('Get Current User With Group, Tenants and Authorization', async ({
    request,
  }) => {
    await test.step('Create Group and Assign User', async () => {
      await createGroupAndStoreResponseFields(request, 1, state, 'Auth');
      const stateParams: Record<string, string> = {
        groupId: state['groupIdAuth1'] as string,
        username: state['username1'] as string,
      };

      await expect(async () => {
        const res = await request.put(
          buildUrl('/groups/{groupId}/users/{username}', stateParams),
          {
            headers: jsonHeaders(),
          },
        );
        expect(res.status()).toBe(204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Create Role and Assign User', async () => {
      await createRole(request, state, 'Auth1');
      const p: Record<string, string> = {
        roleId: state['roleIdAuth1'] as string,
        username: state['username1'] as string,
      };

      await expect(async () => {
        const res = await request.put(
          buildUrl('/roles/{roleId}/users/{username}', p),
          {headers: jsonHeaders()},
        );
        expect(res.status()).toBe(204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Create Tenant and Assign User', async () => {
      const tenant = await createTenant(request, state, '1');
      const p = {
        username: state['username1'] as string,
        tenantId: tenant.tenantId as string,
      };

      await expect(async () => {
        const res = await request.put(
          buildUrl('/tenants/{tenantId}/users/{username}', p),
          {headers: jsonHeaders()},
        );
        expect(res.status()).toBe(204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Create Authorization for User', async () => {
      await createComponentAuthorization(
        request,
        CREATE_COMPONENT_AUTHORIZATION('USER', state['username1'] as string),
      );
    });

    await test.step('Get Current User', async () => {
      const expectedBody = GET_CURRENT_USER_EXPECTED_BODY(
        state['username1'] as string,
        state['name1'] as string,
        state['email1'] as string,
        {
          authorizedComponents: ['*'],
          tenants: [
            TENANT_EXPECTED_BODY(
              state['tenantName1'] as string,
              state['tenantId1'] as string,
              state['tenantDescription1'] as string,
            ),
          ],
          groups: [state['groupIdAuth1'] as string],
          roles: [state['roleIdAuth1'] as string],
        },
      );

      await expect(async () => {
        const res = await request.get(buildUrl('/authentication/me'), {
          headers: jsonHeaders(
            encode(`${state['username1']}:${state['password1']}`),
          ),
        });

        expect(res.status()).toBe(200);
        const json = await res.json();
        assertRequiredFields(json, authenticationRequiredFields);
        assertEqualsForKeys(json, expectedBody, authenticationRequiredFields);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Get Current User Without Group, Tenants and Authorization', async ({
    request,
  }) => {
    const expectedBody = GET_CURRENT_USER_EXPECTED_BODY(
      state['username2'] as string,
      state['name2'] as string,
      state['email2'] as string,
    );

    await expect(async () => {
      const res = await request.get(buildUrl('/authentication/me'), {
        headers: jsonHeaders(
          encode(`${state['username2']}:${state['password2']}`),
        ),
      });

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, authenticationRequiredFields);
      assertEqualsForKeys(json, expectedBody, authenticationRequiredFields);
    }).toPass(defaultAssertionOptions);
  });
});
