/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test as base} from '@playwright/test';
import type {APIRequestContext} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import {Common} from './pages/Common';
import {Login} from './pages/Login';
import {Processes} from './pages/Processes/Processes';
import fs from 'node:fs';
import {Dashboard} from './pages/Dashboard';
import {ProcessInstance} from './pages/ProcessInstance';
import {Decisions} from './pages/Decisions';
import {MigrationView} from './pages/Processes/MigrationView';
import {DecisionInstance} from './pages/DecisionInstance';
import {OperationsLog} from './pages/OperationsLog';
import {getCsrfToken, readSessionStorage, sessionFile} from './utils/session';

const authFile = 'playwright/.auth/user.json';

const test = base.extend<
  {
    processesPage: Processes;
    dashboardPage: Dashboard;
    processInstancePage: ProcessInstance;
    decisionsPage: Decisions;
    decisionInstancePage: DecisionInstance;
    commonPage: Common;
    migrationView: MigrationView;
    loginPage: Login;
    operationsLogPage: OperationsLog;
    request: APIRequestContext;
  },
  {workerStorageState: string}
>({
  request: async ({playwright}, use) => {
    const csrfToken = getCsrfToken();

    const context = await playwright.request.newContext({
      extraHTTPHeaders: csrfToken ? {'X-CSRF-TOKEN': csrfToken} : {},
    });

    await use(context);
    await context.dispose();
  },
  context: async ({browser, workerStorageState}, use) => {
    const context = await browser.newContext({
      storageState: workerStorageState,
    });

    if (fs.existsSync(sessionFile)) {
      const sessionStorage = readSessionStorage();

      await context.addInitScript((session) => {
        try {
          for (const [key, value] of Object.entries(session)) {
            window.sessionStorage.setItem(key, value);
          }
        } catch (_) {
          // sessionStorage is not accessible on all documents (e.g. about:blank)
        }
      }, sessionStorage);
    }

    await use(context);
    await context.close();
  },

  workerStorageState: [
    async (
      {browser},
      use,
      {
        project: {
          use: {baseURL},
        },
      },
    ) => {
      if (fs.existsSync(authFile)) {
        await use(authFile);
        return;
      }

      // Important: make sure we authenticate in a clean environment by unsetting storage state.
      const page = await browser.newPage({storageState: undefined});
      await page.goto(`${baseURL}/login`);
      await page.getByLabel(/^username$/i).fill('demo');
      await page.getByLabel(/^password$/i).fill('demo');
      await page.getByRole('button', {name: 'Login'}).click();

      await page.waitForURL(`${baseURL}`);
      await page.context().storageState({path: authFile});

      const sessionStorage = await page.evaluate((): string =>
        JSON.stringify(sessionStorage),
      );
      fs.writeFileSync(sessionFile, sessionStorage, 'utf-8');

      await page.close();
      await use(authFile);
    },
    {scope: 'worker'},
  ],

  processesPage: async ({page}, use) => {
    await use(new Processes(page));
  },
  dashboardPage: async ({page}, use) => {
    await use(new Dashboard(page));
  },
  processInstancePage: async ({page}, use) => {
    await use(new ProcessInstance(page));
  },
  decisionsPage: async ({page}, use) => {
    await use(new Decisions(page));
  },
  decisionInstancePage: async ({page}, use) => {
    await use(new DecisionInstance(page));
  },
  commonPage: async ({page}, use) => {
    await use(new Common(page));
  },
  migrationView: async ({page}, use) => {
    await use(new MigrationView(page));
  },
  loginPage: async ({page}, use) => {
    await use(new Login(page));
  },
  operationsLogPage: async ({page}, use) => {
    await use(new OperationsLog(page));
  },
});

const loginTest = base.extend<{
  makeAxeBuilder?: () => AxeBuilder;
  commonPage: Common;
  loginPage: Login;
}>({
  commonPage: async ({page}, use) => {
    await use(new Common(page));
  },
  loginPage: async ({page}, use) => {
    await use(new Login(page));
  },
});

export {test, loginTest};
