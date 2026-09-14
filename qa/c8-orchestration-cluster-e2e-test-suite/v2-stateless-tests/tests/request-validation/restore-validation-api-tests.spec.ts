/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/*
 * GENERATED FILE - DO NOT EDIT MANUALLY
 * Generated At: 2026-09-14T11:24:48.545Z
 * Spec Commit: aceb228ded9088275d9853c2dc68763c2f07e0a2
 */
import {test, expect} from '@playwright/test';
import {
  jsonHeaders,
  waitForConfigurationChange,
  buildUrl,
} from '../../../utils/http';

test.describe('Restore Validation API Tests', () => {
  test.describe.configure({mode: 'serial'});
  test('restore - Additional prop __extraField', async ({request}) => {
    const requestBody = {
      from: 'x',
      to: 'x',
      __extraField: 'unexpected',
    };
    let enterRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const enterRecovery = await request.patch(
            buildUrl('/mode', undefined, {mode: 'RECOVERING'}),
            {
              headers: jsonHeaders(),
            },
          );
          if (enterRecovery.status() === 200) {
            ({changeId: enterRecoveryChangeId} = await enterRecovery.json());
          }
          return enterRecovery.status();
        },
        {timeout: 60_000},
      )
      .toBe(200);
    expect(enterRecoveryChangeId).toBeDefined();
    await waitForConfigurationChange(request, enterRecoveryChangeId!);
    const res = await request.post(buildUrl('/restore', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    let exitRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const exitRecovery = await request.patch(
            buildUrl('/mode', undefined, {mode: 'PROCESSING'}),
            {
              headers: jsonHeaders(),
            },
          );
          if (exitRecovery.status() === 200) {
            ({changeId: exitRecoveryChangeId} = await exitRecovery.json());
          }
          return exitRecovery.status();
        },
        {timeout: 60_000},
      )
      .toBe(200);
    expect(exitRecoveryChangeId).toBeDefined();
    await waitForConfigurationChange(request, exitRecoveryChangeId!);
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('restore - Body wrong top-level type', async ({request}) => {
    const requestBody: string[] = [];
    let enterRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const enterRecovery = await request.patch(
            buildUrl('/mode', undefined, {mode: 'RECOVERING'}),
            {
              headers: jsonHeaders(),
            },
          );
          if (enterRecovery.status() === 200) {
            ({changeId: enterRecoveryChangeId} = await enterRecovery.json());
          }
          return enterRecovery.status();
        },
        {timeout: 60_000},
      )
      .toBe(200);
    expect(enterRecoveryChangeId).toBeDefined();
    await waitForConfigurationChange(request, enterRecoveryChangeId!);
    const res = await request.post(buildUrl('/restore', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    let exitRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const exitRecovery = await request.patch(
            buildUrl('/mode', undefined, {mode: 'PROCESSING'}),
            {
              headers: jsonHeaders(),
            },
          );
          if (exitRecovery.status() === 200) {
            ({changeId: exitRecoveryChangeId} = await exitRecovery.json());
          }
          return exitRecovery.status();
        },
        {timeout: 60_000},
      )
      .toBe(200);
    expect(exitRecoveryChangeId).toBeDefined();
    await waitForConfigurationChange(request, exitRecoveryChangeId!);
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('restore - Param from wrong type (#1)', async ({request}) => {
    const requestBody = {
      from: 123,
      to: 'x',
    };
    let enterRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const enterRecovery = await request.patch(
            buildUrl('/mode', undefined, {mode: 'RECOVERING'}),
            {
              headers: jsonHeaders(),
            },
          );
          if (enterRecovery.status() === 200) {
            ({changeId: enterRecoveryChangeId} = await enterRecovery.json());
          }
          return enterRecovery.status();
        },
        {timeout: 60_000},
      )
      .toBe(200);
    expect(enterRecoveryChangeId).toBeDefined();
    await waitForConfigurationChange(request, enterRecoveryChangeId!);
    const res = await request.post(buildUrl('/restore', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    let exitRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const exitRecovery = await request.patch(
            buildUrl('/mode', undefined, {mode: 'PROCESSING'}),
            {
              headers: jsonHeaders(),
            },
          );
          if (exitRecovery.status() === 200) {
            ({changeId: exitRecoveryChangeId} = await exitRecovery.json());
          }
          return exitRecovery.status();
        },
        {timeout: 60_000},
      )
      .toBe(200);
    expect(exitRecoveryChangeId).toBeDefined();
    await waitForConfigurationChange(request, exitRecoveryChangeId!);
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('restore - Param from wrong type (#2)', async ({request}) => {
    const requestBody = {
      from: true,
      to: 'x',
    };
    let enterRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const enterRecovery = await request.patch(
            buildUrl('/mode', undefined, {mode: 'RECOVERING'}),
            {
              headers: jsonHeaders(),
            },
          );
          if (enterRecovery.status() === 200) {
            ({changeId: enterRecoveryChangeId} = await enterRecovery.json());
          }
          return enterRecovery.status();
        },
        {timeout: 60_000},
      )
      .toBe(200);
    expect(enterRecoveryChangeId).toBeDefined();
    await waitForConfigurationChange(request, enterRecoveryChangeId!);
    const res = await request.post(buildUrl('/restore', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    let exitRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const exitRecovery = await request.patch(
            buildUrl('/mode', undefined, {mode: 'PROCESSING'}),
            {
              headers: jsonHeaders(),
            },
          );
          if (exitRecovery.status() === 200) {
            ({changeId: exitRecoveryChangeId} = await exitRecovery.json());
          }
          return exitRecovery.status();
        },
        {timeout: 60_000},
      )
      .toBe(200);
    expect(exitRecoveryChangeId).toBeDefined();
    await waitForConfigurationChange(request, exitRecoveryChangeId!);
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('restore - Param to wrong type (#1)', async ({request}) => {
    const requestBody = {
      from: 'x',
      to: 123,
    };
    let enterRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const enterRecovery = await request.patch(
            buildUrl('/mode', undefined, {mode: 'RECOVERING'}),
            {
              headers: jsonHeaders(),
            },
          );
          if (enterRecovery.status() === 200) {
            ({changeId: enterRecoveryChangeId} = await enterRecovery.json());
          }
          return enterRecovery.status();
        },
        {timeout: 60_000},
      )
      .toBe(200);
    expect(enterRecoveryChangeId).toBeDefined();
    await waitForConfigurationChange(request, enterRecoveryChangeId!);
    const res = await request.post(buildUrl('/restore', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    let exitRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const exitRecovery = await request.patch(
            buildUrl('/mode', undefined, {mode: 'PROCESSING'}),
            {
              headers: jsonHeaders(),
            },
          );
          if (exitRecovery.status() === 200) {
            ({changeId: exitRecoveryChangeId} = await exitRecovery.json());
          }
          return exitRecovery.status();
        },
        {timeout: 60_000},
      )
      .toBe(200);
    expect(exitRecoveryChangeId).toBeDefined();
    await waitForConfigurationChange(request, exitRecoveryChangeId!);
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('restore - Param to wrong type (#2)', async ({request}) => {
    const requestBody = {
      from: 'x',
      to: true,
    };
    let enterRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const enterRecovery = await request.patch(
            buildUrl('/mode', undefined, {mode: 'RECOVERING'}),
            {
              headers: jsonHeaders(),
            },
          );
          if (enterRecovery.status() === 200) {
            ({changeId: enterRecoveryChangeId} = await enterRecovery.json());
          }
          return enterRecovery.status();
        },
        {timeout: 60_000},
      )
      .toBe(200);
    expect(enterRecoveryChangeId).toBeDefined();
    await waitForConfigurationChange(request, enterRecoveryChangeId!);
    const res = await request.post(buildUrl('/restore', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    let exitRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const exitRecovery = await request.patch(
            buildUrl('/mode', undefined, {mode: 'PROCESSING'}),
            {
              headers: jsonHeaders(),
            },
          );
          if (exitRecovery.status() === 200) {
            ({changeId: exitRecoveryChangeId} = await exitRecovery.json());
          }
          return exitRecovery.status();
        },
        {timeout: 60_000},
      )
      .toBe(200);
    expect(exitRecoveryChangeId).toBeDefined();
    await waitForConfigurationChange(request, exitRecoveryChangeId!);
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  // Known failing (see known-failing-tests.json): restore request from/to format validation (added in 3621ed9d6d96) is incomplete
  test.skip('restore - format invalid from', async ({request}) => {
    const requestBody = {
      from: 'not-a-datetime',
      to: 'x',
    };
    let enterRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const enterRecovery = await request.patch(
            buildUrl('/mode', undefined, {mode: 'RECOVERING'}),
            {
              headers: jsonHeaders(),
            },
          );
          if (enterRecovery.status() === 200) {
            ({changeId: enterRecoveryChangeId} = await enterRecovery.json());
          }
          return enterRecovery.status();
        },
        {timeout: 60_000},
      )
      .toBe(200);
    expect(enterRecoveryChangeId).toBeDefined();
    await waitForConfigurationChange(request, enterRecoveryChangeId!);
    const res = await request.post(buildUrl('/restore', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    let exitRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const exitRecovery = await request.patch(
            buildUrl('/mode', undefined, {mode: 'PROCESSING'}),
            {
              headers: jsonHeaders(),
            },
          );
          if (exitRecovery.status() === 200) {
            ({changeId: exitRecoveryChangeId} = await exitRecovery.json());
          }
          return exitRecovery.status();
        },
        {timeout: 60_000},
      )
      .toBe(200);
    expect(exitRecoveryChangeId).toBeDefined();
    await waitForConfigurationChange(request, exitRecoveryChangeId!);
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  // Known failing (see known-failing-tests.json): restore request from/to format validation (added in 3621ed9d6d96) is incomplete
  test.skip('restore - format invalid to', async ({request}) => {
    const requestBody = {
      from: 'x',
      to: 'not-a-datetime',
    };
    let enterRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const enterRecovery = await request.patch(
            buildUrl('/mode', undefined, {mode: 'RECOVERING'}),
            {
              headers: jsonHeaders(),
            },
          );
          if (enterRecovery.status() === 200) {
            ({changeId: enterRecoveryChangeId} = await enterRecovery.json());
          }
          return enterRecovery.status();
        },
        {timeout: 60_000},
      )
      .toBe(200);
    expect(enterRecoveryChangeId).toBeDefined();
    await waitForConfigurationChange(request, enterRecoveryChangeId!);
    const res = await request.post(buildUrl('/restore', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    let exitRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const exitRecovery = await request.patch(
            buildUrl('/mode', undefined, {mode: 'PROCESSING'}),
            {
              headers: jsonHeaders(),
            },
          );
          if (exitRecovery.status() === 200) {
            ({changeId: exitRecoveryChangeId} = await exitRecovery.json());
          }
          return exitRecovery.status();
        },
        {timeout: 60_000},
      )
      .toBe(200);
    expect(exitRecoveryChangeId).toBeDefined();
    await waitForConfigurationChange(request, exitRecoveryChangeId!);
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  // Known failing (see known-failing-tests.json): restore request from/to format validation (added in 3621ed9d6d96) is incomplete
  test.skip('restore - Missing body', async ({request}) => {
    let enterRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const enterRecovery = await request.patch(
            buildUrl('/mode', undefined, {mode: 'RECOVERING'}),
            {
              headers: jsonHeaders(),
            },
          );
          if (enterRecovery.status() === 200) {
            ({changeId: enterRecoveryChangeId} = await enterRecovery.json());
          }
          return enterRecovery.status();
        },
        {timeout: 60_000},
      )
      .toBe(200);
    expect(enterRecoveryChangeId).toBeDefined();
    await waitForConfigurationChange(request, enterRecoveryChangeId!);
    const res = await request.post(buildUrl('/restore', undefined), {
      headers: jsonHeaders(),
    });
    let exitRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const exitRecovery = await request.patch(
            buildUrl('/mode', undefined, {mode: 'PROCESSING'}),
            {
              headers: jsonHeaders(),
            },
          );
          if (exitRecovery.status() === 200) {
            ({changeId: exitRecoveryChangeId} = await exitRecovery.json());
          }
          return exitRecovery.status();
        },
        {timeout: 60_000},
      )
      .toBe(200);
    expect(exitRecoveryChangeId).toBeDefined();
    await waitForConfigurationChange(request, exitRecoveryChangeId!);
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('restore - Param query.dryRun wrong type', async ({request}) => {
    let enterRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const enterRecovery = await request.patch(
            buildUrl('/mode', undefined, {mode: 'RECOVERING'}),
            {
              headers: jsonHeaders(),
            },
          );
          if (enterRecovery.status() === 200) {
            ({changeId: enterRecoveryChangeId} = await enterRecovery.json());
          }
          return enterRecovery.status();
        },
        {timeout: 60_000},
      )
      .toBe(200);
    expect(enterRecoveryChangeId).toBeDefined();
    await waitForConfigurationChange(request, enterRecoveryChangeId!);
    const res = await request.post(
      buildUrl('/restore', undefined, {dryRun: 'notBoolean'}),
      {
        headers: jsonHeaders(),
      },
    );
    let exitRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const exitRecovery = await request.patch(
            buildUrl('/mode', undefined, {mode: 'PROCESSING'}),
            {
              headers: jsonHeaders(),
            },
          );
          if (exitRecovery.status() === 200) {
            ({changeId: exitRecoveryChangeId} = await exitRecovery.json());
          }
          return exitRecovery.status();
        },
        {timeout: 60_000},
      )
      .toBe(200);
    expect(exitRecoveryChangeId).toBeDefined();
    await waitForConfigurationChange(request, exitRecoveryChangeId!);
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
});
