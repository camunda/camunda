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
  assertUnauthorizedRequest,
  assertNotFoundRequest,
  assertConflictRequest,
  assertPaginatedRequest,
} from '../../../../utils/http';
import {
  defaultAssertionOptions,
  generateUniqueId,
} from '../../../../utils/constants';
import {
  assertClientsInResponse,
  assignClientsToRole,
  clientFromState,
  createRole,
} from '@requestHelpers';
import {cleanupRoles} from '../../../../utils/rolesCleanup';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Role Clients API Tests', () => {
  const state: Record<string, unknown> = {};
  const createdRoleIds: string[] = [];

  test.beforeAll(async ({request}) => {
    await createRole(request, state, '1');
    await createRole(request, state, '2');
    await assignClientsToRole(request, 3, state['roleId1'] as string, state);
    await assignClientsToRole(request, 1, state['roleId2'] as string, state);

    createdRoleIds.push(
      ...Object.entries(state)
        .filter(([key]) => key.startsWith('roleId'))
        .map(([, value]) => value as string),
    );
  });

  test.afterAll(async ({request}) => {
    await cleanupRoles(request, createdRoleIds);
  });

  test('Assign Role To Client', async ({request}) => {
    const role = await createRole(request);
    createdRoleIds.push(role.roleId as string);
    const clientId = 'test-client' + generateUniqueId();
    const p = {clientId, roleId: role.roleId as string};

    await expect(async () => {
      const res = await request.put(
        buildUrl('/roles/{roleId}/clients/{clientId}', p),
        {headers: jsonHeaders()},
      );
      expect(res.status()).toBe(204);
    }).toPass(defaultAssertionOptions);
  });

  test('Assign Role To Client Non Existent Client Success', async ({
    request,
  }) => {
    const p = {clientId: 'invalidClientId', roleId: state['roleId1'] as string};
    const res = await request.put(
      buildUrl('/roles/{roleId}/clients/{clientId}', p),
      {headers: jsonHeaders()},
    );
    expect(res.status()).toBe(204);
  });

  test('Assign Role To Client Non Existent Role Not Found', async ({
    request,
  }) => {
    const p = {
      clientId: clientFromState('roleId1', state) as string,
      roleId: 'invalidRoleId',
    };
    const res = await request.put(
      buildUrl('/roles/{roleId}/clients/{clientId}', p),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Command 'ADD_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Assign Role To Client Unauthorized', async ({request}) => {
    const roleId = state['roleId1'] as string;
    const p = {
      clientId: clientFromState('roleId1', state) as string,
      roleId,
    };
    await expect(async () => {
      const res = await request.put(
        buildUrl('/roles/{roleId}/clients/{clientId}', p),
        {headers: {}},
      );
      await assertUnauthorizedRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Assign Already Added Client To Role Conflict', async ({request}) => {
    const p = {
      clientId: clientFromState('roleId1', state) as string,
      roleId: state['roleId1'] as string,
    };
    await expect(async () => {
      const res = await request.put(
        buildUrl('/roles/{roleId}/clients/{clientId}', p),
        {headers: jsonHeaders()},
      );
      await assertConflictRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Role Clients', async ({request}) => {
    const client1 = clientFromState('roleId1', state, 1);
    const client2 = clientFromState('roleId1', state, 2);
    const client3 = clientFromState('roleId1', state, 3);

    await expect(async () => {
      const res = await request.post(
        buildUrl('/roles/{roleId}/clients/search', {
          roleId: state['roleId1'] as string,
        }),
        {headers: jsonHeaders(), data: {}},
      );
      await assertPaginatedRequest(res, {
        totalItemsEqualTo: 3,
        itemsLengthEqualTo: 3,
      });
      const json = await res.json();
      assertClientsInResponse(json, client1);
      assertClientsInResponse(json, client2);
      assertClientsInResponse(json, client3);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Role Clients Unauthorized', async ({request}) => {
    const p = {roleId: state['roleId1'] as string};
    const res = await request.post(
      buildUrl('/roles/{roleId}/clients/search', p),
      {headers: {}, data: {}},
    );
    await assertUnauthorizedRequest(res);
  });

  test('Search Role Clients For Non Existent Role Empty', async ({request}) => {
    const p = {roleId: 'invalidRoleId'};
    const res = await request.post(
      buildUrl('/roles/{roleId}/clients/search', p),
      {headers: jsonHeaders(), data: {}},
    );
    await assertPaginatedRequest(res, {
      totalItemsEqualTo: 0,
      itemsLengthEqualTo: 0,
    });
  });

  test('Unassign Role From Client', async ({request}) => {
    const roleId = state['roleId2'] as string;
    const clientId = clientFromState('roleId2', state, 1);
    const p = {roleId: roleId, clientId: clientId};

    await test.step('Unassign Role From Client', async () => {
      await expect(async () => {
        const res = await request.delete(
          buildUrl('/roles/{roleId}/clients/{clientId}', p),
          {headers: jsonHeaders()},
        );
        expect(res.status()).toBe(204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Search Role Clients After Unassign', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/roles/{roleId}/clients/search', p),
          {headers: jsonHeaders(), data: {}},
        );
        await assertPaginatedRequest(res, {
          totalItemsEqualTo: 0,
          itemsLengthEqualTo: 0,
        });
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Unassign Role From Client Unauthorized', async ({request}) => {
    const roleId = state['roleId1'] as string;
    const clientId = clientFromState('roleId1', state, 1);
    const p = {roleId: roleId, clientId: clientId};

    const res = await request.delete(
      buildUrl('/roles/{roleId}/clients/{clientId}', p),
      {headers: {}},
    );
    await assertUnauthorizedRequest(res);
  });

  test('Unassign Role From Client Non Existent Client Not Found', async ({
    request,
  }) => {
    const p = {clientId: 'invalidClientId', roleId: state['roleId1'] as string};
    const res = await request.delete(
      buildUrl('/roles/{roleId}/clients/{clientId}', p),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Unassign Role From Client Non Existent Role Not Found', async ({
    request,
  }) => {
    const clientId = clientFromState('roleId1', state, 1);
    const p = {clientId, roleId: 'invalidRoleId'};
    const res = await request.delete(
      buildUrl('/roles/{roleId}/clients/{clientId}', p),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });
});
