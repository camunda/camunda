/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {routeTest as test, expect} from '.';
import {
  buildUrl,
  jsonHeaders,
  assertRequiredFields,
  assertEqualsForKeys,
} from '../http';
import {defaultAssertionOptions} from '../constants';
import {createUsersAndStoreResponseFields} from '../requestHelpers';
import {
  authenticationRequiredFields,
  GET_CURRENT_USER_EXPECTED_BODY,
} from '../beans/requestBeans';
// Using routeTest fixture; no decorator needed

test.describe.parallel('Authentication API Tests', () => {
  test.use({routePath: '/authentication/me'});
  const state: Record<string, unknown> = {};

  test.beforeAll(async ({request}) => {
    await createUsersAndStoreResponseFields(request, 2, state);
  });

  test('Get Current User shape & content', async ({
    request,
    routeCtx,
    expectResponseShape,
  }) => {
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

      // Spec-driven required field assertion
      expectResponseShape(json);

      // Keep existing equality assertion using required field set from beans for now
      assertRequiredFields(json, authenticationRequiredFields);
      assertEqualsForKeys(json, expectedBody, authenticationRequiredFields);

      expect(routeCtx).toBeDefined();
    }).toPass(defaultAssertionOptions);
  });
});
