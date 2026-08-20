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

test.describe('Authorizations Validation API Tests', () => {
  test('createAuthorization - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
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
  test('createAuthorization - oneOf ambiguous', async ({request}) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: 'USER',
      resourceId: 'x',
      resourceType: 'x',
      permissionTypes: [],
      resourcePropertyName: 'x',
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
  test('createAuthorization - oneOf cross bleed', async ({request}) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: 'USER',
      resourceId: 'x',
      resourceType: 'x',
      permissionTypes: [],
      resourcePropertyName: 'x',
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
  test('createAuthorization - oneOf none match', async ({request}) => {
    const requestBody = {
      ownerType: '__NOT_ENUM__',
      resourceId: 'x',
      resourceType: 'x',
      permissionTypes: [],
      resourcePropertyName: 'x',
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
  test('createAuthorization - oneOf violation', async ({request}) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: 'USER',
      resourceId: 'x',
      resourceType: 'x',
      permissionTypes: [],
      resourcePropertyName: 'x',
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
  test('updateAuthorization - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
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
  test('updateAuthorization - oneOf ambiguous', async ({request}) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: 'USER',
      resourceId: 'x',
      resourceType: 'x',
      permissionTypes: [],
      resourcePropertyName: 'x',
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
  test('updateAuthorization - oneOf cross bleed', async ({request}) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: 'USER',
      resourceId: 'x',
      resourceType: 'x',
      permissionTypes: [],
      resourcePropertyName: 'x',
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
  test('updateAuthorization - oneOf none match', async ({request}) => {
    const requestBody = {
      ownerType: '__NOT_ENUM__',
      resourceId: 'x',
      resourceType: 'x',
      permissionTypes: [],
      resourcePropertyName: 'x',
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
  test('updateAuthorization - oneOf violation', async ({request}) => {
    const requestBody = {
      ownerId: 'x',
      ownerType: 'USER',
      resourceId: 'x',
      resourceType: 'x',
      permissionTypes: [],
      resourcePropertyName: 'x',
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
