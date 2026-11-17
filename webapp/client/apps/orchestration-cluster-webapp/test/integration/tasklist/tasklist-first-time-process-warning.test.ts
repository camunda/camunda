/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {test, expect} from '#/pw-modules/test-extend';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {
	createGetProcessDefinitionResponse,
	createProcessStartFormResponse,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createQueryUserTasksResponse} from '#/shared-test-modules/api-mocks/user-tasks';
import {
	mockCurrentUserEndpoint,
	mockGetProcessDefinitionEndpoint,
	mockGetProcessStartFormEndpoint,
	mockLicenseEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryUserTasksEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['tasklist']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
		}),
		mockQueryUserTasksEndpoint({successResponse: HttpResponse.json(createQueryUserTasksResponse())}),
	);
});

test('should persist consent and skip the warning on later visits', async ({tasklistProcessesPage, page}) => {
	await tasklistProcessesPage.goto();

	await expect(tasklistProcessesPage.firstTimeWarningDialog).toBeVisible();
	await tasklistProcessesPage.continueFromFirstTimeWarningButton.click();

	await expect(tasklistProcessesPage.firstTimeWarningDialog).not.toBeVisible();
	expect(await page.evaluate(() => localStorage.getItem('tasklist.hasConsentedToStartProcess'))).toBe('true');

	await page.reload();

	await expect(tasklistProcessesPage.heading).toBeVisible();
	await expect(tasklistProcessesPage.firstTimeWarningDialog).not.toBeVisible();
});

test('should gate a directly linked start form until consent is given', async ({network, tasklistProcessesPage}) => {
	const processDefinitionKey = '2251799813685279';
	network.use(
		mockGetProcessDefinitionEndpoint({
			successResponse: HttpResponse.json(
				createGetProcessDefinitionResponse({name: 'Invoice review', processDefinitionKey}),
			),
		}),
		mockGetProcessStartFormEndpoint({
			successResponse: HttpResponse.json(createProcessStartFormResponse()),
		}),
	);

	await tasklistProcessesPage.gotoStartForm(processDefinitionKey);

	await expect(tasklistProcessesPage.firstTimeWarningDialog).toBeVisible();
	await expect(tasklistProcessesPage.startProcessDialog).not.toBeVisible();

	await tasklistProcessesPage.continueFromFirstTimeWarningButton.click();

	await expect(tasklistProcessesPage.startProcessDialog).toBeVisible();
});
