/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test as base} from '@playwright/test';
import {defineNetworkFixture, type NetworkFixture} from '@msw/playwright';
import type {AnyHandler} from 'msw';
import AxeBuilder from '@axe-core/playwright';
import {LoginPage} from '#/pages/Login.page';
import {TasklistIndexPage} from '#/pages/tasklist/TasklistIndex.page';
import {TasklistProcessesPage} from '#/pages/tasklist/TasklistProcesses.page';
import {TaskDetailPage} from '#/pages/tasklist/TaskDetail.page';
import {OperateIndexPage} from '#/pages/operate/OperateIndex.page';
import {OperatePreviewPage} from '#/pages/operate/OperatePreview.page';
import {OperateBatchOperationsPage} from '#/pages/operate/OperateBatchOperations.page';
import {OperateProcessesPage} from '#/pages/operate/OperateProcesses.page';
import {OperateProcessInstancePage} from '#/pages/operate/OperateProcessInstance.page';
import {OperateDecisionsPage} from '#/pages/operate/OperateDecisions.page';
import {OperateDecisionInstancePage} from '#/pages/operate/OperateDecisionInstance.page';
import {OperateOperationsLogPage} from '#/pages/operate/OperateOperationsLog.page';
import {AdminIndexPage} from '#/pages/admin/AdminIndex.page';
import {AdminLoginPage} from '#/pages/admin/AdminLogin.page';
import {AdminMcpProcessesPage} from '#/pages/admin/AdminMcpProcesses.page';
import {AdminOperationsLogPage} from '#/pages/admin/AdminOperationsLog.page';
import {AdminMappingRulesPage} from '#/pages/admin/AdminMappingRules.page';
import {AdminUsersPage} from '#/pages/AdminUsers.page';
import {AdminUserDetailPage} from '#/pages/AdminUserDetail.page';
import {AdminClusterVariablesPage} from '#/pages/admin/AdminClusterVariables.page';
import {AdminGlobalTaskListenersPage} from '#/pages/admin/AdminGlobalTaskListeners.page';
import {AdminAuthorizationsPage} from '#/pages/admin/AdminAuthorizations.page';
import {NotFoundPage} from '#/pages/NotFound.page';
import {ForbiddenPage} from '#/pages/Forbidden.page';
import {ComponentAccessDeniedPage} from '#/pages/ComponentAccessDenied.page';
import {TasklistLoginPage} from '#/pages/tasklist/TasklistLogin.page';

type Fixtures = {
	handlers: Array<AnyHandler>;
	network: NetworkFixture;
	makeAxeBuilder: () => AxeBuilder;
	loginPage: LoginPage;
	tasklistLoginPage: TasklistLoginPage;
	tasklistIndexPage: TasklistIndexPage;
	tasklistProcessesPage: TasklistProcessesPage;
	taskDetailPage: TaskDetailPage;
	operateIndexPage: OperateIndexPage;
	operatePreviewPage: OperatePreviewPage;
	operateBatchOperationsPage: OperateBatchOperationsPage;
	operateProcessesPage: OperateProcessesPage;
	operateProcessInstancePage: OperateProcessInstancePage;
	operateDecisionsPage: OperateDecisionsPage;
	operateDecisionInstancePage: OperateDecisionInstancePage;
	operateOperationsLogPage: OperateOperationsLogPage;
	adminIndexPage: AdminIndexPage;
	adminLoginPage: AdminLoginPage;
	adminMcpProcessesPage: AdminMcpProcessesPage;
	adminOperationsLogPage: AdminOperationsLogPage;
	adminMappingRulesPage: AdminMappingRulesPage;
	adminUsersPage: AdminUsersPage;
	adminUserDetailPage: AdminUserDetailPage;
	adminClusterVariablesPage: AdminClusterVariablesPage;
	adminGlobalTaskListenersPage: AdminGlobalTaskListenersPage;
	adminAuthorizationsPage: AdminAuthorizationsPage;
	notFoundPage: NotFoundPage;
	forbiddenPage: ForbiddenPage;
	componentAccessDeniedPage: ComponentAccessDeniedPage;
};

