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
  assertNotFoundRequest,
  assertEqualsForKeys,
  assertBadRequest,
  assertConflictRequest,
  assertPaginatedRequest,
} from '../../../utils/http';
import {
  defaultAssertionOptions,
  generateUniqueId,
} from '../../../utils/constants';
import {
  assertUserInResponse,
  createUsersAndStoreResponseFields,
} from '@requestHelpers';
import {
  CREATE_NEW_USER,
  UPDATE_USER,
  userRequiredFields,
} from '../../../utils/beans/requestBeans';
import {cleanupUsers} from '../../../utils/usersCleanup';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Users API Tests', () => {
  const state: Record<string, unknown> = {};
  const createdUserIds: string[] = [];

  test.beforeAll(async ({request}) => {
    await createUsersAndStoreResponseFields(request, 8, state);

    createdUserIds.push(
      ...(Object.values(state).filter(
        (value) => typeof value === 'string' && value.startsWith('user'),
      ) as string[]),
    );
  });

  test.afterAll(async ({request}) => {
    await cleanupUsers(request, createdUserIds);
  });

  test('Create User', async ({request}) => {
    await expect(async () => {
      const user = CREATE_NEW_USER();
      const res = await request.post(buildUrl('/users'), {
        headers: jsonHeaders(),
        data: user,
      });

      expect(res.status()).toBe(201);
      const json = await res.json();
      assertRequiredFields(json, userRequiredFields);
      assertEqualsForKeys(json, user, userRequiredFields);
      if (json && json.username) {
        createdUserIds.push(json.username);
      }
    }).toPass(defaultAssertionOptions);
  });

  test('Create User Existing Email Success', async ({request}) => {
    const user = CREATE_NEW_USER();
    user['email'] = state['email1'] as string;
    const expectedBodyForSecondUser = {
      name: user.name,
      username: user.username,
      email: user['email'],
    };
    const expectedBodyForFirstUser = {
      name: state['name1'],
      username: state['username1'],
      email: state['email1'],
    };

    await test.step('Create User With Existing Email', async () => {
      await expect(async () => {
        const res = await request.post(buildUrl('/users'), {
          headers: jsonHeaders(),
          data: user,
        });

        expect(res.status()).toBe(201);
        const json = await res.json();
        assertRequiredFields(json, userRequiredFields);
        assertEqualsForKeys(
          json,
          expectedBodyForSecondUser,
          userRequiredFields,
        );
        if (json && json.username) {
          createdUserIds.push(json.username);
        }
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Search Users By Email', async () => {
      const body = {
        filter: {
          email: user.email,
        },
      };

      await expect(async () => {
        const res = await request.post(buildUrl('/users/search'), {
          headers: jsonHeaders(),
          data: body,
        });

        await assertPaginatedRequest(res, {
          itemsLengthEqualTo: 2,
          totalItemsEqualTo: 2,
        });
        const json = await res.json();
        assertUserInResponse(
          json,
          expectedBodyForFirstUser,
          expectedBodyForFirstUser.username as string,
        );
        assertUserInResponse(
          json,
          expectedBodyForSecondUser,
          expectedBodyForSecondUser.username as string,
        );
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Create User Unauthorized', async ({request}) => {
    const res = await request.post(buildUrl('/users'), {
      headers: {},
      data: CREATE_NEW_USER(),
    });
    await assertUnauthorizedRequest(res);
  });

  test('Create User Missing Username Invalid Body 400', async ({request}) => {
    const body = {
      email: 'onlyemail@example.com',
      password: 'pass',
      name: 'user',
    };
    const res = await request.post(buildUrl('/users'), {
      headers: jsonHeaders(),
      data: body,
    });
    await assertBadRequest(res, 'No username provided', 'INVALID_ARGUMENT');
  });

  test('Create User Missing Password Invalid Body 400', async ({request}) => {
    const body = {
      email: 'onlyemail@example.com',
      username: 'username',
      name: 'user',
    };
    const res = await request.post(buildUrl('/users'), {
      headers: jsonHeaders(),
      data: body,
    });
    await assertBadRequest(res, 'No password provided', 'INVALID_ARGUMENT');
  });

  test('Create User Missing Email Success', async ({request}) => {
    const body = {
      username: `username-${generateUniqueId()}`,
      name: 'user',
      password: 'password',
    };
    const expectedBody = {
      username: body.username,
      name: body.name,
      email: '',
    };

    const res = await request.post(buildUrl('/users'), {
      headers: jsonHeaders(),
      data: body,
    });

    expect(res.status()).toBe(201);
    const json = await res.json();
    assertRequiredFields(json, userRequiredFields);
    assertEqualsForKeys(json, expectedBody, userRequiredFields);
    createdUserIds.push(json.username);
  });

  test('Create User Missing Name Success', async ({request}) => {
    const body = {
      username: `username-${generateUniqueId()}`,
      email: 'user@example.com',
      password: 'password',
    };
    const expectedBody = {
      username: body.username,
      email: body.email,
      name: '',
    };

    const res = await request.post(buildUrl('/users'), {
      headers: jsonHeaders(),
      data: body,
    });

    expect(res.status()).toBe(201);
    const json = await res.json();
    assertRequiredFields(json, userRequiredFields);
    assertEqualsForKeys(json, expectedBody, userRequiredFields);
    createdUserIds.push(json.username);
  });

  test('Create User Empty Name Success', async ({request}) => {
    const body = {
      username: `username-${generateUniqueId()}`,
      email: 'user@example.com',
      password: 'password',
      name: '',
    };
    const expectedBody = {
      username: body.username,
      email: body.email,
      name: body.name,
    };

    const res = await request.post(buildUrl('/users'), {
      headers: jsonHeaders(),
      data: body,
    });

    expect(res.status()).toBe(201);
    const json = await res.json();
    assertRequiredFields(json, userRequiredFields);
    assertEqualsForKeys(json, expectedBody, userRequiredFields);
    createdUserIds.push(json.username);
  });

  test('Create User Empty Username Invalid Body 400', async ({request}) => {
    const body = {username: '', email: 'empty@example.com', password: 'pass'};
    const res = await request.post(buildUrl('/users'), {
      headers: jsonHeaders(),
      data: body,
    });
    await assertBadRequest(res, 'No username provided', 'INVALID_ARGUMENT');
  });

  test('Create User Invalid Email Invalid Body 400', async ({request}) => {
    const body = CREATE_NEW_USER();
    body['email'] = 'invalid-email';

    const res = await request.post(buildUrl('/users'), {
      headers: jsonHeaders(),
      data: body,
    });
    await assertBadRequest(
      res,
      `The provided email '${body['email']}' is not valid.`,
      'INVALID_ARGUMENT',
    );
  });

  test('Create User Conflict', async ({request}) => {
    await expect(async () => {
      const user = CREATE_NEW_USER();
      user['username'] = state['username1'] as string;

      const res = await request.post(buildUrl('/users'), {
        headers: jsonHeaders(),
        data: user,
      });

      await assertConflictRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Get User', async ({request}) => {
    const p = {username: state['username1'] as string};
    const expectedBody = {
      name: state['name1'],
      username: state['username1'],
      email: state['email1'],
    };

    await expect(async () => {
      const res = await request.get(buildUrl('/users/{username}', p), {
        headers: jsonHeaders(),
      });
      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, userRequiredFields);
      assertEqualsForKeys(json, expectedBody, userRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Get User Unauthorized', async ({request}) => {
    const p = {username: state['username2'] as string};
    const res = await request.get(buildUrl('/users/{username}', p), {
      headers: {},
    });
    await assertUnauthorizedRequest(res);
  });

  test('Get User Not Found', async ({request}) => {
    const p = {username: 'does-not-exist'};
    const res = await request.get(buildUrl('/users/{username}', p), {
      headers: jsonHeaders(),
    });
    await assertNotFoundRequest(
      res,
      `User with username '${p.username}' not found`,
    );
  });

  test('Update User', async ({request}) => {
    const p = {username: state['username2'] as string};
    const requestBody = UPDATE_USER();
    const expectedBody = {
      ...p,
      ...requestBody,
    };

    await expect(async () => {
      const res = await request.put(buildUrl('/users/{username}', p), {
        headers: jsonHeaders(),
        data: requestBody,
      });
      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, userRequiredFields);
      assertEqualsForKeys(json, expectedBody, userRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Update User Missing Name Success', async ({request}) => {
    const p = {username: state['username6'] as string};
    const requestBody = {
      email: `updated-${generateUniqueId()}-email@example.com`,
    };
    const expectedBody = {
      ...p,
      ...requestBody,
      name: '',
    };

    await expect(async () => {
      const res = await request.put(buildUrl('/users/{username}', p), {
        headers: jsonHeaders(),
        data: requestBody,
      });
      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, userRequiredFields);
      assertEqualsForKeys(json, expectedBody, userRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Update User Missing Email Success', async ({request}) => {
    const p = {username: state['username7'] as string};
    const requestBody = {
      name: `updated-${generateUniqueId()}-name`,
    };
    const expectedBody = {
      ...p,
      ...requestBody,
      email: '',
    };

    await expect(async () => {
      const res = await request.put(buildUrl('/users/{username}', p), {
        headers: jsonHeaders(),
        data: requestBody,
      });
      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, userRequiredFields);
      assertEqualsForKeys(json, expectedBody, userRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Update User With Password Success', async ({request}) => {
    const p = {username: state['username8'] as string};
    const body = {
      name: 'userWithMissingPassword',
      email: 'userWithMissingPassword@example.com',
      password: 'newPassword123',
    };
    const expectedResponseBody = {
      ...p,
      name: body.name,
      email: body.email,
    };

    await expect(async () => {
      const res = await request.put(buildUrl('/users/{username}', p), {
        headers: jsonHeaders(),
        data: body,
      });

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, userRequiredFields);
      assertEqualsForKeys(json, expectedResponseBody, userRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Update User Unauthorized', async ({request}) => {
    const p = {username: state['username2'] as string};
    const requestBody = UPDATE_USER();

    const res = await request.put(buildUrl('/users/{username}', p), {
      headers: {},
      data: requestBody,
    });
    await assertUnauthorizedRequest(res);
  });

  test('Update User Not Found', async ({request}) => {
    const p = {username: 'invalid-user'};
    const res = await request.put(buildUrl('/users/{username}', p), {
      headers: jsonHeaders(),
      data: UPDATE_USER(),
    });
    await assertNotFoundRequest(
      res,
      "Command 'UPDATE' rejected with code 'NOT_FOUND'",
    );
  });

  test('Delete User', async ({request}) => {
    const p = {username: state['username3'] as string};
    await test.step('Delete User 204', async () => {
      await expect(async () => {
        const res = await request.delete(buildUrl('/users/{username}', p), {
          headers: jsonHeaders(),
        });
        expect(res.status()).toBe(204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Get User After Deletion', async () => {
      await expect(async () => {
        const after = await request.get(buildUrl('/users/{username}', p), {
          headers: jsonHeaders(),
        });
        await assertNotFoundRequest(
          after,
          `User with username '${p.username}' not found`,
        );
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Delete User Unauthorized', async ({request}) => {
    const p = {username: state['username3'] as string};
    const res = await request.delete(buildUrl('/users/{username}', p), {
      headers: {},
    });
    await assertUnauthorizedRequest(res);
  });

  test('Delete User Not Found', async ({request}) => {
    const p = {username: 'invalid-user'};
    const res = await request.delete(buildUrl('/users/{username}', p), {
      headers: jsonHeaders(),
    });
    await assertNotFoundRequest(
      res,
      "Command 'DELETE' rejected with code 'NOT_FOUND'",
    );
  });

  test('Search Users', async ({request}) => {
    const user1ExpectedBody = {
      username: state['username1'],
      name: state['name1'],
      email: state['email1'],
    };
    const user2ExpectedBody = {
      username: state['username5'],
      name: state['name5'],
      email: state['email5'],
    };

    await expect(async () => {
      const res = await request.post(buildUrl('/users/search'), {
        headers: jsonHeaders(),
        data: {},
      });

      await assertPaginatedRequest(res, {
        itemLengthGreaterThan: 2,
        totalItemGreaterThan: 2,
      });
      const json = await res.json();
      assertUserInResponse(
        json,
        user1ExpectedBody,
        user1ExpectedBody.username as string,
      );
      assertUserInResponse(
        json,
        user2ExpectedBody,
        user2ExpectedBody.username as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Users By Username', async ({request}) => {
    const username = state['username5'];
    const body = {
      filter: {
        username: username,
      },
    };
    const expectedBody = {
      username: state['username5'],
      name: state['name5'],
      email: state['email5'],
    };

    await expect(async () => {
      const res = await request.post(buildUrl('/users/search'), {
        headers: jsonHeaders(),
        data: body,
      });

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      assertUserInResponse(json, expectedBody, expectedBody.username as string);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Users By Name', async ({request}) => {
    const body = {
      filter: {
        name: state['name5'],
      },
    };
    const expectedBody = {
      username: state['username5'],
      name: state['name5'],
      email: state['email5'],
    };

    await expect(async () => {
      const res = await request.post(buildUrl('/users/search'), {
        headers: jsonHeaders(),
        data: body,
      });

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      assertUserInResponse(json, expectedBody, expectedBody.username as string);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Users By Email', async ({request}) => {
    const body = {
      filter: {
        email: state['email5'],
      },
    };
    const expectedBody = {
      username: state['username5'],
      name: state['name5'],
      email: state['email5'],
    };

    await expect(async () => {
      const res = await request.post(buildUrl('/users/search'), {
        headers: jsonHeaders(),
        data: body,
      });

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      assertUserInResponse(json, expectedBody, expectedBody.username as string);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Users By Multiple Fields', async ({request}) => {
    const requestBody = {
      filter: {
        username: state['username1'],
        name: state['name1'],
      },
    };
    const expectedBody = {
      ...requestBody.filter,
      email: state['email1'],
    };

    await expect(async () => {
      const res = await request.post(buildUrl('/users/search'), {
        headers: jsonHeaders(),
        data: requestBody,
      });

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      assertUserInResponse(json, expectedBody, expectedBody.username as string);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Users By Invalid User Name', async ({request}) => {
    const body = {
      filter: {
        username: 'invalidusername',
      },
    };

    const res = await request.post(buildUrl('/users/search'), {
      headers: jsonHeaders(),
      data: body,
    });

    await assertPaginatedRequest(res, {
      itemsLengthEqualTo: 0,
      totalItemsEqualTo: 0,
    });
  });

  test('Search Users Unauthorized', async ({request}) => {
    const body = {
      filter: {
        username: state['username1'],
      },
    };
    const res = await request.post(buildUrl('/users/search'), {
      headers: {},
      data: body,
    });
    await assertUnauthorizedRequest(res);
  });
});
