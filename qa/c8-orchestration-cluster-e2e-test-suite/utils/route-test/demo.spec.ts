/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {routeTest as test, expect} from '.';
import {buildUrl, jsonHeaders, assertEqualsForKeys} from '../http';
import {defaultAssertionOptions} from '../constants';
import {createUsersAndStoreResponseFields} from '../requestHelpers';
import {
  authenticationRequiredFields,
  GET_CURRENT_USER_EXPECTED_BODY,
} from '../beans/requestBeans';
// Using routeTest fixture; no decorator needed

test.describe.parallel('Authentication API Tests', () => {
  // Declaratively select the spec route + preferred variant (GET 200 auto-selected anyway)
  test.use({
    routePath: '/authentication/me',
    routeMethod: 'GET',
    routeStatus: '200',
  });
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

  // Spec-driven contract validation (structure, types, enums, wrappers, no-extra-properties)
      expectResponseShape(json);

      // Stateful business logic test
      // Business logic / stateful validation still explicit
  assertEqualsForKeys(json, expectedBody, authenticationRequiredFields);

  // NOTE: Any undeclared field would now surface as an [EXTRA] error above.
  // To experiment with alternate response schemas (e.g. smaller fixture) set ROUTE_TEST_RESPONSES_FILE.
  // To record bodies set TEST_RESPONSE_BODY_RECORD_DIR.

      expect(routeCtx.requiredFieldNames.length).toBeGreaterThan(0);
    }).toPass(defaultAssertionOptions);
  });
});
