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
  assertConflictRequest,
} from '../../../../utils/http';
import {
  assignClientToGroup,
  clientIdFromState,
  createGroupAndStoreResponseFields,
} from '../../../../utils/requestHelpers';
import {
  defaultAssertionOptions,
  generateUniqueId,
} from '../../../../utils/constants';

test.describe.parallel('Groups Clients API Tests', () => {
  const state: Record<string, unknown> = {};

  test.beforeAll(async ({request}) => {
    await createGroupAndStoreResponseFields(request, 3, state);
    await assignClientToGroup(request, 1, state['groupId2'] as string, state);
    await assignClientToGroup(request, 1, state['groupId3'] as string, state);
  });

  test('Assign Client To Group', async ({request}) => {
    const clientId = `test-client` + generateUniqueId();
    const p = {
      groupId: state['groupId1'] as string,
      clientId: clientId as string,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/groups/{groupId}/clients/{clientId}', p),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(204);
    }).toPass(defaultAssertionOptions);
  });

  test('Assign Already Added Client To Group Conflict', async ({request}) => {
    const stateParams: Record<string, string> = {
      groupId: state['groupId2'] as string,
      clientId: clientIdFromState('groupId2', state) as string,
    };
    const res = await request.put(
      buildUrl('/groups/{groupId}/clients/{clientId}', stateParams),
      {
        headers: jsonHeaders(),
      },
    );

    await assertConflictRequest(res);
  });

  test('Search Clients For Group', async ({request}) => {
    const p = {groupId: state['groupId2'] as string};
    const expectedBody = {
      clientId: clientIdFromState('groupId2', state) as string,
    };
    const requiredFields = Object.keys(expectedBody);

    await expect(async () => {
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
    }).toPass(defaultAssertionOptions);
  });

  test('Search Clients For Group Unauthorized', async ({request}) => {
    const p = {groupId: state['groupId1'] as string};
    const res = await request.post(
      buildUrl('/groups/{groupId}/clients/search', p),
      {
        headers: {},
        data: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Search Clients For Group Not Found', async ({request}) => {
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

  test('Unassign Client From Group', async ({request}) => {
    await test.step('Unassign Client', async () => {
      const p = {
        groupId: state['groupId3'] as string,
        clientId: clientIdFromState('groupId3', state) as string,
      };
      await expect(async () => {
        const res = await request.delete(
          buildUrl('/groups/{groupId}/clients/{clientId}', p),
          {
            headers: jsonHeaders(),
          },
        );
        expect(res.status()).toBe(204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Search Clients After Deletion', async () => {
      const p = {groupId: state['groupId3'] as string};

      await expect(async () => {
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
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Unassign Client From Group Unauthorized', async ({request}) => {
    const p = {
      groupId: state['groupId2'] as string,
      clientId: clientIdFromState('groupId2', state) as string,
    };

    const res = await request.delete(
      buildUrl('/groups/{groupId}/clients/{clientId}', p),
      {
        headers: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Unassign Client From Group Not Found', async ({request}) => {
    const p = {
      groupId: 'invalidGroupId',
      clientId: clientIdFromState('groupId2', state) as string,
    };
    const res = await request.delete(
      buildUrl('/groups/{groupId}/clients/{clientId}', p),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      res,
      `Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });
});
