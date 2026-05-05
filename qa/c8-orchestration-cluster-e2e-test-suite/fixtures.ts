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
import {OperateDecisionInstancePage} from '@pages/OperateDecisionInstancePage';
import {OperateDecisionsPage} from '@pages/OperateDecisionsPage';
import {OperateFiltersPanelPage} from '@pages/OperateFiltersPanelPage';
import {OperateDashboardPage} from '@pages/OperateDashboardPage';
import {OperateDiagramPage} from '@pages/OperateDiagramPage';
import {OperateProcessMigrationModePage} from '@pages/OperateProcessMigrationModePage';
import {OperateProcessModificationModePage} from '@pages/OperateProcessModificationModePage';
import {OperateProcessInstanceViewModificationModePage} from '@pages/OperateProcessInstanceViewModificationMode';
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
import {IdentityAuditLogPage} from '@pages/IdentityAuditLogPage';
import {IdentityGlobalTaskListenersPage} from '@pages/IdentityGlobalTaskListenersPage';
import {IdentityMcpProcessesPage} from '@pages/IdentityMcpProcessesPage';
import {OperateOperationsDetailsPage} from '@pages/OperateOperationsDetailsPage';
import {OperateOperationsLogPage} from '@pages/OperateOperationsLogPage';
import {SwaggerPage} from '@pages/SwaggerPage';
import {OperateBatchOperationsPage} from '@pages/OperateBatchOperationsPage';
import {OptimizeLoginPage} from '@pages/OptimizeLoginPage';
import {OptimizeHomePage} from '@pages/OptimizeHomePage';
import {OptimizeCollectionPage} from '@pages/OptimizeCollectionPage';
import {OptimizeDashboardPage} from '@pages/OptimizeDashboardPage';
import {OptimizeProcessReportPage} from '@pages/OptimizeProcessReportPage';

type PlaywrightFixtures = {
  makeAxeBuilder: () => AxeBuilder;
  operateHomePage: OperateHomePage;
  loginPage: LoginPage;
  taskPanelPage: TaskPanelPage;
  operateProcessesPage: OperateProcessesPage;
  operateProcessInstancePage: OperateProcessInstancePage;
  operateDecisionInstancePage: OperateDecisionInstancePage;
  operateDecisionsPage: OperateDecisionsPage;
  operateFiltersPanelPage: OperateFiltersPanelPage;
  operateDashboardPage: OperateDashboardPage;
  operateDiagramPage: OperateDiagramPage;
  operateProcessMigrationModePage: OperateProcessMigrationModePage;
  operateProcessModificationModePage: OperateProcessModificationModePage;
  operateProcessInstanceViewModificationModePage: OperateProcessInstanceViewModificationModePage;
  operateOperationsDetailsPage: OperateOperationsDetailsPage;
  operateOperationsLogPage: OperateOperationsLogPage;
  operateBatchOperationsPage: OperateBatchOperationsPage;
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
  identityAuditLogPage: IdentityAuditLogPage;
  identityMcpProcessesPage: IdentityMcpProcessesPage;
  swaggerPage: SwaggerPage;
  identityGlobalTaskListenersPage: IdentityGlobalTaskListenersPage;
  suppressHelperModals: void;
  optimizeLoginPage: OptimizeLoginPage;
  optimizeHomePage: OptimizeHomePage;
  optimizeCollectionPage: OptimizeCollectionPage;
  optimizeDashboardPage: OptimizeDashboardPage;
  optimizeProcessReportPage: OptimizeProcessReportPage;
};

const test = base.extend<PlaywrightFixtures>({
  suppressHelperModals: [
    async ({page}, use) => {
      await page.addInitScript(() => {
        const current = JSON.parse(
          window.localStorage.getItem('sharedState') || '{}',
        );
        window.localStorage.setItem(
          'sharedState',
          JSON.stringify({...current, hideProcessInstanceHelperModal: true}),
        );
      });
      await use();
    },
    {auto: true},
  ],
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
  operateProcessMigrationModePage: async ({page}, use) => {
    await use(new OperateProcessMigrationModePage(page));
  },
  operateProcessModificationModePage: async ({page}, use) => {
    await use(new OperateProcessModificationModePage(page));
  },
  loginPage: async ({page}, use) => {
    await use(new LoginPage(page));
  },
  swaggerPage: async ({page}, use) => {
    await use(new SwaggerPage(page));
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
  operateDecisionInstancePage: async ({page}, use) => {
    await use(new OperateDecisionInstancePage(page));
  },
  operateDecisionsPage: async ({page}, use) => {
    await use(new OperateDecisionsPage(page));
  },
  operateFiltersPanelPage: async ({page}, use) => {
    await use(new OperateFiltersPanelPage(page));
  },
  operateProcessInstanceViewModificationModePage: async ({page}, use) => {
    await use(new OperateProcessInstanceViewModificationModePage(page));
  },
  operateOperationsDetailsPage: async ({page}, use) => {
    await use(new OperateOperationsDetailsPage(page));
  },
  operateOperationsLogPage: async ({page}, use) => {
    await use(new OperateOperationsLogPage(page));
  },
  operateBatchOperationsPage: async ({page}, use) => {
    await use(new OperateBatchOperationsPage(page));
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
  identityAuditLogPage: async ({page}, use) => {
    await use(new IdentityAuditLogPage(page));
  },
  identityGlobalTaskListenersPage: async ({page}, use) => {
    await use(new IdentityGlobalTaskListenersPage(page));
  },
  identityMcpProcessesPage: async ({page}, use) => {
    await use(new IdentityMcpProcessesPage(page));
  },
  optimizeLoginPage: async ({page}, use) => {
    await use(new OptimizeLoginPage(page));
  },
  optimizeHomePage: async ({page}, use) => {
    await use(new OptimizeHomePage(page));
  },
  optimizeCollectionPage: async ({page}, use) => {
    await use(new OptimizeCollectionPage(page));
  },
  optimizeDashboardPage: async ({page}, use) => {
    await use(new OptimizeDashboardPage(page));
  },
  optimizeProcessReportPage: async ({page}, use) => {
    await use(new OptimizeProcessReportPage(page));
  },
});

export {test};
