/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';
import {
	mockCurrentUserEndpoint,
	mockLicenseEndpoint,
	mockSystemConfigurationEndpoint,
	mockSearchGlobalTaskListenersEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {
	createGlobalTaskListener,
	createQueryGlobalTaskListenersResponse,
} from '#/shared-test-modules/api-mocks/global-task-listeners';

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['admin']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockSearchGlobalTaskListenersEndpoint({
			successResponse: HttpResponse.json(
				createQueryGlobalTaskListenersResponse({
					items: [
						createGlobalTaskListener({
							id: 'creation-notifier',
							type: 'notify-on-creation',
							eventTypes: ['creating', 'completing'],
						}),
					],
				}),
			),
		}),
	);
});

test('should match the global task listeners page snapshot', async ({adminGlobalTaskListenersPage, page}) => {
	await adminGlobalTaskListenersPage.goto();
	await expect(adminGlobalTaskListenersPage.row('creation-notifier')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the add global task listener modal snapshot', async ({adminGlobalTaskListenersPage, page}) => {
	await adminGlobalTaskListenersPage.goto();
	await adminGlobalTaskListenersPage.addButton.click();

	await expect(adminGlobalTaskListenersPage.addModal.dialog).toBeVisible();
	await expect(page).toHaveScreenshot();
});
