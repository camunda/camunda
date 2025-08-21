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
} from '../../../../utils/beans/request-beans';
import {sleep} from '../../../../utils/sleep';

test.describe('Group Users API Tests', () => {
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
      await sleep(5000);
    });

    await test.step('Assign User To Group Not Found', async () => {
      state['username'] = 'demo';
      const stateParams: Record<string, string> = {
        groupId: 'invalidgroupid',
        username: state['username'] as string,
      };
      const res = await request.put(
        buildUrl('/groups/{groupId}/users/{username}', stateParams),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(404);
    });

    await test.step('Assign User To Group', async () => {
      state['username'] = 'demo';
      const stateParams: Record<string, string> = {
        groupId: state['groupId'] as string,
        username: state['username'] as string,
      };
      const res = await request.put(
        buildUrl('/groups/{groupId}/users/{username}', stateParams),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(204);
    });

    await test.step('Assign Already Added User To Group Conflict', async () => {
      state['username'] = 'demo';
      const stateParams: Record<string, string> = {
        groupId: state['groupId'] as string,
        username: state['username'] as string,
      };
      const res = await request.put(
        buildUrl('/groups/{groupId}/users/{username}', stateParams),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(409);
      const json = await res.json();
      assertRequiredFields(json, ['title']);
      expect(json['title']).toContain('ALREADY_EXISTS');
    });

    await test.step('Search Users For Group', async () => {
      await sleep(10000);
      const stateParams: Record<string, string> = {
        groupId: state['groupId'] as string,
        username: state['username'] as string,
      };
      const expectedBody = {username: state['username'] as string};
      const requiredFields = Object.keys(expectedBody);

      const res = await request.post(
        buildUrl('/groups/{groupId}/users/search', stateParams),
        {headers: jsonHeaders()},
      );

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, paginatedResponseFields);
      expect(json.page.totalItems).toBe(1);
      assertRequiredFields(json.items[0], requiredFields);
      assertEqualsForKeys(json.items[0], expectedBody, requiredFields);
    });

    await test.step('Search Users For Group Unauthorized', async () => {
      state['username'] = 'demo';
      const stateParams: Record<string, string> = {
        groupId: state['groupId'] as string,
        username: state['username'] as string,
      };
      const res = await request.post(
        buildUrl('/groups/{groupId}/users/search', stateParams),
        {headers: {}},
      );
      expect(res.status()).toBe(401);
    });

    await test.step('Search Users For Group Not Found', async () => {
      const stateParams: Record<string, string> = {
        groupId: 'invalidGroupName',
        username: state['username'] as string,
      };

      const res = await request.post(
        buildUrl('/groups/{groupId}/users/search', stateParams),
        {headers: jsonHeaders()},
      );

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, paginatedResponseFields);
      expect(json.page.totalItems).toBe(0);
      expect(json.items.length).toBe(0);
    });

    await test.step('Unassign User From Group', async () => {
      const p = {
        groupId: state['groupId'] as string,
        username: state['username'] as string,
      };
      const res = await request.delete(
        buildUrl('/groups/{groupId}/users/{username}', p),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(204);
    });

    await test.step('Search Users After Deletion', async () => {
      await sleep(10000);
      const p = {groupId: state['groupId'] as string};

      const res = await request.post(
        buildUrl('/groups/{groupId}/users/search', p),
        {
          headers: jsonHeaders(),
          data: {},
        },
      );
      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, paginatedResponseFields);
      expect(json.page.totalItems).toBe(0);
      expect(json.items.length).toBe(0);
    });

    await test.step('Unassign User From Group Unauthorized', async () => {
      const p = {
        groupId: state['groupId'] as string,
        username: state['username'] as string,
      };
      const res = await request.delete(
        buildUrl('/groups/{groupId}/users/{username}', p),
        {
          headers: {},
        },
      );
      expect(res.status()).toBe(401);
    });

    await test.step('Unassign User From Group Not Found', async () => {
      const p = {
        groupId: state['groupId'] as string,
        username: state['username'] as string,
      };
      const res = await request.delete(
        buildUrl('/groups/{groupId}/users/{username}', p),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(404);
    });
  });
});
