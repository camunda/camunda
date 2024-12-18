/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test as base} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import {Common} from './pages/Common';
import {Login} from './pages/Login';
import {Processes} from './pages/Processes/Processes';
// import fs from 'fs';
import {Dashboard} from './pages/Dashboard';
import {ProcessInstance} from './pages/ProcessInstance';
import {Decisions} from './pages/Decisions';
import {MigrationView} from './pages/Processes/MigrationView';
import {DecisionInstance} from './pages/DecisionInstance';

type Fixture = {
  makeAxeBuilder: () => AxeBuilder;
  resetData: () => Promise<void>;
  commonPage: Common;
  loginPage: Login;
  dashboardPage: Dashboard;
  processInstancePage: ProcessInstance;
};

const loginTest = base.extend<Fixture>({
  commonPage: async ({page}, use) => {
    await use(new Common(page));
  },
  loginPage: async ({page}, use) => {
    await use(new Login(page));
  },
});

// const authFile = 'playwright/.auth/user.json';

const test = base.extend<
  {
    makeAxeBuilder: () => AxeBuilder;
    processesPage: Processes;
    dashboardPage: Dashboard;
    processInstancePage: ProcessInstance;
    decisionsPage: Decisions;
    decisionInstancePage: DecisionInstance;
    commonPage: Common;
    migrationView: MigrationView;
  },
  {workerStorageState: string}
>({
  // storageState: ({workerStorageState}, use) => use(workerStorageState),

  // workerStorageState: [
  //   async (
  //     {browser},
  //     use,
  //     {
  //       project: {
  //         use: {baseURL},
  //       },
  //     },
  //   ) => {
  //     if (fs.existsSync(authFile)) {
  //       await use(authFile);
  //       return;
  //     }

  //     // when using a baseURL containing domain + basePath, playwright expects a slash at the end of the baseURL when navigating and validating Urls
  //     // https://playwright.dev/docs/api/class-browser#browser-new-context-option-base-url
  //     // removing the slash here, as the used goto(url) below does not work with double slashes
  //     if (baseURL && baseURL.endsWith('/')) {
  //       baseURL = baseURL.slice(0, -1);
  //     }

  //     // Important: make sure we authenticate in a clean environment by unsetting storage state.
  //     const page = await browser.newPage({storageState: undefined});
  //     await page.goto(`${baseURL}/login`);
  //     await page.getByLabel(/^username$/i).fill('demo');
  //     await page.getByLabel(/^password$/i).fill('demo');
  //     await page.getByRole('button', {name: 'Login'}).click();

  //     await page.waitForURL(`${baseURL}`);

  //     await page.context().storageState({path: authFile});
  //     await page.close();
  //     await use(authFile);
  //   },
  //   {scope: 'worker'},
  // ],
  makeAxeBuilder: async ({page}, use) => {
    const makeAxeBuilder = () => new AxeBuilder({page});
    await use(makeAxeBuilder);
  },
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
});

export {loginTest, test};
