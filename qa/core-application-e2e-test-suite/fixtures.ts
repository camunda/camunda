/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test as base} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import {OperateLoginPage} from '@pages/OperateLoginPage';
import {OperateHomePage} from '@pages/OperateHomePage';
import {TaskPanelPage} from '@pages/TaskPanelPage';
import {TaskListLoginPage} from '@pages/TaskListLoginPage';
import {OperateProcessesPage} from '@pages/OperateProcessesPage';
import {OperateProcessInstancePage} from '@pages/OperateProcessInstancePage';
import {TaskDetailsPage} from '@pages/TaskDetailsPage';
import {TasklistHeader} from '@pages/TasklistHeader';
import {TasklistProcessesPage} from '@pages/TasklistProcessesPage';
import {PublicFormsPage} from '@pages/PublicFormsPage';
import {sleep} from 'utils/sleep';

type PlaywrightFixtures = {
  makeAxeBuilder: () => AxeBuilder;
  resetData: () => Promise<void>;
  operateLoginPage: OperateLoginPage;
  operateHomePage: OperateHomePage;
  taskListLoginPage: TaskListLoginPage;
  taskPanelPage: TaskPanelPage;
  operateProcessesPage: OperateProcessesPage;
  operateProcessInstancePage: OperateProcessInstancePage;
  taskDetailsPage: TaskDetailsPage;
  tasklistHeader: TasklistHeader;
  tasklistProcessesPage: TasklistProcessesPage;
  publicFormsPage: PublicFormsPage;
};

const test = base.extend<PlaywrightFixtures>({
  makeAxeBuilder: async ({page}, use) => {
    const makeAxeBuilder = () =>
      new AxeBuilder({page}).withTags([
        'best-practice',
        'wcag2a',
        'wcag2aa',
        'cat.semantics',
        'cat.forms',
      ]);

    await use(makeAxeBuilder);
  },
  operateLoginPage: async ({page}, use) => {
    await use(new OperateLoginPage(page));
  },
  operateHomePage: async ({page}, use) => {
    await use(new OperateHomePage(page));
  },
  taskListLoginPage: async ({page}, use) => {
    await use(new TaskListLoginPage(page));
  },
  taskPanelPage: async ({page}, use) => {
    await use(new TaskPanelPage(page));
  },
  operateProcessesPage: async ({page}, use) => {
    await use(new OperateProcessesPage(page));
  },
  operateProcessInstancePage: async ({page}, use) => {
    await use(new OperateProcessInstancePage(page));
  },
  taskDetailsPage: async ({page}, use) => {
    await use(new TaskDetailsPage(page));
  },
  tasklistHeader: async ({page}, use) => {
    await use(new TasklistHeader(page));
  },
  tasklistProcessesPage: async ({page}, use) => {
    await use(new TasklistProcessesPage(page));
  },
  resetData: async ({baseURL}, use) => {
    await use(async () => {
      await fetch(`${baseURL}../v1/external/devUtil/recreateData`, {
        method: 'POST',
      });

      await sleep(1000);
    });
  },
  publicFormsPage: async ({page}, use) => {
    await use(new PublicFormsPage(page));
  },
});

export {test};
