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

test.describe('Setup Validation API Tests', () => {
  test('createAdminUser - Additional prop __extraField', async ({request}) => {
    const requestBody = {
      password: 'x',
      username: 'x',
      email: 'x',
      __extraField: 'unexpected',
    };
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAdminUser - Body wrong top-level type', async ({request}) => {
    const requestBody: string[] = [];
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAdminUser - Param email wrong type (#1)', async ({request}) => {
    const requestBody = {
      password: 'x',
      username: 'x',
      email: 123,
    };
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAdminUser - Param email wrong type (#2)', async ({request}) => {
    const requestBody = {
      password: 'x',
      username: 'x',
      email: true,
    };
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAdminUser - Param password wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      password: 123,
      username: 'x',
      email: 'x',
    };
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAdminUser - Param password wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      password: true,
      username: 'x',
      email: 'x',
    };
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAdminUser - Param username wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      password: 'x',
      username: 123,
      email: 'x',
    };
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAdminUser - Param username wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      password: 'x',
      username: true,
      email: 'x',
    };
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAdminUser - Missing email', async ({request}) => {
    const requestBody = {
      password: 'x',
      username: 'x',
    };
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAdminUser - Missing password', async ({request}) => {
    const requestBody = {
      username: 'x',
      email: 'x',
    };
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAdminUser - Missing username', async ({request}) => {
    const requestBody = {
      password: 'x',
      email: 'x',
    };
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAdminUser - Missing body', async ({request}) => {
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAdminUser - Missing combo password,email', async ({request}) => {
    const requestBody = {
      username: 'x',
    };
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAdminUser - Missing combo password,username', async ({
    request,
  }) => {
    const requestBody = {
      email: 'x',
    };
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAdminUser - Missing combo password,username,email', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAdminUser - Missing combo username,email', async ({request}) => {
    const requestBody = {
      password: 'x',
    };
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
});
