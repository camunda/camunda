/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/* eslint-disable no-empty-pattern */

import {
  createCamundaClient,
  type CamundaClient,
} from '@camunda8/orchestration-cluster-api';
import {test as base, expect, type Cookie} from '@playwright/test';

const BASE_URL = 'http://localhost:8080';
const LOGIN_URL = `${BASE_URL}/login`;

interface TestFixture {
  camunda: CamundaClient;
  /**
   * {@linkcode AsyncDisposableStack} that should be used to defer cleanup in tests.
   * Using the `await using` keyword fails to run to completing before Playwright terminates.
   */
  cleanup: AsyncDisposableStack;
}

interface WorkerFixture {
  loginUser: {username: string; password: string};
  loginState: {cookies: Cookie[]; csrfToken: string};
}

const test = base.extend<TestFixture, WorkerFixture>({
  baseURL: BASE_URL,
  loginUser: [{username: 'demo', password: 'demo'}, {scope: 'worker'}],
  camunda: createCamundaClient({
    config: {
      CAMUNDA_AUTH_STRATEGY: 'BASIC',
      CAMUNDA_BASIC_AUTH_USERNAME: 'demo',
      CAMUNDA_BASIC_AUTH_PASSWORD: 'demo',
    },
  }),
  cleanup: async ({}, use) => {
    const stack = new AsyncDisposableStack();
    await use(stack);
    await stack.disposeAsync();
  },
  page: async ({page, context, loginState}, use) => {
    await context.setStorageState({cookies: loginState.cookies, origins: []});
    await page.addInitScript((csrfToken) => {
      sessionStorage.setItem('X-CSRF-TOKEN', csrfToken);
    }, loginState.csrfToken);
    await use(page);
  },
  loginState: [
    async ({browser, loginUser}, use) => {
      const context = await browser.newContext();
      // The login endpoint always enforces CSRF, and a GET of the login page is the only place
      // where the server sends a token to an anonymous caller. The POST must send that token back.
      const loginPage = await context.request.get(LOGIN_URL, {
        headers: {Accept: 'text/html'},
      });
      const csrfTokenOfLoginPage = loginPage.headers()['x-csrf-token'] ?? '';

      const response = await context.request.post(LOGIN_URL, {
        form: loginUser,
        headers: csrfTokenOfLoginPage
          ? {'X-CSRF-TOKEN': csrfTokenOfLoginPage}
          : {},
      });
      // A login that fails here leaves every test of this worker unauthenticated, which shows up
      // later as a confusing assertion on a login screen.
      expect(response.status()).toBe(204);

      const csrfToken =
        response.headers()['x-csrf-token'] ?? csrfTokenOfLoginPage;
      const cookies = await context.cookies();

      await context.close();
      await use({cookies, csrfToken});
    },
    {scope: 'worker'},
  ],
});

export {expect, test};
