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

test.describe('Mappingrules Validation API Tests', () => {
  test('createMappingRule - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      claimName: 'x',
      claimValue: 'x',
      name: 'x',
      mappingRuleId: null,
      __unexpectedField: 'x',
    };
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createMappingRule - Body wrong top-level type', async ({request}) => {
    const requestBody: string[] = [];
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createMappingRule - Param claimName wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      claimName: 123,
      claimValue: 'x',
      name: 'x',
      mappingRuleId: null,
    };
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createMappingRule - Param claimName wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      claimName: true,
      claimValue: 'x',
      name: 'x',
      mappingRuleId: null,
    };
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createMappingRule - Param claimValue wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      claimName: 'x',
      claimValue: 123,
      name: 'x',
      mappingRuleId: null,
    };
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createMappingRule - Param claimValue wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      claimName: 'x',
      claimValue: true,
      name: 'x',
      mappingRuleId: null,
    };
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createMappingRule - Param name wrong type (#1)', async ({request}) => {
    const requestBody = {
      claimName: 'x',
      claimValue: 'x',
      name: 123,
      mappingRuleId: null,
    };
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createMappingRule - Param name wrong type (#2)', async ({request}) => {
    const requestBody = {
      claimName: 'x',
      claimValue: 'x',
      name: true,
      mappingRuleId: null,
    };
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createMappingRule - Missing claimName', async ({request}) => {
    const requestBody = {
      claimValue: 'x',
      name: 'x',
      mappingRuleId: null,
    };
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createMappingRule - Missing claimValue', async ({request}) => {
    const requestBody = {
      claimName: 'x',
      name: 'x',
      mappingRuleId: null,
    };
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createMappingRule - Missing mappingRuleId (#1)', async ({request}) => {
    const requestBody = {
      claimName: 'x',
      claimValue: 'x',
      name: 'x',
    };
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createMappingRule - Missing name', async ({request}) => {
    const requestBody = {
      claimName: 'x',
      claimValue: 'x',
      mappingRuleId: null,
    };
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createMappingRule - Missing mappingRuleId (#2)', async ({request}) => {
    const requestBody = {};
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createMappingRule - Missing body', async ({request}) => {
    const res = await request.post(buildUrl('/mapping-rules', undefined), {
      headers: jsonHeaders(),
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  // Known failing (see known-failing-tests.json): empty trailing path segment never reaches the controller (resolves as a static-resource 404 in Spring MVC before validation runs) - routing-level fix needed, not a validator fix
  test.skip('deleteMappingRule - Path param mappingRuleId pattern violation', async ({
    request,
  }) => {
    const res = await request.delete(
      buildUrl('/mapping-rules/{mappingRuleId}', {mappingRuleId: '!'}),
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
  // Known failing (see known-failing-tests.json): empty trailing path segment never reaches the controller (resolves as a static-resource 404 in Spring MVC before validation runs) - routing-level fix needed, not a validator fix
  test.skip('getMappingRule - Path param mappingRuleId pattern violation', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/mapping-rules/{mappingRuleId}', {mappingRuleId: '!'}),
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
  test('searchMappingRule - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/mapping-rules/search', undefined),
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
  test('searchMappingRule - Body wrong top-level type', async ({request}) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/mapping-rules/search', undefined),
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
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
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
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
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
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
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
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
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
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
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
      buildUrl('/mapping-rules/{mappingRuleId}', {mappingRuleId: 'x'}),
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
  test('updateMappingRule - Body wrong top-level type', async ({request}) => {
    const requestBody: string[] = [];
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', {mappingRuleId: 'x'}),
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
  test('updateMappingRule - Param claimName wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      claimName: 123,
      claimValue: 'x',
      name: 'x',
    };
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', {mappingRuleId: 'x'}),
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
  test('updateMappingRule - Param claimName wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      claimName: true,
      claimValue: 'x',
      name: 'x',
    };
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', {mappingRuleId: 'x'}),
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
  test('updateMappingRule - Param claimValue wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      claimName: 'x',
      claimValue: 123,
      name: 'x',
    };
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', {mappingRuleId: 'x'}),
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
  test('updateMappingRule - Param claimValue wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      claimName: 'x',
      claimValue: true,
      name: 'x',
    };
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', {mappingRuleId: 'x'}),
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
  test('updateMappingRule - Param name wrong type (#1)', async ({request}) => {
    const requestBody = {
      claimName: 'x',
      claimValue: 'x',
      name: 123,
    };
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', {mappingRuleId: 'x'}),
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
  test('updateMappingRule - Param name wrong type (#2)', async ({request}) => {
    const requestBody = {
      claimName: 'x',
      claimValue: 'x',
      name: true,
    };
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', {mappingRuleId: 'x'}),
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
  test('updateMappingRule - Missing claimName', async ({request}) => {
    const requestBody = {
      claimValue: 'x',
      name: 'x',
    };
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', {mappingRuleId: 'x'}),
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
  test('updateMappingRule - Missing claimValue', async ({request}) => {
    const requestBody = {
      claimName: 'x',
      name: 'x',
    };
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', {mappingRuleId: 'x'}),
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
  test('updateMappingRule - Missing name', async ({request}) => {
    const requestBody = {
      claimName: 'x',
      claimValue: 'x',
    };
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', {mappingRuleId: 'x'}),
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
  test('updateMappingRule - Path param mappingRuleId pattern violation', async ({
    request,
  }) => {
    const res = await request.put(
      buildUrl('/mapping-rules/{mappingRuleId}', {mappingRuleId: '!'}),
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
