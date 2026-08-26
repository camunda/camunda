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

    await test.step('root redirect enters the Spring Security OIDC login flow', () => {
      expect(redirectLocation).toContain('/oauth2/authorization/oidc');
    });

    await test.step('login flow targets the Optimize OIDC authorization endpoint', async () => {
      // Optimize now delegates unauthenticated access to Spring Security's
      // OAuth2 client, so hitting `/` redirects to the local
      // `/oauth2/authorization/oidc` login-initiation endpoint first; that
      // endpoint builds the real authorization request and issues the second
      // 302 to the OIDC provider. Follow that one hop to assert the provider
      // target (no realm needs to exist — the URL is built locally). No
      // audience param is asserted: CAMUNDA_OPTIMIZE_IDENTITY_AUDIENCE feeds
      // oidc.audiences, which validates incoming JWTs, and is never added to
      // the authorization request.
      const authResponse = await request.get(redirectLocation, {
        maxRedirects: 0,
        timeout: 5000,
      });
      expect(authResponse.status()).toBe(302);

      const authLocation = authResponse.headers()['location'] ?? '';
      expect(authLocation).toContain('/protocol/openid-connect/auth');
      expect(authLocation).toContain('client_id=optimize');
      expect(authLocation).toContain('/api/authentication/callback');
    });
  });
});
