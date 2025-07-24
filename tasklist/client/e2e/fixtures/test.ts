/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test as base} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import {Header} from '@/pageElements/Header';
import {LoginPage} from '@/pageElements/LoginPage';
import {TaskVariableView} from '@/pageElements/TaskVariableView';
import {TasksPage} from '@/pageElements/TasksPage';
import {PublicFormsPage} from '@/pageElements/PublicFormsPage';
import {ProcessesPage} from '@/pageElements/ProcessesPage';
import {TaskFormView} from '@/pageElements/TaskFormView';
import {sleep} from '@/utils/sleep';

type PlaywrightFixtures = {
  makeAxeBuilder: () => AxeBuilder;
  resetData: () => Promise<void>;
  header: Header;
  loginPage: LoginPage;
  taskVariableView: TaskVariableView;
  tasksPage: TasksPage;
  publicFormsPage: PublicFormsPage;
  processesPage: ProcessesPage;
  taskFormView: TaskFormView;
};

const test = base.extend<PlaywrightFixtures>({
  makeAxeBuilder: async ({page}, use) => {
    const makeAxeBuilder = () =>
      // @ts-expect-error will be fixed in a next PR
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
      await fetch(`${baseURL}../v1/external/devUtil/recreateData`, {
        method: 'POST',
      });

      await sleep(1000);
    });
  },
  header: async ({page}, use) => {
    await use(new Header(page));
  },
  loginPage: async ({page}, use) => {
    await use(new LoginPage(page));
  },
  taskVariableView: async ({page}, use) => {
    await use(new TaskVariableView(page));
  },
  tasksPage: async ({page}, use) => {
    await use(new TasksPage(page));
  },
  publicFormsPage: async ({page}, use) => {
    await use(new PublicFormsPage(page));
  },
  processesPage: async ({page}, use) => {
    await use(new ProcessesPage(page));
  },
  taskFormView: async ({page}, use) => {
    await use(new TaskFormView(page));
  },
});

export {test};
