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
  assignClientsToTenant,
  clientFromState,
  createTenant,
} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Tenant Clients API Tests', () => {
  const state: Record<string, unknown> = {};

  test.beforeAll(async ({request}) => {
    await createTenant(request, state, '1');
    await createTenant(request, state, '2');
    await assignClientsToTenant(
      request,
      3,
      state['tenantId1'] as string,
      state,
    );
    await assignClientsToTenant(
      request,
      1,
      state['tenantId2'] as string,
      state,
    );
  });

  test('Assign Client To Tenant', async ({request}) => {
    const tenant = await createTenant(request);
    const clientId = 'test-client' + generateUniqueId();
    const p = {clientId, tenantId: tenant.tenantId as string};

    await expect(async () => {
      const res = await request.put(
        buildUrl('/tenants/{tenantId}/clients/{clientId}', p),
        {headers: jsonHeaders()},
      );
      expect(res.status()).toBe(204);
    }).toPass(defaultAssertionOptions);
  });

  test('Assign Client To Tenant Non Existent Client Success', async ({
    request,
  }) => {
    const p = {
      clientId: 'invalidClientId',
      tenantId: state['tenantId1'] as string,
    };
    const res = await request.put(
      buildUrl('/tenants/{tenantId}/clients/{clientId}', p),
      {headers: jsonHeaders()},
    );
    expect(res.status()).toBe(204);
  });

  test('Assign Client To Tenant Non Existent Tenant Not Found', async ({
    request,
  }) => {
    const p = {
      clientId: clientFromState('tenantId1', state) as string,
      tenantId: 'invalidTenantId',
    };
    const res = await request.put(
      buildUrl('/tenants/{tenantId}/clients/{clientId}', p),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Command 'ADD_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Assign Client To Tenant Unauthorized', async ({request}) => {
    const tenantId = state['tenantId1'] as string;
    const p = {
      clientId: clientFromState('tenantId1', state) as string,
      tenantId,
    };
    await expect(async () => {
      const res = await request.put(
        buildUrl('/tenants/{tenantId}/clients/{clientId}', p),
        {headers: {}},
      );
      await assertUnauthorizedRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Assign Already Added Client To Tenant Conflict', async ({request}) => {
    const p = {
      clientId: clientFromState('tenantId1', state) as string,
      tenantId: state['tenantId1'] as string,
    };
    await expect(async () => {
      const res = await request.put(
        buildUrl('/tenants/{tenantId}/clients/{clientId}', p),
        {headers: jsonHeaders()},
      );
      await assertConflictRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Tenant Clients', async ({request}) => {
    const client1 = clientFromState('tenantId1', state, 1);
    const client2 = clientFromState('tenantId1', state, 2);
    const client3 = clientFromState('tenantId1', state, 3);

    await expect(async () => {
      const res = await request.post(
        buildUrl('/tenants/{tenantId}/clients/search', {
          tenantId: state['tenantId1'] as string,
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

  test('Search Tenant Clients Unauthorized', async ({request}) => {
    const p = {tenantId: state['tenantId1'] as string};
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/clients/search', p),
      {headers: {}, data: {}},
    );
    await assertUnauthorizedRequest(res);
  });

  test('Search Tenant Clients For Non Existent Tenant Empty', async ({
    request,
  }) => {
    const p = {tenantId: 'invalidTenantId'};
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/clients/search', p),
      {headers: jsonHeaders(), data: {}},
    );
    await assertPaginatedRequest(res, {
      totalItemsEqualTo: 0,
      itemsLengthEqualTo: 0,
    });
  });

  test('Unassign Client From Tenant', async ({request}) => {
    const tenantId = state['tenantId2'] as string;
    const clientId = clientFromState('tenantId2', state, 1);
    const p = {tenantId: tenantId, clientId: clientId};

    await test.step('Unassign Client From Tenant', async () => {
      await expect(async () => {
        const res = await request.delete(
          buildUrl('/tenants/{tenantId}/clients/{clientId}', p),
          {headers: jsonHeaders()},
        );
        expect(res.status()).toBe(204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Search Tenant Clients After Unassign', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/tenants/{tenantId}/clients/search', p),
          {headers: jsonHeaders(), data: {}},
        );
        await assertPaginatedRequest(res, {
          totalItemsEqualTo: 0,
          itemsLengthEqualTo: 0,
        });
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Unassign Client From Tenant Unauthorized', async ({request}) => {
    const tenantId = state['tenantId1'] as string;
    const clientId = clientFromState('tenantId1', state, 1);
    const p = {tenantId: tenantId, clientId: clientId};

    const res = await request.delete(
      buildUrl('/tenants/{tenantId}/clients/{clientId}', p),
      {headers: {}},
    );
    await assertUnauthorizedRequest(res);
  });

  test('Unassign Client From Tenant Non Existent Client Not Found', async ({
    request,
  }) => {
    const p = {
      clientId: 'invalidClientId',
      tenantId: state['tenantId1'] as string,
    };
    const res = await request.delete(
      buildUrl('/tenants/{tenantId}/clients/{clientId}', p),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Unassign Client From Tenant Non Existent Tenant Not Found', async ({
    request,
  }) => {
    const clientId = clientFromState('tenantId1', state, 1);
    const p = {clientId, tenantId: 'invalidTenantId'};
    const res = await request.delete(
      buildUrl('/tenants/{tenantId}/clients/{clientId}', p),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });
});
