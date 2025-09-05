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

test.describe('Processdefinitions Validation API Tests', () => {
  test('getProcessDefinitionStatistics - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl(
        '/process-definitions/{processDefinitionKey}/statistics/element-instances',
        { processDefinitionKey: 'x' },
      ),
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
  test('getProcessDefinitionStatistics - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody = [];
    const res = await request.post(
      buildUrl(
        '/process-definitions/{processDefinitionKey}/statistics/element-instances',
        { processDefinitionKey: 'x' },
      ),
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
  test('getProcessDefinitionStatistics - Missing body', async ({ request }) => {
    const res = await request.post(
      buildUrl(
        '/process-definitions/{processDefinitionKey}/statistics/element-instances',
        { processDefinitionKey: 'x' },
      ),
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
  test('getProcessDefinitionStatistics - uniqueItems violation filter.$or.0.tags', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        $or: {
          '0': {
            tags: [1, 1, 1],
          },
        },
      },
    };
    const res = await request.post(
      buildUrl(
        '/process-definitions/{processDefinitionKey}/statistics/element-instances',
        { processDefinitionKey: 'x' },
      ),
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
  test('getProcessDefinitionStatistics - uniqueItems violation filter.tags', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [1, 1, 1],
      },
    };
    const res = await request.post(
      buildUrl(
        '/process-definitions/{processDefinitionKey}/statistics/element-instances',
        { processDefinitionKey: 'x' },
      ),
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
  test('searchProcessDefinitions - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/process-definitions/search', undefined),
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
  test('searchProcessDefinitions - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody = [];
    const res = await request.post(
      buildUrl('/process-definitions/search', undefined),
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
  test('searchProcessDefinitions - Enum violation sort.0.field (#1)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'processDefinitionKey_INVALID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/process-definitions/search', undefined),
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
  test('searchProcessDefinitions - Enum violation sort.0.field (#2)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'PROCESSDEFINITIONKEY',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/process-definitions/search', undefined),
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
  test('searchProcessDefinitions - Enum violation sort.0.field (#3)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'processdefinitionkey',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/process-definitions/search', undefined),
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
  test('searchProcessDefinitions - Enum violation sort.0.order (#1)', async ({
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
      buildUrl('/process-definitions/search', undefined),
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
  test('searchProcessDefinitions - Enum violation sort.0.order (#2)', async ({
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
      buildUrl('/process-definitions/search', undefined),
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
  test('searchProcessDefinitions - Missing body', async ({ request }) => {
    const res = await request.post(
      buildUrl('/process-definitions/search', undefined),
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
});
