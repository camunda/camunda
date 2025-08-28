/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '@playwright/test';
import {
  jsonHeaders,
  buildUrl,
  assertRequiredFields,
  assertEqualsForKeys,
  paginatedResponseFields,
  assertUnauthorizedRequest,
  assertNotFoundRequest,
  assertBadRequest,
} from '../../../../utils/http';
import {
  CREATE_NEW_GROUP,
  groupRequiredFields,
} from '../../../../utils/beans/requestBeans';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {createGroupAndStoreResponseFields} from '../../../../utils/requestHelpers';

test.describe.parallel('Groups API Tests', () => {
  const state: Record<string, unknown> = {};

  test.beforeAll(async ({request}) => {
    await createGroupAndStoreResponseFields(request, 3, state);
  });

  test('Create Group', async ({request}) => {
    const requestBody = CREATE_NEW_GROUP();

    const res = await request.post(buildUrl('/groups'), {
      headers: jsonHeaders(),
      data: requestBody,
    });

    expect(res.status()).toBe(201);
    const json = await res.json();
    assertRequiredFields(json, groupRequiredFields);
    assertEqualsForKeys(json, requestBody, groupRequiredFields);
  });

  test('Create Group Unauthorized', async ({request}) => {
    const requestBody = CREATE_NEW_GROUP();
    const res = await request.post(buildUrl('/groups', {}), {
      headers: {},
      data: requestBody,
    });
    await assertUnauthorizedRequest(res);
  });

  test('Create Group Bad Request', async ({request}) => {
    const invalidRequest = {
      name: 'x',
    };

    const res = await request.post(buildUrl('/groups'), {
      headers: jsonHeaders(),
      data: invalidRequest,
    });
    await assertBadRequest(res, `No groupId provided`, 'INVALID_ARGUMENT');
  });

  test('Search Groups By Name', async ({request}) => {
    const groupName = state['name1'];
    const body = {
      filter: {
        name: groupName,
      },
    };
    const expectedBody = {
      groupId: state['groupId1'],
      name: state['name1'],
      description: state['description1'],
    };

    await expect(async () => {
      const res = await request.post(buildUrl('/groups/search'), {
        headers: jsonHeaders(),
        data: body,
      });
      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, paginatedResponseFields);
      expect(json.page.totalItems).toBe(1);
      assertRequiredFields(json.items[0], groupRequiredFields);
      assertEqualsForKeys(json.items[0], expectedBody, groupRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Groups By Id', async ({request}) => {
    const groupId = state['groupId1'];
    const body = {
      filter: {
        groupId: groupId,
      },
    };
    const expectedBody = {
      groupId: state['groupId1'],
      name: state['name1'],
      description: state['description1'],
    };

    const res = await request.post(buildUrl('/groups/search'), {
      headers: jsonHeaders(),
      data: body,
    });

    expect(res.status()).toBe(200);
    const json = await res.json();
    assertRequiredFields(json, paginatedResponseFields);
    expect(json.page.totalItems).toBe(1);
    assertRequiredFields(json.items[0], groupRequiredFields);
    assertEqualsForKeys(json.items[0], expectedBody, groupRequiredFields);
  });

  test('Search Groups By Invalid Id', async ({request}) => {
    const body = {
      filter: {
        groupId: 'invalidGroupId',
      },
    };

    const res = await request.post(buildUrl('/groups/search'), {
      headers: jsonHeaders(),
      data: body,
    });

    expect(res.status()).toBe(200);
    const json = await res.json();
    assertRequiredFields(json, paginatedResponseFields);
    expect(json.page.totalItems).toBe(0);
    expect(json.items.length).toBe(0);
  });

  test('Search Groups Unauthorized', async ({request}) => {
    const groupName = state['name1'];
    const body = {
      filter: {
        name: groupName,
      },
    };

    const res = await request.post(buildUrl('/groups/search'), {
      headers: {},
      data: body,
    });
    await assertUnauthorizedRequest(res);
  });

  test('Get Group', async ({request}) => {
    const param: Record<string, string> = {
      groupId: state['groupId1'] as string,
    };
    const expectedBody: Record<string, string> = {
      groupId: state['groupId1'] as string,
      name: state['name1'] as string,
      description: state['description1'] as string,
    };

    await expect(async () => {
      const res = await request.get(buildUrl('/groups/{groupId}', param), {
        headers: jsonHeaders(),
      });

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, groupRequiredFields);
      assertEqualsForKeys(json, expectedBody, groupRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Get Group Not Found', async ({request}) => {
    const param: Record<string, string> = {
      groupId: 'invalidGroupName',
    };
    const res = await request.get(buildUrl('/groups/{groupId}', param), {
      headers: jsonHeaders(),
    });
    await assertNotFoundRequest(
      res,
      `Group with id '${param['groupId']}' not found`,
    );
  });

  test('Get Group Unauthorized', async ({request}) => {
    const param: Record<string, string> = {
      groupId: state['groupId1'] as string,
    };
    const res = await request.get(buildUrl('/groups/{groupId}', param), {
      headers: {},
    });
    await assertUnauthorizedRequest(res);
  });

  test('Update Group', async ({request}) => {
    const p = {groupId: state['groupId2'] as string};
    const body = {
      name: `${state['name2']}-updated`,
      description: `${state['description2']}-updated`,
    };

    await expect(async () => {
      const res = await request.put(buildUrl('/groups/{groupId}', p), {
        headers: jsonHeaders(),
        data: body,
      });

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, ['groupId', 'name']);
      assertEqualsForKeys(json, {name: body.name}, ['name']);
      assertEqualsForKeys(json, {description: body.description}, [
        'description',
      ]);
      state['name2'] = json.name;
      state['description2'] = json.description;
    }).toPass(defaultAssertionOptions);
  });

  test('Update Group Unauthorized', async ({request}) => {
    const p = {groupId: state['groupId2'] as string};
    const body = {
      name: `${state['name2']}-updated`,
      description: `${state['description2']}-updated`,
    };
    const res = await request.put(buildUrl('/groups/{groupId}', p), {
      headers: {},
      data: body,
    });
    await assertUnauthorizedRequest(res);
  });

  test('Update Group Bad Request', async ({request}) => {
    const p = {groupId: state['groupId2'] as string};
    const invalid = {description: 'x'};
    const res = await request.put(buildUrl('/groups/{groupId}', p), {
      headers: jsonHeaders(),
      data: invalid,
    });
    await assertBadRequest(res, `No name provided`, 'INVALID_ARGUMENT');
  });

  test('Update Group Not Found', async ({request}) => {
    const p = {groupId: 'invalidGroupId'};
    const body = {
      name: `${state['name2']}-updated`,
      description: `${state['description2']}-updated`,
    };
    const res = await request.put(buildUrl('/groups/{groupId}', p), {
      headers: jsonHeaders(),
      data: body,
    });
    await assertNotFoundRequest(
      res,
      `Command 'UPDATE' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Delete Group', async ({request}) => {
    await test.step('Delete Group 204', async () => {
      const p = {groupId: state['groupId3'] as string};

      await expect(async () => {
        const res = await request.delete(buildUrl('/groups/{groupId}', p), {
          headers: jsonHeaders(),
        });
        expect(res.status()).toBe(204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Get Group After Deletion', async () => {
      const p = {groupId: state['groupId3'] as string};

      await expect(async () => {
        const after = await request.get(buildUrl('/groups/{groupId}', p), {
          headers: jsonHeaders(),
        });
        await assertNotFoundRequest(
          after,
          `Group with id '${state['groupId3']}' not found`,
        );
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Delete Group Unauthorized', async ({request}) => {
    const p = {groupId: state['groupId3'] as string};
    const res = await request.delete(buildUrl('/groups/{groupId}', p), {
      headers: {},
    });
    await assertUnauthorizedRequest(res);
  });

  test('Delete Group Not Found', async ({request}) => {
    const p = {groupId: 'invalidGroupId'};
    const res = await request.delete(buildUrl('/groups/{groupId}', p), {
      headers: jsonHeaders(),
    });
    await assertNotFoundRequest(
      res,
      `Command 'DELETE' rejected with code 'NOT_FOUND'`,
    );
  });
});
