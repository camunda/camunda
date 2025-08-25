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
} from '../../../../utils/http';
import {
  CREATE_NEW_GROUP,
  groupRequiredFields,
} from '../../../../utils/beans/request-beans';
import {sleep} from '../../../../utils/sleep';

test.describe('Groups Clients API Tests', () => {
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
      state['groupId'] = json.groupId;
    });

    await test.step('Assign Client To Group', async () => {
      state['clientId'] = 'test-client';
      const p = {
        groupId: state['groupId'] as string,
        clientId: state['clientId'] as string,
      };
      const res = await request.put(
        buildUrl('/groups/{groupId}/clients/{clientId}', p),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(204);
    });

    await test.step('Search Clients For Group', async () => {
      await sleep(10000);
      const p = {groupId: state['groupId'] as string};
      const expectedBody = {clientId: state['clientId'] as string};
      const requiredFields = Object.keys(expectedBody);

      const res = await request.post(
        buildUrl('/groups/{groupId}/clients/search', p),
        {
          headers: jsonHeaders(),
          data: {},
        },
      );
      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, paginatedResponseFields);
      expect(json.page.totalItems).toBe(1);
      assertRequiredFields(json.items[0], requiredFields);
      assertEqualsForKeys(json.items[0], expectedBody, requiredFields);
    });

    await test.step('Search Clients For Group Unauthorized', async () => {
      const p = {groupId: state['groupId'] as string};
      const res = await request.post(
        buildUrl('/groups/{groupId}/clients/search', p),
        {
          headers: {},
          data: {},
        },
      );
      expect(res.status()).toBe(401);
    });

    await test.step('Search Clients For Group Not Found', async () => {
      const p = {groupId: 'invalidGroupId'};

      const res = await request.post(
        buildUrl('/groups/{groupId}/clients/search', p),
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

    await test.step('Unassign Client From Group', async () => {
      const p = {
        groupId: state['groupId'] as string,
        clientId: state['clientId'] as string,
      };
      const res = await request.delete(
        buildUrl('/groups/{groupId}/clients/{clientId}', p),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(204);
    });

    await test.step('Search Clients After Deletion', async () => {
      await sleep(10000);
      const p = {groupId: state['groupId'] as string};

      const res = await request.post(
        buildUrl('/groups/{groupId}/clients/search', p),
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

    await test.step('Unassign Client From Group Unauthorized', async () => {
      const p = {
        groupId: state['groupId'] as string,
        clientId: state['clientId'] as string,
      };
      const res = await request.delete(
        buildUrl('/groups/{groupId}/clients/{clientId}', p),
        {
          headers: {},
        },
      );
      expect(res.status()).toBe(401);
    });

    await test.step('Unassign Client From Group Not Found', async () => {
      const p = {
        groupId: state['groupId'] as string,
        clientId: state['clientId'] as string,
      };
      const res = await request.delete(
        buildUrl('/groups/{groupId}/clients/{clientId}', p),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(404);
    });
  });
});
