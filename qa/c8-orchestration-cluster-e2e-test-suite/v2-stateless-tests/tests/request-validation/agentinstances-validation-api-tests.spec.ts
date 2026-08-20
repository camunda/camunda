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

test.describe('Agentinstances Validation API Tests', () => {
  test('createAgentInstance - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      definition: {
        model: 'x',
        provider: 'x',
        systemPrompt: 'x',
      },
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
  test('createAgentInstance - Param definition.model wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      definition: {
        model: 123,
        provider: 'x',
        systemPrompt: 'x',
      },
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
  test('createAgentInstance - Param definition.model wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      definition: {
        model: true,
        provider: 'x',
        systemPrompt: 'x',
      },
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
  test('createAgentInstance - Param definition.provider wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      definition: {
        model: 'x',
        provider: 123,
        systemPrompt: 'x',
      },
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
  test('createAgentInstance - Param definition.provider wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      definition: {
        model: 'x',
        provider: true,
        systemPrompt: 'x',
      },
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
  test('createAgentInstance - Param definition.systemPrompt wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      definition: {
        model: 'x',
        provider: 'x',
        systemPrompt: 123,
      },
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
  test('createAgentInstance - Param definition.systemPrompt wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      definition: {
        model: 'x',
        provider: 'x',
        systemPrompt: true,
      },
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
  test('createAgentInstance - Missing definition (#1)', async ({request}) => {
    const requestBody = {
      elementInstanceKey: null,
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
  test('createAgentInstance - Missing definition.model', async ({request}) => {
    const requestBody = {
      elementInstanceKey: null,
      definition: {
        provider: 'x',
        systemPrompt: 'x',
      },
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
  test('createAgentInstance - Missing definition.provider', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      definition: {
        model: 'x',
        systemPrompt: 'x',
      },
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
  test('createAgentInstance - Missing definition.systemPrompt', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      definition: {
        model: 'x',
        provider: 'x',
      },
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
      definition: {
        model: 'x',
        provider: 'x',
        systemPrompt: 'x',
      },
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
  test('createAgentInstance - Missing definition (#2)', async ({request}) => {
    const requestBody = {
      elementInstanceKey: 'x',
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
      definition: 'x',
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
  test('createAgentInstance - Missing combo elementInstanceKey,definition', async ({
    request,
  }) => {
    const requestBody = {};
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
  test('createAgentInstanceHistoryItem - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      jobKey: null,
      jobLease: 'x',
      role: null,
      content: [null],
      producedAt: 'x',
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Param jobLease wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      jobKey: null,
      jobLease: 123,
      role: null,
      content: [null],
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Param jobLease wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      jobKey: null,
      jobLease: true,
      role: null,
      content: [null],
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Param producedAt wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      jobKey: null,
      jobLease: 'x',
      role: null,
      content: [null],
      producedAt: 123,
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Param producedAt wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      jobKey: null,
      jobLease: 'x',
      role: null,
      content: [null],
      producedAt: true,
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing content (#1)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      jobKey: null,
      jobLease: 'x',
      role: null,
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing elementInstanceKey (#1)', async ({
    request,
  }) => {
    const requestBody = {
      jobKey: null,
      jobLease: 'x',
      role: null,
      content: [null],
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing jobKey (#1)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      jobLease: 'x',
      role: null,
      content: [null],
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing jobLease (#1)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      jobKey: null,
      role: null,
      content: [null],
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing producedAt (#1)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      jobKey: null,
      jobLease: 'x',
      role: null,
      content: [null],
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing role (#1)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      jobKey: null,
      jobLease: 'x',
      content: [null],
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - format invalid producedAt', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: null,
      jobKey: null,
      jobLease: 'x',
      role: null,
      content: [null],
      producedAt: 'not-a-datetime',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing content (#2)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      jobKey: 'x',
      jobLease: 'x',
      role: 'x',
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing elementInstanceKey (#2)', async ({
    request,
  }) => {
    const requestBody = {
      jobKey: 'x',
      jobLease: 'x',
      role: 'x',
      content: [],
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing jobKey (#2)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      jobLease: 'x',
      role: 'x',
      content: [],
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing jobLease (#2)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      jobKey: 'x',
      role: 'x',
      content: [],
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing producedAt (#2)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      jobKey: 'x',
      jobLease: 'x',
      role: 'x',
      content: [],
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing role (#2)', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      jobKey: 'x',
      jobLease: 'x',
      content: [],
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing body', async ({request}) => {
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
        agentInstanceKey: 'x',
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
  test('createAgentInstanceHistoryItem - Missing combo content,producedAt', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      jobKey: 'x',
      jobLease: 'x',
      role: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo elementInstanceKey,content', async ({
    request,
  }) => {
    const requestBody = {
      jobKey: 'x',
      jobLease: 'x',
      role: 'x',
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo elementInstanceKey,content,producedAt', async ({
    request,
  }) => {
    const requestBody = {
      jobKey: 'x',
      jobLease: 'x',
      role: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo elementInstanceKey,jobKey', async ({
    request,
  }) => {
    const requestBody = {
      jobLease: 'x',
      role: 'x',
      content: [],
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo elementInstanceKey,jobKey,content', async ({
    request,
  }) => {
    const requestBody = {
      jobLease: 'x',
      role: 'x',
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo elementInstanceKey,jobKey,jobLease', async ({
    request,
  }) => {
    const requestBody = {
      role: 'x',
      content: [],
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo elementInstanceKey,jobKey,producedAt', async ({
    request,
  }) => {
    const requestBody = {
      jobLease: 'x',
      role: 'x',
      content: [],
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo elementInstanceKey,jobKey,role', async ({
    request,
  }) => {
    const requestBody = {
      jobLease: 'x',
      content: [],
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo elementInstanceKey,jobLease', async ({
    request,
  }) => {
    const requestBody = {
      jobKey: 'x',
      role: 'x',
      content: [],
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo elementInstanceKey,jobLease,content', async ({
    request,
  }) => {
    const requestBody = {
      jobKey: 'x',
      role: 'x',
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo elementInstanceKey,jobLease,producedAt', async ({
    request,
  }) => {
    const requestBody = {
      jobKey: 'x',
      role: 'x',
      content: [],
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo elementInstanceKey,jobLease,role', async ({
    request,
  }) => {
    const requestBody = {
      jobKey: 'x',
      content: [],
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo elementInstanceKey,producedAt', async ({
    request,
  }) => {
    const requestBody = {
      jobKey: 'x',
      jobLease: 'x',
      role: 'x',
      content: [],
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo elementInstanceKey,role', async ({
    request,
  }) => {
    const requestBody = {
      jobKey: 'x',
      jobLease: 'x',
      content: [],
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo elementInstanceKey,role,content', async ({
    request,
  }) => {
    const requestBody = {
      jobKey: 'x',
      jobLease: 'x',
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo elementInstanceKey,role,producedAt', async ({
    request,
  }) => {
    const requestBody = {
      jobKey: 'x',
      jobLease: 'x',
      content: [],
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo jobKey,content', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      jobLease: 'x',
      role: 'x',
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo jobKey,content,producedAt', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      jobLease: 'x',
      role: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo jobKey,jobLease', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      role: 'x',
      content: [],
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo jobKey,jobLease,content', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      role: 'x',
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo jobKey,jobLease,producedAt', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      role: 'x',
      content: [],
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo jobKey,jobLease,role', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      content: [],
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo jobKey,producedAt', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      jobLease: 'x',
      role: 'x',
      content: [],
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo jobKey,role', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      jobLease: 'x',
      content: [],
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo jobKey,role,content', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      jobLease: 'x',
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo jobKey,role,producedAt', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      jobLease: 'x',
      content: [],
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo jobLease,content', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      jobKey: 'x',
      role: 'x',
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo jobLease,content,producedAt', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      jobKey: 'x',
      role: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo jobLease,producedAt', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      jobKey: 'x',
      role: 'x',
      content: [],
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo jobLease,role', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      jobKey: 'x',
      content: [],
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo jobLease,role,content', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      jobKey: 'x',
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo jobLease,role,producedAt', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      jobKey: 'x',
      content: [],
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo role,content', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      jobKey: 'x',
      jobLease: 'x',
      producedAt: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo role,content,producedAt', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      jobKey: 'x',
      jobLease: 'x',
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Missing combo role,producedAt', async ({
    request,
  }) => {
    const requestBody = {
      elementInstanceKey: 'x',
      jobKey: 'x',
      jobLease: 'x',
      content: [],
    };
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('createAgentInstanceHistoryItem - Path param agentInstanceKey pattern violation', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/agent-instances/{agentInstanceKey}/history', {
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
  test('updateAgentInstance - Missing elementInstanceKey', async ({
    request,
  }) => {
    const requestBody = {};
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
