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

test.describe('Globaltasklisteners Validation API Tests', () => {
  test('createGlobalTaskListener - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      id: 'x',
      type: 'x',
      eventTypes: ['all'],
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/global-task-listeners', undefined),
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
  test('createGlobalTaskListener - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/global-task-listeners', undefined),
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
  test('createGlobalTaskListener - Param eventTypes.0 wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      id: 'x',
      type: 'x',
      eventTypes: [123],
    };
    const res = await request.post(
      buildUrl('/global-task-listeners', undefined),
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
  test('createGlobalTaskListener - Param eventTypes.0 wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      id: 'x',
      type: 'x',
      eventTypes: [true],
    };
    const res = await request.post(
      buildUrl('/global-task-listeners', undefined),
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
  test('createGlobalTaskListener - Param id wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      id: 123,
      type: 'x',
      eventTypes: ['all'],
    };
    const res = await request.post(
      buildUrl('/global-task-listeners', undefined),
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
  test('createGlobalTaskListener - Param id wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      id: true,
      type: 'x',
      eventTypes: ['all'],
    };
    const res = await request.post(
      buildUrl('/global-task-listeners', undefined),
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
  test('createGlobalTaskListener - Param type wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      id: 'x',
      type: 123,
      eventTypes: ['all'],
    };
    const res = await request.post(
      buildUrl('/global-task-listeners', undefined),
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
  test('createGlobalTaskListener - Param type wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      id: 'x',
      type: true,
      eventTypes: ['all'],
    };
    const res = await request.post(
      buildUrl('/global-task-listeners', undefined),
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
  test('createGlobalTaskListener - Constraint violation id (#1)', async ({
    request,
  }) => {
    const requestBody = {
      id: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      type: 'x',
      eventTypes: ['all'],
    };
    const res = await request.post(
      buildUrl('/global-task-listeners', undefined),
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
  test('createGlobalTaskListener - Constraint violation id (#2)', async ({
    request,
  }) => {
    const requestBody = {
      id: '',
      type: 'x',
      eventTypes: ['all'],
    };
    const res = await request.post(
      buildUrl('/global-task-listeners', undefined),
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
  test('createGlobalTaskListener - Constraint violation id (#3)', async ({
    request,
  }) => {
    const requestBody = {
      id: '\n',
      type: 'x',
      eventTypes: ['all'],
    };
    const res = await request.post(
      buildUrl('/global-task-listeners', undefined),
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
  test('createGlobalTaskListener - Constraint violation id (#4)', async ({
    request,
  }) => {
    const requestBody = {
      id: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      type: 'x',
      eventTypes: ['all'],
    };
    const res = await request.post(
      buildUrl('/global-task-listeners', undefined),
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
  test('createGlobalTaskListener - Missing id (#1)', async ({request}) => {
    const requestBody = {
      type: 'x',
      eventTypes: ['all'],
    };
    const res = await request.post(
      buildUrl('/global-task-listeners', undefined),
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
  test('createGlobalTaskListener - Missing type (#1)', async ({request}) => {
    const requestBody = {
      id: 'x',
      eventTypes: ['all'],
    };
    const res = await request.post(
      buildUrl('/global-task-listeners', undefined),
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
  test('createGlobalTaskListener - Enum violation eventTypes.0 (#1)', async ({
    request,
  }) => {
    const requestBody = {
      id: 'x',
      type: 'x',
      eventTypes: [
        {
          __invalidEnum: true,
          value: 'all_INVALID',
        },
      ],
    };
    const res = await request.post(
      buildUrl('/global-task-listeners', undefined),
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
  test('createGlobalTaskListener - Enum violation eventTypes.0 (#2)', async ({
    request,
  }) => {
    const requestBody = {
      id: 'x',
      type: 'x',
      eventTypes: [
        {
          __invalidEnum: true,
          value: 'ALL',
        },
      ],
    };
    const res = await request.post(
      buildUrl('/global-task-listeners', undefined),
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
  test('createGlobalTaskListener - Missing eventTypes', async ({request}) => {
    const requestBody = {
      id: 'x',
      type: 'x',
    };
    const res = await request.post(
      buildUrl('/global-task-listeners', undefined),
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
  test('createGlobalTaskListener - Missing id (#2)', async ({request}) => {
    const requestBody = {
      type: 'x',
      eventTypes: 'x',
    };
    const res = await request.post(
      buildUrl('/global-task-listeners', undefined),
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
  test('createGlobalTaskListener - Missing type (#2)', async ({request}) => {
    const requestBody = {
      id: 'x',
      eventTypes: 'x',
    };
    const res = await request.post(
      buildUrl('/global-task-listeners', undefined),
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
  test('createGlobalTaskListener - Missing body', async ({request}) => {
    const res = await request.post(
      buildUrl('/global-task-listeners', undefined),
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
  test('createGlobalTaskListener - Missing combo id,eventTypes', async ({
    request,
  }) => {
    const requestBody = {
      type: 'x',
    };
    const res = await request.post(
      buildUrl('/global-task-listeners', undefined),
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
  test('createGlobalTaskListener - Missing combo id,type', async ({
    request,
  }) => {
    const requestBody = {
      eventTypes: 'x',
    };
    const res = await request.post(
      buildUrl('/global-task-listeners', undefined),
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
  test('createGlobalTaskListener - Missing combo id,type,eventTypes', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.post(
      buildUrl('/global-task-listeners', undefined),
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
  test('createGlobalTaskListener - Missing combo type,eventTypes', async ({
    request,
  }) => {
    const requestBody = {
      id: 'x',
    };
    const res = await request.post(
      buildUrl('/global-task-listeners', undefined),
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
  test('deleteGlobalTaskListener - Path param id pattern violation', async ({
    request,
  }) => {
    const res = await request.delete(
      buildUrl('/global-task-listeners/{id}', {id: '!'}),
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
  test('getGlobalTaskListener - Path param id pattern violation', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/global-task-listeners/{id}', {id: '!'}),
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
  test('searchGlobalTaskListeners - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/global-task-listeners/search', undefined),
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
  test('searchGlobalTaskListeners - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/global-task-listeners/search', undefined),
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
  test('searchGlobalTaskListeners - Enum violation sort.0.field (#1)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'id_INVALID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/global-task-listeners/search', undefined),
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
  test('searchGlobalTaskListeners - Enum violation sort.0.field (#2)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'ID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/global-task-listeners/search', undefined),
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
  test('searchGlobalTaskListeners - Enum violation sort.0.order (#1)', async ({
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
      buildUrl('/global-task-listeners/search', undefined),
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
  test('searchGlobalTaskListeners - Enum violation sort.0.order (#2)', async ({
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
      buildUrl('/global-task-listeners/search', undefined),
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
  test('updateGlobalTaskListener - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      type: 'x',
      eventTypes: ['all'],
      __extraField: 'unexpected',
    };
    const res = await request.put(
      buildUrl('/global-task-listeners/{id}', {id: 'x'}),
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
  test('updateGlobalTaskListener - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.put(
      buildUrl('/global-task-listeners/{id}', {id: 'x'}),
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
  test('updateGlobalTaskListener - Param eventTypes.0 wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      type: 'x',
      eventTypes: [123],
    };
    const res = await request.put(
      buildUrl('/global-task-listeners/{id}', {id: 'x'}),
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
  test('updateGlobalTaskListener - Param eventTypes.0 wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      type: 'x',
      eventTypes: [true],
    };
    const res = await request.put(
      buildUrl('/global-task-listeners/{id}', {id: 'x'}),
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
  test('updateGlobalTaskListener - Param type wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      type: 123,
      eventTypes: ['all'],
    };
    const res = await request.put(
      buildUrl('/global-task-listeners/{id}', {id: 'x'}),
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
  test('updateGlobalTaskListener - Param type wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      type: true,
      eventTypes: ['all'],
    };
    const res = await request.put(
      buildUrl('/global-task-listeners/{id}', {id: 'x'}),
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
  test('updateGlobalTaskListener - Missing type (#1)', async ({request}) => {
    const requestBody = {
      eventTypes: ['all'],
    };
    const res = await request.put(
      buildUrl('/global-task-listeners/{id}', {id: 'x'}),
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
  test('updateGlobalTaskListener - Enum violation eventTypes.0 (#1)', async ({
    request,
  }) => {
    const requestBody = {
      type: 'x',
      eventTypes: [
        {
          __invalidEnum: true,
          value: 'all_INVALID',
        },
      ],
    };
    const res = await request.put(
      buildUrl('/global-task-listeners/{id}', {id: 'x'}),
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
  test('updateGlobalTaskListener - Enum violation eventTypes.0 (#2)', async ({
    request,
  }) => {
    const requestBody = {
      type: 'x',
      eventTypes: [
        {
          __invalidEnum: true,
          value: 'ALL',
        },
      ],
    };
    const res = await request.put(
      buildUrl('/global-task-listeners/{id}', {id: 'x'}),
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
  test('updateGlobalTaskListener - Missing eventTypes', async ({request}) => {
    const requestBody = {
      type: 'x',
    };
    const res = await request.put(
      buildUrl('/global-task-listeners/{id}', {id: 'x'}),
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
  test('updateGlobalTaskListener - Missing type (#2)', async ({request}) => {
    const requestBody = {
      eventTypes: 'x',
    };
    const res = await request.put(
      buildUrl('/global-task-listeners/{id}', {id: 'x'}),
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
  test('updateGlobalTaskListener - Missing body', async ({request}) => {
    const res = await request.put(
      buildUrl('/global-task-listeners/{id}', {id: 'x'}),
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
  test('updateGlobalTaskListener - Missing combo type,eventTypes', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.put(
      buildUrl('/global-task-listeners/{id}', {id: 'x'}),
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
  test('updateGlobalTaskListener - Path param id pattern violation', async ({
    request,
  }) => {
    const res = await request.put(
      buildUrl('/global-task-listeners/{id}', {id: '!'}),
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
