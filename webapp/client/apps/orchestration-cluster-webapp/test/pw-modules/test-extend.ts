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
import {OperateIndexPage} from '#/pages/OperateIndex.page';
import {AdminIndexPage} from '#/pages/AdminIndex.page';
import {NotFoundPage} from '#/pages/NotFound.page';

type Fixtures = {
	handlers: Array<AnyHandler>;
	network: NetworkFixture;
	makeAxeBuilder: () => AxeBuilder;
	loginPage: LoginPage;
	tasklistIndexPage: TasklistIndexPage;
	tasklistProcessesPage: TasklistProcessesPage;
	operateIndexPage: OperateIndexPage;
	adminIndexPage: AdminIndexPage;
	notFoundPage: NotFoundPage;
};

const test = base.extend<Fixtures>({
	makeAxeBuilder: async ({page}, use) => {
		const makeAxeBuilder = () => new AxeBuilder({page});
		await use(makeAxeBuilder);
	},
	loginPage: async ({page}, use) => {
		await use(new LoginPage(page));
	},
	tasklistIndexPage: async ({page}, use) => {
		await use(new TasklistIndexPage(page));
	},
	tasklistProcessesPage: async ({page}, use) => {
		await use(new TasklistProcessesPage(page));
	},
	operateIndexPage: async ({page}, use) => {
		await use(new OperateIndexPage(page));
	},
	adminIndexPage: async ({page}, use) => {
		await use(new AdminIndexPage(page));
	},
	notFoundPage: async ({page}, use) => {
		await use(new NotFoundPage(page));
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
