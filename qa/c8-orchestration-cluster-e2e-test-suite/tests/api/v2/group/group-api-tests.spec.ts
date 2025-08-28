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
  extractAndStoreIds,
  buildUrl,
  assertRequiredFields,
  assertEqualsForKeys,
  paginatedResponseFields,
} from '../../../../utils/http';
import {
  CREATE_NEW_GROUP,
  groupRequiredFields,
} from '../../../../utils/beans/requestBeans';
import {sleep} from '../../../../utils/sleep';

test.describe('Groups API Tests', () => {
  const state: Record<string, unknown> = {};

  test('CRUD', async ({request}) => {
    await test.step('Create Group', async () => {
      const requestBody = CREATE_NEW_GROUP();

      const res = await request.post(buildUrl('/groups'), {
        headers: jsonHeaders(),
        data: requestBody,
      });

      expect(res.status()).toBe(201);
      const json = await res.json();
      assertRequiredFields(json, groupRequiredFields);
      assertEqualsForKeys(json, requestBody, groupRequiredFields);
      await extractAndStoreIds(res, state);
    });

    await test.step('Create Group Unauthorized', async () => {
      const requestBody = CREATE_NEW_GROUP();
      const res = await request.post(buildUrl('/groups', {}), {
        headers: {},
        data: requestBody,
      });
      expect(res.status()).toBe(401);
    });

    await test.step('Create Group Bad Request', async () => {
      const invalidRequest = {
        name: 'x',
      };

      const res = await request.post(buildUrl('/groups'), {
        headers: jsonHeaders(),
        data: invalidRequest,
      });
      expect(res.status()).toBe(400);
    });

    await test.step('Search Groups By Name', async () => {
      await sleep(10000);
      const groupName = state['name'];
      const body = {
        filter: {
          name: groupName,
        },
      };
      const expectedBody = {
        groupId: state['groupId'],
        name: state['name'],
        description: state['description'],
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

    await test.step('Search Groups By Id', async () => {
      const groupId = state['groupId'];
      const body = {
        filter: {
          groupId: groupId,
        },
      };
      const expectedBody = {
        groupId: state['groupId'],
        name: state['name'],
        description: state['description'],
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

    await test.step('Search Groups By Invalid Id', async () => {
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

    await test.step('Search Groups Unauthorized', async () => {
      const groupName = state['name'];
      const body = {
        filter: {
          name: groupName,
        },
      };

      const res = await request.post(buildUrl('/groups/search'), {
        headers: {},
        data: body,
      });
      expect(res.status()).toBe(401);
    });

    await test.step('Get Group', async () => {
      const param: Record<string, string> = {
        groupId: state['groupId'] as string,
      };
      const expectedBody: Record<string, string> = {
        groupId: state['groupId'] as string,
        name: state['name'] as string,
        description: state['description'] as string,
      };

      const res = await request.get(buildUrl('/groups/{groupId}', param), {
        headers: jsonHeaders(),
      });

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, groupRequiredFields);
      assertEqualsForKeys(json, expectedBody, groupRequiredFields);
    });

    await test.step('Get Group Not Found', async () => {
      const param: Record<string, string> = {
        groupId: 'invalidGroupName',
      };
      const res = await request.get(buildUrl('/groups/{groupId}', param), {
        headers: jsonHeaders(),
      });
      expect(res.status()).toBe(404);
    });

    await test.step('Get Group Unauthorized', async () => {
      const param: Record<string, string> = {
        groupId: state['groupId'] as string,
      };
      const res = await request.get(buildUrl('/groups/{groupId}', param), {
        headers: {},
      });
      expect(res.status()).toBe(401);
    });

    await test.step('Update Group', async () => {
      const p = {groupId: state['groupId'] as string};
      const body = {
        name: `${state['name']}-updated`,
        description: `${state['description']}-updated`,
      };

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
      state['name'] = json.name;
      state['description'] = json.description;
    });

    await test.step('Update Group Unauthorized', async () => {
      const p = {groupId: state['groupId'] as string};
      const body = {
        name: `${state['name']}-updated`,
        description: `${state['description']}-updated`,
      };
      const res = await request.put(buildUrl('/groups/{groupId}', p), {
        headers: {},
        data: body,
      });
      expect(res.status()).toBe(401);
    });

    await test.step('Update Group Bad Request', async () => {
      const p = {groupId: state['groupId'] as string};
      const invalid = {description: 'x'};
      const res = await request.put(buildUrl('/groups/{groupId}', p), {
        headers: jsonHeaders(),
        data: invalid,
      });
      expect(res.status()).toBe(400);
    });

    await test.step('Update Group Not Found', async () => {
      const p = {groupId: 'invalidGroupId'};
      const body = {
        name: `${state['name']}-updated`,
        description: `${state['description']}-updated`,
      };
      const res = await request.put(buildUrl('/groups/{groupId}', p), {
        headers: jsonHeaders(),
        data: body,
      });
      expect(res.status()).toBe(404);
    });

    await test.step('Delete Group', async () => {
      const p = {groupId: state['groupId'] as string};
      const res = await request.delete(buildUrl('/groups/{groupId}', p), {
        headers: jsonHeaders(),
      });
      expect(res.status()).toBe(204);
    });

    await test.step('Get Group After Deletion', async () => {
      await sleep(10000);
      const p = {groupId: state['groupId'] as string};
      const after = await request.get(buildUrl('/groups/{groupId}', p), {
        headers: jsonHeaders(),
      });
      expect(after.status()).toBe(404);
    });

    await test.step('Delete Group Unauthorized', async () => {
      const p = {groupId: state['groupId'] as string};
      const res = await request.delete(buildUrl('/groups/{groupId}', p), {
        headers: {},
      });
      expect(res.status()).toBe(401);
    });

    await test.step('Delete Group Not Found', async () => {
      const p = {groupId: 'invalidGroupId'};
      const res = await request.delete(buildUrl('/groups/{groupId}', p), {
        headers: jsonHeaders(),
      });
      expect(res.status()).toBe(404);
    });
  });
});
