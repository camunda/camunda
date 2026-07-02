/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {
  assertForbiddenRequest,
  buildUrl,
  encode,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {cleanupUsers} from '../../../../utils/usersCleanup';
import {
  createUser,
  findUserTask,
  grantUserResourceAuthorization,
  setupProcessInstanceForTests,
} from '@requestHelpers';

const READ_FORBIDDEN_DETAIL =
  "'READ_USER_TASK' on 'PROCESS_DEFINITION' or 'READ' on 'USER_TASK'";
const UPDATE_USER_TASK_FORBIDDEN_DETAIL =
  "Insufficient permissions to perform operation 'UPDATE_USER_TASK' on resource 'PROCESS_DEFINITION'";

test.describe.serial('User Task Permission API - Forbidden', () => {
  const {state, beforeAll, beforeEach, afterEach} =
    setupProcessInstanceForTests('user_task_api_test_process');

  let userWithoutUserTaskPermissions: {
    username: string;
    name: string;
    email: string;
    password: string;
  };
  let userWithoutUserTaskPermissionsToken: string;

  test.beforeAll(async ({request}) => {
    await beforeAll();

    userWithoutUserTaskPermissions = await createUser(request);
    await grantUserResourceAuthorization(
      request,
      userWithoutUserTaskPermissions,
    );
    userWithoutUserTaskPermissionsToken = encode(
      `${userWithoutUserTaskPermissions.username}:${userWithoutUserTaskPermissions.password}`,
    );
  });

  test.beforeEach(beforeEach);

  test.afterEach(afterEach);

  test.afterAll(async ({request}) => {
    await cleanupUsers(request, [userWithoutUserTaskPermissions.username]);
  });

  test('Get user task - forbidden without READ permission', async ({
    request,
  }) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );

    await expect(async () => {
      const res = await request.get(buildUrl(`/user-tasks/${userTaskKey}`), {
        headers: jsonHeaders(userWithoutUserTaskPermissionsToken),
      });
      await assertForbiddenRequest(res, READ_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Assign user task - forbidden without UPDATE_USER_TASK permission', async ({
    request,
  }) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );

    await expect(async () => {
      const res = await request.post(
        buildUrl(`/user-tasks/${userTaskKey}/assignment`),
        {
          headers: jsonHeaders(userWithoutUserTaskPermissionsToken),
          data: {assignee: userWithoutUserTaskPermissions.username},
        },
      );
      await assertForbiddenRequest(res, UPDATE_USER_TASK_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Unassign user task - forbidden without UPDATE_USER_TASK permission', async ({
    request,
  }) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );

    await expect(async () => {
      const res = await request.delete(
        buildUrl(`/user-tasks/${userTaskKey}/assignee`),
        {
          headers: jsonHeaders(userWithoutUserTaskPermissionsToken),
        },
      );
      await assertForbiddenRequest(res, UPDATE_USER_TASK_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Update user task - forbidden without UPDATE_USER_TASK permission', async ({
    request,
  }) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );

    await expect(async () => {
      const res = await request.patch(buildUrl(`/user-tasks/${userTaskKey}`), {
        headers: jsonHeaders(userWithoutUserTaskPermissionsToken),
        data: {
          changeset: {priority: 80},
          action: 'customUpdate',
        },
      });
      await assertForbiddenRequest(res, UPDATE_USER_TASK_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Complete user task - forbidden without UPDATE_USER_TASK permission', async ({
    request,
  }) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );

    await expect(async () => {
      const res = await request.post(
        buildUrl(`/user-tasks/${userTaskKey}/completion`),
        {
          headers: jsonHeaders(userWithoutUserTaskPermissionsToken),
          data: {},
        },
      );
      await assertForbiddenRequest(res, UPDATE_USER_TASK_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });
});
