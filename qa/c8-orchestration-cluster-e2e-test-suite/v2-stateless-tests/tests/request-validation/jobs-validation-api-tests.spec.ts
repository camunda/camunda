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

test.describe('Jobs Validation API Tests', () => {
  test('activateJobs - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      type: 'x',
      maxJobsToActivate: 1,
      timeout: 1,
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
      maxJobsToActivate: 'not-a-number',
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
  test('activateJobs - Param maxJobsToActivate wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      type: 'x',
      maxJobsToActivate: true,
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
  test('activateJobs - Param requestTimeout wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      type: 'x',
      maxJobsToActivate: 1,
      timeout: 1,
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
      maxJobsToActivate: 1,
      timeout: 1,
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
      maxJobsToActivate: 1,
      timeout: 'not-a-number',
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
      maxJobsToActivate: 1,
      timeout: true,
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
      maxJobsToActivate: 1,
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
  test('activateJobs - Param type wrong type (#2)', async ({request}) => {
    const requestBody = {
      type: true,
      maxJobsToActivate: 1,
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
      maxJobsToActivate: 1,
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
      maxJobsToActivate: 1,
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
  test('activateJobs - Missing combo maxJobsToActivate,timeout', async ({
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
  test('activateJobs - Missing combo type,maxJobsToActivate,timeout', async ({
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
  test('getGlobalJobStatistics - Missing param query.from', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/jobs/statistics/global', undefined, {
        to: '2030-01-01T00:00:00.000Z',
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
  test('getGlobalJobStatistics - Missing param query.to', async ({request}) => {
    const res = await request.get(
      buildUrl('/jobs/statistics/global', undefined, {
        from: '2020-01-01T00:00:00.000Z',
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
  test('getGlobalJobStatistics - Param query.from wrong type', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/jobs/statistics/global', undefined, {
        from: '__INVALID_STRING__',
        to: '2030-01-01T00:00:00.000Z',
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
  test('getGlobalJobStatistics - Param query.to wrong type', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/jobs/statistics/global', undefined, {
        from: '2020-01-01T00:00:00.000Z',
        to: '__INVALID_STRING__',
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
  test('getJobErrorStatistics - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 'x',
        to: 'x',
        jobType: 'x',
      },
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/errors', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobErrorStatistics - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/jobs/statistics/errors', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobErrorStatistics - Param filter.from wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 123,
        to: 'x',
        jobType: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/errors', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobErrorStatistics - Param filter.from wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: true,
        to: 'x',
        jobType: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/errors', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobErrorStatistics - Param filter.jobType wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 'x',
        to: 'x',
        jobType: 123,
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/errors', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobErrorStatistics - Param filter.jobType wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 'x',
        to: 'x',
        jobType: true,
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/errors', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobErrorStatistics - Param filter.to wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 'x',
        to: 123,
        jobType: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/errors', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobErrorStatistics - Param filter.to wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 'x',
        to: true,
        jobType: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/errors', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobErrorStatistics - Missing filter.from', async ({request}) => {
    const requestBody = {
      filter: {
        to: 'x',
        jobType: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/errors', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobErrorStatistics - Missing filter.jobType', async ({request}) => {
    const requestBody = {
      filter: {
        from: 'x',
        to: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/errors', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobErrorStatistics - Missing filter.to', async ({request}) => {
    const requestBody = {
      filter: {
        from: 'x',
        jobType: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/errors', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobErrorStatistics - format invalid filter.from', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 'not-a-datetime',
        to: 'x',
        jobType: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/errors', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobErrorStatistics - format invalid filter.to', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 'x',
        to: 'not-a-datetime',
        jobType: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/errors', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobErrorStatistics - Missing filter', async ({request}) => {
    const requestBody = {};
    const res = await request.post(
      buildUrl('/jobs/statistics/errors', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobErrorStatistics - Missing body', async ({request}) => {
    const res = await request.post(
      buildUrl('/jobs/statistics/errors', undefined),
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
  test('getJobTimeSeriesStatistics - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 'x',
        to: 'x',
        jobType: 'x',
      },
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/time-series', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobTimeSeriesStatistics - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/jobs/statistics/time-series', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobTimeSeriesStatistics - Param filter.from wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 123,
        to: 'x',
        jobType: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/time-series', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobTimeSeriesStatistics - Param filter.from wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: true,
        to: 'x',
        jobType: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/time-series', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobTimeSeriesStatistics - Param filter.jobType wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 'x',
        to: 'x',
        jobType: 123,
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/time-series', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobTimeSeriesStatistics - Param filter.jobType wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 'x',
        to: 'x',
        jobType: true,
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/time-series', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobTimeSeriesStatistics - Param filter.to wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 'x',
        to: 123,
        jobType: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/time-series', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobTimeSeriesStatistics - Param filter.to wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 'x',
        to: true,
        jobType: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/time-series', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobTimeSeriesStatistics - Missing filter.from', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        to: 'x',
        jobType: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/time-series', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobTimeSeriesStatistics - Missing filter.jobType', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 'x',
        to: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/time-series', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobTimeSeriesStatistics - Missing filter.to', async ({request}) => {
    const requestBody = {
      filter: {
        from: 'x',
        jobType: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/time-series', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobTimeSeriesStatistics - format invalid filter.from', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 'not-a-datetime',
        to: 'x',
        jobType: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/time-series', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobTimeSeriesStatistics - format invalid filter.to', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 'x',
        to: 'not-a-datetime',
        jobType: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/time-series', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobTimeSeriesStatistics - Missing filter', async ({request}) => {
    const requestBody = {};
    const res = await request.post(
      buildUrl('/jobs/statistics/time-series', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobTimeSeriesStatistics - Missing body', async ({request}) => {
    const res = await request.post(
      buildUrl('/jobs/statistics/time-series', undefined),
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
  test('getJobTypeStatistics - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/by-types', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobTypeStatistics - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/jobs/statistics/by-types', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobTypeStatistics - Missing body', async ({request}) => {
    const res = await request.post(
      buildUrl('/jobs/statistics/by-types', undefined),
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
  test('getJobWorkerStatistics - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 'x',
        to: 'x',
        jobType: 'x',
      },
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/by-workers', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobWorkerStatistics - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/jobs/statistics/by-workers', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobWorkerStatistics - Param filter.from wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 123,
        to: 'x',
        jobType: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/by-workers', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobWorkerStatistics - Param filter.from wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: true,
        to: 'x',
        jobType: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/by-workers', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobWorkerStatistics - Param filter.jobType wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 'x',
        to: 'x',
        jobType: 123,
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/by-workers', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobWorkerStatistics - Param filter.jobType wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 'x',
        to: 'x',
        jobType: true,
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/by-workers', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobWorkerStatistics - Param filter.to wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 'x',
        to: 123,
        jobType: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/by-workers', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobWorkerStatistics - Param filter.to wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 'x',
        to: true,
        jobType: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/by-workers', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobWorkerStatistics - Missing filter.from', async ({request}) => {
    const requestBody = {
      filter: {
        to: 'x',
        jobType: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/by-workers', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobWorkerStatistics - Missing filter.jobType', async ({request}) => {
    const requestBody = {
      filter: {
        from: 'x',
        to: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/by-workers', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobWorkerStatistics - Missing filter.to', async ({request}) => {
    const requestBody = {
      filter: {
        from: 'x',
        jobType: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/by-workers', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobWorkerStatistics - format invalid filter.from', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 'not-a-datetime',
        to: 'x',
        jobType: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/by-workers', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobWorkerStatistics - format invalid filter.to', async ({
    request,
  }) => {
    const requestBody = {
      filter: {
        from: 'x',
        to: 'not-a-datetime',
        jobType: 'x',
      },
    };
    const res = await request.post(
      buildUrl('/jobs/statistics/by-workers', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobWorkerStatistics - Missing filter', async ({request}) => {
    const requestBody = {};
    const res = await request.post(
      buildUrl('/jobs/statistics/by-workers', undefined),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getJobWorkerStatistics - Missing body', async ({request}) => {
    const res = await request.post(
      buildUrl('/jobs/statistics/by-workers', undefined),
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
            value: 'creationTime_INVALID',
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
            value: 'CREATIONTIME',
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
  test('searchJobs - Enum violation sort.0.field (#3)', async ({request}) => {
    const requestBody = {
      sort: {
        '0': {
          field: {
            __invalidEnum: true,
            value: 'creationtime',
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
        priority: 1,
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
  test('updateJob - Param changeset.priority wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      changeset: {
        retries: 1,
        timeout: 1,
        priority: 'not-a-number',
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
  test('updateJob - Param changeset.priority wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      changeset: {
        retries: 1,
        timeout: 1,
        priority: true,
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
  test('updateJob - Param changeset.retries wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      changeset: {
        retries: 'not-a-number',
        timeout: 1,
        priority: 1,
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
        priority: 1,
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
        priority: 1,
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
        priority: 1,
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
        priority: 1,
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
        priority: 1,
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
        priority: 1,
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
  // Known failing (see known-failing-tests.json): operationReference validator only rejects the first invalid-format variant the generator produces
  test.skip('updateJob - Constraint violation operationReference (#2)', async ({
    request,
  }) => {
    const requestBody = {
      changeset: {
        retries: 1,
        timeout: 1,
        priority: 1,
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
  // Known failing (see known-failing-tests.json): operationReference validator only rejects the first invalid-format variant the generator produces
  test.skip('updateJob - Constraint violation operationReference (#3)', async ({
    request,
  }) => {
    const requestBody = {
      changeset: {
        retries: 1,
        timeout: 1,
        priority: 1,
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
  test('updateJobsBatchOperation - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      filter: {},
      changeset: {
        retries: 1,
        timeout: 1,
        priority: 1,
      },
      operationReference: 1,
      __unexpectedField: 'x',
    };
    const res = await request.post(buildUrl('/jobs/batch-update', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJobsBatchOperation - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(buildUrl('/jobs/batch-update', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJobsBatchOperation - Param changeset.priority wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {},
      changeset: {
        retries: 1,
        timeout: 1,
        priority: 'not-a-number',
      },
      operationReference: 1,
    };
    const res = await request.post(buildUrl('/jobs/batch-update', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJobsBatchOperation - Param changeset.priority wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {},
      changeset: {
        retries: 1,
        timeout: 1,
        priority: true,
      },
      operationReference: 1,
    };
    const res = await request.post(buildUrl('/jobs/batch-update', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJobsBatchOperation - Param changeset.retries wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {},
      changeset: {
        retries: 'not-a-number',
        timeout: 1,
        priority: 1,
      },
      operationReference: 1,
    };
    const res = await request.post(buildUrl('/jobs/batch-update', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJobsBatchOperation - Param changeset.retries wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {},
      changeset: {
        retries: true,
        timeout: 1,
        priority: 1,
      },
      operationReference: 1,
    };
    const res = await request.post(buildUrl('/jobs/batch-update', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJobsBatchOperation - Param changeset.timeout wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {},
      changeset: {
        retries: 1,
        timeout: 'not-a-number',
        priority: 1,
      },
      operationReference: 1,
    };
    const res = await request.post(buildUrl('/jobs/batch-update', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJobsBatchOperation - Param changeset.timeout wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {},
      changeset: {
        retries: 1,
        timeout: true,
        priority: 1,
      },
      operationReference: 1,
    };
    const res = await request.post(buildUrl('/jobs/batch-update', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJobsBatchOperation - Param operationReference wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {},
      changeset: {
        retries: 1,
        timeout: 1,
        priority: 1,
      },
      operationReference: 'not-a-number',
    };
    const res = await request.post(buildUrl('/jobs/batch-update', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJobsBatchOperation - Param operationReference wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {},
      changeset: {
        retries: 1,
        timeout: 1,
        priority: 1,
      },
      operationReference: true,
    };
    const res = await request.post(buildUrl('/jobs/batch-update', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJobsBatchOperation - Constraint violation operationReference (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {},
      changeset: {
        retries: 1,
        timeout: 1,
        priority: 1,
      },
      operationReference: 0.99999,
    };
    const res = await request.post(buildUrl('/jobs/batch-update', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJobsBatchOperation - Constraint violation operationReference (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {},
      changeset: {
        retries: 1,
        timeout: 1,
        priority: 1,
      },
      operationReference: 0,
    };
    const res = await request.post(buildUrl('/jobs/batch-update', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJobsBatchOperation - Constraint violation operationReference (#3)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {},
      changeset: {
        retries: 1,
        timeout: 1,
        priority: 1,
      },
      operationReference: -99,
    };
    const res = await request.post(buildUrl('/jobs/batch-update', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJobsBatchOperation - Missing changeset (#1)', async ({
    request,
  }) => {
    const requestBody = {
      filter: {},
      operationReference: 1,
    };
    const res = await request.post(buildUrl('/jobs/batch-update', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJobsBatchOperation - Missing filter (#1)', async ({request}) => {
    const requestBody = {
      changeset: {
        retries: 1,
        timeout: 1,
        priority: 1,
      },
      operationReference: 1,
    };
    const res = await request.post(buildUrl('/jobs/batch-update', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJobsBatchOperation - Missing changeset (#2)', async ({
    request,
  }) => {
    const requestBody = {
      filter: 'x',
    };
    const res = await request.post(buildUrl('/jobs/batch-update', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJobsBatchOperation - Missing filter (#2)', async ({request}) => {
    const requestBody = {
      changeset: 'x',
    };
    const res = await request.post(buildUrl('/jobs/batch-update', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJobsBatchOperation - Missing body', async ({request}) => {
    const res = await request.post(buildUrl('/jobs/batch-update', undefined), {
      headers: jsonHeaders(),
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('updateJobsBatchOperation - Missing combo filter,changeset', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.post(buildUrl('/jobs/batch-update', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
});
