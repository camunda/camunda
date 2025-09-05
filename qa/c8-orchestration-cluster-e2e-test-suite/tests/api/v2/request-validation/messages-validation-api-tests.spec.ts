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

test.describe('Messages Validation API Tests', () => {
  test('correlateMessage - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      name: 'x',
      correlationKey: 'x',
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
  test('correlateMessage - Body wrong top-level type', async ({ request }) => {
    const requestBody = [];
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
  test('correlateMessage - Param correlationKey wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      name: 'x',
      correlationKey: 123,
    };
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
  test('correlateMessage - Param correlationKey wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      name: 'x',
      correlationKey: true,
    };
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
  test('correlateMessage - Param name wrong type (#1)', async ({ request }) => {
    const requestBody = {
      name: 123,
      correlationKey: 'x',
    };
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
  test('correlateMessage - Param name wrong type (#2)', async ({ request }) => {
    const requestBody = {
      name: true,
      correlationKey: 'x',
    };
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
  test('correlateMessage - Missing correlationKey', async ({ request }) => {
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
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('correlateMessage - Missing name', async ({ request }) => {
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
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('correlateMessage - Missing body', async ({ request }) => {
    const res = await request.post(
      buildUrl('/messages/correlation', undefined),
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
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('publishMessage - Additional prop __extraField', async ({ request }) => {
    const requestBody = {
      name: 'x',
      correlationKey: 'x',
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
  test('publishMessage - Body wrong top-level type', async ({ request }) => {
    const requestBody = [];
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
  test('publishMessage - Param correlationKey wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      name: 'x',
      correlationKey: 123,
    };
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
  test('publishMessage - Param correlationKey wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      name: 'x',
      correlationKey: true,
    };
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
  test('publishMessage - Param name wrong type (#1)', async ({ request }) => {
    const requestBody = {
      name: 123,
      correlationKey: 'x',
    };
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
  test('publishMessage - Param name wrong type (#2)', async ({ request }) => {
    const requestBody = {
      name: true,
      correlationKey: 'x',
    };
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
  test('publishMessage - Missing correlationKey', async ({ request }) => {
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
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('publishMessage - Missing name', async ({ request }) => {
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
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('publishMessage - Missing body', async ({ request }) => {
    const res = await request.post(
      buildUrl('/messages/publication', undefined),
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
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
});
