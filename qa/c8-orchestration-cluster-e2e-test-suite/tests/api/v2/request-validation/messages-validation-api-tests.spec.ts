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

test.describe('Messages Validation API Tests', () => {
  test('correlateMessage - Additional prop __extraField', async ({request}) => {
    const requestBody = {
      name: 'x',
      correlationKey: 'x',
      tenantId: 'x',
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
  test('correlateMessage - Body wrong top-level type', async ({request}) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
  test('correlateMessage - Param correlationKey wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      name: 'x',
      correlationKey: 123,
      tenantId: 'x',
    };
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
  test('correlateMessage - Param correlationKey wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      name: 'x',
      correlationKey: true,
      tenantId: 'x',
    };
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
  test('correlateMessage - Param name wrong type (#1)', async ({request}) => {
    const requestBody = {
      name: 123,
      correlationKey: 'x',
      tenantId: 'x',
    };
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
  test('correlateMessage - Param name wrong type (#2)', async ({request}) => {
    const requestBody = {
      name: true,
      correlationKey: 'x',
      tenantId: 'x',
    };
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
  test('correlateMessage - Param tenantId wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      name: 'x',
      correlationKey: 'x',
      tenantId: 123,
    };
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
  test('correlateMessage - Param tenantId wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      name: 'x',
      correlationKey: 'x',
      tenantId: true,
    };
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
  test('correlateMessage - Constraint violation tenantId (#1)', async ({
    request,
  }) => {
    const requestBody = {
      name: 'x',
      correlationKey: 'x',
      tenantId:
        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    };
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
  test('correlateMessage - Constraint violation tenantId (#2)', async ({
    request,
  }) => {
    const requestBody = {
      name: 'x',
      correlationKey: 'x',
      tenantId: '',
    };
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
  test('correlateMessage - Constraint violation tenantId (#3)', async ({
    request,
  }) => {
    const requestBody = {
      name: 'x',
      correlationKey: 'x',
      tenantId: '__NO_MATCH__',
    };
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
  test('correlateMessage - Constraint violation tenantId (#4)', async ({
    request,
  }) => {
    const requestBody = {
      name: 'x',
      correlationKey: 'x',
      tenantId:
        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    };
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
  test('correlateMessage - Missing correlationKey (#1)', async ({request}) => {
    const requestBody = {
      name: 'x',
      tenantId: 'x',
    };
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
  test('correlateMessage - Missing name (#1)', async ({request}) => {
    const requestBody = {
      correlationKey: 'x',
      tenantId: 'x',
    };
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
  test('correlateMessage - Missing correlationKey (#2)', async ({request}) => {
    const requestBody = {
      name: 'x',
    };
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
  test('correlateMessage - Missing name (#2)', async ({request}) => {
    const requestBody = {
      correlationKey: 'x',
    };
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
  test('correlateMessage - Missing body', async ({request}) => {
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
  test('correlateMessage - Missing combo name,correlationKey', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
  test('publishMessage - Additional prop __extraField', async ({request}) => {
    const requestBody = {
      name: 'x',
      correlationKey: 'x',
      tenantId: 'x',
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
  test('publishMessage - Body wrong top-level type', async ({request}) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
  test('publishMessage - Param correlationKey wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      name: 'x',
      correlationKey: 123,
      tenantId: 'x',
    };
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
  test('publishMessage - Param correlationKey wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      name: 'x',
      correlationKey: true,
      tenantId: 'x',
    };
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
  test('publishMessage - Param name wrong type (#1)', async ({request}) => {
    const requestBody = {
      name: 123,
      correlationKey: 'x',
      tenantId: 'x',
    };
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
  test('publishMessage - Param name wrong type (#2)', async ({request}) => {
    const requestBody = {
      name: true,
      correlationKey: 'x',
      tenantId: 'x',
    };
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
  test('publishMessage - Param tenantId wrong type (#1)', async ({request}) => {
    const requestBody = {
      name: 'x',
      correlationKey: 'x',
      tenantId: 123,
    };
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
  test('publishMessage - Param tenantId wrong type (#2)', async ({request}) => {
    const requestBody = {
      name: 'x',
      correlationKey: 'x',
      tenantId: true,
    };
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
  test('publishMessage - Constraint violation tenantId (#1)', async ({
    request,
  }) => {
    const requestBody = {
      name: 'x',
      correlationKey: 'x',
      tenantId:
        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    };
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
  test('publishMessage - Constraint violation tenantId (#2)', async ({
    request,
  }) => {
    const requestBody = {
      name: 'x',
      correlationKey: 'x',
      tenantId: '',
    };
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
  test('publishMessage - Constraint violation tenantId (#3)', async ({
    request,
  }) => {
    const requestBody = {
      name: 'x',
      correlationKey: 'x',
      tenantId: '__NO_MATCH__',
    };
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
  test('publishMessage - Constraint violation tenantId (#4)', async ({
    request,
  }) => {
    const requestBody = {
      name: 'x',
      correlationKey: 'x',
      tenantId:
        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    };
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
  test('publishMessage - Missing correlationKey (#1)', async ({request}) => {
    const requestBody = {
      name: 'x',
      tenantId: 'x',
    };
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
  test('publishMessage - Missing name (#1)', async ({request}) => {
    const requestBody = {
      correlationKey: 'x',
      tenantId: 'x',
    };
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
  test('publishMessage - Missing correlationKey (#2)', async ({request}) => {
    const requestBody = {
      name: 'x',
    };
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
  test('publishMessage - Missing name (#2)', async ({request}) => {
    const requestBody = {
      correlationKey: 'x',
    };
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
  test('publishMessage - Missing body', async ({request}) => {
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
  test('publishMessage - Missing combo name,correlationKey', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
