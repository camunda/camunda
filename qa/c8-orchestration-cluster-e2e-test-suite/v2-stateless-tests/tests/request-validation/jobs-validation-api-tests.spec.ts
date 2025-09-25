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

test.describe('Jobs Validation API Tests', () => {
  test('activateJobs - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      type: 'x',
      timeout: 1,
      maxJobsToActivate: 1,
      requestTimeout: 1,
      __unexpectedField: 'x',
    };
    const res = await request.post(buildUrl('/jobs/activation', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('activateJobs - Body wrong top-level type', async ({request}) => {
    const requestBody: string[] = [];
    const res = await request.post(buildUrl('/jobs/activation', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('activateJobs - Param maxJobsToActivate wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      type: 'x',
      timeout: 1,
      maxJobsToActivate: 'not-a-number',
      requestTimeout: 1,
    };
    const res = await request.post(buildUrl('/jobs/activation', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('activateJobs - Param maxJobsToActivate wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      type: 'x',
      timeout: 1,
      maxJobsToActivate: true,
      requestTimeout: 1,
    };
    const res = await request.post(buildUrl('/jobs/activation', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('activateJobs - Param requestTimeout wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      type: 'x',
      timeout: 1,
      maxJobsToActivate: 1,
      requestTimeout: 'not-a-number',
    };
    const res = await request.post(buildUrl('/jobs/activation', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('activateJobs - Param requestTimeout wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      type: 'x',
      timeout: 1,
      maxJobsToActivate: 1,
      requestTimeout: true,
    };
    const res = await request.post(buildUrl('/jobs/activation', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('activateJobs - Param timeout wrong type (#1)', async ({request}) => {
    const requestBody = {
      type: 'x',
      timeout: 'not-a-number',
      maxJobsToActivate: 1,
      requestTimeout: 1,
    };
    const res = await request.post(buildUrl('/jobs/activation', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('activateJobs - Param timeout wrong type (#2)', async ({request}) => {
    const requestBody = {
      type: 'x',
      timeout: true,
      maxJobsToActivate: 1,
      requestTimeout: 1,
    };
    const res = await request.post(buildUrl('/jobs/activation', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('activateJobs - Param type wrong type (#1)', async ({request}) => {
    const requestBody = {
      type: 123,
      timeout: 1,
      maxJobsToActivate: 1,
      requestTimeout: 1,
    };
    const res = await request.post(buildUrl('/jobs/activation', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('activateJobs - Param type wrong type (#2)', async ({request}) => {
    const requestBody = {
      type: true,
      timeout: 1,
      maxJobsToActivate: 1,
      requestTimeout: 1,
    };
    const res = await request.post(buildUrl('/jobs/activation', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('activateJobs - Missing maxJobsToActivate (#1)', async ({request}) => {
    const requestBody = {
      type: 'x',
      timeout: 1,
      requestTimeout: 1,
    };
    const res = await request.post(buildUrl('/jobs/activation', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('activateJobs - Missing timeout (#1)', async ({request}) => {
    const requestBody = {
      type: 'x',
      maxJobsToActivate: 1,
      requestTimeout: 1,
    };
    const res = await request.post(buildUrl('/jobs/activation', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('activateJobs - Missing type (#1)', async ({request}) => {
    const requestBody = {
      timeout: 1,
      maxJobsToActivate: 1,
      requestTimeout: 1,
    };
    const res = await request.post(buildUrl('/jobs/activation', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('activateJobs - Missing maxJobsToActivate (#2)', async ({request}) => {
    const requestBody = {
      type: 'x',
      timeout: 1,
    };
    const res = await request.post(buildUrl('/jobs/activation', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('activateJobs - Missing timeout (#2)', async ({request}) => {
    const requestBody = {
      type: 'x',
      maxJobsToActivate: 1,
    };
    const res = await request.post(buildUrl('/jobs/activation', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('activateJobs - Missing type (#2)', async ({request}) => {
    const requestBody = {
      timeout: 1,
      maxJobsToActivate: 1,
    };
    const res = await request.post(buildUrl('/jobs/activation', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('activateJobs - Missing body', async ({request}) => {
    const res = await request.post(buildUrl('/jobs/activation', undefined), {
      headers: jsonHeaders(),
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('activateJobs - Missing combo timeout,maxJobsToActivate', async ({
    request,
  }) => {
    const requestBody = {
      type: 'x',
    };
    const res = await request.post(buildUrl('/jobs/activation', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('activateJobs - Missing combo type,maxJobsToActivate', async ({
    request,
  }) => {
    const requestBody = {
      timeout: 1,
    };
    const res = await request.post(buildUrl('/jobs/activation', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('activateJobs - Missing combo type,timeout', async ({request}) => {
    const requestBody = {
      maxJobsToActivate: 1,
    };
    const res = await request.post(buildUrl('/jobs/activation', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('activateJobs - Missing combo type,timeout,maxJobsToActivate', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.post(buildUrl('/jobs/activation', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('completeJob - Additional prop __unexpectedField', async ({request}) => {
    const requestBody = {
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/jobs/{jobKey}/completion', {jobKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('completeJob - Body wrong top-level type', async ({request}) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/jobs/{jobKey}/completion', {jobKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('completeJob - Path param jobKey pattern violation', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/jobs/{jobKey}/completion', {jobKey: 'a'}),
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
  test('failJob - Additional prop __unexpectedField', async ({request}) => {
    const requestBody = {
      retries: 1,
      retryBackOff: 1,
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/jobs/{jobKey}/failure', {jobKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('failJob - Body wrong top-level type', async ({request}) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/jobs/{jobKey}/failure', {jobKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('failJob - Param retries wrong type (#1)', async ({request}) => {
    const requestBody = {
      retries: 'not-a-number',
      retryBackOff: 1,
    };
    const res = await request.post(
      buildUrl('/jobs/{jobKey}/failure', {jobKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('failJob - Param retries wrong type (#2)', async ({request}) => {
    const requestBody = {
      retries: true,
      retryBackOff: 1,
    };
    const res = await request.post(
      buildUrl('/jobs/{jobKey}/failure', {jobKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('failJob - Param retryBackOff wrong type (#1)', async ({request}) => {
    const requestBody = {
      retries: 1,
      retryBackOff: 'not-a-number',
    };
    const res = await request.post(
      buildUrl('/jobs/{jobKey}/failure', {jobKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('failJob - Param retryBackOff wrong type (#2)', async ({request}) => {
    const requestBody = {
      retries: 1,
      retryBackOff: true,
    };
    const res = await request.post(
      buildUrl('/jobs/{jobKey}/failure', {jobKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('failJob - Path param jobKey pattern violation', async ({request}) => {
    const res = await request.post(
      buildUrl('/jobs/{jobKey}/failure', {jobKey: 'a'}),
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
  test('searchJobs - Additional prop __unexpectedField', async ({request}) => {
    const requestBody = {
      __unexpectedField: 'x',
    };
    const res = await request.post(buildUrl('/jobs/search', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchJobs - Body wrong top-level type', async ({request}) => {
    const requestBody: string[] = [];
    const res = await request.post(buildUrl('/jobs/search', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchJobs - Enum violation sort.0.field (#1)', async ({request}) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'deadline_INVALID',
          },
        },
      },
    };
    const res = await request.post(buildUrl('/jobs/search', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchJobs - Enum violation sort.0.field (#2)', async ({request}) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'DEADLINE',
          },
        },
      },
    };
    const res = await request.post(buildUrl('/jobs/search', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchJobs - Enum violation sort.0.order (#1)', async ({request}) => {
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
    const res = await request.post(buildUrl('/jobs/search', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('searchJobs - Enum violation sort.0.order (#2)', async ({request}) => {
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
    const res = await request.post(buildUrl('/jobs/search', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('throwJobError - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      errorCode: 'x',
      __unexpectedField: 'x',
    };
    const res = await request.post(
      buildUrl('/jobs/{jobKey}/error', {jobKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('throwJobError - Body wrong top-level type', async ({request}) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/jobs/{jobKey}/error', {jobKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('throwJobError - Param errorCode wrong type (#1)', async ({request}) => {
    const requestBody = {
      errorCode: 123,
    };
    const res = await request.post(
      buildUrl('/jobs/{jobKey}/error', {jobKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('throwJobError - Param errorCode wrong type (#2)', async ({request}) => {
    const requestBody = {
      errorCode: true,
    };
    const res = await request.post(
      buildUrl('/jobs/{jobKey}/error', {jobKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('throwJobError - Missing errorCode', async ({request}) => {
    const requestBody = {};
    const res = await request.post(
      buildUrl('/jobs/{jobKey}/error', {jobKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('throwJobError - Missing body', async ({request}) => {
    const res = await request.post(
      buildUrl('/jobs/{jobKey}/error', {jobKey: 'x'}),
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
  test('throwJobError - Path param jobKey pattern violation', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/jobs/{jobKey}/error', {jobKey: 'a'}),
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
  test('updateJob - Additional prop __unexpectedField', async ({request}) => {
    const requestBody = {
      changeset: {
        retries: 1,
        timeout: 1,
      },
      operationReference: 1,
      __unexpectedField: 'x',
    };
    const res = await request.patch(buildUrl('/jobs/{jobKey}', {jobKey: 'x'}), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJob - Body wrong top-level type', async ({request}) => {
    const requestBody: string[] = [];
    const res = await request.patch(buildUrl('/jobs/{jobKey}', {jobKey: 'x'}), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJob - Param changeset.retries wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      changeset: {
        retries: 'not-a-number',
        timeout: 1,
      },
      operationReference: 1,
    };
    const res = await request.patch(buildUrl('/jobs/{jobKey}', {jobKey: 'x'}), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJob - Param changeset.retries wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      changeset: {
        retries: true,
        timeout: 1,
      },
      operationReference: 1,
    };
    const res = await request.patch(buildUrl('/jobs/{jobKey}', {jobKey: 'x'}), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJob - Param changeset.timeout wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      changeset: {
        retries: 1,
        timeout: 'not-a-number',
      },
      operationReference: 1,
    };
    const res = await request.patch(buildUrl('/jobs/{jobKey}', {jobKey: 'x'}), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJob - Param changeset.timeout wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      changeset: {
        retries: 1,
        timeout: true,
      },
      operationReference: 1,
    };
    const res = await request.patch(buildUrl('/jobs/{jobKey}', {jobKey: 'x'}), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJob - Param operationReference wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      changeset: {
        retries: 1,
        timeout: 1,
      },
      operationReference: 'not-a-number',
    };
    const res = await request.patch(buildUrl('/jobs/{jobKey}', {jobKey: 'x'}), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJob - Param operationReference wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      changeset: {
        retries: 1,
        timeout: 1,
      },
      operationReference: true,
    };
    const res = await request.patch(buildUrl('/jobs/{jobKey}', {jobKey: 'x'}), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJob - Constraint violation operationReference (#1)', async ({
    request,
  }) => {
    const requestBody = {
      changeset: {
        retries: 1,
        timeout: 1,
      },
      operationReference: 0.99999,
    };
    const res = await request.patch(buildUrl('/jobs/{jobKey}', {jobKey: '1'}), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJob - Constraint violation operationReference (#2)', async ({
    request,
  }) => {
    const requestBody = {
      changeset: {
        retries: 1,
        timeout: 1,
      },
      operationReference: 0,
    };
    const res = await request.patch(buildUrl('/jobs/{jobKey}', {jobKey: '1'}), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJob - Constraint violation operationReference (#3)', async ({
    request,
  }) => {
    const requestBody = {
      changeset: {
        retries: 1,
        timeout: 1,
      },
      operationReference: -99,
    };
    const res = await request.patch(buildUrl('/jobs/{jobKey}', {jobKey: '1'}), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJob - Missing changeset (#1)', async ({request}) => {
    const requestBody = {
      operationReference: 1,
    };
    const res = await request.patch(buildUrl('/jobs/{jobKey}', {jobKey: 'x'}), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJob - Missing changeset (#2)', async ({request}) => {
    const requestBody = {};
    const res = await request.patch(buildUrl('/jobs/{jobKey}', {jobKey: 'x'}), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJob - Missing body', async ({request}) => {
    const res = await request.patch(buildUrl('/jobs/{jobKey}', {jobKey: 'x'}), {
      headers: jsonHeaders(),
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJob - Path param jobKey pattern violation', async ({request}) => {
    const res = await request.patch(buildUrl('/jobs/{jobKey}', {jobKey: 'a'}), {
      headers: jsonHeaders(),
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
});
