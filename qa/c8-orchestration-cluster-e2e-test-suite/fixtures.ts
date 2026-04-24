/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test as base, expect} from '@playwright/test';
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
import {OperateOperationsDetailsPage} from '@pages/OperateOperationsDetailsPage';
import {OperateOperationsLogPage} from '@pages/OperateOperationsLogPage';
import {buildUrl, jsonHeaders} from 'utils/http';

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
  suppressHelperModals: void;
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
  taskDetailsPage: async ({page}, use) => {
    await use(new TaskDetailsPage(page));
  },
  tasklistHeader: async ({page}, use) => {
    await use(new TasklistHeader(page));
  },
  tasklistProcessesPage: async ({page}, use) => {
    await use(new TasklistProcessesPage(page));
  },
  resetData: async ({request}, use) => {
    await use(async () => {
      const res = await request.post(
        buildUrl('/process-instances/cancellation'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              state: 'ACTIVE',
            },
          },
        },
      );

      if (res.status() !== 200) {
        const body = await res.text().catch(() => '<no-body>');
        throw new Error(
          `resetData: failed to create cancellation batch (HTTP ${res.status()}): ${body}`,
        );
      }

      const json = await res.json();
      const batchKey = json.batchOperationKey;

      await expect(async () => {
        const statusRes = await request.get(
          buildUrl(`/batch-operations/${batchKey}`),
          {headers: jsonHeaders()},
        );
        const body = await statusRes.json();
        if (body.state === 'FAILED') {
          throw new Error(
            `resetData: cancellation batch operation ${batchKey} failed`,
          );
        }
        expect(body.state).toBe('COMPLETED');
      }).toPass({
        intervals: [2_000, 5_000, 10_000, 15_000],
        timeout: 60_000,
      });
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
  identityAuditLogPage: async ({page}, use) => {
    await use(new IdentityAuditLogPage(page));
  },
});

export {test};
