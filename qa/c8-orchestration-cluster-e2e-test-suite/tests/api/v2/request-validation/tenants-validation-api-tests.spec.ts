/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/*
 * GENERATED FILE - DO NOT EDIT MANUALLY
 * Generated At: 2025-09-05T06:07:46.867Z
 * Spec Commit: 3445d1d86c2ad361858dc12e734eeb6197e426a5
 */
import { test, expect } from '@playwright/test';
import { jsonHeaders, buildUrl } from '../../../../utils/http';

test.describe('Tenants Validation API Tests', () => {
  test('assignClientToTenant - Param clientId wrong type', async ({
    request,
  }) => {
    const res = await request.put(
      buildUrl('/tenants/{tenantId}/clients/{clientId}', {
        tenantId: 'x',
        clientId: '12345',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('assignGroupToTenant - Param groupId wrong type', async ({
    request,
  }) => {
    const res = await request.put(
      buildUrl('/tenants/{tenantId}/groups/{groupId}', {
        tenantId: 'x',
        groupId: '12345',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('assignMappingRuleToTenant - Param mappingRuleId wrong type', async ({
    request,
  }) => {
    const res = await request.put(
      buildUrl('/tenants/{tenantId}/mapping-rules/{mappingRuleId}', {
        tenantId: 'x',
        mappingRuleId: '12345',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('assignRoleToTenant - Param roleId wrong type', async ({ request }) => {
    const res = await request.put(
      buildUrl('/tenants/{tenantId}/roles/{roleId}', {
        tenantId: 'x',
        roleId: '12345',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('createTenant - Additional prop __extraField', async ({ request }) => {
    const requestBody = {
      tenantId: 'x',
      name: 'x',
      __extraField: 'unexpected',
    };
    const res = await request.post(buildUrl('/tenants', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('createTenant - Body wrong top-level type', async ({ request }) => {
    const requestBody = [];
    const res = await request.post(buildUrl('/tenants', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('createTenant - Param name wrong type (#1)', async ({ request }) => {
    const requestBody = {
      tenantId: 'x',
      name: 123,
    };
    const res = await request.post(buildUrl('/tenants', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('createTenant - Param name wrong type (#2)', async ({ request }) => {
    const requestBody = {
      tenantId: 'x',
      name: true,
    };
    const res = await request.post(buildUrl('/tenants', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('createTenant - Param tenantId wrong type (#1)', async ({ request }) => {
    const requestBody = {
      tenantId: 123,
      name: 'x',
    };
    const res = await request.post(buildUrl('/tenants', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('createTenant - Param tenantId wrong type (#2)', async ({ request }) => {
    const requestBody = {
      tenantId: true,
      name: 'x',
    };
    const res = await request.post(buildUrl('/tenants', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('createTenant - Constraint violation tenantId (#1)', async ({
    request,
  }) => {
    const requestBody = {
      tenantId:
        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      name: 'x',
    };
    const res = await request.post(buildUrl('/tenants', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('createTenant - Constraint violation tenantId (#2)', async ({
    request,
  }) => {
    const requestBody = {
      tenantId: '',
      name: 'x',
    };
    const res = await request.post(buildUrl('/tenants', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('createTenant - Constraint violation tenantId (#3)', async ({
    request,
  }) => {
    const requestBody = {
      tenantId: '__NO_MATCH__',
      name: 'x',
    };
    const res = await request.post(buildUrl('/tenants', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('createTenant - Constraint violation tenantId (#4)', async ({
    request,
  }) => {
    const requestBody = {
      tenantId:
        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      name: 'x',
    };
    const res = await request.post(buildUrl('/tenants', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('createTenant - Missing name', async ({ request }) => {
    const requestBody = {
      tenantId: 'x',
    };
    const res = await request.post(buildUrl('/tenants', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('createTenant - Missing tenantId', async ({ request }) => {
    const requestBody = {
      name: 'x',
    };
    const res = await request.post(buildUrl('/tenants', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('createTenant - Missing body', async ({ request }) => {
    const res = await request.post(buildUrl('/tenants', undefined), {
      headers: jsonHeaders(),
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('createTenant - Missing combo tenantId,name', async ({ request }) => {
    const requestBody = {};
    const res = await request.post(buildUrl('/tenants', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchClientsForTenant - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/clients/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchClientsForTenant - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody = [];
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/clients/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchClientsForTenant - Enum violation sort.0.field (#1)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'clientId_INVALID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/clients/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchClientsForTenant - Enum violation sort.0.field (#2)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'CLIENTID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/clients/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchClientsForTenant - Enum violation sort.0.field (#3)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'clientid',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/clients/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchClientsForTenant - Enum violation sort.0.order (#1)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          order: {
            __invalidEnum: true,
            value: 'ASC_INVALID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/clients/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchClientsForTenant - Enum violation sort.0.order (#2)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          order: {
            __invalidEnum: true,
            value: 'asc',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/clients/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchClientsForTenant - Missing body', async ({ request }) => {
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/clients/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchGroupIdsForTenant - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/groups/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchGroupIdsForTenant - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody = [];
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/groups/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchGroupIdsForTenant - Enum violation sort.0.field (#1)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'groupId_INVALID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/groups/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchGroupIdsForTenant - Enum violation sort.0.field (#2)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'GROUPID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/groups/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchGroupIdsForTenant - Enum violation sort.0.field (#3)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'groupid',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/groups/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchGroupIdsForTenant - Enum violation sort.0.order (#1)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          order: {
            __invalidEnum: true,
            value: 'ASC_INVALID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/groups/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchGroupIdsForTenant - Enum violation sort.0.order (#2)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          order: {
            __invalidEnum: true,
            value: 'asc',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/groups/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchGroupIdsForTenant - Missing body', async ({ request }) => {
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/groups/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchMappingsForTenant - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/mapping-rules/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchMappingsForTenant - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody = [];
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/mapping-rules/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchMappingsForTenant - Enum violation sort.0.field (#1)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'mappingRuleId_INVALID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/mapping-rules/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchMappingsForTenant - Enum violation sort.0.field (#2)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'MAPPINGRULEID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/mapping-rules/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchMappingsForTenant - Enum violation sort.0.field (#3)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'mappingruleid',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/mapping-rules/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchMappingsForTenant - Enum violation sort.0.order (#1)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          order: {
            __invalidEnum: true,
            value: 'ASC_INVALID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/mapping-rules/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchMappingsForTenant - Enum violation sort.0.order (#2)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          order: {
            __invalidEnum: true,
            value: 'asc',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/mapping-rules/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchMappingsForTenant - Missing body', async ({ request }) => {
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/mapping-rules/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchRolesForTenant - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/roles/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchRolesForTenant - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody = [];
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/roles/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchRolesForTenant - Enum violation sort.0.field (#1)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'name_INVALID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/roles/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchRolesForTenant - Enum violation sort.0.field (#2)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'NAME',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/roles/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchRolesForTenant - Enum violation sort.0.order (#1)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          order: {
            __invalidEnum: true,
            value: 'ASC_INVALID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/roles/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchRolesForTenant - Enum violation sort.0.order (#2)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          order: {
            __invalidEnum: true,
            value: 'asc',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/roles/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchRolesForTenant - Missing body', async ({ request }) => {
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/roles/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchTenants - Additional prop __extraField', async ({ request }) => {
    const requestBody = {
      __extraField: 'unexpected',
    };
    const res = await request.post(buildUrl('/tenants/search', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchTenants - Body wrong top-level type', async ({ request }) => {
    const requestBody = [];
    const res = await request.post(buildUrl('/tenants/search', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchTenants - Enum violation sort.0.field (#1)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'key_INVALID',
          },
        },
      },
    };
    const res = await request.post(buildUrl('/tenants/search', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchTenants - Enum violation sort.0.field (#2)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'KEY',
          },
        },
      },
    };
    const res = await request.post(buildUrl('/tenants/search', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchTenants - Enum violation sort.0.order (#1)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          order: {
            __invalidEnum: true,
            value: 'ASC_INVALID',
          },
        },
      },
    };
    const res = await request.post(buildUrl('/tenants/search', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchTenants - Enum violation sort.0.order (#2)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          order: {
            __invalidEnum: true,
            value: 'asc',
          },
        },
      },
    };
    const res = await request.post(buildUrl('/tenants/search', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchTenants - Missing body', async ({ request }) => {
    const res = await request.post(buildUrl('/tenants/search', undefined), {
      headers: jsonHeaders(),
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchUsersForTenant - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/users/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchUsersForTenant - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody = [];
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/users/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchUsersForTenant - Enum violation sort.0.field (#1)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'username_INVALID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/users/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchUsersForTenant - Enum violation sort.0.field (#2)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'USERNAME',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/users/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchUsersForTenant - Enum violation sort.0.order (#1)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          order: {
            __invalidEnum: true,
            value: 'ASC_INVALID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/users/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchUsersForTenant - Enum violation sort.0.order (#2)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          order: {
            __invalidEnum: true,
            value: 'asc',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/users/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('searchUsersForTenant - Missing body', async ({ request }) => {
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/users/search', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('unassignClientFromTenant - Param clientId wrong type', async ({
    request,
  }) => {
    const res = await request.delete(
      buildUrl('/tenants/{tenantId}/clients/{clientId}', {
        tenantId: 'x',
        clientId: '12345',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('unassignGroupFromTenant - Param groupId wrong type', async ({
    request,
  }) => {
    const res = await request.delete(
      buildUrl('/tenants/{tenantId}/groups/{groupId}', {
        tenantId: 'x',
        groupId: '12345',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('unassignMappingRuleFromTenant - Param mappingRuleId wrong type', async ({
    request,
  }) => {
    const res = await request.delete(
      buildUrl('/tenants/{tenantId}/mapping-rules/{mappingRuleId}', {
        tenantId: 'x',
        mappingRuleId: '12345',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('unassignRoleFromTenant - Param roleId wrong type', async ({
    request,
  }) => {
    const res = await request.delete(
      buildUrl('/tenants/{tenantId}/roles/{roleId}', {
        tenantId: 'x',
        roleId: '12345',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('updateTenant - Additional prop __extraField', async ({ request }) => {
    const requestBody = {
      name: 'x',
      description: 'x',
      __extraField: 'unexpected',
    };
    const res = await request.put(
      buildUrl('/tenants/{tenantId}', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('updateTenant - Body wrong top-level type', async ({ request }) => {
    const requestBody = [];
    const res = await request.put(
      buildUrl('/tenants/{tenantId}', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('updateTenant - Param description wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      name: 'x',
      description: 123,
    };
    const res = await request.put(
      buildUrl('/tenants/{tenantId}', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('updateTenant - Param description wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      name: 'x',
      description: true,
    };
    const res = await request.put(
      buildUrl('/tenants/{tenantId}', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('updateTenant - Param name wrong type (#1)', async ({ request }) => {
    const requestBody = {
      name: 123,
      description: 'x',
    };
    const res = await request.put(
      buildUrl('/tenants/{tenantId}', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('updateTenant - Param name wrong type (#2)', async ({ request }) => {
    const requestBody = {
      name: true,
      description: 'x',
    };
    const res = await request.put(
      buildUrl('/tenants/{tenantId}', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('updateTenant - Missing description', async ({ request }) => {
    const requestBody = {
      name: 'x',
    };
    const res = await request.put(
      buildUrl('/tenants/{tenantId}', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('updateTenant - Missing name', async ({ request }) => {
    const requestBody = {
      description: 'x',
    };
    const res = await request.put(
      buildUrl('/tenants/{tenantId}', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('updateTenant - Missing body', async ({ request }) => {
    const res = await request.put(
      buildUrl('/tenants/{tenantId}', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('updateTenant - Missing combo name,description', async ({ request }) => {
    const requestBody = {};
    const res = await request.put(
      buildUrl('/tenants/{tenantId}', { tenantId: 'x' }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
});
