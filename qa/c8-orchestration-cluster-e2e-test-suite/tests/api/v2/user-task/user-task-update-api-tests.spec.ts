import {test, expect} from '@playwright/test';
import {
  buildUrl,
  jsonHeaders,
  assertStatusCode,
  findUserTask,
  setupProcessInstanceForTests,
  defaultAssertionOptions,
} from '../../../utils/http';

/*
 * These tests verify the User Task Update API (/v2/user-tasks/:key PATCH).
 * They test:
 *  - Updating a user task (due date, follow-up date, candidate users/groups, priority)
 *  - Handling of bad requests (empty changeset, invalid priority values)
 *  - Confirming the response fields are correct after an update
 *
 * Each test creates a process instance with a user task, looks up that user task,
 * then patches it with new data, verifying both the status code and the returned fields.
 *
 * All tests in this describe block run in parallel within the same describe block
 * and share a single process instance.
 */

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

  test('Update user task - success - with due date and follow-up date', async ({
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
          followUpDate: generateFutureDates().followUpDate,
        },
      },
    });
    await assertStatusCode(res, 204);
  });

  test('Update user task - success - with candidate users', async ({
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
          candidateUsers: ['userA', 'userB'],
        },
      },
    });
    await assertStatusCode(res, 204);
  });

  test('Update user task - success - with candidate groups', async ({
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
          candidateGroups: ['groupA', 'groupB'],
        },
      },
    });
    await assertStatusCode(res, 204);
  });

  test('Update user task - success - with priority', async ({request}) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );

    const res = await request.patch(buildUrl(`/user-tasks/${userTaskKey}`), {
      headers: jsonHeaders(),
      data: {
        changeset: {
          priority: 50,
        },
      },
    });
    await assertStatusCode(res, 204);
  });

  test('Update user task - success - with all fields', async ({request}) => {
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
          candidateUsers: ['userA'],
          candidateGroups: ['groupA'],
          priority: 75,
        },
      },
    });
    await assertStatusCode(res, 204);
  });

  test('Update user task - success - with minimal changeset (only dueDate)', async ({
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

  test('Update user task - success - with zero priority', async ({
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
          priority: 0,
        },
      },
    });
    await assertStatusCode(res, 204);
  });

  test('Update user task - success - with maximum priority', async ({
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
          priority: 100,
        },
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
    await assertStatusCode(res, 400);
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
    await assertStatusCode(res, 400);
  });

  test('Update user task - bad request - invalid priority non-integer', async ({
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
          priority: 50.5,
        },
      },
    });
    await assertStatusCode(res, 400);
  });

  test('Update user task - bad request - empty changeset', async ({
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
        changeset: {},
      },
    });
    await assertStatusCode(res, 400);
  });

  test('Update user task - not found', async ({request}) => {
    const res = await request.patch(
      buildUrl('/user-tasks/9999999999999999'),
      {
        headers: jsonHeaders(),
        data: {
          changeset: {
            dueDate: generateFutureDates().dueDate,
          },
        },
      },
    );
    await assertStatusCode(res, 404);
  });

  test('Update user task - success - clear due date', async ({request}) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );

    const res = await request.patch(buildUrl(`/user-tasks/${userTaskKey}`), {
      headers: jsonHeaders(),
      data: {
        changeset: {
          dueDate: null,
          priority: 42,
        },
      },
    });
    await assertStatusCode(res, 204);
  });
});
