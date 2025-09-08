/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/*
 * GENERATED FILE - DO NOT EDIT MANUALLY
 * Generated At: 2025-09-08T04:28:13.914Z
 * Spec Commit: 177fb9193d6c4d0ab558734d76c501bbac1f2454
 */
import {test, expect} from '@playwright/test';
import {jsonHeaders, buildUrl} from '../../../../utils/http';

test.describe('Processinstances Validation API Tests', () => {
  test('cancelProcessInstance - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 1,
      __extraField: 'unexpected',
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
  test('cancelProcessInstance - Constraint violation operationReference (#2)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 0,
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
  test('cancelProcessInstance - Constraint violation operationReference (#3)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: -99,
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
  test('cancelProcessInstance - Missing body', async ({request}) => {
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/cancellation', {
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
  test('cancelProcessInstancesBatchOperation - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [null],
      },
      __extraField: 'unexpected',
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
        tags: [null],
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
  test('migrateProcessInstance - Additional prop __extraField', async ({
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
      __extraField: 'unexpected',
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
  test('migrateProcessInstancesBatchOperation - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [null],
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
      __extraField: 'unexpected',
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
        tags: [null],
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
        tags: [null],
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
        tags: [null],
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
        tags: [null],
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
  test('modifyProcessInstance - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 1,
      __extraField: 'unexpected',
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
  test('modifyProcessInstance - Constraint violation operationReference (#2)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 0,
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
  test('modifyProcessInstance - Constraint violation operationReference (#3)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: -99,
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
  test('modifyProcessInstancesBatchOperation - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [null],
      },
      moveInstructions: [
        {
          sourceElementId: null,
          targetElementId: null,
        },
      ],
      __extraField: 'unexpected',
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
        tags: [null],
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
        tags: [null],
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
  test('resolveIncidentsBatchOperation - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        tags: [null],
      },
      __extraField: 'unexpected',
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
        tags: [null],
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
  test('searchProcessInstanceIncidents - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      __extraField: 'unexpected',
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
  test('searchProcessInstanceIncidents - Missing body', async ({request}) => {
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/incidents/search', {
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
  test('searchProcessInstances - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      __extraField: 'unexpected',
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
  test('searchProcessInstances - Missing body', async ({request}) => {
    const res = await request.post(
      buildUrl('/process-instances/search', undefined),
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
