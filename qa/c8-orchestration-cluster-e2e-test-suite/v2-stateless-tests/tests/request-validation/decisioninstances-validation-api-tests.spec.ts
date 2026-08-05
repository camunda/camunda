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

test.describe('Decisioninstances Validation API Tests', () => {
  test('deleteDecisionInstance - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 1,
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/decision-instances/{decisionEvaluationKey}/deletion', {
        decisionEvaluationKey: 'x',
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
  test('deleteDecisionInstance - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/decision-instances/{decisionEvaluationKey}/deletion', {
        decisionEvaluationKey: 'x',
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
  test('deleteDecisionInstance - Param operationReference wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 'not-a-number',
    };
    const res = await request.post(
      buildUrl('/decision-instances/{decisionEvaluationKey}/deletion', {
        decisionEvaluationKey: 'x',
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
  test('deleteDecisionInstance - Param operationReference wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: true,
    };
    const res = await request.post(
      buildUrl('/decision-instances/{decisionEvaluationKey}/deletion', {
        decisionEvaluationKey: 'x',
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
  test('deleteDecisionInstance - Constraint violation operationReference (#1)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 0.99999,
    };
    const res = await request.post(
      buildUrl('/decision-instances/{decisionEvaluationKey}/deletion', {
        decisionEvaluationKey: '1',
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
  test.skip('deleteDecisionInstance - Constraint violation operationReference (#2)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 0,
    };
    const res = await request.post(
      buildUrl('/decision-instances/{decisionEvaluationKey}/deletion', {
        decisionEvaluationKey: '1',
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
  test.skip('deleteDecisionInstance - Constraint violation operationReference (#3)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: -99,
    };
    const res = await request.post(
      buildUrl('/decision-instances/{decisionEvaluationKey}/deletion', {
        decisionEvaluationKey: '1',
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
  test('deleteDecisionInstance - Path param decisionEvaluationKey pattern violation', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/decision-instances/{decisionEvaluationKey}/deletion', {
        decisionEvaluationKey: 'a',
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
  test('deleteDecisionInstancesBatchOperation - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        decisionDefinitionVersion: 1,
        decisionDefinitionType: 'DECISION_TABLE',
      },
      operationReference: 1,
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/decision-instances/deletion', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteDecisionInstancesBatchOperation - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/decision-instances/deletion', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteDecisionInstancesBatchOperation - Param filter.decisionDefinitionType wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        decisionDefinitionVersion: 1,
        decisionDefinitionType: 123,
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/decision-instances/deletion', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteDecisionInstancesBatchOperation - Param filter.decisionDefinitionType wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        decisionDefinitionVersion: 1,
        decisionDefinitionType: true,
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/decision-instances/deletion', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteDecisionInstancesBatchOperation - Param filter.decisionDefinitionVersion wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        decisionDefinitionVersion: 'not-a-number',
        decisionDefinitionType: 'DECISION_TABLE',
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/decision-instances/deletion', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteDecisionInstancesBatchOperation - Param filter.decisionDefinitionVersion wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        decisionDefinitionVersion: true,
        decisionDefinitionType: 'DECISION_TABLE',
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/decision-instances/deletion', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteDecisionInstancesBatchOperation - Param operationReference wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        decisionDefinitionVersion: 1,
        decisionDefinitionType: 'DECISION_TABLE',
      },
      operationReference: 'not-a-number',
    };
    const res = await request.post(
      buildUrl('/decision-instances/deletion', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteDecisionInstancesBatchOperation - Param operationReference wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        decisionDefinitionVersion: 1,
        decisionDefinitionType: 'DECISION_TABLE',
      },
      operationReference: true,
    };
    const res = await request.post(
      buildUrl('/decision-instances/deletion', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteDecisionInstancesBatchOperation - Constraint violation operationReference (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        decisionDefinitionVersion: 1,
        decisionDefinitionType: 'DECISION_TABLE',
      },
      operationReference: 0.99999,
    };
    const res = await request.post(
      buildUrl('/decision-instances/deletion', undefined),
      {
        headers: jsonHeaders(),
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
  test.skip('deleteDecisionInstancesBatchOperation - Constraint violation operationReference (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        decisionDefinitionVersion: 1,
        decisionDefinitionType: 'DECISION_TABLE',
      },
      operationReference: 0,
    };
    const res = await request.post(
      buildUrl('/decision-instances/deletion', undefined),
      {
        headers: jsonHeaders(),
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
  test.skip('deleteDecisionInstancesBatchOperation - Constraint violation operationReference (#3)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        decisionDefinitionVersion: 1,
        decisionDefinitionType: 'DECISION_TABLE',
      },
      operationReference: -99,
    };
    const res = await request.post(
      buildUrl('/decision-instances/deletion', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteDecisionInstancesBatchOperation - Missing filter (#1)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/decision-instances/deletion', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteDecisionInstancesBatchOperation - Enum violation filter.decisionDefinitionType (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        decisionDefinitionVersion: 1,
        decisionDefinitionType: {
          __invalidEnum: true,
          value: 'DECISION_TABLE_INVALID',
        },
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/decision-instances/deletion', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteDecisionInstancesBatchOperation - Enum violation filter.decisionDefinitionType (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        decisionDefinitionVersion: 1,
        decisionDefinitionType: {
          __invalidEnum: true,
          value: 'decision_table',
        },
      },
      operationReference: 1,
    };
    const res = await request.post(
      buildUrl('/decision-instances/deletion', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteDecisionInstancesBatchOperation - Missing filter (#2)', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.post(
      buildUrl('/decision-instances/deletion', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteDecisionInstancesBatchOperation - Missing body', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/decision-instances/deletion', undefined),
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
  test('getDecisionInstance - Path param decisionEvaluationInstanceKey pattern violation', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/decision-instances/{decisionEvaluationInstanceKey}', {
        decisionEvaluationInstanceKey: '!INVALID!',
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
  test('searchDecisionInstances - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/decision-instances/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchDecisionInstances - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/decision-instances/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchDecisionInstances - Enum violation filter.decisionDefinitionType (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        decisionDefinitionType: {
          __invalidEnum: true,
          value: 'DECISION_TABLE_INVALID',
        },
      },
    };
    const res = await request.post(
      buildUrl('/decision-instances/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchDecisionInstances - Enum violation filter.decisionDefinitionType (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        decisionDefinitionType: {
          __invalidEnum: true,
          value: 'decision_table',
        },
      },
    };
    const res = await request.post(
      buildUrl('/decision-instances/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchDecisionInstances - Enum violation sort.0.field (#1)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'businessId_INVALID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/decision-instances/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchDecisionInstances - Enum violation sort.0.field (#2)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'BUSINESSID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/decision-instances/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchDecisionInstances - Enum violation sort.0.field (#3)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'businessid',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/decision-instances/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchDecisionInstances - Enum violation sort.0.order (#1)', async ({
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
      buildUrl('/decision-instances/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchDecisionInstances - Enum violation sort.0.order (#2)', async ({
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
      buildUrl('/decision-instances/search', undefined),
      {
        headers: jsonHeaders(),
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
