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

test.describe('Jobs Validation API Tests', () => {
  test('activateJobs - Additional prop __extraField', async ({request}) => {
    const requestBody = {
      type: 'x',
      timeout: 1,
      maxJobsToActivate: 1,
      __extraField: 'unexpected',
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
  test('activateJobs - Missing maxJobsToActivate', async ({request}) => {
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
  test('activateJobs - Missing timeout', async ({request}) => {
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
  test('activateJobs - Missing type', async ({request}) => {
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
  test('completeJob - Additional prop __extraField', async ({request}) => {
    const requestBody = {
      __extraField: 'unexpected',
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
  test('completeJob - Missing body', async ({request}) => {
    const res = await request.post(
      buildUrl('/jobs/{jobKey}/completion', {jobKey: 'x'}),
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
  test('failJob - Additional prop __extraField', async ({request}) => {
    const requestBody = {
      retries: 1,
      __extraField: 'unexpected',
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
  test('failJob - Missing body', async ({request}) => {
    const res = await request.post(
      buildUrl('/jobs/{jobKey}/failure', {jobKey: 'x'}),
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
  test('searchJobs - Additional prop __extraField', async ({request}) => {
    const requestBody = {
      __extraField: 'unexpected',
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
  test('searchJobs - Missing body', async ({request}) => {
    const res = await request.post(buildUrl('/jobs/search', undefined), {
      headers: jsonHeaders(),
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('throwJobError - Additional prop __extraField', async ({request}) => {
    const requestBody = {
      errorCode: 'x',
      __extraField: 'unexpected',
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
  test('updateJob - Additional prop __extraField', async ({request}) => {
    const requestBody = {
      changeset: {
        retries: 1,
      },
      operationReference: 1,
      __extraField: 'unexpected',
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
      },
      operationReference: 0.99999,
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
  test('updateJob - Constraint violation operationReference (#2)', async ({
    request,
  }) => {
    const requestBody = {
      changeset: {
        retries: 1,
      },
      operationReference: 0,
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
  test('updateJob - Constraint violation operationReference (#3)', async ({
    request,
  }) => {
    const requestBody = {
      changeset: {
        retries: 1,
      },
      operationReference: -99,
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
});
