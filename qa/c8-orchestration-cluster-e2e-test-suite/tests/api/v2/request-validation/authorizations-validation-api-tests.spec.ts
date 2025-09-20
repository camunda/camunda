/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/*
 * GENERATED FILE - DO NOT EDIT MANUALLY
 * Generated At: 2025-09-15T03:11:51.640Z
 * Spec Commit: 0fe50d88d8253bb5367efab5a2c911758c95e7ea
 */
import {test, expect} from '@playwright/test';
import {jsonHeaders, buildUrl} from '../../../../utils/http';

test.describe('Authorizations Validation API Tests', () => {
  test('createAuthorization - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      resourceId: 'x',
      resourceType: {},
      permissionTypes: ['x'],
      __extraField: 'unexpected',
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Body wrong top-level type', async ({request}) => {
    const requestBody: string[] = [];
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Param ownerId wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 123,
      ownerType: {},
      resourceId: 'x',
      resourceType: {},
      permissionTypes: ['x'],
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Param ownerId wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: true,
      ownerType: {},
      resourceId: 'x',
      resourceType: {},
      permissionTypes: ['x'],
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Param permissionTypes.0 wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      resourceId: 'x',
      resourceType: {},
      permissionTypes: [123],
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Param permissionTypes.0 wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      resourceId: 'x',
      resourceType: {},
      permissionTypes: [true],
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Param resourceId wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      resourceId: 123,
      resourceType: {},
      permissionTypes: ['x'],
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Param resourceId wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      resourceId: true,
      resourceType: {},
      permissionTypes: ['x'],
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing ownerId (#1)', async ({request}) => {
    const requestBody = {
      ownerType: {},
      resourceId: 'x',
      resourceType: {},
      permissionTypes: ['x'],
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing ownerType (#1)', async ({request}) => {
    const requestBody = {
      ownerId: 'x',
      resourceId: 'x',
      resourceType: {},
      permissionTypes: ['x'],
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing resourceId (#1)', async ({request}) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      resourceType: {},
      permissionTypes: ['x'],
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing resourceType (#1)', async ({request}) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      resourceId: 'x',
      permissionTypes: ['x'],
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing ownerId (#2)', async ({request}) => {
    const requestBody = {
      ownerType: {},
      resourceId: 'x',
      resourceType: {},
      permissionTypes: [],
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing ownerType (#2)', async ({request}) => {
    const requestBody = {
      ownerId: 'x',
      resourceId: 'x',
      resourceType: {},
      permissionTypes: [],
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing permissionTypes', async ({request}) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      resourceId: 'x',
      resourceType: {},
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing resourceId (#2)', async ({request}) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      resourceType: {},
      permissionTypes: [],
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing resourceType (#2)', async ({request}) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      resourceId: 'x',
      permissionTypes: [],
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing body', async ({request}) => {
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing combo ownerId,ownerType', async ({
    request,
  }) => {
    const requestBody = {
      resourceId: 'x',
      resourceType: {},
      permissionTypes: [],
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing combo ownerId,ownerType,permissionTypes', async ({
    request,
  }) => {
    const requestBody = {
      resourceId: 'x',
      resourceType: {},
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing combo ownerId,ownerType,resourceId', async ({
    request,
  }) => {
    const requestBody = {
      resourceType: {},
      permissionTypes: [],
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing combo ownerId,ownerType,resourceType', async ({
    request,
  }) => {
    const requestBody = {
      resourceId: 'x',
      permissionTypes: [],
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing combo ownerId,permissionTypes', async ({
    request,
  }) => {
    const requestBody = {
      ownerType: {},
      resourceId: 'x',
      resourceType: {},
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing combo ownerId,resourceId', async ({
    request,
  }) => {
    const requestBody = {
      ownerType: {},
      resourceType: {},
      permissionTypes: [],
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing combo ownerId,resourceId,permissionTypes', async ({
    request,
  }) => {
    const requestBody = {
      ownerType: {},
      resourceType: {},
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing combo ownerId,resourceId,resourceType', async ({
    request,
  }) => {
    const requestBody = {
      ownerType: {},
      permissionTypes: [],
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing combo ownerId,resourceType', async ({
    request,
  }) => {
    const requestBody = {
      ownerType: {},
      resourceId: 'x',
      permissionTypes: [],
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing combo ownerId,resourceType,permissionTypes', async ({
    request,
  }) => {
    const requestBody = {
      ownerType: {},
      resourceId: 'x',
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing combo ownerType,permissionTypes', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      resourceId: 'x',
      resourceType: {},
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing combo ownerType,resourceId', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      resourceType: {},
      permissionTypes: [],
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing combo ownerType,resourceId,permissionTypes', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      resourceType: {},
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing combo ownerType,resourceId,resourceType', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      permissionTypes: [],
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing combo ownerType,resourceType', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      resourceId: 'x',
      permissionTypes: [],
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing combo ownerType,resourceType,permissionTypes', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      resourceId: 'x',
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing combo resourceId,permissionTypes', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      resourceType: {},
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing combo resourceId,resourceType', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      permissionTypes: [],
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing combo resourceId,resourceType,permissionTypes', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAuthorization - Missing combo resourceType,permissionTypes', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      resourceId: 'x',
    };
    const res = await request.post(buildUrl('/authorizations', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchAuthorizations - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/authorizations/search', undefined),
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
  test('searchAuthorizations - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/authorizations/search', undefined),
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
  test('searchAuthorizations - Enum violation sort.0.field (#1)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'ownerId_INVALID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/authorizations/search', undefined),
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
  test('searchAuthorizations - Enum violation sort.0.field (#2)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'OWNERID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/authorizations/search', undefined),
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
  test('searchAuthorizations - Enum violation sort.0.field (#3)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'ownerid',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/authorizations/search', undefined),
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
  test('searchAuthorizations - Enum violation sort.0.order (#1)', async ({
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
      buildUrl('/authorizations/search', undefined),
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
  test('searchAuthorizations - Enum violation sort.0.order (#2)', async ({
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
      buildUrl('/authorizations/search', undefined),
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
  test('searchAuthorizations - Missing body', async ({request}) => {
    const res = await request.post(
      buildUrl('/authorizations/search', undefined),
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
  test('updateAuthorization - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      resourceId: 'x',
      resourceType: {},
      permissionTypes: ['x'],
      __extraField: 'unexpected',
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Body wrong top-level type', async ({request}) => {
    const requestBody: string[] = [];
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Param ownerId wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 123,
      ownerType: {},
      resourceId: 'x',
      resourceType: {},
      permissionTypes: ['x'],
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Param ownerId wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: true,
      ownerType: {},
      resourceId: 'x',
      resourceType: {},
      permissionTypes: ['x'],
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Param permissionTypes.0 wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      resourceId: 'x',
      resourceType: {},
      permissionTypes: [123],
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Param permissionTypes.0 wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      resourceId: 'x',
      resourceType: {},
      permissionTypes: [true],
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Param resourceId wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      resourceId: 123,
      resourceType: {},
      permissionTypes: ['x'],
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Param resourceId wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      resourceId: true,
      resourceType: {},
      permissionTypes: ['x'],
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing ownerId (#1)', async ({request}) => {
    const requestBody = {
      ownerType: {},
      resourceId: 'x',
      resourceType: {},
      permissionTypes: ['x'],
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing ownerType (#1)', async ({request}) => {
    const requestBody = {
      ownerId: 'x',
      resourceId: 'x',
      resourceType: {},
      permissionTypes: ['x'],
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing resourceId (#1)', async ({request}) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      resourceType: {},
      permissionTypes: ['x'],
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing resourceType (#1)', async ({request}) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      resourceId: 'x',
      permissionTypes: ['x'],
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing ownerId (#2)', async ({request}) => {
    const requestBody = {
      ownerType: {},
      resourceId: 'x',
      resourceType: {},
      permissionTypes: [],
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing ownerType (#2)', async ({request}) => {
    const requestBody = {
      ownerId: 'x',
      resourceId: 'x',
      resourceType: {},
      permissionTypes: [],
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing permissionTypes', async ({request}) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      resourceId: 'x',
      resourceType: {},
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing resourceId (#2)', async ({request}) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      resourceType: {},
      permissionTypes: [],
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing resourceType (#2)', async ({request}) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      resourceId: 'x',
      permissionTypes: [],
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing body', async ({request}) => {
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing combo ownerId,ownerType', async ({
    request,
  }) => {
    const requestBody = {
      resourceId: 'x',
      resourceType: {},
      permissionTypes: [],
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing combo ownerId,ownerType,permissionTypes', async ({
    request,
  }) => {
    const requestBody = {
      resourceId: 'x',
      resourceType: {},
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing combo ownerId,ownerType,resourceId', async ({
    request,
  }) => {
    const requestBody = {
      resourceType: {},
      permissionTypes: [],
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing combo ownerId,ownerType,resourceType', async ({
    request,
  }) => {
    const requestBody = {
      resourceId: 'x',
      permissionTypes: [],
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing combo ownerId,permissionTypes', async ({
    request,
  }) => {
    const requestBody = {
      ownerType: {},
      resourceId: 'x',
      resourceType: {},
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing combo ownerId,resourceId', async ({
    request,
  }) => {
    const requestBody = {
      ownerType: {},
      resourceType: {},
      permissionTypes: [],
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing combo ownerId,resourceId,permissionTypes', async ({
    request,
  }) => {
    const requestBody = {
      ownerType: {},
      resourceType: {},
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing combo ownerId,resourceId,resourceType', async ({
    request,
  }) => {
    const requestBody = {
      ownerType: {},
      permissionTypes: [],
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing combo ownerId,resourceType', async ({
    request,
  }) => {
    const requestBody = {
      ownerType: {},
      resourceId: 'x',
      permissionTypes: [],
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing combo ownerId,resourceType,permissionTypes', async ({
    request,
  }) => {
    const requestBody = {
      ownerType: {},
      resourceId: 'x',
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing combo ownerType,permissionTypes', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      resourceId: 'x',
      resourceType: {},
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing combo ownerType,resourceId', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      resourceType: {},
      permissionTypes: [],
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing combo ownerType,resourceId,permissionTypes', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      resourceType: {},
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing combo ownerType,resourceId,resourceType', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      permissionTypes: [],
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing combo ownerType,resourceType', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      resourceId: 'x',
      permissionTypes: [],
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing combo ownerType,resourceType,permissionTypes', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      resourceId: 'x',
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing combo resourceId,permissionTypes', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      resourceType: {},
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing combo resourceId,resourceType', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      permissionTypes: [],
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing combo resourceId,resourceType,permissionTypes', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
  test('updateAuthorization - Missing combo resourceType,permissionTypes', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {},
      resourceId: 'x',
    };
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'x'}),
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
});
