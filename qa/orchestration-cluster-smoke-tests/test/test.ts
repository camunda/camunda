/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  createCamundaClient,
  type CamundaClient,
} from '@camunda8/orchestration-cluster-api';
import {test as base, expect, type Cookie} from '@playwright/test';

interface TestFixture {
  camunda: CamundaClient;
}

interface WorkerFixture {
  loginUser: {username: string; password: string};
  loginState: {cookies: Cookie[]; csrfToken: string};
}

const test = base.extend<TestFixture, WorkerFixture>({
  baseURL: 'http://localhost:8080',
  loginUser: [{username: 'demo', password: 'demo'}, {scope: 'worker'}],
  camunda: createCamundaClient({
    config: {
      CAMUNDA_AUTH_STRATEGY: 'BASIC',
      CAMUNDA_BASIC_AUTH_USERNAME: 'demo',
      CAMUNDA_BASIC_AUTH_PASSWORD: 'demo',
    },
  }),
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
      const response = await context.request.post(
        'http://localhost:8080/login',
        {form: loginUser},
      );
      const csrfToken = response.headers()['x-csrf-token'] ?? '';
      const cookies = await context.cookies();

      await context.close();
      await use({cookies, csrfToken});
    },
    {scope: 'worker'},
  ],
});

export {expect, test};
