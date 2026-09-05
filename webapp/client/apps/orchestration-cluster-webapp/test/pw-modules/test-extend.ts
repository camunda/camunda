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
import {TasklistIndexPage} from '#/pages/TasklistIndex.page';
import {TasklistProcessesPage} from '#/pages/TasklistProcesses.page';
import {TaskDetailPage} from '#/pages/TaskDetail.page';
import {OperateIndexPage} from '#/pages/OperateIndex.page';
import {OperateBatchOperationsPage} from '#/pages/OperateBatchOperations.page';
import {OperateProcessesPage} from '#/pages/OperateProcesses.page';
import {AdminIndexPage} from '#/pages/AdminIndex.page';
import {NotFoundPage} from '#/pages/NotFound.page';
import {ForbiddenPage} from '#/pages/Forbidden.page';
import {ComponentAccessDeniedPage} from '#/pages/ComponentAccessDenied.page';
import {TasklistLoginPage} from '#/pages/TasklistLogin.page';
import {ShadcnTasklistIndexPage} from '#/pages/ShadcnTasklistIndex.page';
import {ShadcnTasklistProcessesPage} from '#/pages/ShadcnTasklistProcesses.page';
import {ShadcnTasklistLoginPage} from '#/pages/ShadcnTasklistLogin.page';
import {ShadcnTaskDetailPage} from '#/pages/ShadcnTaskDetail.page';

type Fixtures = {
	handlers: Array<AnyHandler>;
	network: NetworkFixture;
	makeAxeBuilder: () => AxeBuilder;
	loginPage: LoginPage;
	tasklistLoginPage: TasklistLoginPage;
	shadcnTasklistLoginPage: ShadcnTasklistLoginPage;
	shadcnTasklistProcessesPage: ShadcnTasklistProcessesPage;
	shadcnTaskDetailPage: ShadcnTaskDetailPage;
	tasklistIndexPage: TasklistIndexPage;
	tasklistProcessesPage: TasklistProcessesPage;
	taskDetailPage: TaskDetailPage;
	operateIndexPage: OperateIndexPage;
	operateBatchOperationsPage: OperateBatchOperationsPage;
	operateProcessesPage: OperateProcessesPage;
	adminIndexPage: AdminIndexPage;
	notFoundPage: NotFoundPage;
	forbiddenPage: ForbiddenPage;
	componentAccessDeniedPage: ComponentAccessDeniedPage;
	shadcnTasklistIndexPage: ShadcnTasklistIndexPage;
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
	shadcnTasklistLoginPage: async ({page}, use) => {
		await use(new ShadcnTasklistLoginPage(page));
	},
	shadcnTasklistProcessesPage: async ({page}, use) => {
		await use(new ShadcnTasklistProcessesPage(page));
	},
	shadcnTaskDetailPage: async ({page}, use) => {
		await use(new ShadcnTaskDetailPage(page));
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
	operateBatchOperationsPage: async ({page}, use) => {
		await use(new OperateBatchOperationsPage(page));
	},
	operateProcessesPage: async ({page}, use) => {
		await use(new OperateProcessesPage(page));
	},
	adminIndexPage: async ({page}, use) => {
		await use(new AdminIndexPage(page));
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
	shadcnTasklistIndexPage: async ({page}, use) => {
		await use(new ShadcnTasklistIndexPage(page));
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
