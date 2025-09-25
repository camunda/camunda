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

test.describe('Elementinstances Validation API Tests', () => {
  test('activateAdHocSubProcessActivities - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      elements: [
        {
          elementId: null,
        },
      ],
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl(
        '/element-instances/ad-hoc-activities/{adHocSubProcessInstanceKey}/activation',
        {adHocSubProcessInstanceKey: 'x'},
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
  test('activateAdHocSubProcessActivities - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl(
        '/element-instances/ad-hoc-activities/{adHocSubProcessInstanceKey}/activation',
        {adHocSubProcessInstanceKey: 'x'},
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
  test('activateAdHocSubProcessActivities - Missing elements', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.post(
      buildUrl(
        '/element-instances/ad-hoc-activities/{adHocSubProcessInstanceKey}/activation',
        {adHocSubProcessInstanceKey: 'x'},
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
  test('activateAdHocSubProcessActivities - Missing body', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl(
        '/element-instances/ad-hoc-activities/{adHocSubProcessInstanceKey}/activation',
        {adHocSubProcessInstanceKey: 'x'},
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
  test('activateAdHocSubProcessActivities - Path param adHocSubProcessInstanceKey pattern violation', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl(
        '/element-instances/ad-hoc-activities/{adHocSubProcessInstanceKey}/activation',
        {adHocSubProcessInstanceKey: 'a'},
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
  test('createElementInstanceVariables - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      variables: {},
      operationReference: 1,
      __unexpectedField: 'x',
    };
    const res = await request.put(
      buildUrl('/element-instances/{elementInstanceKey}/variables', {
        elementInstanceKey: 'x',
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
  test('createElementInstanceVariables - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.put(
      buildUrl('/element-instances/{elementInstanceKey}/variables', {
        elementInstanceKey: 'x',
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
  test('createElementInstanceVariables - Param operationReference wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      variables: {},
      operationReference: 'not-a-number',
    };
    const res = await request.put(
      buildUrl('/element-instances/{elementInstanceKey}/variables', {
        elementInstanceKey: 'x',
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
  test('createElementInstanceVariables - Param operationReference wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      variables: {},
      operationReference: true,
    };
    const res = await request.put(
      buildUrl('/element-instances/{elementInstanceKey}/variables', {
        elementInstanceKey: 'x',
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
  test('createElementInstanceVariables - Constraint violation operationReference (#1)', async ({
    request,
  }) => {
    const requestBody = {
      variables: {},
      operationReference: 0.99999,
    };
    const res = await request.put(
      buildUrl('/element-instances/{elementInstanceKey}/variables', {
        elementInstanceKey: '1',
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
  test('createElementInstanceVariables - Constraint violation operationReference (#2)', async ({
    request,
  }) => {
    const requestBody = {
      variables: {},
      operationReference: 0,
    };
    const res = await request.put(
      buildUrl('/element-instances/{elementInstanceKey}/variables', {
        elementInstanceKey: '1',
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
  test('createElementInstanceVariables - Constraint violation operationReference (#3)', async ({
    request,
  }) => {
    const requestBody = {
      variables: {},
      operationReference: -99,
    };
    const res = await request.put(
      buildUrl('/element-instances/{elementInstanceKey}/variables', {
        elementInstanceKey: '1',
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
  test('createElementInstanceVariables - Missing variables (#1)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 1,
    };
    const res = await request.put(
      buildUrl('/element-instances/{elementInstanceKey}/variables', {
        elementInstanceKey: 'x',
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
  test('createElementInstanceVariables - Missing variables (#2)', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.put(
      buildUrl('/element-instances/{elementInstanceKey}/variables', {
        elementInstanceKey: 'x',
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
  test('createElementInstanceVariables - Missing body', async ({request}) => {
    const res = await request.put(
      buildUrl('/element-instances/{elementInstanceKey}/variables', {
        elementInstanceKey: 'x',
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
  test('createElementInstanceVariables - Path param elementInstanceKey pattern violation', async ({
    request,
  }) => {
    const res = await request.put(
      buildUrl('/element-instances/{elementInstanceKey}/variables', {
        elementInstanceKey: 'a',
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
  test('getElementInstance - Path param elementInstanceKey pattern violation', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/element-instances/{elementInstanceKey}', {
        elementInstanceKey: 'a',
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
  test('searchElementInstances - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/element-instances/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchElementInstances - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/element-instances/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchElementInstances - Enum violation filter.type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        type: {
          __invalidEnum: true,
          value: 'UNSPECIFIED_INVALID',
        },
      },
    };
    const res = await request.post(
      buildUrl('/element-instances/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchElementInstances - Enum violation filter.type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        type: {
          __invalidEnum: true,
          value: 'unspecified',
        },
      },
    };
    const res = await request.post(
      buildUrl('/element-instances/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchElementInstances - Enum violation sort.0.field (#1)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'elementInstanceKey_INVALID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/element-instances/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchElementInstances - Enum violation sort.0.field (#2)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'ELEMENTINSTANCEKEY',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/element-instances/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchElementInstances - Enum violation sort.0.field (#3)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'elementinstancekey',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/element-instances/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchElementInstances - Enum violation sort.0.order (#1)', async ({
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
      buildUrl('/element-instances/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchElementInstances - Enum violation sort.0.order (#2)', async ({
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
      buildUrl('/element-instances/search', undefined),
      {
        headers: jsonHeaders(),
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
