/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import test, {expect} from '@playwright/test';
import {setupProcessInstanceForTests} from '@requestHelpers';
import {
  assertBadRequest,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from 'utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {validateResponseShape} from 'assert-json-body';
import {findUserTask} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Search User Task Variables Tests', () => {
  const {state, beforeAll, beforeEach, afterEach} =
    setupProcessInstanceForTests(
      'user_task_api_test_process',
      'user_task_api_test_process',
      {testset1: 'something', testset2: 'something else', zip: 123},
    );

  test.beforeAll(beforeAll);
  test.beforeEach(beforeEach);
  test.afterEach(afterEach);

  test('Search user task variables - success', async ({request}) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );

    await expect(async () => {
      const res = await request.post(
        buildUrl(`/user-tasks/${userTaskKey}/variables/search`),
        {
          headers: jsonHeaders(),
          data: {},
        },
      );
      const json = await res.json();
      validateResponseShape(
        {
          path: '/user-tasks/{userTaskKey}/variables/search',
          method: 'POST',
          status: '200',
        },
        json,
      );
      expect(json.page.totalItems).toBe(3);
      expect(json.items.length).toBe(3);
    }).toPass(defaultAssertionOptions);
  });

  test('Search user task variables sort desc - success', async ({request}) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );

    await expect(async () => {
      const res = await request.post(
        buildUrl(`/user-tasks/${userTaskKey}/variables/search`),
        {
          headers: jsonHeaders(),
          data: {
            sort: [{field: 'name', order: 'DESC'}],
          },
        },
      );
      const json = await res.json();
      validateResponseShape(
        {
          path: '/user-tasks/{userTaskKey}/variables/search',
          method: 'POST',
          status: '200',
        },
        json,
      );
      expect(json.page.totalItems).toBe(3);
      expect(json.items.length).toBe(3);
      expect(json.items[0].name).toEqual('zip');
      expect(json.items[1].name).toEqual('testset2');
      expect(json.items[2].name).toEqual('testset1');
      expect(json.items[0].value).toEqual('123');
      expect(json.items[1].value).toEqual('"something else"');
      expect(json.items[2].value).toEqual('"something"');
    }).toPass(defaultAssertionOptions);
  });

  test('Search user task variables - unauthorized', async ({request}) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const res = await request.post(
      buildUrl(`/user-tasks/${userTaskKey}/variables/search`),
      {
        // No auth headers
        headers: {
          'Content-Type': 'application/json',
        },
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Search user task variables - bad request - invalid payload', async ({
    request,
  }) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const res = await request.post(
      buildUrl(`/user-tasks/${userTaskKey}/variables/search`),
      {
        headers: jsonHeaders(),
        data: {
          // Invalid field
          page: 'invalidValue',
        },
      },
    );
    await assertBadRequest(
      res,
      'At least one of [from, after, before, limit] is required.',
    );
  });

  test('Search user task variables - bad request - invalid user task key', async ({
    request,
  }) => {
    const invalidUserTaskKey = 'invalidKey';
    const res = await request.post(
      buildUrl(`/user-tasks/${invalidUserTaskKey}/variables/search`),
      {
        headers: jsonHeaders(),
        data: {},
      },
    );
    await assertBadRequest(
      res,
      `Failed to convert 'userTaskKey' with value: '${invalidUserTaskKey}'`,
    );
  });

  test('Search user task variables - filter by variable name', async ({
    request,
  }) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    await expect(async () => {
      const res = await request.post(
        buildUrl(`/user-tasks/${userTaskKey}/variables/search`),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              name: 'testset1',
            },
          },
        },
      );
      const json = await res.json();
      validateResponseShape(
        {
          path: '/user-tasks/{userTaskKey}/variables/search',
          method: 'POST',
          status: '200',
        },
        json,
      );
      expect(json.page.totalItems).toBe(1);
      expect(json.items.length).toBe(1);
      expect(json.items[0].name).toBe('testset1');
      expect(json.items[0].value).toBe('"something"');
    }).toPass(defaultAssertionOptions);
  });

  test('Search user task variables - advanced filter by variable name with wildcard', async ({
    request,
  }) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    await expect(async () => {
      const res = await request.post(
        buildUrl(`/user-tasks/${userTaskKey}/variables/search`),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              name: {
                $like: '*set*',
              },
            },
          },
        },
      );
      const json = await res.json();
      validateResponseShape(
        {
          path: '/user-tasks/{userTaskKey}/variables/search',
          method: 'POST',
          status: '200',
        },
        json,
      );
      expect(json.page.totalItems).toBe(2);
      expect(json.items.length).toBe(2);
      expect(json.items[0].name).toBe('testset1');
      expect(json.items[0].value).toBe('"something"');
      expect(json.items[1].name).toBe('testset2');
      expect(json.items[1].value).toBe('"something else"');
    }).toPass(defaultAssertionOptions);
  });
});
