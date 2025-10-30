/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/*
 * GENERATED FILE - DO NOT EDIT MANUALLY
 * Generated At: 2025-09-22T18:40:25.704Z
 * Spec Commit: f2fd6a1393ca4c7feae1efd10c7c863c0f146187
 */
import {test, expect} from '@playwright/test';
import {jsonHeaders, buildUrl} from '../../../utils/http';

test.describe('Authorizations Validation API Tests', () => {
  test('createAuthorization - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: 'USER',
      resourceId: 'x',
      resourceType: 'x',
      permissionTypes: [null],
      __unexpectedField: 'x',
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
      ownerType: 'USER',
      resourceId: 'x',
      resourceType: 'x',
      permissionTypes: [null],
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
      ownerType: 'USER',
      resourceId: 'x',
      resourceType: 'x',
      permissionTypes: [null],
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
  test('createAuthorization - Param ownerType wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: 123,
      resourceId: 'x',
      resourceType: 'x',
      permissionTypes: [null],
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
  test('createAuthorization - Param ownerType wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: true,
      resourceId: 'x',
      resourceType: 'x',
      permissionTypes: [null],
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
      ownerType: 'USER',
      resourceId: 123,
      resourceType: 'x',
      permissionTypes: [null],
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
      ownerType: 'USER',
      resourceId: true,
      resourceType: 'x',
      permissionTypes: [null],
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
  test('createAuthorization - Param resourceType wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: 'USER',
      resourceId: 'x',
      resourceType: 123,
      permissionTypes: [null],
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
  test('createAuthorization - Param resourceType wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: 'USER',
      resourceId: 'x',
      resourceType: true,
      permissionTypes: [null],
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
      ownerType: 'USER',
      resourceId: 'x',
      resourceType: 'x',
      permissionTypes: [null],
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
      resourceType: 'x',
      permissionTypes: [null],
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
      ownerType: 'USER',
      resourceType: 'x',
      permissionTypes: [null],
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
      ownerType: 'USER',
      resourceId: 'x',
      permissionTypes: [null],
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
  test('createAuthorization - Enum violation ownerType (#1)', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {
        __invalidEnum: true,
        value: 'USER_INVALID',
      },
      resourceId: 'x',
      resourceType: 'x',
      permissionTypes: [null],
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
  test('createAuthorization - Enum violation ownerType (#2)', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {
        __invalidEnum: true,
        value: 'user',
      },
      resourceId: 'x',
      resourceType: 'x',
      permissionTypes: [null],
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
      ownerType: 'USER',
      resourceId: 'x',
      resourceType: 'x',
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
      resourceType: 'x',
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
      ownerType: 'USER',
      resourceId: 'x',
      resourceType: 'x',
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
      ownerType: 'USER',
      resourceType: 'x',
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
      ownerType: 'USER',
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
      resourceType: 'x',
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
      resourceType: 'x',
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
      resourceType: 'x',
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
      ownerType: 'USER',
      resourceId: 'x',
      resourceType: 'x',
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
      ownerType: 'USER',
      resourceType: 'x',
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
      ownerType: 'USER',
      resourceType: 'x',
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
      ownerType: 'USER',
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
      ownerType: 'USER',
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
      ownerType: 'USER',
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
      resourceType: 'x',
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
      resourceType: 'x',
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
      resourceType: 'x',
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
      ownerType: 'USER',
      resourceType: 'x',
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
      ownerType: 'USER',
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
      ownerType: 'USER',
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
      ownerType: 'USER',
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
  test('deleteAuthorization - Path param authorizationKey pattern violation', async ({
    request,
  }) => {
    const res = await request.delete(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'a'}),
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
  test('getAuthorization - Path param authorizationKey pattern violation', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'a'}),
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
  test('searchAuthorizations - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      __unexpectedField: 'x',
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
  test('searchAuthorizations - Enum violation filter.ownerType (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        ownerType: {
          __invalidEnum: true,
          value: 'USER_INVALID',
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
  test('searchAuthorizations - Enum violation filter.ownerType (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        ownerType: {
          __invalidEnum: true,
          value: 'user',
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
  test('updateAuthorization - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: 'USER',
      resourceId: 'x',
      resourceType: 'x',
      permissionTypes: [null],
      __unexpectedField: 'x',
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
      ownerType: 'USER',
      resourceId: 'x',
      resourceType: 'x',
      permissionTypes: [null],
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
      ownerType: 'USER',
      resourceId: 'x',
      resourceType: 'x',
      permissionTypes: [null],
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
  test('updateAuthorization - Param ownerType wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: 123,
      resourceId: 'x',
      resourceType: 'x',
      permissionTypes: [null],
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
  test('updateAuthorization - Param ownerType wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: true,
      resourceId: 'x',
      resourceType: 'x',
      permissionTypes: [null],
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
      ownerType: 'USER',
      resourceId: 123,
      resourceType: 'x',
      permissionTypes: [null],
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
      ownerType: 'USER',
      resourceId: true,
      resourceType: 'x',
      permissionTypes: [null],
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
  test('updateAuthorization - Param resourceType wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: 'USER',
      resourceId: 'x',
      resourceType: 123,
      permissionTypes: [null],
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
  test('updateAuthorization - Param resourceType wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: 'USER',
      resourceId: 'x',
      resourceType: true,
      permissionTypes: [null],
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
      ownerType: 'USER',
      resourceId: 'x',
      resourceType: 'x',
      permissionTypes: [null],
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
      resourceType: 'x',
      permissionTypes: [null],
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
      ownerType: 'USER',
      resourceType: 'x',
      permissionTypes: [null],
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
      ownerType: 'USER',
      resourceId: 'x',
      permissionTypes: [null],
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
  test('updateAuthorization - Enum violation ownerType (#1)', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {
        __invalidEnum: true,
        value: 'USER_INVALID',
      },
      resourceId: 'x',
      resourceType: 'x',
      permissionTypes: [null],
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
  test('updateAuthorization - Enum violation ownerType (#2)', async ({
    request,
  }) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: {
        __invalidEnum: true,
        value: 'user',
      },
      resourceId: 'x',
      resourceType: 'x',
      permissionTypes: [null],
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
      ownerType: 'USER',
      resourceId: 'x',
      resourceType: 'x',
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
      resourceType: 'x',
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
      ownerType: 'USER',
      resourceId: 'x',
      resourceType: 'x',
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
      ownerType: 'USER',
      resourceType: 'x',
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
      ownerType: 'USER',
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
      resourceType: 'x',
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
      resourceType: 'x',
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
      resourceType: 'x',
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
      ownerType: 'USER',
      resourceId: 'x',
      resourceType: 'x',
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
      ownerType: 'USER',
      resourceType: 'x',
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
      ownerType: 'USER',
      resourceType: 'x',
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
      ownerType: 'USER',
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
      ownerType: 'USER',
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
      ownerType: 'USER',
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
      resourceType: 'x',
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
      resourceType: 'x',
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
      resourceType: 'x',
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
      ownerType: 'USER',
      resourceType: 'x',
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
      ownerType: 'USER',
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
      ownerType: 'USER',
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
      ownerType: 'USER',
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
  test('updateAuthorization - Path param authorizationKey pattern violation', async ({
    request,
  }) => {
    const res = await request.put(
      buildUrl('/authorizations/{authorizationKey}', {authorizationKey: 'a'}),
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
