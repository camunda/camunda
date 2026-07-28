/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/*
 * GENERATED FILE - DO NOT EDIT MANUALLY
 * Generated At: 2026-07-28T14:59:54.260Z
 * Spec Commit: a85af569edb1e8502a52942193a277eed43e9508
 */
import {test, expect} from '@playwright/test';
import {jsonHeaders, buildUrl} from '../../../utils/http';

test.describe('Exporting Validation API Tests', () => {
  // Known failing (see known-failing-tests.json): query param type mismatch not rejected
  test.skip('pauseExporting - Param query.soft wrong type', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/exporting/pause', {soft: 'notBoolean'}),
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
