/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/*
 * GENERATED FILE - DO NOT EDIT MANUALLY
 * Generated At: 2025-09-22T18:40:25.704Z
 * Spec Commit: f2fd6a1393ca4c7feae1efd10c7c863c0f146187
 */
import {test, expect} from '@playwright/test';
import {jsonHeaders, buildUrl} from '../../../utils/http';

test.describe('Processinstances Validation API Tests', () => {
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
  test('cancelProcessInstancesBatchOperation - Missing filter', async ({
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
      processDefinitionId: 'x',
      processDefinitionKey: 'x',
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
      processDefinitionId: 'x',
      processDefinitionKey: 'x',
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
      processDefinitionId: 'x',
      processDefinitionKey: 'x',
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
      migrationPlan: {},
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
  test('resolveIncidentsBatchOperation - Missing filter', async ({request}) => {
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
  test('resolveIncidentsBatchOperation - Missing body', async ({request}) => {
    const res = await request.post(
      buildUrl('/process-instances/incident-resolution', undefined),
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
});
