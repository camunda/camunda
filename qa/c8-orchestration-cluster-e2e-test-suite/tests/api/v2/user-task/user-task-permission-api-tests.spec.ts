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
  assertStatusCode,
  buildUrl,
  encode,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {cleanupUsers} from '../../../../utils/usersCleanup';
import {
  cleanupAuthorizations,
  createGroup,
  createUser,
  createUserWithHeaders,
  findUserTask,
  grantUserResourceAuthorization,
  grantUserTaskAuthorization,
  lookupUserTask,
  setupProcessInstanceForTests,
  startTwoUserTaskInstance,
} from '@requestHelpers';
import {cleanupGroups} from '../../../../utils/groupsCleanup';
import {cancelProcessInstance} from '../../../../utils/zeebeClient';

const READ_FORBIDDEN_DETAIL =
  "Unauthorized to perform any of the operations: 'READ_USER_TASK' on 'PROCESS_DEFINITION' or 'READ' on 'USER_TASK'";
const LOOKUP_FORBIDDEN_DETAIL =
  "Insufficient permissions to perform operation 'READ_USER_TASK' on resource 'PROCESS_DEFINITION'";
const UPDATE_USER_TASK_FORBIDDEN_DETAIL =
  "Insufficient permissions to perform operation 'UPDATE_USER_TASK' on resource 'PROCESS_DEFINITION'";

