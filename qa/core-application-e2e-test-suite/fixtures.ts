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

type PlaywrightFixtures = {
  makeAxeBuilder: () => AxeBuilder;
  operateLoginPage: OperateLoginPage;
  operateHomePage: OperateHomePage;
  taskListLoginPage: TaskListLoginPage;
  taskPanelPage: TaskPanelPage;
  operateProcessesPage: OperateProcessesPage;
  operateProcessInstancePage: OperateProcessInstancePage;
  taskDetailsPage: TaskDetailsPage;
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
});

export {test};
