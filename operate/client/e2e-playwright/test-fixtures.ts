/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {test as base} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import {Common} from './pages/Common';
import {Login} from './pages/Login';
import {Processes} from './pages/Processes/Processes';
import fs from 'fs';
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

const authFile = 'playwright/.auth/user.json';

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
      await page.getByLabel(/^username$/i).fill('demo');
      await page.getByLabel(/^password$/i).fill('demo');
      await page.getByRole('button', {name: 'Login'}).click();

      await page.waitForURL(`${baseURL}`);

      await page.context().storageState({path: authFile});
      await page.close();
      await use(authFile);
    },
    {scope: 'worker'},
  ],
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