test.describe.serial('User Task Permission API - Forbidden', () => {
  const {state, beforeAll, beforeEach, afterEach} =
    setupProcessInstanceForTests('user_task_api_test_process');

  let userWithoutPermissions: {
    username: string;
    name: string;
    email: string;
    password: string;
  };
  let userWithoutPermissionsToken: string;

  test.beforeAll(async ({request}) => {
    await beforeAll();

    userWithoutPermissions = await createUser(request);
    await grantUserResourceAuthorization(request, userWithoutPermissions);
    userWithoutPermissionsToken = encode(
      `${userWithoutPermissions.username}:${userWithoutPermissions.password}`,
    );
  });

  test.beforeEach(beforeEach);

  test.afterEach(afterEach);

  test.afterAll(async ({request}) => {
    await cleanupUsers(request, [userWithoutPermissions.username]);
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
      const res = await request.get(
        buildUrl('/user-tasks/{userTaskKey}', {userTaskKey}),
        {
          headers: jsonHeaders(userWithoutPermissionsToken),
        },
      );
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
        buildUrl('/user-tasks/{userTaskKey}/assignment', {userTaskKey}),
        {
          headers: jsonHeaders(userWithoutPermissionsToken),
          data: {assignee: userWithoutPermissions.username},
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
        buildUrl('/user-tasks/{userTaskKey}/assignee', {userTaskKey}),
        {
          headers: jsonHeaders(userWithoutPermissionsToken),
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
      const res = await request.patch(
        buildUrl('/user-tasks/{userTaskKey}', {userTaskKey}),
        {
          headers: jsonHeaders(userWithoutPermissionsToken),
          data: {
            changeset: {priority: 80},
            action: 'customUpdate',
          },
        },
      );
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
        buildUrl('/user-tasks/{userTaskKey}/completion', {userTaskKey}),
        {
          headers: jsonHeaders(userWithoutPermissionsToken),
          data: {},
        },
      );
      await assertForbiddenRequest(res, UPDATE_USER_TASK_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });
});

test.describe.serial('User Task Permission API - Lookup', () => {
  const instancesToCancel: string[] = [];
  const authorizationKeys: string[] = [];
  const usernames: string[] = [];
  const groupIds: string[] = [];

  test.afterAll(async ({request}) => {
    await Promise.allSettled(
      instancesToCancel.map((key) => cancelProcessInstance(key)),
    );
    await cleanupAuthorizations(request, authorizationKeys);
    await cleanupGroups(request, groupIds);
    await cleanupUsers(request, usernames);
  });

  test('Lookup user task - USER_TASK READ returns the variables that variable search does not', async ({
    request,
  }) => {
    const {processInstanceKey, userTaskKey} = await startTwoUserTaskInstance(
      request,
      instancesToCancel,
      {recommendation: 'pending'},
    );
    const reader = await createUserWithHeaders(request);
    usernames.push(reader.username);
    authorizationKeys.push(
      await grantUserTaskAuthorization(request, reader.username, {
        resourceId: '*',
      }),
    );

    await expect(async () => {
      const res = await lookupUserTask(
        request,
        userTaskKey,
        {},
        reader.headers,
      );
      await assertStatusCode(res, 200);
      expect((await res.json()).variables).toEqual({
        recommendation: 'pending',
      });
    }).toPass(defaultAssertionOptions);
    const search = await request.post(buildUrl('/variables/search'), {
      headers: reader.headers,
      data: {filter: {processInstanceKey}},
    });
    await assertStatusCode(search, 200);
    expect((await search.json()).items).toEqual([]);
  });

  test('Lookup user task - a READ grant on one task key covers only that task', async ({
    request,
  }) => {
    const {userTaskKey} = await startTwoUserTaskInstance(
      request,
      instancesToCancel,
    );
    const {userTaskKey: otherTaskKey} = await startTwoUserTaskInstance(
      request,
      instancesToCancel,
    );
    const reader = await createUserWithHeaders(request);
    usernames.push(reader.username);
    authorizationKeys.push(
      await grantUserTaskAuthorization(request, reader.username, {
        resourceId: userTaskKey,
      }),
    );

    await expect(async () => {
      await assertStatusCode(
        await lookupUserTask(request, userTaskKey, {}, reader.headers),
        200,
      );
    }).toPass(defaultAssertionOptions);
    await assertForbiddenRequest(
      await lookupUserTask(request, otherTaskKey, {}, reader.headers),
      LOOKUP_FORBIDDEN_DETAIL,
    );
  });

  test('Lookup user task - a candidateGroups grant covers only tasks offered to the group', async ({
    request,
  }) => {
    const {userTaskKey} = await startTwoUserTaskInstance(
      request,
      instancesToCancel,
    );
    const {userTaskKey: otherTaskKey} = await startTwoUserTaskInstance(
      request,
      instancesToCancel,
    );
    const {groupId} = await createGroup(request);
    groupIds.push(groupId);
    const member = await createUserWithHeaders(request);
    usernames.push(member.username);
    await assertStatusCode(
      await request.put(
        buildUrl('/groups/{groupId}/users/{username}', {
          groupId,
          username: member.username,
        }),
        {headers: jsonHeaders()},
      ),
      204,
    );
    await assertStatusCode(
      await request.patch(
        buildUrl('/user-tasks/{userTaskKey}', {userTaskKey}),
        {
          headers: jsonHeaders(),
          data: {changeset: {candidateGroups: [groupId]}},
        },
      ),
      204,
    );
    authorizationKeys.push(
      await grantUserTaskAuthorization(request, member.username, {
        resourcePropertyName: 'candidateGroups',
      }),
    );

    await expect(async () => {
      await assertStatusCode(
        await lookupUserTask(request, userTaskKey, {}, member.headers),
        200,
      );
    }).toPass(defaultAssertionOptions);
    await assertForbiddenRequest(
      await lookupUserTask(request, otherTaskKey, {}, member.headers),
      LOOKUP_FORBIDDEN_DETAIL,
    );
  });

  test('Lookup user task - forbidden with USER_TASK UPDATE only', async ({
    request,
  }) => {
    const {userTaskKey} = await startTwoUserTaskInstance(
      request,
      instancesToCancel,
    );
    const updater = await createUserWithHeaders(request);
    usernames.push(updater.username);
    authorizationKeys.push(
      await grantUserTaskAuthorization(
        request,
        updater.username,
        {resourceId: '*'},
        ['UPDATE'],
      ),
    );

    await expect(async () => {
      await assertForbiddenRequest(
        await lookupUserTask(request, userTaskKey, {}, updater.headers),
        LOOKUP_FORBIDDEN_DETAIL,
      );
    }).toPass(defaultAssertionOptions);
  });
});
