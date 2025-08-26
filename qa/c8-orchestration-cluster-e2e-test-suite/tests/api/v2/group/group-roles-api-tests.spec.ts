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
  CREATE_NEW_ROLE,
  groupRequiredFields,
  roleRequiredFields,
} from '../../../../utils/beans/request-beans';
import {sleep} from '../../../../utils/sleep';

test.describe('Group Roles API Tests', () => {
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
      await sleep(5000);
    });

    await test.step('Create Role', async () => {
      const body = CREATE_NEW_ROLE();

      const res = await request.post(buildUrl('/roles'), {
        headers: jsonHeaders(),
        data: body,
      });

      expect(res.status()).toBe(201);
      await extractAndStoreIds(res, state);
    });

    await test.step('Assign Role To Group', async () => {
      await sleep(5000);
      const p = {
        groupId: state['groupId'] as string,
        roleId: state['roleId'] as string,
      };
      const res = await request.put(
        buildUrl('/roles/{roleId}/groups/{groupId}', p),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(204);
    });

    await test.step('Search Roles For Group', async () => {
      await sleep(10000);
      const p = {groupId: state['groupId'] as string};
      const expectedBody: Record<string, string> = {
        name: state['name'] as string,
        roleId: state['roleId'] as string,
        description: state['description'] as string,
      };

      const res = await request.post(
        buildUrl('/groups/{groupId}/roles/search', p),
        {
          headers: jsonHeaders(),
          data: {},
        },
      );

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, paginatedResponseFields);
      expect(json.page.totalItems).toBe(1);
      assertRequiredFields(json.items[0], roleRequiredFields);
      assertEqualsForKeys(json.items[0], expectedBody, roleRequiredFields);
    });

    await test.step('Search Roles For Group Unauthorized', async () => {
      const p = {groupId: state['groupId'] as string};
      const res = await request.post(
        buildUrl('/groups/{groupId}/roles/search', p),
        {
          headers: {},
          data: {},
        },
      );
      expect(res.status()).toBe(401);
    });

    await test.step('Search Roles For Group Not Found', async () => {
      const p = {groupId: 'invalidGroupId'};

      const res = await request.post(
        buildUrl('/groups/{groupId}/roles/search', p),
        {
          headers: jsonHeaders(),
          data: {},
        },
      );

      const json = await res.json();
      assertRequiredFields(json, paginatedResponseFields);
      expect(json.page.totalItems).toBe(0);
      expect(json.items.length).toBe(0);
    });

    await test.step('Unassign Role From Group', async () => {
      const p = {
        groupId: state['groupId'] as string,
        roleId: state['roleId'] as string,
      };
      const res = await request.delete(
        buildUrl('/roles/{roleId}/groups/{groupId}', p),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(204);
    });

    await test.step('Search Roles For Group After Deletion', async () => {
      await expect(async () => {
        const p = {groupId: state['groupId'] as string};

        const res = await request.post(
          buildUrl('/groups/{groupId}/roles/search', p),
          {
            headers: jsonHeaders(),
            data: {},
          },
        );

        expect(res.status()).toBe(200);
        const json = await res.json();
        assertRequiredFields(json, paginatedResponseFields);
        expect(json.page.totalItems).toBe(0);
      }).toPass({
        intervals: [5_000, 10_000, 15_000],
        timeout: 30_000,
      });
    });

    await test.step('Unassign Role From Group Unauthorized', async () => {
      const p = {
        groupId: state['groupId'] as string,
        roleId: state['roleId'] as string,
      };
      const res = await request.delete(
        buildUrl('/roles/{roleId}/groups/{groupId}', p),
        {
          headers: {},
        },
      );
      expect(res.status()).toBe(401);
    });

    await test.step('Unassign Role From Group Not Found', async () => {
      const p = {
        groupId: state['groupId'] as string,
        roleId: state['roleId'] as string,
      };
      const res = await request.delete(
        buildUrl('/roles/{roleId}/groups/{groupId}', p),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(404);
    });
  });
});
