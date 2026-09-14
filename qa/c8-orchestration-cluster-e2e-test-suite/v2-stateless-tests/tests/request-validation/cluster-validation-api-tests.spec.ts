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
  clusterAdminJsonHeaders,
  waitForConfigurationChange,
  buildUrl,
} from '../../../utils/http';

test.describe('Cluster Validation API Tests', () => {
  test.describe.configure({mode: 'serial'});
  test('changeClusterModeAsClusterAdmin - Query param mode enum violation', async ({
    request,
  }) => {
    const res = await request.patch(
      buildUrl('/cluster/v2/mode', undefined, {mode: 'PROCESSING_X'}),
      {
        headers: clusterAdminJsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('changeClusterModeAsClusterAdmin__paramEnum__query__mode', async ({
    request,
  }) => {
    const res = await request.patch(
      buildUrl('/cluster/v2/mode', undefined, {mode: 'PROCESSING_X'}),
      {
        headers: clusterAdminJsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('changeClusterModeAsClusterAdmin - Missing param query.mode', async ({
    request,
  }) => {
    const res = await request.patch(buildUrl('/cluster/v2/mode', undefined), {
      headers: clusterAdminJsonHeaders(),
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('changeClusterModeAsClusterAdmin - Param query.dryRun wrong type', async ({
    request,
  }) => {
    const res = await request.patch(
      buildUrl('/cluster/v2/mode', undefined, {
        mode: 'PROCESSING',
        dryRun: 'notBoolean',
      }),
      {
        headers: clusterAdminJsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('changeClusterModeAsClusterAdmin - Param query.mode wrong type', async ({
    request,
  }) => {
    const res = await request.patch(
      buildUrl('/cluster/v2/mode', undefined, {mode: '__INVALID_STRING__'}),
      {
        headers: clusterAdminJsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteHistoryBackupAsClusterAdmin - Param backupId wrong type', async ({
    request,
  }) => {
    const res = await request.delete(
      buildUrl('/cluster/v2/backups/history/{backupId}', {
        backupId: 'not-a-number',
      }),
      {
        headers: clusterAdminJsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteRuntimeBackupAsClusterAdmin - Param backupId wrong type', async ({
    request,
  }) => {
    const res = await request.delete(
      buildUrl('/cluster/v2/backups/runtime/{backupId}', {
        backupId: 'not-a-number',
      }),
      {
        headers: clusterAdminJsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getHistoryBackupAsClusterAdmin - Param backupId wrong type', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/cluster/v2/backups/history/{backupId}', {
        backupId: 'not-a-number',
      }),
      {
        headers: clusterAdminJsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getRuntimeBackupAsClusterAdmin - Param backupId wrong type', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/cluster/v2/backups/runtime/{backupId}', {
        backupId: 'not-a-number',
      }),
      {
        headers: clusterAdminJsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('listHistoryBackupsAsClusterAdmin - Query param prefix pattern violation', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/cluster/v2/backups/history', undefined, {prefix: '\n'}),
      {
        headers: clusterAdminJsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('listHistoryBackupsAsClusterAdmin - Param query.verbose wrong type', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/cluster/v2/backups/history', undefined, {
        verbose: 'notBoolean',
      }),
      {
        headers: clusterAdminJsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('listRuntimeBackupsAsClusterAdmin - Query param prefix pattern violation', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/cluster/v2/backups/runtime', undefined, {prefix: '\n'}),
      {
        headers: clusterAdminJsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('pauseClusterExporting - Param query.soft wrong type', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/cluster/v2/exporting/pause', undefined, {soft: 'notBoolean'}),
      {
        headers: clusterAdminJsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('restoreAsClusterAdmin - Additional prop __extraField', async ({
    request,
  }) => {
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
            buildUrl('/cluster/v2/mode', undefined, {mode: 'RECOVERING'}),
            {
              headers: clusterAdminJsonHeaders(),
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
    const res = await request.post(buildUrl('/cluster/v2/restore', undefined), {
      headers: clusterAdminJsonHeaders(),
      data: requestBody,
    });
    let exitRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const exitRecovery = await request.patch(
            buildUrl('/cluster/v2/mode', undefined, {mode: 'PROCESSING'}),
            {
              headers: clusterAdminJsonHeaders(),
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
  test('restoreAsClusterAdmin - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    let enterRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const enterRecovery = await request.patch(
            buildUrl('/cluster/v2/mode', undefined, {mode: 'RECOVERING'}),
            {
              headers: clusterAdminJsonHeaders(),
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
    const res = await request.post(buildUrl('/cluster/v2/restore', undefined), {
      headers: clusterAdminJsonHeaders(),
      data: requestBody,
    });
    let exitRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const exitRecovery = await request.patch(
            buildUrl('/cluster/v2/mode', undefined, {mode: 'PROCESSING'}),
            {
              headers: clusterAdminJsonHeaders(),
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
  test('restoreAsClusterAdmin - Param from wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      from: 123,
      to: 'x',
    };
    let enterRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const enterRecovery = await request.patch(
            buildUrl('/cluster/v2/mode', undefined, {mode: 'RECOVERING'}),
            {
              headers: clusterAdminJsonHeaders(),
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
    const res = await request.post(buildUrl('/cluster/v2/restore', undefined), {
      headers: clusterAdminJsonHeaders(),
      data: requestBody,
    });
    let exitRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const exitRecovery = await request.patch(
            buildUrl('/cluster/v2/mode', undefined, {mode: 'PROCESSING'}),
            {
              headers: clusterAdminJsonHeaders(),
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
  test('restoreAsClusterAdmin - Param from wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      from: true,
      to: 'x',
    };
    let enterRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const enterRecovery = await request.patch(
            buildUrl('/cluster/v2/mode', undefined, {mode: 'RECOVERING'}),
            {
              headers: clusterAdminJsonHeaders(),
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
    const res = await request.post(buildUrl('/cluster/v2/restore', undefined), {
      headers: clusterAdminJsonHeaders(),
      data: requestBody,
    });
    let exitRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const exitRecovery = await request.patch(
            buildUrl('/cluster/v2/mode', undefined, {mode: 'PROCESSING'}),
            {
              headers: clusterAdminJsonHeaders(),
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
  test('restoreAsClusterAdmin - Param to wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      from: 'x',
      to: 123,
    };
    let enterRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const enterRecovery = await request.patch(
            buildUrl('/cluster/v2/mode', undefined, {mode: 'RECOVERING'}),
            {
              headers: clusterAdminJsonHeaders(),
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
    const res = await request.post(buildUrl('/cluster/v2/restore', undefined), {
      headers: clusterAdminJsonHeaders(),
      data: requestBody,
    });
    let exitRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const exitRecovery = await request.patch(
            buildUrl('/cluster/v2/mode', undefined, {mode: 'PROCESSING'}),
            {
              headers: clusterAdminJsonHeaders(),
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
  test('restoreAsClusterAdmin - Param to wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      from: 'x',
      to: true,
    };
    let enterRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const enterRecovery = await request.patch(
            buildUrl('/cluster/v2/mode', undefined, {mode: 'RECOVERING'}),
            {
              headers: clusterAdminJsonHeaders(),
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
    const res = await request.post(buildUrl('/cluster/v2/restore', undefined), {
      headers: clusterAdminJsonHeaders(),
      data: requestBody,
    });
    let exitRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const exitRecovery = await request.patch(
            buildUrl('/cluster/v2/mode', undefined, {mode: 'PROCESSING'}),
            {
              headers: clusterAdminJsonHeaders(),
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
  test('restoreAsClusterAdmin - format invalid from', async ({request}) => {
    const requestBody = {
      from: 'not-a-datetime',
      to: 'x',
    };
    let enterRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const enterRecovery = await request.patch(
            buildUrl('/cluster/v2/mode', undefined, {mode: 'RECOVERING'}),
            {
              headers: clusterAdminJsonHeaders(),
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
    const res = await request.post(buildUrl('/cluster/v2/restore', undefined), {
      headers: clusterAdminJsonHeaders(),
      data: requestBody,
    });
    let exitRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const exitRecovery = await request.patch(
            buildUrl('/cluster/v2/mode', undefined, {mode: 'PROCESSING'}),
            {
              headers: clusterAdminJsonHeaders(),
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
  test('restoreAsClusterAdmin - format invalid to', async ({request}) => {
    const requestBody = {
      from: 'x',
      to: 'not-a-datetime',
    };
    let enterRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const enterRecovery = await request.patch(
            buildUrl('/cluster/v2/mode', undefined, {mode: 'RECOVERING'}),
            {
              headers: clusterAdminJsonHeaders(),
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
    const res = await request.post(buildUrl('/cluster/v2/restore', undefined), {
      headers: clusterAdminJsonHeaders(),
      data: requestBody,
    });
    let exitRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const exitRecovery = await request.patch(
            buildUrl('/cluster/v2/mode', undefined, {mode: 'PROCESSING'}),
            {
              headers: clusterAdminJsonHeaders(),
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
  test('restoreAsClusterAdmin - Missing body', async ({request}) => {
    let enterRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const enterRecovery = await request.patch(
            buildUrl('/cluster/v2/mode', undefined, {mode: 'RECOVERING'}),
            {
              headers: clusterAdminJsonHeaders(),
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
    const res = await request.post(buildUrl('/cluster/v2/restore', undefined), {
      headers: clusterAdminJsonHeaders(),
    });
    let exitRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const exitRecovery = await request.patch(
            buildUrl('/cluster/v2/mode', undefined, {mode: 'PROCESSING'}),
            {
              headers: clusterAdminJsonHeaders(),
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
  test('restoreAsClusterAdmin - Param query.dryRun wrong type', async ({
    request,
  }) => {
    let enterRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const enterRecovery = await request.patch(
            buildUrl('/cluster/v2/mode', undefined, {mode: 'RECOVERING'}),
            {
              headers: clusterAdminJsonHeaders(),
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
      buildUrl('/cluster/v2/restore', undefined, {dryRun: 'notBoolean'}),
      {
        headers: clusterAdminJsonHeaders(),
      },
    );
    let exitRecoveryChangeId: string | undefined;
    await expect
      .poll(
        async () => {
          const exitRecovery = await request.patch(
            buildUrl('/cluster/v2/mode', undefined, {mode: 'PROCESSING'}),
            {
              headers: clusterAdminJsonHeaders(),
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
  test('takeHistoryBackupAsClusterAdmin - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      backupId: null,
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/cluster/v2/backups/history', undefined),
      {
        headers: clusterAdminJsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('takeHistoryBackupAsClusterAdmin - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/cluster/v2/backups/history', undefined),
      {
        headers: clusterAdminJsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('takeHistoryBackupAsClusterAdmin - Missing backupId', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.post(
      buildUrl('/cluster/v2/backups/history', undefined),
      {
        headers: clusterAdminJsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('takeHistoryBackupAsClusterAdmin - Missing body', async ({request}) => {
    const res = await request.post(
      buildUrl('/cluster/v2/backups/history', undefined),
      {
        headers: clusterAdminJsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('takeRuntimeBackupAsClusterAdmin - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/cluster/v2/backups/runtime', undefined),
      {
        headers: clusterAdminJsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('takeRuntimeBackupAsClusterAdmin - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/cluster/v2/backups/runtime', undefined),
      {
        headers: clusterAdminJsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('triggerClusterRebalance - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      replicationLagThreshold: 1,
      maxTransferAttempts: 1,
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/cluster/v2/rebalance', undefined),
      {
        headers: clusterAdminJsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('triggerClusterRebalance - Body wrong top-level type', async ({
    request,
  }) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/cluster/v2/rebalance', undefined),
      {
        headers: clusterAdminJsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('triggerClusterRebalance - Param maxTransferAttempts wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      replicationLagThreshold: 1,
      maxTransferAttempts: 'not-a-number',
    };
    const res = await request.post(
      buildUrl('/cluster/v2/rebalance', undefined),
      {
        headers: clusterAdminJsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('triggerClusterRebalance - Param maxTransferAttempts wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      replicationLagThreshold: 1,
      maxTransferAttempts: true,
    };
    const res = await request.post(
      buildUrl('/cluster/v2/rebalance', undefined),
      {
        headers: clusterAdminJsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('triggerClusterRebalance - Param replicationLagThreshold wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      replicationLagThreshold: 'not-a-number',
      maxTransferAttempts: 1,
    };
    const res = await request.post(
      buildUrl('/cluster/v2/rebalance', undefined),
      {
        headers: clusterAdminJsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('triggerClusterRebalance - Param replicationLagThreshold wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      replicationLagThreshold: true,
      maxTransferAttempts: 1,
    };
    const res = await request.post(
      buildUrl('/cluster/v2/rebalance', undefined),
      {
        headers: clusterAdminJsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('triggerClusterRebalance - Constraint violation maxTransferAttempts (#1)', async ({
    request,
  }) => {
    const requestBody = {
      replicationLagThreshold: 1,
      maxTransferAttempts: 0.99999,
    };
    const res = await request.post(
      buildUrl('/cluster/v2/rebalance', undefined),
      {
        headers: clusterAdminJsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('triggerClusterRebalance - Constraint violation maxTransferAttempts (#2)', async ({
    request,
  }) => {
    const requestBody = {
      replicationLagThreshold: 1,
      maxTransferAttempts: 0,
    };
    const res = await request.post(
      buildUrl('/cluster/v2/rebalance', undefined),
      {
        headers: clusterAdminJsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('triggerClusterRebalance - Constraint violation maxTransferAttempts (#3)', async ({
    request,
  }) => {
    const requestBody = {
      replicationLagThreshold: 1,
      maxTransferAttempts: -99,
    };
    const res = await request.post(
      buildUrl('/cluster/v2/rebalance', undefined),
      {
        headers: clusterAdminJsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('triggerClusterRebalance - Constraint violation replicationLagThreshold (#1)', async ({
    request,
  }) => {
    const requestBody = {
      replicationLagThreshold: -0.00001,
      maxTransferAttempts: 1,
    };
    const res = await request.post(
      buildUrl('/cluster/v2/rebalance', undefined),
      {
        headers: clusterAdminJsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('triggerClusterRebalance - Constraint violation replicationLagThreshold (#2)', async ({
    request,
  }) => {
    const requestBody = {
      replicationLagThreshold: -1,
      maxTransferAttempts: 1,
    };
    const res = await request.post(
      buildUrl('/cluster/v2/rebalance', undefined),
      {
        headers: clusterAdminJsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('triggerClusterRebalance - Constraint violation replicationLagThreshold (#3)', async ({
    request,
  }) => {
    const requestBody = {
      replicationLagThreshold: -100,
      maxTransferAttempts: 1,
    };
    const res = await request.post(
      buildUrl('/cluster/v2/rebalance', undefined),
      {
        headers: clusterAdminJsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('triggerClusterRebalance - Param query.dryRun wrong type', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/cluster/v2/rebalance', undefined, {dryRun: 'notBoolean'}),
      {
        headers: clusterAdminJsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
});
