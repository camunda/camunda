/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {test as base} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import {TestSetupPage} from './pages/TestSetupPage';
import {MainPage} from './pages/MainPage';
import {LoginPage} from './pages/LoginPage';
import {TaskDetailsPage} from './pages/TaskDetailsPage';
import {TaskPanelPage} from './pages/TaskPanelPage';
import {PublicFormsPage} from './pages/PublicFormsPage';
import {ProcessesPage} from './pages/ProcessesPage';

type PlaywrightFixtures = {
  makeAxeBuilder: () => AxeBuilder;
  resetData: () => Promise<void>;
  testSetupPage: TestSetupPage;
  mainPage: MainPage;
  loginPage: LoginPage;
  taskDetailsPage: TaskDetailsPage;
  taskPanelPage: TaskPanelPage;
  publicFormsPage: PublicFormsPage;
  processesPage: ProcessesPage;
};

const test = base.extend<PlaywrightFixtures>({
  makeAxeBuilder: async ({page}, use) => {
    const makeAxeBuilder = () =>
      new AxeBuilder({page}).withTags([
        'best-practice',
        'wcag2a',
        'wcag2aa',
        'wcag21a',
        'wcag21aa',
        'cat.semantics',
        'cat.forms',
      ]);

    await use(makeAxeBuilder);
  },
  resetData: async ({baseURL}, use) => {
    await use(async () => {
      await fetch(`${baseURL}/v1/external/devUtil/recreateData`, {
        method: 'POST',
      });
    });
  },
  testSetupPage: async ({page}, use) => {
    await use(new TestSetupPage(page));
  },
  mainPage: async ({page}, use) => {
    await use(new MainPage(page));
  },
  loginPage: async ({page}, use) => {
    await use(new LoginPage(page));
  },
  taskDetailsPage: async ({page}, use) => {
    await use(new TaskDetailsPage(page));
  },
  taskPanelPage: async ({page}, use) => {
    await use(new TaskPanelPage(page));
  },
  publicFormsPage: async ({page}, use) => {
    await use(new PublicFormsPage(page));
  },
  processesPage: async ({page}, use) => {
    await use(new ProcessesPage(page));
  },
});

export {test};
