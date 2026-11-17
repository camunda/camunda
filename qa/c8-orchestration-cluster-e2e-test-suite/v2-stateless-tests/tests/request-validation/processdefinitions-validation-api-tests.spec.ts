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

test.describe('Processdefinitions Validation API Tests', () => {
  test('getProcessDefinition - Path param processDefinitionKey pattern violation', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/process-definitions/{processDefinitionKey}', {
        processDefinitionKey: 'a',
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
  test('getProcessDefinitionInstanceStatistics - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/process-definitions/statistics/process-instances', undefined),
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
  test('getProcessDefinitionInstanceStatistics - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/process-definitions/statistics/process-instances', undefined),
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
  test('getProcessDefinitionInstanceStatistics - Enum violation sort.0.field (#1)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'processDefinitionId_INVALID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/process-definitions/statistics/process-instances', undefined),
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
  test('getProcessDefinitionInstanceStatistics - Enum violation sort.0.field (#2)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'PROCESSDEFINITIONID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/process-definitions/statistics/process-instances', undefined),
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
  test('getProcessDefinitionInstanceStatistics - Enum violation sort.0.field (#3)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'processdefinitionid',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/process-definitions/statistics/process-instances', undefined),
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
  test('getProcessDefinitionInstanceStatistics - Enum violation sort.0.order (#1)', async ({
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
      buildUrl('/process-definitions/statistics/process-instances', undefined),
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
  test('getProcessDefinitionInstanceStatistics - Enum violation sort.0.order (#2)', async ({
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
      buildUrl('/process-definitions/statistics/process-instances', undefined),
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
  test('getProcessDefinitionInstanceVersionStatistics - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        processDefinitionId: null,
      },
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl(
        '/process-definitions/statistics/process-instances-by-version',
        undefined,
      ),
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
  test('getProcessDefinitionInstanceVersionStatistics - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl(
        '/process-definitions/statistics/process-instances-by-version',
        undefined,
      ),
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
  test('getProcessDefinitionInstanceVersionStatistics - Missing filter.processDefinitionId', async ({
    request,
  }) => {
    const requestBody = {
      filter: {},
    };
    const res = await request.post(
      buildUrl(
        '/process-definitions/statistics/process-instances-by-version',
        undefined,
      ),
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
  test('getProcessDefinitionInstanceVersionStatistics - Enum violation sort.0.field (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        processDefinitionId: null,
      },
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'processDefinitionId_INVALID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl(
        '/process-definitions/statistics/process-instances-by-version',
        undefined,
      ),
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
  test('getProcessDefinitionInstanceVersionStatistics - Enum violation sort.0.field (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        processDefinitionId: null,
      },
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'PROCESSDEFINITIONID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl(
        '/process-definitions/statistics/process-instances-by-version',
        undefined,
      ),
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
  test('getProcessDefinitionInstanceVersionStatistics - Enum violation sort.0.field (#3)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        processDefinitionId: null,
      },
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'processdefinitionid',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl(
        '/process-definitions/statistics/process-instances-by-version',
        undefined,
      ),
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
  test('getProcessDefinitionInstanceVersionStatistics - Enum violation sort.0.order (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        processDefinitionId: null,
      },
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
      buildUrl(
        '/process-definitions/statistics/process-instances-by-version',
        undefined,
      ),
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
  test('getProcessDefinitionInstanceVersionStatistics - Enum violation sort.0.order (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        processDefinitionId: null,
      },
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
      buildUrl(
        '/process-definitions/statistics/process-instances-by-version',
        undefined,
      ),
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
  test('getProcessDefinitionInstanceVersionStatistics - Missing filter', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.post(
      buildUrl(
        '/process-definitions/statistics/process-instances-by-version',
        undefined,
      ),
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
  test('getProcessDefinitionInstanceVersionStatistics - Missing body', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl(
        '/process-definitions/statistics/process-instances-by-version',
        undefined,
      ),
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
  test('getProcessDefinitionMessageSubscriptionStatistics - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl(
        '/process-definitions/statistics/message-subscriptions',
        undefined,
      ),
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
  test('getProcessDefinitionMessageSubscriptionStatistics - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl(
        '/process-definitions/statistics/message-subscriptions',
        undefined,
      ),
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
  test('getProcessDefinitionStatistics - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl(
        '/process-definitions/{processDefinitionKey}/statistics/element-instances',
        {processDefinitionKey: 'x'},
      ),
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
  test('getProcessDefinitionStatistics - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl(
        '/process-definitions/{processDefinitionKey}/statistics/element-instances',
        {processDefinitionKey: 'x'},
      ),
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
  test('getProcessDefinitionStatistics - Path param processDefinitionKey pattern violation', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl(
        '/process-definitions/{processDefinitionKey}/statistics/element-instances',
        {processDefinitionKey: 'a'},
      ),
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
        {processDefinitionKey: 'x'},
      ),
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
        {processDefinitionKey: 'x'},
      ),
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
  test('getProcessDefinitionXML - Path param processDefinitionKey pattern violation', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/process-definitions/{processDefinitionKey}/xml', {
        processDefinitionKey: 'a',
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
  test('getStartProcessForm - Path param processDefinitionKey pattern violation', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/process-definitions/{processDefinitionKey}/form', {
        processDefinitionKey: 'a',
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
  test('searchProcessDefinitions - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/process-definitions/search', undefined),
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
  test('searchProcessDefinitions - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/process-definitions/search', undefined),
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
  test('searchProcessDefinitions - Enum violation filter.state (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        state: {
          __invalidEnum: true,
          value: 'ACTIVE_INVALID',
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
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchProcessDefinitions - Enum violation filter.state (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        state: {
          __invalidEnum: true,
          value: 'active',
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
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
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
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
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
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
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
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
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
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
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
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchProcessDefinitionVariableNames - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl(
        '/process-definitions/{processDefinitionKey}/variable-names/search',
        {processDefinitionKey: 'x'},
      ),
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
  test('searchProcessDefinitionVariableNames - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl(
        '/process-definitions/{processDefinitionKey}/variable-names/search',
        {processDefinitionKey: 'x'},
      ),
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
  test('searchProcessDefinitionVariableNames - Path param processDefinitionKey pattern violation', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl(
        '/process-definitions/{processDefinitionKey}/variable-names/search',
        {processDefinitionKey: 'a'},
      ),
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
