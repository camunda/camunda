/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/*
 * GENERATED FILE - DO NOT EDIT MANUALLY
 * Generated At: 2026-08-04T11:55:54.253Z
 * Spec Commit: 7ad6907f6d9cf772438213329bf52fa21d343ed2
 */
import {test, expect} from '@playwright/test';
import {jsonHeaders, buildUrl} from '../../../utils/http';

test.describe('Clustervariables Validation API Tests', () => {
  test('createGlobalClusterVariable - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      name: null,
      value: {},
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/cluster-variables/global', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createGlobalClusterVariable - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/cluster-variables/global', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createGlobalClusterVariable - Missing value (#1)', async ({
    request,
  }) => {
    const requestBody = {
      name: null,
    };
    const res = await request.post(
      buildUrl('/cluster-variables/global', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createGlobalClusterVariable - Missing name', async ({request}) => {
    const requestBody = {
      value: {},
    };
    const res = await request.post(
      buildUrl('/cluster-variables/global', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createGlobalClusterVariable - Missing value (#2)', async ({
    request,
  }) => {
    const requestBody = {
      name: 'x',
    };
    const res = await request.post(
      buildUrl('/cluster-variables/global', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createGlobalClusterVariable - Missing body', async ({request}) => {
    const res = await request.post(
      buildUrl('/cluster-variables/global', undefined),
      {
        headers: jsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createGlobalClusterVariable - Missing combo name,value', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.post(
      buildUrl('/cluster-variables/global', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createTenantClusterVariable - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      name: null,
      value: {},
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/cluster-variables/tenants/{tenantId}', {tenantId: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createTenantClusterVariable - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/cluster-variables/tenants/{tenantId}', {tenantId: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createTenantClusterVariable - Missing value (#1)', async ({
    request,
  }) => {
    const requestBody = {
      name: null,
    };
    const res = await request.post(
      buildUrl('/cluster-variables/tenants/{tenantId}', {tenantId: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createTenantClusterVariable - Missing name', async ({request}) => {
    const requestBody = {
      value: {},
    };
    const res = await request.post(
      buildUrl('/cluster-variables/tenants/{tenantId}', {tenantId: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createTenantClusterVariable - Missing value (#2)', async ({
    request,
  }) => {
    const requestBody = {
      name: 'x',
    };
    const res = await request.post(
      buildUrl('/cluster-variables/tenants/{tenantId}', {tenantId: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createTenantClusterVariable - Missing body', async ({request}) => {
    const res = await request.post(
      buildUrl('/cluster-variables/tenants/{tenantId}', {tenantId: 'x'}),
      {
        headers: jsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createTenantClusterVariable - Missing combo name,value', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.post(
      buildUrl('/cluster-variables/tenants/{tenantId}', {tenantId: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createTenantClusterVariable - Path param tenantId pattern violation', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/cluster-variables/tenants/{tenantId}', {
        tenantId: '!INVALID!',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteGlobalClusterVariable - Path param name pattern violation', async ({
    request,
  }) => {
    const res = await request.delete(
      buildUrl('/cluster-variables/global/{name}', {name: '!'}),
      {
        headers: jsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteTenantClusterVariable - Path param name pattern violation', async ({
    request,
  }) => {
    const res = await request.delete(
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId: 'x',
        name: '!',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteTenantClusterVariable - Path param tenantId pattern violation', async ({
    request,
  }) => {
    const res = await request.delete(
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId: '!INVALID!',
        name: 'x',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getGlobalClusterVariable - Path param name pattern violation', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/cluster-variables/global/{name}', {name: '!'}),
      {
        headers: jsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getTenantClusterVariable - Path param name pattern violation', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId: 'x',
        name: '!',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getTenantClusterVariable - Path param tenantId pattern violation', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId: '!INVALID!',
        name: 'x',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchClusterVariables - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/cluster-variables/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchClusterVariables - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/cluster-variables/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchClusterVariables - Enum violation sort.0.field (#1)', async ({
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
      buildUrl('/cluster-variables/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchClusterVariables - Enum violation sort.0.field (#2)', async ({
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
      buildUrl('/cluster-variables/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchClusterVariables - Enum violation sort.0.order (#1)', async ({
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
      buildUrl('/cluster-variables/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchClusterVariables - Enum violation sort.0.order (#2)', async ({
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
      buildUrl('/cluster-variables/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchClusterVariables - Param query.truncateValues wrong type', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/cluster-variables/search', undefined, {
        truncateValues: 'notBoolean',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateGlobalClusterVariable - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      value: {},
      __extraField: 'unexpected',
    };
    const res = await request.put(
      buildUrl('/cluster-variables/global/{name}', {name: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateGlobalClusterVariable - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.put(
      buildUrl('/cluster-variables/global/{name}', {name: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateGlobalClusterVariable - Missing value', async ({request}) => {
    const requestBody = {};
    const res = await request.put(
      buildUrl('/cluster-variables/global/{name}', {name: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateGlobalClusterVariable - Missing body', async ({request}) => {
    const res = await request.put(
      buildUrl('/cluster-variables/global/{name}', {name: 'x'}),
      {
        headers: jsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateGlobalClusterVariable - Path param name pattern violation', async ({
    request,
  }) => {
    const res = await request.put(
      buildUrl('/cluster-variables/global/{name}', {name: '!'}),
      {
        headers: jsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateTenantClusterVariable - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      value: {},
      __extraField: 'unexpected',
    };
    const res = await request.put(
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId: 'x',
        name: 'x',
      }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateTenantClusterVariable - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.put(
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId: 'x',
        name: 'x',
      }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateTenantClusterVariable - Missing value', async ({request}) => {
    const requestBody = {};
    const res = await request.put(
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId: 'x',
        name: 'x',
      }),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateTenantClusterVariable - Missing body', async ({request}) => {
    const res = await request.put(
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId: 'x',
        name: 'x',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateTenantClusterVariable - Path param name pattern violation', async ({
    request,
  }) => {
    const res = await request.put(
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId: 'x',
        name: '!',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateTenantClusterVariable - Path param tenantId pattern violation', async ({
    request,
  }) => {
    const res = await request.put(
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId: '!INVALID!',
        name: 'x',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
});
