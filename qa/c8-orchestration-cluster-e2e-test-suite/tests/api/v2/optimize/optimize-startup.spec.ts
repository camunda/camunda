/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';

const OPTIMIZE_BASE_URL =
  process.env.OPTIMIZE_BASE_URL ?? 'http://localhost:8083';

test.describe('Optimize startup and accessibility', () => {
  test('should start and redirect unauthenticated users to the OIDC login', async ({
    request,
  }) => {
    let redirectLocation = '';

    await test.step('Optimize web endpoint becomes reachable', async () => {
      await expect
        .poll(
          async () => {
            try {
              const response = await request.get(`${OPTIMIZE_BASE_URL}/`, {
                maxRedirects: 0,
                timeout: 5000,
              });
              redirectLocation = response.headers()['location'] ?? '';
              return response.status();
            } catch {
              return 0;
            }
          },
          {timeout: 120_000, intervals: [2_000, 5_000]},
        )
        .toBe(302);
    });

    await test.step('redirect targets the Optimize OIDC login', () => {
      expect(redirectLocation).toContain('/protocol/openid-connect/auth');
      expect(redirectLocation).toContain('client_id=optimize');
      expect(redirectLocation).toContain('audience=optimize-api');
    });
  });
});
