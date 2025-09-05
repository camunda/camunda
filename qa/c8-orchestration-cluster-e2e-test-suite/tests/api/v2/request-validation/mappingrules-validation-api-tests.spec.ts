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

test.describe('Mappingrules Validation API Tests', () => {
  test('createMappingRule - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      claimName: 'x',
      claimValue: 'x',
      name: 'x',
      mappingRuleId: 'x',
      __extraField: 'unexpected',
    };
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
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
  test('createMappingRule - Body wrong top-level type', async ({ request }) => {
    const requestBody = [];
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
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
  test('createMappingRule - Param claimName wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      claimName: 123,
      claimValue: 'x',
      name: 'x',
      mappingRuleId: 'x',
    };
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
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
  test('createMappingRule - Param claimName wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      claimName: true,
      claimValue: 'x',
      name: 'x',
      mappingRuleId: 'x',
    };
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
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
  test('createMappingRule - Param claimValue wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      claimName: 'x',
      claimValue: 123,
      name: 'x',
      mappingRuleId: 'x',
    };
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
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
  test('createMappingRule - Param claimValue wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      claimName: 'x',
      claimValue: true,
      name: 'x',
      mappingRuleId: 'x',
    };
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
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
  test('createMappingRule - Param mappingRuleId wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      claimName: 'x',
      claimValue: 'x',
      name: 'x',
      mappingRuleId: 123,
    };
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
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
  test('createMappingRule - Param mappingRuleId wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      claimName: 'x',
      claimValue: 'x',
      name: 'x',
      mappingRuleId: true,
    };
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
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
  test('createMappingRule - Param name wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      claimName: 'x',
      claimValue: 'x',
      name: 123,
      mappingRuleId: 'x',
    };
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
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
  test('createMappingRule - Param name wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      claimName: 'x',
      claimValue: 'x',
      name: true,
      mappingRuleId: 'x',
    };
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
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
  test('createMappingRule - Missing claimName', async ({ request }) => {
    const requestBody = {
      claimValue: 'x',
      name: 'x',
      mappingRuleId: 'x',
    };
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
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
  test('createMappingRule - Missing claimValue', async ({ request }) => {
    const requestBody = {
      claimName: 'x',
      name: 'x',
      mappingRuleId: 'x',
    };
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
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
  test('createMappingRule - Missing mappingRuleId (#1)', async ({
    request,
  }) => {
    const requestBody = {
      claimName: 'x',
      claimValue: 'x',
      name: 'x',
    };
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
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
  test('createMappingRule - Missing name', async ({ request }) => {
    const requestBody = {
      claimName: 'x',
      claimValue: 'x',
      mappingRuleId: 'x',
    };
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
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
  test('createMappingRule - Missing mappingRuleId (#2)', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
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
  test('createMappingRule - Missing body', async ({ request }) => {
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
      headers: jsonHeaders(),
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('deleteMappingRule - Param mappingRuleId wrong type', async ({
    request,
  }) => {
    const res = await request.delete(
      buildUrl('/mapping-rules/{mappingRuleId}', { mappingRuleId: '12345' }),
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
  test('getMappingRule - Param mappingRuleId wrong type', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/mapping-rules/{mappingRuleId}', { mappingRuleId: '12345' }),
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
  test('searchMappingRule - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/mapping-rules/search', undefined),
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
  test('searchMappingRule - Body wrong top-level type', async ({ request }) => {
    const requestBody = [];
    const res = await request.post(
      buildUrl('/mapping-rules/search', undefined),
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
  test('searchMappingRule - Enum violation sort.0.field (#1)', async ({
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
      buildUrl('/mapping-rules/search', undefined),
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
  test('searchMappingRule - Enum violation sort.0.field (#2)', async ({
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
      buildUrl('/mapping-rules/search', undefined),
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
  test('searchMappingRule - Enum violation sort.0.field (#3)', async ({
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
      buildUrl('/mapping-rules/search', undefined),
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
  test('searchMappingRule - Enum violation sort.0.order (#1)', async ({
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
      buildUrl('/mapping-rules/search', undefined),
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
  test('searchMappingRule - Enum violation sort.0.order (#2)', async ({
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
      buildUrl('/mapping-rules/search', undefined),
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
  test('searchMappingRule - Missing body', async ({ request }) => {
    const res = await request.post(
      buildUrl('/mapping-rules/search', undefined),
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
  test('updateMappingRule - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      claimName: 'x',
      claimValue: 'x',
      name: 'x',
      __extraField: 'unexpected',
    };
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', { mappingRuleId: 'x' }),
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
  test('updateMappingRule - Body wrong top-level type', async ({ request }) => {
    const requestBody = [];
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', { mappingRuleId: 'x' }),
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
  test('updateMappingRule - Param claimName wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      claimName: 123,
      claimValue: 'x',
      name: 'x',
    };
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', { mappingRuleId: 'x' }),
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
  test('updateMappingRule - Param claimName wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      claimName: true,
      claimValue: 'x',
      name: 'x',
    };
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', { mappingRuleId: 'x' }),
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
  test('updateMappingRule - Param claimValue wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      claimName: 'x',
      claimValue: 123,
      name: 'x',
    };
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', { mappingRuleId: 'x' }),
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
  test('updateMappingRule - Param claimValue wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      claimName: 'x',
      claimValue: true,
      name: 'x',
    };
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', { mappingRuleId: 'x' }),
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
  test('updateMappingRule - Param name wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      claimName: 'x',
      claimValue: 'x',
      name: 123,
    };
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', { mappingRuleId: 'x' }),
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
  test('updateMappingRule - Param name wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      claimName: 'x',
      claimValue: 'x',
      name: true,
    };
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', { mappingRuleId: 'x' }),
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
  test('updateMappingRule - Missing claimName', async ({ request }) => {
    const requestBody = {
      claimValue: 'x',
      name: 'x',
    };
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', { mappingRuleId: 'x' }),
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
  test('updateMappingRule - Missing claimValue', async ({ request }) => {
    const requestBody = {
      claimName: 'x',
      name: 'x',
    };
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', { mappingRuleId: 'x' }),
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
  test('updateMappingRule - Missing name', async ({ request }) => {
    const requestBody = {
      claimName: 'x',
      claimValue: 'x',
    };
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', { mappingRuleId: 'x' }),
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
  test('updateMappingRule - Missing body', async ({ request }) => {
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', { mappingRuleId: 'x' }),
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
  test('updateMappingRule - Param mappingRuleId wrong type', async ({
    request,
  }) => {
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', { mappingRuleId: '12345' }),
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
