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

  test('Search user tasks - bad request - invalid payload', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/user-tasks/search'), {
      headers: jsonHeaders(),
      data: {
        // Invalid field
        page: 'invalidValue',
      },
    });
    await assertBadRequest(
      res,
      'At least one of [from, after, before, limit] is required.',
    );
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
      expect(json.items).toHaveLength(0);
    }).toPass(defaultAssertionOptions);
  });

  test('Search user task - filter by processDefinitionKey with $in operator', async ({
    request,
  }) => {
    let processDefinitionKey = '';

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
      expect(json.items.length).toBeGreaterThan(0);
      processDefinitionKey = String(json.items[0].processDefinitionKey);
    }).toPass(defaultAssertionOptions);

    await expect(async () => {
      const res = await request.post(buildUrl('/user-tasks/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processDefinitionKey: {
              $in: [processDefinitionKey, '9999999999999'],
            },
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
      expect(json.page.totalItems).toBeGreaterThanOrEqual(3);
      expect(
        json.items.every(
          (item: {processDefinitionKey: number | string}) =>
            String(item.processDefinitionKey) === processDefinitionKey,
        ),
      ).toBe(true);
    }).toPass(defaultAssertionOptions);
  });

  test('Search user task - filter by processDefinitionId with $like operator', async ({
    request,
  }) => {
    let idSuffix = '';

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
      expect(json.items.length).toBeGreaterThan(0);
      // Use a suffix of the ID so the pattern genuinely exercises the * wildcard
      idSuffix = json.items[0].processDefinitionId.slice(-10);
    }).toPass(defaultAssertionOptions);

    await expect(async () => {
      const res = await request.post(buildUrl('/user-tasks/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processDefinitionId: {
              $like: `*${idSuffix}`,
            },
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
      expect(json.page.totalItems).toBeGreaterThanOrEqual(3);
      expect(
        json.items.every((item: {processDefinitionId: string}) =>
          item.processDefinitionId.endsWith(idSuffix),
        ),
      ).toBe(true);
    }).toPass(defaultAssertionOptions);
  });

  test('Search user task - filter by processInstanceKey with $exists operator', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/user-tasks/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: {
              $exists: true,
            },
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
      expect(json.page.totalItems).toBeGreaterThan(0);
      expect(
        json.items.every(
          (item: {processInstanceKey: number | null | undefined}) =>
            item.processInstanceKey !== null &&
            item.processInstanceKey !== undefined,
        ),
      ).toBe(true);
    }).toPass(defaultAssertionOptions);
  });
});
