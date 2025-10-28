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
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {setupProcessInstanceForTests} from '@requestHelpers';
import {validateResponseShape} from '../../../../json-body-assertions';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Search User Task Tests', () => {
  const {state, beforeAll, beforeEach, afterEach} =
    setupProcessInstanceForTests('process_with_multiple_user_tasks');

  test.beforeAll(beforeAll);
  test.beforeEach(beforeEach);
  test.afterEach(afterEach);

  test('Search user tasks - success', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/user-tasks/search'), {
        headers: jsonHeaders(),
        data: {},
      });
      const json = await res.json();
      validateResponseShape(
        {
          path: '/user-tasks/search',
          method: 'POST',
          status: '200',
        },
        json,
      );

      expect(json.page.totalItems).toBeGreaterThan(3);
    }).toPass(defaultAssertionOptions);
  });

  test('Search user tasks - unauthorized', async ({request}) => {
    const res = await request.post(buildUrl('/user-tasks/search'), {
      // No auth headers
      headers: {
        'Content-Type': 'application/json',
      },
    });
    await assertUnauthorizedRequest(res);
  });

  // Skipped due to bug 39819:  https://github.com/camunda/camunda/issues/39819
  test.skip('Search user tasks - bad request - invalid payload', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/user-tasks/search'), {
      headers: jsonHeaders(),
      data: {
        // Invalid field
        page: 'invalidValue',
      },
    });
    await assertBadRequest(res, 'Request property [page] cannot be parsed');
  });

  test('Search user task - filter by processInstanceKey', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/user-tasks/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: state['processInstanceKey'],
          },
        },
      });
      const json = await res.json();
      validateResponseShape(
        {
          path: '/user-tasks/search',
          method: 'POST',
          status: '200',
        },
        json,
      );
      expect(json.page.totalItems).toBe(3);
    }).toPass(defaultAssertionOptions);
  });

  test('Search user task - filter by processInstanceKey and name', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/user-tasks/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: state['processInstanceKey'],
            name: 'First user task',
          },
        },
      });
      const json = await res.json();
      validateResponseShape(
        {
          path: '/user-tasks/search',
          method: 'POST',
          status: '200',
        },
        json,
      );
      expect(json.page.totalItems).toBe(1);
      expect(json.items[0].name).toBe('First user task');
    }).toPass(defaultAssertionOptions);
  });

  test('Search user task - find no tasks', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/user-tasks/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: '9999999999999',
          },
        },
      });
      const json = await res.json();
      validateResponseShape(
        {
          path: '/user-tasks/search',
          method: 'POST',
          status: '200',
        },
        json,
      );
      expect(json.page.totalItems).toBe(0);
      expect(json.items.length).toBe(0);
    }).toPass(defaultAssertionOptions);
  });
});
