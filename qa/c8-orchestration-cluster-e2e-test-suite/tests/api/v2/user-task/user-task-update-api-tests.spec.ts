/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  completeUserTask,
  findUserTask,
  setupProcessInstanceForTests,
} from '@requestHelpers';
import {expect, test} from '@playwright/test';
import {
  assertInvalidState,
  assertNotFoundRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
  assertInvalidArgument,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';

const generateFutureDates = () => {
  const now = new Date();
  return {
    dueDate: new Date(now.getFullYear() + 1, 11, 31, 23, 59, 59, 0).toISOString(),
    followUpDate: new Date(now.getFullYear() + 1, 5, 15, 10, 0, 0, 0).toISOString(),
  };
};
  
/* eslint-disable playwright/expect-expect */
test.describe.parallel('Update User Task Tests', () => {
  const {state, beforeAll, beforeEach, afterEach} =
    setupProcessInstanceForTests('user_task_update_api_process');

  test.beforeAll(beforeAll);
  test.beforeEach(beforeEach);
  test.afterEach(afterEach);

  test('Update user task - success - update all fields', async ({request}) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const res = await request.patch(buildUrl(`/user-tasks/${userTaskKey}`), {
      headers: jsonHeaders(),
      data: {
        changeset: {
          dueDate: generateFutureDates().dueDate,
          followUpDate: generateFutureDates().followUpDate,
          candidateUsers: ['user1', 'user2'],
          candidateGroups: ['group1', 'group2'],
          priority: 80,
        },
        action: 'customUpdate',
      },
    });
    await assertStatusCode(res, 204);
  });

  test('Update user task - success - update only dueDate', async ({
    request,
  }) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const res = await request.patch(buildUrl(`/user-tasks/${userTaskKey}`), {
      headers: jsonHeaders(),
      data: {
        changeset: {
          dueDate: generateFutureDates().dueDate,
        },
      },
    });
    await assertStatusCode(res, 204);
  });

  test('Update user task - success - update only followUpDate', async ({
    request,
  }) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const res = await request.patch(buildUrl(`/user-tasks/${userTaskKey}`), {
      headers: jsonHeaders(),
      data: {
        changeset: {
          followUpDate: generateFutureDates().followUpDate,
        },
      },
    });
    await assertStatusCode(res, 204);
  });

  test('Update user task - success - update only candidateUsers', async ({
    request,
  }) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const res = await request.patch(buildUrl(`/user-tasks/${userTaskKey}`), {
      headers: jsonHeaders(),
      data: {
        changeset: {
          candidateUsers: ['user1', 'user2'],
        },
      },
    });
    await assertStatusCode(res, 204);
  });

  test('Update user task - success - update only candidateGroups', async ({
    request,
  }) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const res = await request.patch(buildUrl(`/user-tasks/${userTaskKey}`), {
      headers: jsonHeaders(),
      data: {
        changeset: {
          candidateGroups: ['group1'],
        },
      },
    });
    await assertStatusCode(res, 204);
  });

  test('Update user task - success - update only priority', async ({
    request,
  }) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const res = await request.patch(buildUrl(`/user-tasks/${userTaskKey}`), {
      headers: jsonHeaders(),
      data: {
        changeset: {
          priority: 10,
        },
      },
    });
    await assertStatusCode(res, 204);
  });

  test('Update user task - success - reset dueDate and followUpDate', async ({
    request,
  }) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const res = await request.patch(buildUrl(`/user-tasks/${userTaskKey}`), {
      headers: jsonHeaders(),
      data: {
        changeset: {
          dueDate: '',
          followUpDate: '',
        },
      },
    });
    await assertStatusCode(res, 204);
  });

  test('Update user task - success - reset candidateUsers and candidateGroups', async ({
    request,
  }) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const res = await request.patch(buildUrl(`/user-tasks/${userTaskKey}`), {
      headers: jsonHeaders(),
      data: {
        changeset: {
          candidateUsers: [],
          candidateGroups: [],
        },
      },
    });
    await assertStatusCode(res, 204);
  });

  test('Update user task - bad request - empty changeset', async ({request}) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const res = await request.patch(buildUrl(`/user-tasks/${userTaskKey}`), {
      headers: jsonHeaders(),
      data: {
        changeset: {},
      },
    });
    await assertInvalidArgument(res, 400, 'changeset');
  });

  test('Update user task - success - with custom action', async ({
    request,
  }) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const res = await request.patch(buildUrl(`/user-tasks/${userTaskKey}`), {
      headers: jsonHeaders(),
      data: {
        changeset: {
          priority: 75,
        },
        action: 'myCustomAction',
      },
    });
    await assertStatusCode(res, 204);
  });

  test('Update user task - success - verify updated fields', async ({
    request,
  }) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );

    const dueDate = generateFutureDates().dueDate;

    await test.step('Update the user task', async () => {
      const updateRes = await request.patch(
        buildUrl(`/user-tasks/${userTaskKey}`),
        {
          headers: jsonHeaders(),
          data: {
            changeset: {
              dueDate: dueDate,
              candidateUsers: ['verifyUser'],
              priority: 90,
            },
          },
        },
      );
      await assertStatusCode(updateRes, 204);
    });

    await test.step('Verify the updated fields via GET', async () => {
      await expect(async () => {
        const getRes = await request.get(
          buildUrl(`/user-tasks/${userTaskKey}`),
          {
            headers: jsonHeaders(),
          },
        );
        await assertStatusCode(getRes, 200);
        const json = await getRes.json();
        expect(new Date(json.dueDate).getTime()).toBe(new Date(dueDate).getTime());
        expect(json.candidateUsers).toEqual(['verifyUser']);
        expect(json.priority).toBe(90);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Update user task - bad request - invalid priority above max', async ({
    request,
  }) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const res = await request.patch(buildUrl(`/user-tasks/${userTaskKey}`), {
      headers: jsonHeaders(),
      data: {
        changeset: {
          priority: 101,
        },
      },
    });
    await assertInvalidArgument(res, 400, 'priority');
  });

  test('Update user task - bad request - invalid priority below min', async ({
    request,
  }) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const res = await request.patch(buildUrl(`/user-tasks/${userTaskKey}`), {
      headers: jsonHeaders(),
      data: {
        changeset: {
          priority: -1,
        },
      },
    });
    await assertInvalidArgument(res, 400, 'priority');
  });

  test('Update user task - unauthorized', async ({request}) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const res = await request.patch(buildUrl(`/user-tasks/${userTaskKey}`), {
      // No auth headers
      headers: {
        'Content-Type': 'application/json',
      },
      data: {
        changeset: {
          priority: 50,
        },
      },
    });
    await assertUnauthorizedRequest(res);
  });

  test('Update user task - not found', async ({request}) => {
    const unknownUserTaskKey = '2251799813694876';
    const res = await request.patch(
      buildUrl(`/user-tasks/${unknownUserTaskKey}`),
      {
        headers: jsonHeaders(),
        data: {
          changeset: {
            priority: 50,
          },
        },
      },
    );
    await assertNotFoundRequest(
      res,
      `Command 'UPDATE' rejected with code 'NOT_FOUND': Expected to update user task with key '${unknownUserTaskKey}', but no such user task was found`,
    );
  });

  test('Update user task - conflict - task already completed', async ({
    request,
  }) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );

    await test.step('Complete the user task first', async () => {
      await completeUserTask(request, userTaskKey);
    });

    await test.step(
      'Attempt to update the completed user task',
      async () => {
        await expect(async () => {
          const updateRes = await request.patch(
            buildUrl(`/user-tasks/${userTaskKey}`),
            {
              headers: jsonHeaders(),
              data: {
                changeset: {
                  priority: 50,
                },
              },
            },
          );
          await assertInvalidState(updateRes);
        }).toPass(defaultAssertionOptions);
      },
    );

    state['processCompleted'] = true;
  });
});
