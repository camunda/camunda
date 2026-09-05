/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/*
 * GENERATED FILE - DO NOT EDIT MANUALLY
 * Generated At: 2026-08-21T16:01:38.423Z
 * Spec Commit: c30231a08a134faa0138733d391778da818845e3
 */
import {test, expect} from '@playwright/test';
import {jsonHeaders, buildUrl} from '../../../utils/http';

test.describe('Agentinstances Validation API Tests', () => {
  test('createAgentInstance - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      jobKey: null,
      jobLease: 'x',
      history: [
        {
          historyItemId: 'x',
          loopIteration: null,
          role: null,
          content: [null],
          producedAt: 'x',
        },
      ],
      __extraField: 'unexpected',
    };
    const res = await request.post(buildUrl('/agent-instances', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAgentInstance - Body wrong top-level type', async ({request}) => {
    const requestBody: string[] = [];
    const res = await request.post(buildUrl('/agent-instances', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAgentInstance - Param history.0.historyItemId wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      jobKey: null,
      jobLease: 'x',
      history: [
        {
          historyItemId: 123,
          loopIteration: null,
          role: null,
          content: [null],
          producedAt: 'x',
        },
      ],
    };
    const res = await request.post(buildUrl('/agent-instances', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAgentInstance - Param history.0.historyItemId wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      jobKey: null,
      jobLease: 'x',
      history: [
        {
          historyItemId: true,
          loopIteration: null,
          role: null,
          content: [null],
          producedAt: 'x',
        },
      ],
    };
    const res = await request.post(buildUrl('/agent-instances', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAgentInstance - Param history.0.producedAt wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      jobKey: null,
      jobLease: 'x',
      history: [
        {
          historyItemId: 'x',
          loopIteration: null,
          role: null,
          content: [null],
          producedAt: 123,
        },
      ],
    };
    const res = await request.post(buildUrl('/agent-instances', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAgentInstance - Param history.0.producedAt wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      jobKey: null,
      jobLease: 'x',
      history: [
        {
          historyItemId: 'x',
          loopIteration: null,
          role: null,
          content: [null],
          producedAt: true,
        },
      ],
    };
    const res = await request.post(buildUrl('/agent-instances', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAgentInstance - Param jobLease wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      jobKey: null,
      jobLease: 123,
      history: [
        {
          historyItemId: 'x',
          loopIteration: null,
          role: null,
          content: [null],
          producedAt: 'x',
        },
      ],
    };
    const res = await request.post(buildUrl('/agent-instances', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAgentInstance - Param jobLease wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      jobKey: null,
      jobLease: true,
      history: [
        {
          historyItemId: 'x',
          loopIteration: null,
          role: null,
          content: [null],
          producedAt: 'x',
        },
      ],
    };
    const res = await request.post(buildUrl('/agent-instances', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAgentInstance - Missing elementInstanceKey (#1)', async ({
    request,
  }) => {
    const requestBody = {
      jobKey: null,
      jobLease: 'x',
      history: [
        {
          historyItemId: 'x',
          loopIteration: null,
          role: null,
          content: [null],
          producedAt: 'x',
        },
      ],
    };
    const res = await request.post(buildUrl('/agent-instances', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAgentInstance - Missing history (#1)', async ({request}) => {
    const requestBody = {
      elementInstanceKey: null,
      jobKey: null,
      jobLease: 'x',
    };
    const res = await request.post(buildUrl('/agent-instances', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAgentInstance - Missing jobKey (#1)', async ({request}) => {
    const requestBody = {
      elementInstanceKey: null,
      jobLease: 'x',
      history: [
        {
          historyItemId: 'x',
          loopIteration: null,
          role: null,
          content: [null],
          producedAt: 'x',
        },
      ],
    };
    const res = await request.post(buildUrl('/agent-instances', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAgentInstance - format invalid history.0.producedAt', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      jobKey: null,
      jobLease: 'x',
      history: [
        {
          historyItemId: 'x',
          loopIteration: null,
          role: null,
          content: [null],
          producedAt: 'not-a-datetime',
        },
      ],
    };
    const res = await request.post(buildUrl('/agent-instances', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAgentInstance - Missing elementInstanceKey (#2)', async ({
    request,
  }) => {
    const requestBody = {
      jobKey: 'x',
      jobLease: 'x',
      history: [],
    };
    const res = await request.post(buildUrl('/agent-instances', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAgentInstance - Missing history (#2)', async ({request}) => {
    const requestBody = {
      elementInstanceKey: 'x',
      jobKey: 'x',
      jobLease: 'x',
    };
    const res = await request.post(buildUrl('/agent-instances', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAgentInstance - Missing jobKey (#2)', async ({request}) => {
    const requestBody = {
      elementInstanceKey: 'x',
      jobLease: 'x',
      history: [],
    };
    const res = await request.post(buildUrl('/agent-instances', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAgentInstance - Missing body', async ({request}) => {
    const res = await request.post(buildUrl('/agent-instances', undefined), {
      headers: jsonHeaders(),
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAgentInstance - Missing combo elementInstanceKey,history', async ({
    request,
  }) => {
    const requestBody = {
      jobKey: 'x',
      jobLease: 'x',
    };
    const res = await request.post(buildUrl('/agent-instances', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAgentInstance - Missing combo elementInstanceKey,jobKey', async ({
    request,
  }) => {
    const requestBody = {
      jobLease: 'x',
      history: [],
    };
    const res = await request.post(buildUrl('/agent-instances', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAgentInstance - Missing combo elementInstanceKey,jobKey,history', async ({
    request,
  }) => {
    const requestBody = {
      jobLease: 'x',
    };
    const res = await request.post(buildUrl('/agent-instances', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAgentInstance - Missing combo jobKey,history', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      jobLease: 'x',
    };
    const res = await request.post(buildUrl('/agent-instances', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getAgentInstance - Path param agentInstanceKey pattern violation', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/agent-instances/{agentInstanceKey}', {agentInstanceKey: 'a'}),
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
  test('searchAgentInstanceHistory - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history/search', {
        agentInstanceKey: 'x',
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
  test('searchAgentInstanceHistory - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history/search', {
        agentInstanceKey: 'x',
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
  test('searchAgentInstanceHistory - Enum violation sort.0.field (#1)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'producedAt_INVALID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history/search', {
        agentInstanceKey: 'x',
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
  test('searchAgentInstanceHistory - Enum violation sort.0.field (#2)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'PRODUCEDAT',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history/search', {
        agentInstanceKey: 'x',
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
  test('searchAgentInstanceHistory - Enum violation sort.0.field (#3)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'producedat',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history/search', {
        agentInstanceKey: 'x',
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
  test('searchAgentInstanceHistory - Enum violation sort.0.order (#1)', async ({
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
      buildUrl('/agent-instances/{agentInstanceKey}/history/search', {
        agentInstanceKey: 'x',
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
  test('searchAgentInstanceHistory - Enum violation sort.0.order (#2)', async ({
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
      buildUrl('/agent-instances/{agentInstanceKey}/history/search', {
        agentInstanceKey: 'x',
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
  test('searchAgentInstanceHistory - Path param agentInstanceKey pattern violation', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history/search', {
        agentInstanceKey: 'a',
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
  test('searchAgentInstances - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchAgentInstances - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/agent-instances/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchAgentInstances - Enum violation sort.0.field (#1)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'agentInstanceKey_INVALID',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/agent-instances/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchAgentInstances - Enum violation sort.0.field (#2)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'AGENTINSTANCEKEY',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/agent-instances/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchAgentInstances - Enum violation sort.0.field (#3)', async ({
    request,
  }) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'agentinstancekey',
          },
        },
      },
    };
    const res = await request.post(
      buildUrl('/agent-instances/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchAgentInstances - Enum violation sort.0.order (#1)', async ({
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
      buildUrl('/agent-instances/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchAgentInstances - Enum violation sort.0.order (#2)', async ({
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
      buildUrl('/agent-instances/search', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateAgentInstance - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      jobKey: null,
      jobLease: 'x',
      __extraField: 'unexpected',
    };
    const res = await request.patch(
      buildUrl('/agent-instances/{agentInstanceKey}', {agentInstanceKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateAgentInstance - Body wrong top-level type', async ({request}) => {
    const requestBody: string[] = [];
    const res = await request.patch(
      buildUrl('/agent-instances/{agentInstanceKey}', {agentInstanceKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateAgentInstance - Param jobLease wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      jobKey: null,
      jobLease: 123,
    };
    const res = await request.patch(
      buildUrl('/agent-instances/{agentInstanceKey}', {agentInstanceKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateAgentInstance - Param jobLease wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      jobKey: null,
      jobLease: true,
    };
    const res = await request.patch(
      buildUrl('/agent-instances/{agentInstanceKey}', {agentInstanceKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateAgentInstance - Missing elementInstanceKey (#1)', async ({
    request,
  }) => {
    const requestBody = {
      jobKey: null,
      jobLease: 'x',
    };
    const res = await request.patch(
      buildUrl('/agent-instances/{agentInstanceKey}', {agentInstanceKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateAgentInstance - Missing elementInstanceKey (#2)', async ({
    request,
  }) => {
    const requestBody = {
      jobKey: 'x',
      jobLease: 'x',
    };
    const res = await request.patch(
      buildUrl('/agent-instances/{agentInstanceKey}', {agentInstanceKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateAgentInstance - Missing body', async ({request}) => {
    const res = await request.patch(
      buildUrl('/agent-instances/{agentInstanceKey}', {agentInstanceKey: 'x'}),
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
  test('updateAgentInstance - Path param agentInstanceKey pattern violation', async ({
    request,
  }) => {
    const res = await request.patch(
      buildUrl('/agent-instances/{agentInstanceKey}', {agentInstanceKey: 'a'}),
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