const test = base.extend<Fixtures>({
	makeAxeBuilder: async ({page}, use) => {
		const makeAxeBuilder = () => new AxeBuilder({page});
		await use(makeAxeBuilder);
	},
	loginPage: async ({page}, use) => {
		await use(new LoginPage(page));
	},
	tasklistLoginPage: async ({page}, use) => {
		await use(new TasklistLoginPage(page));
	},
	tasklistIndexPage: async ({page}, use) => {
		await use(new TasklistIndexPage(page));
	},
	tasklistProcessesPage: async ({page}, use) => {
		await use(new TasklistProcessesPage(page));
	},
	taskDetailPage: async ({page}, use) => {
		await use(new TaskDetailPage(page));
	},
	operateIndexPage: async ({page}, use) => {
		await use(new OperateIndexPage(page));
	},
	operatePreviewPage: async ({page}, use) => {
		await use(new OperatePreviewPage(page));
	},
	operateBatchOperationsPage: async ({page}, use) => {
		await use(new OperateBatchOperationsPage(page));
	},
	operateProcessesPage: async ({page}, use) => {
		await use(new OperateProcessesPage(page));
	},
	operateProcessInstancePage: async ({page}, use) => {
		await use(new OperateProcessInstancePage(page));
	},
	operateDecisionsPage: async ({page}, use) => {
		await use(new OperateDecisionsPage(page));
	},
	operateDecisionInstancePage: async ({page}, use) => {
		await use(new OperateDecisionInstancePage(page));
	},
	operateOperationsLogPage: async ({page}, use) => {
		await use(new OperateOperationsLogPage(page));
	},
	adminIndexPage: async ({page}, use) => {
		await use(new AdminIndexPage(page));
	},
	adminLoginPage: async ({page}, use) => {
		await use(new AdminLoginPage(page));
	},
	adminMcpProcessesPage: async ({page}, use) => {
		await use(new AdminMcpProcessesPage(page));
	},
	adminMappingRulesPage: async ({page}, use) => {
		await use(new AdminMappingRulesPage(page));
	},
	adminClusterVariablesPage: async ({page}, use) => {
		await use(new AdminClusterVariablesPage(page));
	},
	adminGlobalTaskListenersPage: async ({page}, use) => {
		await use(new AdminGlobalTaskListenersPage(page));
	},
	adminAuthorizationsPage: async ({page}, use) => {
		await use(new AdminAuthorizationsPage(page));
	},
	adminOperationsLogPage: async ({page}, use) => {
		await use(new AdminOperationsLogPage(page));
	},
	adminUsersPage: async ({page}, use) => {
		await use(new AdminUsersPage(page));
	},
	adminUserDetailPage: async ({page}, use) => {
		await use(new AdminUserDetailPage(page));
	},
	notFoundPage: async ({page}, use) => {
		await use(new NotFoundPage(page));
	},
	forbiddenPage: async ({page}, use) => {
		await use(new ForbiddenPage(page));
	},
	componentAccessDeniedPage: async ({page}, use) => {
		await use(new ComponentAccessDeniedPage(page));
	},
	handlers: [[], {option: true}],
	network: [
		async ({context, handlers, baseURL}, use) => {
			const appOrigin = baseURL === undefined ? undefined : new URL(baseURL).origin;

			const network = defineNetworkFixture({
				context,
				handlers,
				onUnhandledRequest(request, print) {
					const url = new URL(request.url);
					if (
						request.method === 'GET' &&
						url.origin === appOrigin &&
						request.headers.get('accept')?.includes('text/html')
					) {
						return;
					}
					print.error();
				},
			});

			await network.enable();
			await use(network);
			await network.disable();
		},
		{auto: true},
	],
});

export {expect, test};
