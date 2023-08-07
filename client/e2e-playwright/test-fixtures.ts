/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {test as base} from '@playwright/test';
import {Common} from './pages/Common';
import {Login} from './pages/Login';
import {Processes} from './pages/Processes';
import fs from 'fs';
import {Dashboard} from './pages/Dashboard';

type Fixture = {
  resetData: () => Promise<void>;
  commonPage: Common;
  loginPage: Login;
  dashboardPage: Dashboard;
};

const loginTest = base.extend<Fixture>({
  commonPage: async ({page}, use) => {
    await use(new Common(page));
  },
  loginPage: async ({page}, use) => {
    await use(new Login(page));
  },
});

const authFile = 'playwright/.auth/user.json';

const test = base.extend<
  {processesPage: Processes; dashboardPage: Dashboard},
  {workerStorageState: string}
>({
  storageState: ({workerStorageState}, use) => use(workerStorageState),

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
      await page.getByLabel('Username').fill('demo');
      await page.getByLabel('Password').fill('demo');
      await page.getByRole('button', {name: 'Login'}).click();

      await page.waitForURL(`${baseURL}`);

      await page.context().storageState({path: authFile});
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
});

export {loginTest, test};
