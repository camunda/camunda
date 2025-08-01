/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test as base} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import {OperateHomePage} from '@pages/OperateHomePage';
import {TaskPanelPage} from '@pages/TaskPanelPage';
import {LoginPage} from '@pages/LoginPage';
import {OperateProcessesPage} from '@pages/OperateProcessesPage';
import {OperateProcessInstancePage} from '@pages/OperateProcessInstancePage';
import {OperateFiltersPanelPage} from '@pages/OperateFiltersPanelPage';
import {OperateDashboardPage} from '@pages/OperateDashboardPage';
import {OperateDiagramPage} from '@pages/OperateDiagramPage';
import {TaskDetailsPage} from '@pages/TaskDetailsPage';
import {TasklistHeader} from '@pages/TasklistHeader';
import {TasklistProcessesPage} from '@pages/TasklistProcessesPage';
import {PublicFormsPage} from '@pages/PublicFormsPage';
import {IdentityHeader} from '@pages/IdentityHeader';
import {IdentityAuthorizationsPage} from '@pages/IdentityAuthorizationsPage';
import {IdentityGroupsPage} from '@pages/IdentityGroupsPage';
import {IdentityUsersPage} from '@pages/IdentityUsersPage';
import {IdentityMappingRulesPage} from '@pages/IdentityMappingRulesPage';
import {IdentityRolesPage} from '@pages/IdentityRolesPage';
import {IdentityTenantsPage} from '@pages/IdentityTenantsPage';
import {IdentityRolesDetailsPage} from '@pages/IdentityRolesDetailsPage';
import {AccessDeniedPage} from '@pages/AccessDeniedPage';

import {sleep} from 'utils/sleep';

type PlaywrightFixtures = {
  makeAxeBuilder: () => AxeBuilder;
  operateHomePage: OperateHomePage;
  loginPage: LoginPage;
  taskPanelPage: TaskPanelPage;
  operateProcessesPage: OperateProcessesPage;
  operateProcessInstancePage: OperateProcessInstancePage;
  operateFiltersPanelPage: OperateFiltersPanelPage;
  operateDashboardPage: OperateDashboardPage;
  operateDiagramPage: OperateDiagramPage;
  taskDetailsPage: TaskDetailsPage;
  tasklistHeader: TasklistHeader;
  tasklistProcessesPage: TasklistProcessesPage;
  resetData: () => Promise<void>;
  publicFormsPage: PublicFormsPage;
  identityHeader: IdentityHeader;
  identityMappingRulesPage: IdentityMappingRulesPage;
  identityUsersPage: IdentityUsersPage;
  identityGroupsPage: IdentityGroupsPage;
  identityAuthorizationsPage: IdentityAuthorizationsPage;
  identityRolesPage: IdentityRolesPage;
  identityTenantsPage: IdentityTenantsPage;
  identityRolesDetailsPage: IdentityRolesDetailsPage;
  accessDeniedPage: AccessDeniedPage;
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
  operateHomePage: async ({page}, use) => {
    await use(new OperateHomePage(page));
  },
  operateDashboardPage: async ({page}, use) => {
    await use(new OperateDashboardPage(page));
  },
  operateDiagramPage: async ({page}, use) => {
    await use(new OperateDiagramPage(page));
  },
  loginPage: async ({page}, use) => {
    await use(new LoginPage(page));
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
  operateFiltersPanelPage: async ({page}, use) => {
    await use(new OperateFiltersPanelPage(page));
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
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        Authorization: `${process.env.CAMUNDA_AUTH_STRATEGY} ${Buffer.from(
          `${process.env.CAMUNDA_BASIC_AUTH_USERNAME}:${process.env.CAMUNDA_BASIC_AUTH_PASSWORD}`,
        ).toString('base64')}`,
      };

      const response = await fetch(
        `${baseURL}/v1/external/devUtil/recreateData`,
        {
          method: 'POST',
          headers,
        },
      );

      if (!response.ok) {
        throw new Error(`Failed to reset data: ${response.statusText}`);
      }

      await sleep(1000);
    });
  },

  publicFormsPage: async ({page}, use) => {
    await use(new PublicFormsPage(page));
  },

  identityHeader: async ({page}, use) => {
    await use(new IdentityHeader(page));
  },
  identityMappingRulesPage: async ({page}, use) => {
    await use(new IdentityMappingRulesPage(page));
  },

  identityUsersPage: async ({page}, use) => {
    await use(new IdentityUsersPage(page));
  },

  identityGroupsPage: async ({page}, use) => {
    await use(new IdentityGroupsPage(page));
  },

  identityAuthorizationsPage: async ({page}, use) => {
    await use(new IdentityAuthorizationsPage(page));
  },

  identityRolesPage: async ({page}, use) => {
    await use(new IdentityRolesPage(page));
  },

  identityTenantsPage: async ({page}, use) => {
    await use(new IdentityTenantsPage(page));
  },

  identityRolesDetailsPage: async ({page}, use) => {
    await use(new IdentityRolesDetailsPage(page));
  },

  accessDeniedPage: async ({page}, use) => {
    await use(new AccessDeniedPage(page));
  },
});

export {test};
