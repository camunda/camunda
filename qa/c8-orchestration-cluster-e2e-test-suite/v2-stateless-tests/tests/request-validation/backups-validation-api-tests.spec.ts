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

test.describe('Backups Validation API Tests', () => {
  test('deleteRuntimeBackup - Param backupId wrong type', async ({request}) => {
    const res = await request.delete(
      buildUrl('/backups/runtime/{backupId}', {backupId: 'not-a-number'}),
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
  test('getRuntimeBackup - Param backupId wrong type', async ({request}) => {
    const res = await request.get(
      buildUrl('/backups/runtime/{backupId}', {backupId: 'not-a-number'}),
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
  test('listRuntimeBackups - Query param prefix pattern violation', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/backups/runtime', undefined, {prefix: '\n'}),
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
  test('takeRuntimeBackup - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      __extraField: 'unexpected',
    };
    const res = await request.post(buildUrl('/backups/runtime', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('takeRuntimeBackup - Body wrong top-level type', async ({request}) => {
    const requestBody: string[] = [];
    const res = await request.post(buildUrl('/backups/runtime', undefined), {
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
