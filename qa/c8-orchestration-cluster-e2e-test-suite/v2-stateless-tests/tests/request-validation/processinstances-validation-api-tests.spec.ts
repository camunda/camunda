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

test.describe('Processinstances Validation API Tests', () => {
  test('assignProcessInstanceBusinessId - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      businessId: 'x',
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl(
        '/process-instances/{processInstanceKey}/business-id-assignment',
        {processInstanceKey: 'x'},
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
  test('assignProcessInstanceBusinessId - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl(
        '/process-instances/{processInstanceKey}/business-id-assignment',
        {processInstanceKey: 'x'},
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
  test('assignProcessInstanceBusinessId - Param businessId wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      businessId: 123,
    };
    const res = await request.post(
      buildUrl(
        '/process-instances/{processInstanceKey}/business-id-assignment',
        {processInstanceKey: 'x'},
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
  test('assignProcessInstanceBusinessId - Param businessId wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      businessId: true,
    };
    const res = await request.post(
      buildUrl(
        '/process-instances/{processInstanceKey}/business-id-assignment',
        {processInstanceKey: 'x'},
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
  test('assignProcessInstanceBusinessId - Constraint violation businessId (#1)', async ({
    request,
  }) => {
    const requestBody = {
      businessId:
        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    };
    const res = await request.post(
      buildUrl(
        '/process-instances/{processInstanceKey}/business-id-assignment',
        {processInstanceKey: '1'},
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
  test('assignProcessInstanceBusinessId - Constraint violation businessId (#2)', async ({
    request,
  }) => {
    const requestBody = {
      businessId: '',
    };
    const res = await request.post(
      buildUrl(
        '/process-instances/{processInstanceKey}/business-id-assignment',
        {processInstanceKey: '1'},
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
  test('assignProcessInstanceBusinessId - Constraint violation businessId (#3)', async ({
    request,
  }) => {
    const requestBody = {
      businessId:
        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    };
    const res = await request.post(
      buildUrl(
        '/process-instances/{processInstanceKey}/business-id-assignment',
        {processInstanceKey: '1'},
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
  test('assignProcessInstanceBusinessId - Missing businessId', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.post(
      buildUrl(
        '/process-instances/{processInstanceKey}/business-id-assignment',
        {processInstanceKey: 'x'},
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
  test('assignProcessInstanceBusinessId - Missing body', async ({request}) => {
    const res = await request.post(
      buildUrl(
        '/process-instances/{processInstanceKey}/business-id-assignment',
        {processInstanceKey: 'x'},
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
  test('assignProcessInstanceBusinessId - Path param processInstanceKey pattern violation', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl(
        '/process-instances/{processInstanceKey}/business-id-assignment',
        {processInstanceKey: 'a'},
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
  test('cancelProcessInstance - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 1,
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/cancellation', {
        processInstanceKey: 'x',
      }),
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
  test('cancelProcessInstance - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/cancellation', {
        processInstanceKey: 'x',
      }),
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
  test('cancelProcessInstance - Param operationReference wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 'not-a-number',
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/cancellation', {
        processInstanceKey: 'x',
      }),
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
  test('cancelProcessInstance - Param operationReference wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: true,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/cancellation', {
        processInstanceKey: 'x',
      }),
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
  test('cancelProcessInstance - Constraint violation operationReference (#1)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 0.99999,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/cancellation', {
        processInstanceKey: '1',
      }),
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
  test('cancelProcessInstance - Constraint violation operationReference (#2)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 0,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/cancellation', {
        processInstanceKey: '1',
      }),
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
  test('cancelProcessInstance - Constraint violation operationReference (#3)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: -99,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/cancellation', {
        processInstanceKey: '1',
      }),
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
  test('cancelProcessInstance - Path param processInstanceKey pattern violation', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/cancellation', {
        processInstanceKey: 'a',
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
  test('cancelProcessInstancesBatchOperation - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: 1,
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/process-instances/cancellation', undefined),
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
  test('cancelProcessInstancesBatchOperation - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/process-instances/cancellation', undefined),
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
  test('cancelProcessInstancesBatchOperation - Param filter.tags.0 wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [123],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/cancellation', undefined),
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
  test('cancelProcessInstancesBatchOperation - Param filter.tags.0 wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [true],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/cancellation', undefined),
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
  test('cancelProcessInstancesBatchOperation - Param operationReference wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: 'not-a-number',
    };
    const res = await request.post(
      buildUrl('/process-instances/cancellation', undefined),
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
  test('cancelProcessInstancesBatchOperation - Param operationReference wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: true,
    };
    const res = await request.post(
      buildUrl('/process-instances/cancellation', undefined),
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
  test('cancelProcessInstancesBatchOperation - Constraint violation filter.tags (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/cancellation', undefined),
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
  test('cancelProcessInstancesBatchOperation - Constraint violation filter.tags (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/cancellation', undefined),
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
  test('cancelProcessInstancesBatchOperation - Constraint violation filter.tags.0 (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        ],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/cancellation', undefined),
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
  test('cancelProcessInstancesBatchOperation - Constraint violation filter.tags.0 (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [''],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/cancellation', undefined),
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
  test('cancelProcessInstancesBatchOperation - Constraint violation filter.tags.0 (#3)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['\n'],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/cancellation', undefined),
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
  test('cancelProcessInstancesBatchOperation - Constraint violation filter.tags.0 (#4)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        ],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/cancellation', undefined),
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
  test('cancelProcessInstancesBatchOperation - Constraint violation operationReference (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: 0.99999,
    };
    const res = await request.post(
      buildUrl('/process-instances/cancellation', undefined),
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
  // Known failing (see known-failing-tests.json): operationReference validator only rejects the first invalid-format variant the generator produces
  test.skip('cancelProcessInstancesBatchOperation - Constraint violation operationReference (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: 0,
    };
    const res = await request.post(
      buildUrl('/process-instances/cancellation', undefined),
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
  // Known failing (see known-failing-tests.json): operationReference validator only rejects the first invalid-format variant the generator produces
  test.skip('cancelProcessInstancesBatchOperation - Constraint violation operationReference (#3)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: -99,
    };
    const res = await request.post(
      buildUrl('/process-instances/cancellation', undefined),
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
  test('cancelProcessInstancesBatchOperation - Missing filter (#1)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/cancellation', undefined),
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
  test('cancelProcessInstancesBatchOperation - Missing filter (#2)', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.post(
      buildUrl('/process-instances/cancellation', undefined),
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
  test('cancelProcessInstancesBatchOperation - Missing body', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-instances/cancellation', undefined),
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
  test('cancelProcessInstancesBatchOperation - uniqueItems violation filter.$or.0.tags', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
        $or: {
          '0': {
            tags: [1, 1, 1],
          },
        },
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/cancellation', undefined),
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
  test('cancelProcessInstancesBatchOperation - uniqueItems violation filter.tags', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [1, 1, 1],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/cancellation', undefined),
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
  test('createProcessInstance - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody = 42;
    const res = await request.post(buildUrl('/process-instances', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createProcessInstance - Missing body', async ({request}) => {
    const res = await request.post(buildUrl('/process-instances', undefined), {
      headers: jsonHeaders(),
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createProcessInstance - oneOf ambiguous', async ({request}) => {
    const requestBody = {
      processDefinitionKey: 'x',
      processDefinitionId: 'x',
    };
    const res = await request.post(buildUrl('/process-instances', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createProcessInstance - oneOf cross bleed', async ({request}) => {
    const requestBody = {
      processDefinitionKey: 'x',
      processDefinitionId: 'x',
    };
    const res = await request.post(buildUrl('/process-instances', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createProcessInstance - oneOf none match', async ({request}) => {
    const requestBody = {};
    const res = await request.post(buildUrl('/process-instances', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createProcessInstance - oneOf violation', async ({request}) => {
    const requestBody = {
      processDefinitionKey: 'x',
      processDefinitionId: 'x',
    };
    const res = await request.post(buildUrl('/process-instances', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteProcessInstance - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 1,
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/deletion', {
        processInstanceKey: 'x',
      }),
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
  test('deleteProcessInstance - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/deletion', {
        processInstanceKey: 'x',
      }),
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
  test('deleteProcessInstance - Param operationReference wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 'not-a-number',
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/deletion', {
        processInstanceKey: 'x',
      }),
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
  test('deleteProcessInstance - Param operationReference wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: true,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/deletion', {
        processInstanceKey: 'x',
      }),
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
  test('deleteProcessInstance - Constraint violation operationReference (#1)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 0.99999,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/deletion', {
        processInstanceKey: '1',
      }),
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
  // Known failing (see known-failing-tests.json): operationReference validator only rejects the first invalid-format variant the generator produces
  test.skip('deleteProcessInstance - Constraint violation operationReference (#2)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 0,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/deletion', {
        processInstanceKey: '1',
      }),
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
  // Known failing (see known-failing-tests.json): operationReference validator only rejects the first invalid-format variant the generator produces
  test.skip('deleteProcessInstance - Constraint violation operationReference (#3)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: -99,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/deletion', {
        processInstanceKey: '1',
      }),
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
  test('deleteProcessInstance - Path param processInstanceKey pattern violation', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/deletion', {
        processInstanceKey: 'a',
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
  test('deleteProcessInstancesBatchOperation - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: 1,
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/process-instances/deletion', undefined),
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
  test('deleteProcessInstancesBatchOperation - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/process-instances/deletion', undefined),
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
  test('deleteProcessInstancesBatchOperation - Param filter.tags.0 wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [123],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/deletion', undefined),
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
  test('deleteProcessInstancesBatchOperation - Param filter.tags.0 wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [true],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/deletion', undefined),
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
  test('deleteProcessInstancesBatchOperation - Param operationReference wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: 'not-a-number',
    };
    const res = await request.post(
      buildUrl('/process-instances/deletion', undefined),
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
  test('deleteProcessInstancesBatchOperation - Param operationReference wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: true,
    };
    const res = await request.post(
      buildUrl('/process-instances/deletion', undefined),
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
  test('deleteProcessInstancesBatchOperation - Constraint violation filter.tags (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/deletion', undefined),
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
  test('deleteProcessInstancesBatchOperation - Constraint violation filter.tags (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/deletion', undefined),
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
  test('deleteProcessInstancesBatchOperation - Constraint violation filter.tags.0 (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        ],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/deletion', undefined),
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
  test('deleteProcessInstancesBatchOperation - Constraint violation filter.tags.0 (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [''],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/deletion', undefined),
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
  test('deleteProcessInstancesBatchOperation - Constraint violation filter.tags.0 (#3)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['\n'],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/deletion', undefined),
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
  test('deleteProcessInstancesBatchOperation - Constraint violation filter.tags.0 (#4)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        ],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/deletion', undefined),
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
  test('deleteProcessInstancesBatchOperation - Constraint violation operationReference (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: 0.99999,
    };
    const res = await request.post(
      buildUrl('/process-instances/deletion', undefined),
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
  // Known failing (see known-failing-tests.json): operationReference validator only rejects the first invalid-format variant the generator produces
  test.skip('deleteProcessInstancesBatchOperation - Constraint violation operationReference (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: 0,
    };
    const res = await request.post(
      buildUrl('/process-instances/deletion', undefined),
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
  // Known failing (see known-failing-tests.json): operationReference validator only rejects the first invalid-format variant the generator produces
  test.skip('deleteProcessInstancesBatchOperation - Constraint violation operationReference (#3)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: -99,
    };
    const res = await request.post(
      buildUrl('/process-instances/deletion', undefined),
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
  test('deleteProcessInstancesBatchOperation - Missing filter (#1)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/deletion', undefined),
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
  test('deleteProcessInstancesBatchOperation - Missing filter (#2)', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.post(
      buildUrl('/process-instances/deletion', undefined),
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
  test('deleteProcessInstancesBatchOperation - Missing body', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-instances/deletion', undefined),
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
  test('deleteProcessInstancesBatchOperation - uniqueItems violation filter.$or.0.tags', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
        $or: {
          '0': {
            tags: [1, 1, 1],
          },
        },
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/deletion', undefined),
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
  test('deleteProcessInstancesBatchOperation - uniqueItems violation filter.tags', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [1, 1, 1],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/deletion', undefined),
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
  test('getProcessInstance - Path param processInstanceKey pattern violation', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/process-instances/{processInstanceKey}', {
        processInstanceKey: 'a',
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
  test('getProcessInstanceCallHierarchy - Path param processInstanceKey pattern violation', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/process-instances/{processInstanceKey}/call-hierarchy', {
        processInstanceKey: 'a',
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
  test('getProcessInstanceSequenceFlows - Path param processInstanceKey pattern violation', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/process-instances/{processInstanceKey}/sequence-flows', {
        processInstanceKey: 'a',
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
  test('getProcessInstanceStatistics - Path param processInstanceKey pattern violation', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl(
        '/process-instances/{processInstanceKey}/statistics/element-instances',
        {processInstanceKey: 'a'},
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
  test('getProcessInstanceWaitStateStatistics - Path param processInstanceKey pattern violation', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl(
        '/process-instances/{processInstanceKey}/statistics/wait-states',
        {processInstanceKey: 'a'},
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
  test('migrateProcessInstance - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      targetProcessDefinitionKey: null,
      mappingInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      operationReference: 1,
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/migration', {
        processInstanceKey: 'x',
      }),
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
  test('migrateProcessInstance - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/migration', {
        processInstanceKey: 'x',
      }),
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
  test('migrateProcessInstance - Param operationReference wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      targetProcessDefinitionKey: null,
      mappingInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      operationReference: 'not-a-number',
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/migration', {
        processInstanceKey: 'x',
      }),
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
  test('migrateProcessInstance - Param operationReference wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      targetProcessDefinitionKey: null,
      mappingInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      operationReference: true,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/migration', {
        processInstanceKey: 'x',
      }),
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
  test('migrateProcessInstance - Constraint violation operationReference (#1)', async ({
    request,
  }) => {
    const requestBody = {
      targetProcessDefinitionKey: null,
      mappingInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      operationReference: 0.99999,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/migration', {
        processInstanceKey: '1',
      }),
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
  test('migrateProcessInstance - Constraint violation operationReference (#2)', async ({
    request,
  }) => {
    const requestBody = {
      targetProcessDefinitionKey: null,
      mappingInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      operationReference: 0,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/migration', {
        processInstanceKey: '1',
      }),
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
  test('migrateProcessInstance - Constraint violation operationReference (#3)', async ({
    request,
  }) => {
    const requestBody = {
      targetProcessDefinitionKey: null,
      mappingInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      operationReference: -99,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/migration', {
        processInstanceKey: '1',
      }),
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
  test('migrateProcessInstance - Missing mappingInstructions (#1)', async ({
    request,
  }) => {
    const requestBody = {
      targetProcessDefinitionKey: null,
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/migration', {
        processInstanceKey: 'x',
      }),
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
  test('migrateProcessInstance - Missing targetProcessDefinitionKey (#1)', async ({
    request,
  }) => {
    const requestBody = {
      mappingInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/migration', {
        processInstanceKey: 'x',
      }),
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
  test('migrateProcessInstance - Missing mappingInstructions (#2)', async ({
    request,
  }) => {
    const requestBody = {
      targetProcessDefinitionKey: 'x',
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/migration', {
        processInstanceKey: 'x',
      }),
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
  test('migrateProcessInstance - Missing targetProcessDefinitionKey (#2)', async ({
    request,
  }) => {
    const requestBody = {
      mappingInstructions: [],
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/migration', {
        processInstanceKey: 'x',
      }),
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
  test('migrateProcessInstance - Missing body', async ({request}) => {
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/migration', {
        processInstanceKey: 'x',
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
  test('migrateProcessInstance - Missing combo targetProcessDefinitionKey,mappingInstructions', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/migration', {
        processInstanceKey: 'x',
      }),
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
  test('migrateProcessInstance - Path param processInstanceKey pattern violation', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/migration', {
        processInstanceKey: 'a',
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
  test('migrateProcessInstancesBatchOperation - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      migrationPlan: {
        targetProcessDefinitionKey: null,
        mappingInstructions: [
          {
            sourceElementId: null,
            targetElementId: null,
          },
        ],
      },
      operationReference: 1,
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('migrateProcessInstancesBatchOperation - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('migrateProcessInstancesBatchOperation - Param filter.tags.0 wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [123],
      },
      migrationPlan: {
        targetProcessDefinitionKey: null,
        mappingInstructions: [
          {
            sourceElementId: null,
            targetElementId: null,
          },
        ],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('migrateProcessInstancesBatchOperation - Param filter.tags.0 wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [true],
      },
      migrationPlan: {
        targetProcessDefinitionKey: null,
        mappingInstructions: [
          {
            sourceElementId: null,
            targetElementId: null,
          },
        ],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('migrateProcessInstancesBatchOperation - Param operationReference wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      migrationPlan: {
        targetProcessDefinitionKey: null,
        mappingInstructions: [
          {
            sourceElementId: null,
            targetElementId: null,
          },
        ],
      },
      operationReference: 'not-a-number',
    };
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('migrateProcessInstancesBatchOperation - Param operationReference wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      migrationPlan: {
        targetProcessDefinitionKey: null,
        mappingInstructions: [
          {
            sourceElementId: null,
            targetElementId: null,
          },
        ],
      },
      operationReference: true,
    };
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('migrateProcessInstancesBatchOperation - Constraint violation filter.tags (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1],
      },
      migrationPlan: {
        targetProcessDefinitionKey: null,
        mappingInstructions: [
          {
            sourceElementId: null,
            targetElementId: null,
          },
        ],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('migrateProcessInstancesBatchOperation - Constraint violation filter.tags (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1],
      },
      migrationPlan: {
        targetProcessDefinitionKey: null,
        mappingInstructions: [
          {
            sourceElementId: null,
            targetElementId: null,
          },
        ],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('migrateProcessInstancesBatchOperation - Constraint violation filter.tags.0 (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        ],
      },
      migrationPlan: {
        targetProcessDefinitionKey: null,
        mappingInstructions: [
          {
            sourceElementId: null,
            targetElementId: null,
          },
        ],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('migrateProcessInstancesBatchOperation - Constraint violation filter.tags.0 (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [''],
      },
      migrationPlan: {
        targetProcessDefinitionKey: null,
        mappingInstructions: [
          {
            sourceElementId: null,
            targetElementId: null,
          },
        ],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('migrateProcessInstancesBatchOperation - Constraint violation filter.tags.0 (#3)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['\n'],
      },
      migrationPlan: {
        targetProcessDefinitionKey: null,
        mappingInstructions: [
          {
            sourceElementId: null,
            targetElementId: null,
          },
        ],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('migrateProcessInstancesBatchOperation - Constraint violation filter.tags.0 (#4)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        ],
      },
      migrationPlan: {
        targetProcessDefinitionKey: null,
        mappingInstructions: [
          {
            sourceElementId: null,
            targetElementId: null,
          },
        ],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('migrateProcessInstancesBatchOperation - Constraint violation operationReference (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      migrationPlan: {
        targetProcessDefinitionKey: null,
        mappingInstructions: [
          {
            sourceElementId: null,
            targetElementId: null,
          },
        ],
      },
      operationReference: 0.99999,
    };
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('migrateProcessInstancesBatchOperation - Constraint violation operationReference (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      migrationPlan: {
        targetProcessDefinitionKey: null,
        mappingInstructions: [
          {
            sourceElementId: null,
            targetElementId: null,
          },
        ],
      },
      operationReference: 0,
    };
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('migrateProcessInstancesBatchOperation - Constraint violation operationReference (#3)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      migrationPlan: {
        targetProcessDefinitionKey: null,
        mappingInstructions: [
          {
            sourceElementId: null,
            targetElementId: null,
          },
        ],
      },
      operationReference: -99,
    };
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('migrateProcessInstancesBatchOperation - Missing filter (#1)', async ({
    request,
  }) => {
    const requestBody = {
      migrationPlan: {
        targetProcessDefinitionKey: null,
        mappingInstructions: [
          {
            sourceElementId: null,
            targetElementId: null,
          },
        ],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('migrateProcessInstancesBatchOperation - Missing migrationPlan (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('migrateProcessInstancesBatchOperation - Missing migrationPlan.mappingInstructions', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      migrationPlan: {
        targetProcessDefinitionKey: null,
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('migrateProcessInstancesBatchOperation - Missing migrationPlan.targetProcessDefinitionKey', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      migrationPlan: {
        mappingInstructions: [
          {
            sourceElementId: null,
            targetElementId: null,
          },
        ],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('migrateProcessInstancesBatchOperation - Missing filter (#2)', async ({
    request,
  }) => {
    const requestBody = {
      migrationPlan: 'x',
    };
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('migrateProcessInstancesBatchOperation - Missing migrationPlan (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: 'x',
    };
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('migrateProcessInstancesBatchOperation - Missing body', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('migrateProcessInstancesBatchOperation - Missing combo filter,migrationPlan', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('migrateProcessInstancesBatchOperation - uniqueItems violation filter.$or.0.tags', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
        $or: {
          '0': {
            tags: [1, 1, 1],
          },
        },
      },
      migrationPlan: {
        targetProcessDefinitionKey: null,
        mappingInstructions: [
          {
            sourceElementId: null,
            targetElementId: null,
          },
        ],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('migrateProcessInstancesBatchOperation - uniqueItems violation filter.tags', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [1, 1, 1],
      },
      migrationPlan: {
        targetProcessDefinitionKey: null,
        mappingInstructions: [
          {
            sourceElementId: null,
            targetElementId: null,
          },
        ],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/migration', undefined),
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
  test('modifyProcessInstance - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 1,
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/modification', {
        processInstanceKey: 'x',
      }),
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
  test('modifyProcessInstance - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/modification', {
        processInstanceKey: 'x',
      }),
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
  test('modifyProcessInstance - Param operationReference wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 'not-a-number',
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/modification', {
        processInstanceKey: 'x',
      }),
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
  test('modifyProcessInstance - Param operationReference wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: true,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/modification', {
        processInstanceKey: 'x',
      }),
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
  test('modifyProcessInstance - Constraint violation operationReference (#1)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 0.99999,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/modification', {
        processInstanceKey: '1',
      }),
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
  test('modifyProcessInstance - Constraint violation operationReference (#2)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 0,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/modification', {
        processInstanceKey: '1',
      }),
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
  test('modifyProcessInstance - Constraint violation operationReference (#3)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: -99,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/modification', {
        processInstanceKey: '1',
      }),
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
  test('modifyProcessInstance - Missing body', async ({request}) => {
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/modification', {
        processInstanceKey: 'x',
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
  test('modifyProcessInstance - Path param processInstanceKey pattern violation', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/modification', {
        processInstanceKey: 'a',
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
  test('modifyProcessInstancesBatchOperation - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      moveInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      operationReference: 1,
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/process-instances/modification', undefined),
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
  test('modifyProcessInstancesBatchOperation - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/process-instances/modification', undefined),
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
  test('modifyProcessInstancesBatchOperation - Param filter.tags.0 wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [123],
      },
      moveInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/modification', undefined),
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
  test('modifyProcessInstancesBatchOperation - Param filter.tags.0 wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [true],
      },
      moveInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/modification', undefined),
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
  test('modifyProcessInstancesBatchOperation - Param operationReference wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      moveInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      operationReference: 'not-a-number',
    };
    const res = await request.post(
      buildUrl('/process-instances/modification', undefined),
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
  test('modifyProcessInstancesBatchOperation - Param operationReference wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      moveInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      operationReference: true,
    };
    const res = await request.post(
      buildUrl('/process-instances/modification', undefined),
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
  test('modifyProcessInstancesBatchOperation - Constraint violation filter.tags (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1],
      },
      moveInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/modification', undefined),
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
  test('modifyProcessInstancesBatchOperation - Constraint violation filter.tags (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1],
      },
      moveInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/modification', undefined),
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
  test('modifyProcessInstancesBatchOperation - Constraint violation filter.tags.0 (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        ],
      },
      moveInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/modification', undefined),
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
  test('modifyProcessInstancesBatchOperation - Constraint violation filter.tags.0 (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [''],
      },
      moveInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/modification', undefined),
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
  test('modifyProcessInstancesBatchOperation - Constraint violation filter.tags.0 (#3)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['\n'],
      },
      moveInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/modification', undefined),
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
  test('modifyProcessInstancesBatchOperation - Constraint violation filter.tags.0 (#4)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        ],
      },
      moveInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/modification', undefined),
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
  test('modifyProcessInstancesBatchOperation - Constraint violation operationReference (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      moveInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      operationReference: 0.99999,
    };
    const res = await request.post(
      buildUrl('/process-instances/modification', undefined),
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
  test('modifyProcessInstancesBatchOperation - Constraint violation operationReference (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      moveInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      operationReference: 0,
    };
    const res = await request.post(
      buildUrl('/process-instances/modification', undefined),
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
  test('modifyProcessInstancesBatchOperation - Constraint violation operationReference (#3)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      moveInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      operationReference: -99,
    };
    const res = await request.post(
      buildUrl('/process-instances/modification', undefined),
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
  test('modifyProcessInstancesBatchOperation - Missing filter (#1)', async ({
    request,
  }) => {
    const requestBody = {
      moveInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/modification', undefined),
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
  test('modifyProcessInstancesBatchOperation - Missing moveInstructions (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/modification', undefined),
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
  test('modifyProcessInstancesBatchOperation - Missing filter (#2)', async ({
    request,
  }) => {
    const requestBody = {
      moveInstructions: [],
    };
    const res = await request.post(
      buildUrl('/process-instances/modification', undefined),
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
  test('modifyProcessInstancesBatchOperation - Missing moveInstructions (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: 'x',
    };
    const res = await request.post(
      buildUrl('/process-instances/modification', undefined),
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
  test('modifyProcessInstancesBatchOperation - Missing body', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-instances/modification', undefined),
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
  test('modifyProcessInstancesBatchOperation - Missing combo filter,moveInstructions', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.post(
      buildUrl('/process-instances/modification', undefined),
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
  test('modifyProcessInstancesBatchOperation - uniqueItems violation filter.$or.0.tags', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
        $or: {
          '0': {
            tags: [1, 1, 1],
          },
        },
      },
      moveInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/modification', undefined),
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
  test('modifyProcessInstancesBatchOperation - uniqueItems violation filter.tags', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [1, 1, 1],
      },
      moveInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/modification', undefined),
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
  test('resolveIncidentsBatchOperation - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: 1,
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/process-instances/incident-resolution', undefined),
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
  test('resolveIncidentsBatchOperation - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/process-instances/incident-resolution', undefined),
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
  test('resolveIncidentsBatchOperation - Param filter.tags.0 wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [123],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/incident-resolution', undefined),
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
  test('resolveIncidentsBatchOperation - Param filter.tags.0 wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [true],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/incident-resolution', undefined),
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
  test('resolveIncidentsBatchOperation - Param operationReference wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: 'not-a-number',
    };
    const res = await request.post(
      buildUrl('/process-instances/incident-resolution', undefined),
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
  test('resolveIncidentsBatchOperation - Param operationReference wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: true,
    };
    const res = await request.post(
      buildUrl('/process-instances/incident-resolution', undefined),
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
  test('resolveIncidentsBatchOperation - Constraint violation filter.tags (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/incident-resolution', undefined),
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
  test('resolveIncidentsBatchOperation - Constraint violation filter.tags (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/incident-resolution', undefined),
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
  test('resolveIncidentsBatchOperation - Constraint violation filter.tags.0 (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        ],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/incident-resolution', undefined),
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
  test('resolveIncidentsBatchOperation - Constraint violation filter.tags.0 (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [''],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/incident-resolution', undefined),
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
  test('resolveIncidentsBatchOperation - Constraint violation filter.tags.0 (#3)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['\n'],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/incident-resolution', undefined),
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
  test('resolveIncidentsBatchOperation - Constraint violation filter.tags.0 (#4)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        ],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/incident-resolution', undefined),
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
  test('resolveIncidentsBatchOperation - Constraint violation operationReference (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: 0.99999,
    };
    const res = await request.post(
      buildUrl('/process-instances/incident-resolution', undefined),
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
  // Known failing (see known-failing-tests.json): operationReference validator only rejects the first invalid-format variant the generator produces
  test.skip('resolveIncidentsBatchOperation - Constraint violation operationReference (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: 0,
    };
    const res = await request.post(
      buildUrl('/process-instances/incident-resolution', undefined),
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
  // Known failing (see known-failing-tests.json): operationReference validator only rejects the first invalid-format variant the generator produces
  test.skip('resolveIncidentsBatchOperation - Constraint violation operationReference (#3)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: -99,
    };
    const res = await request.post(
      buildUrl('/process-instances/incident-resolution', undefined),
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
  test('resolveIncidentsBatchOperation - Missing filter (#1)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/incident-resolution', undefined),
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
  test('resolveIncidentsBatchOperation - Missing filter (#2)', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.post(
      buildUrl('/process-instances/incident-resolution', undefined),
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
  test('resolveIncidentsBatchOperation - uniqueItems violation filter.$or.0.tags', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
        $or: {
          '0': {
            tags: [1, 1, 1],
          },
        },
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/incident-resolution', undefined),
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
  test('resolveIncidentsBatchOperation - uniqueItems violation filter.tags', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [1, 1, 1],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/incident-resolution', undefined),
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
  test('resolveProcessInstanceIncidents - Path param processInstanceKey pattern violation', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/incident-resolution', {
        processInstanceKey: 'a',
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
  test('resumeProcessInstance - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 1,
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/resumption', {
        processInstanceKey: 'x',
      }),
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
  test('resumeProcessInstance - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/resumption', {
        processInstanceKey: 'x',
      }),
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
  test('resumeProcessInstance - Param operationReference wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 'not-a-number',
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/resumption', {
        processInstanceKey: 'x',
      }),
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
  test('resumeProcessInstance - Param operationReference wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: true,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/resumption', {
        processInstanceKey: 'x',
      }),
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
  test('resumeProcessInstance - Constraint violation operationReference (#1)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 0.99999,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/resumption', {
        processInstanceKey: '1',
      }),
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
  test('resumeProcessInstance - Constraint violation operationReference (#2)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 0,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/resumption', {
        processInstanceKey: '1',
      }),
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
  test('resumeProcessInstance - Constraint violation operationReference (#3)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: -99,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/resumption', {
        processInstanceKey: '1',
      }),
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
  test('resumeProcessInstance - Path param processInstanceKey pattern violation', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/resumption', {
        processInstanceKey: 'a',
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
  test('resumeProcessInstancesBatchOperation - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: 1,
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/process-instances/resumption', undefined),
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
  test('resumeProcessInstancesBatchOperation - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/process-instances/resumption', undefined),
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
  test('resumeProcessInstancesBatchOperation - Param filter.tags.0 wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [123],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/resumption', undefined),
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
  test('resumeProcessInstancesBatchOperation - Param filter.tags.0 wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [true],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/resumption', undefined),
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
  test('resumeProcessInstancesBatchOperation - Param operationReference wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: 'not-a-number',
    };
    const res = await request.post(
      buildUrl('/process-instances/resumption', undefined),
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
  test('resumeProcessInstancesBatchOperation - Param operationReference wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: true,
    };
    const res = await request.post(
      buildUrl('/process-instances/resumption', undefined),
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
  test('resumeProcessInstancesBatchOperation - Constraint violation filter.tags (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/resumption', undefined),
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
  test('resumeProcessInstancesBatchOperation - Constraint violation filter.tags (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/resumption', undefined),
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
  test('resumeProcessInstancesBatchOperation - Constraint violation filter.tags.0 (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        ],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/resumption', undefined),
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
  test('resumeProcessInstancesBatchOperation - Constraint violation filter.tags.0 (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [''],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/resumption', undefined),
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
  test('resumeProcessInstancesBatchOperation - Constraint violation filter.tags.0 (#3)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['\n'],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/resumption', undefined),
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
  test('resumeProcessInstancesBatchOperation - Constraint violation filter.tags.0 (#4)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        ],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/resumption', undefined),
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
  test('resumeProcessInstancesBatchOperation - Constraint violation operationReference (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: 0.99999,
    };
    const res = await request.post(
      buildUrl('/process-instances/resumption', undefined),
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
  // Known failing (see known-failing-tests.json): operationReference validator only rejects the first invalid-format variant the generator produces
  test.skip('resumeProcessInstancesBatchOperation - Constraint violation operationReference (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: 0,
    };
    const res = await request.post(
      buildUrl('/process-instances/resumption', undefined),
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
  // Known failing (see known-failing-tests.json): operationReference validator only rejects the first invalid-format variant the generator produces
  test.skip('resumeProcessInstancesBatchOperation - Constraint violation operationReference (#3)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: -99,
    };
    const res = await request.post(
      buildUrl('/process-instances/resumption', undefined),
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
  test('resumeProcessInstancesBatchOperation - Missing filter (#1)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/resumption', undefined),
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
  test('resumeProcessInstancesBatchOperation - Missing filter (#2)', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.post(
      buildUrl('/process-instances/resumption', undefined),
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
  test('resumeProcessInstancesBatchOperation - Missing body', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-instances/resumption', undefined),
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
  test('resumeProcessInstancesBatchOperation - uniqueItems violation filter.$or.0.tags', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
        $or: {
          '0': {
            tags: [1, 1, 1],
          },
        },
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/resumption', undefined),
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
  test('resumeProcessInstancesBatchOperation - uniqueItems violation filter.tags', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [1, 1, 1],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/resumption', undefined),
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
  test('searchProcessInstanceIncidents - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/incidents/search', {
        processInstanceKey: 'x',
      }),
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
  test('searchProcessInstanceIncidents - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/incidents/search', {
        processInstanceKey: 'x',
      }),
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
  test('searchProcessInstanceIncidents - Enum violation sort.0.field (#1)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'incidentKey_INVALID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/incidents/search', {
        processInstanceKey: 'x',
      }),
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
  test('searchProcessInstanceIncidents - Enum violation sort.0.field (#2)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'INCIDENTKEY',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/incidents/search', {
        processInstanceKey: 'x',
      }),
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
  test('searchProcessInstanceIncidents - Enum violation sort.0.field (#3)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'incidentkey',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/incidents/search', {
        processInstanceKey: 'x',
      }),
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
  test('searchProcessInstanceIncidents - Enum violation sort.0.order (#1)', async ({
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
      buildUrl('/process-instances/{processInstanceKey}/incidents/search', {
        processInstanceKey: 'x',
      }),
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
  test('searchProcessInstanceIncidents - Enum violation sort.0.order (#2)', async ({
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
      buildUrl('/process-instances/{processInstanceKey}/incidents/search', {
        processInstanceKey: 'x',
      }),
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
  test('searchProcessInstanceIncidents - Path param processInstanceKey pattern violation', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/incidents/search', {
        processInstanceKey: 'a',
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
  test('searchProcessInstances - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/process-instances/search', undefined),
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
  test('searchProcessInstances - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/process-instances/search', undefined),
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
  test('searchProcessInstances - Enum violation sort.0.field (#1)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'processInstanceKey_INVALID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/process-instances/search', undefined),
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
  test('searchProcessInstances - Enum violation sort.0.field (#2)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'PROCESSINSTANCEKEY',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/process-instances/search', undefined),
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
  test('searchProcessInstances - Enum violation sort.0.field (#3)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'processinstancekey',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/process-instances/search', undefined),
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
  test('searchProcessInstances - Enum violation sort.0.order (#1)', async ({
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
      buildUrl('/process-instances/search', undefined),
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
  test('searchProcessInstances - Enum violation sort.0.order (#2)', async ({
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
      buildUrl('/process-instances/search', undefined),
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
  test('searchProcessInstances - uniqueItems violation filter.$or.0.tags', async ({
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
      buildUrl('/process-instances/search', undefined),
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
  test('searchProcessInstances - uniqueItems violation filter.tags', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [1, 1, 1],
      },
    };
    const res = await request.post(
      buildUrl('/process-instances/search', undefined),
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
  test('suspendProcessInstance - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 1,
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/suspension', {
        processInstanceKey: 'x',
      }),
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
  test('suspendProcessInstance - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/suspension', {
        processInstanceKey: 'x',
      }),
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
  test('suspendProcessInstance - Param operationReference wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 'not-a-number',
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/suspension', {
        processInstanceKey: 'x',
      }),
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
  test('suspendProcessInstance - Param operationReference wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: true,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/suspension', {
        processInstanceKey: 'x',
      }),
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
  test('suspendProcessInstance - Constraint violation operationReference (#1)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 0.99999,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/suspension', {
        processInstanceKey: '1',
      }),
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
  test('suspendProcessInstance - Constraint violation operationReference (#2)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 0,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/suspension', {
        processInstanceKey: '1',
      }),
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
  test('suspendProcessInstance - Constraint violation operationReference (#3)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: -99,
    };
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/suspension', {
        processInstanceKey: '1',
      }),
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
  test('suspendProcessInstance - Path param processInstanceKey pattern violation', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/suspension', {
        processInstanceKey: 'a',
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
  test('suspendProcessInstancesBatchOperation - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: 1,
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/process-instances/suspension', undefined),
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
  test('suspendProcessInstancesBatchOperation - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/process-instances/suspension', undefined),
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
  test('suspendProcessInstancesBatchOperation - Param filter.tags.0 wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [123],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/suspension', undefined),
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
  test('suspendProcessInstancesBatchOperation - Param filter.tags.0 wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [true],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/suspension', undefined),
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
  test('suspendProcessInstancesBatchOperation - Param operationReference wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: 'not-a-number',
    };
    const res = await request.post(
      buildUrl('/process-instances/suspension', undefined),
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
  test('suspendProcessInstancesBatchOperation - Param operationReference wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: true,
    };
    const res = await request.post(
      buildUrl('/process-instances/suspension', undefined),
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
  test('suspendProcessInstancesBatchOperation - Constraint violation filter.tags (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/suspension', undefined),
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
  test('suspendProcessInstancesBatchOperation - Constraint violation filter.tags (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/suspension', undefined),
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
  test('suspendProcessInstancesBatchOperation - Constraint violation filter.tags.0 (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        ],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/suspension', undefined),
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
  test('suspendProcessInstancesBatchOperation - Constraint violation filter.tags.0 (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [''],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/suspension', undefined),
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
  test('suspendProcessInstancesBatchOperation - Constraint violation filter.tags.0 (#3)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['\n'],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/suspension', undefined),
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
  test('suspendProcessInstancesBatchOperation - Constraint violation filter.tags.0 (#4)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        ],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/suspension', undefined),
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
  test('suspendProcessInstancesBatchOperation - Constraint violation operationReference (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: 0.99999,
    };
    const res = await request.post(
      buildUrl('/process-instances/suspension', undefined),
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
  // Known failing (see known-failing-tests.json): operationReference validator only rejects the first invalid-format variant the generator produces
  test.skip('suspendProcessInstancesBatchOperation - Constraint violation operationReference (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: 0,
    };
    const res = await request.post(
      buildUrl('/process-instances/suspension', undefined),
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
  // Known failing (see known-failing-tests.json): operationReference validator only rejects the first invalid-format variant the generator produces
  test.skip('suspendProcessInstancesBatchOperation - Constraint violation operationReference (#3)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
      },
      operationReference: -99,
    };
    const res = await request.post(
      buildUrl('/process-instances/suspension', undefined),
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
  test('suspendProcessInstancesBatchOperation - Missing filter (#1)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/suspension', undefined),
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
  test('suspendProcessInstancesBatchOperation - Missing filter (#2)', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.post(
      buildUrl('/process-instances/suspension', undefined),
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
  test('suspendProcessInstancesBatchOperation - Missing body', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-instances/suspension', undefined),
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
  test('suspendProcessInstancesBatchOperation - uniqueItems violation filter.$or.0.tags', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: ['x'],
        $or: {
          '0': {
            tags: [1, 1, 1],
          },
        },
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/suspension', undefined),
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
  test('suspendProcessInstancesBatchOperation - uniqueItems violation filter.tags', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [1, 1, 1],
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/process-instances/suspension', undefined),
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
