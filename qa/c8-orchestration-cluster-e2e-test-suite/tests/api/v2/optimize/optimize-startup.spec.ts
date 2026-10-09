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

    // Optimize delegates unauthenticated access to Spring Security's OAuth2
    // client, so `/` first redirects to the local login-initiation endpoint,
    // which then builds the authorization request and redirects to the OIDC
    // provider. Follow that one hop to reach the real authorization URL.
    await test.step('redirect enters the Spring Security OIDC login flow', () => {
      expect(redirectLocation).toContain('/oauth2/authorization/oidc');
    });

    await test.step('login initiation redirects to the Optimize OIDC provider', async () => {
      const response = await request.get(redirectLocation, {
        maxRedirects: 0,
        timeout: 5000,
      });
      expect(response.status()).toBe(302);

      const authorizationLocation = response.headers()['location'] ?? '';
      expect(authorizationLocation).toContain('/protocol/openid-connect/auth');
      expect(authorizationLocation).toContain('client_id=optimize');
      expect(authorizationLocation).toContain('/api/authentication/callback');
    });
  });
});
