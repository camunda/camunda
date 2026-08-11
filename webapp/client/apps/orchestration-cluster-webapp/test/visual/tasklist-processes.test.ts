/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {
	createGetProcessDefinitionResponse,
	createProcessDefinition,
	createProcessStartFormResponse,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createQueryUserTasksResponse} from '#/shared-test-modules/api-mocks/user-tasks';
import {
	mockCurrentUserEndpoint,
	mockCreateProcessInstanceEndpoint,
	mockGetProcessDefinitionEndpoint,
	mockGetProcessStartFormEndpoint,
	mockLicenseEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryUserTasksEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {HttpResponse} from 'msw';

test.beforeEach(async ({network, page}) => {
	await page.addInitScript(() => {
		localStorage.setItem('tasklist.hasConsentedToStartProcess', JSON.stringify(true));
	});
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['tasklist']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
		}),
		mockQueryUserTasksEndpoint({
			successResponse: HttpResponse.json(createQueryUserTasksResponse()),
		}),
	);
});

test('should match the populated processes page snapshot', async ({network, tasklistProcessesPage, page}) => {
	network.use(
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({
					items: [
						createProcessDefinition({name: 'Invoice review', processDefinitionKey: '1', hasStartForm: true}),
						createProcessDefinition({name: 'Order approval', processDefinitionKey: '2'}),
						createProcessDefinition({
							name: 'Customer onboarding process with a very long descriptive process name that exceeds the card width',
							processDefinitionId: 'customer-onboarding-with-a-process-definition-id-that-exceeds-the-card-width',
							processDefinitionKey: '3',
							hasStartForm: true,
						}),
						createProcessDefinition({
							name: null,
							processDefinitionId: 'expense-reimbursement',
							processDefinitionKey: '4',
						}),
						createProcessDefinition({name: 'Contract renewal', processDefinitionKey: '5'}),
						createProcessDefinition({name: 'Purchase request', processDefinitionKey: '6', hasStartForm: true}),
					],
					page: {endCursor: 'next-page', hasMoreTotalItems: true},
				}),
			),
		}),
	);

	await tasklistProcessesPage.goto();
	await expect(tasklistProcessesPage.processHeading('Invoice review')).toBeVisible();
	await expect(tasklistProcessesPage.loadMoreButton).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the start-process form modal snapshot', async ({network, tasklistProcessesPage, page}) => {
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
	await expect(tasklistProcessesPage.startProcessDialog.getByRole('textbox', {name: 'Customer name'})).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the start-process form error snapshots', async ({network, tasklistProcessesPage, page}) => {
	const processDefinitionKey = '2251799813685279';
	network.use(
		mockGetProcessDefinitionEndpoint({
			successResponse: HttpResponse.json(createGetProcessDefinitionResponse({processDefinitionKey})),
		}),
		mockGetProcessStartFormEndpoint({successResponse: new HttpResponse(null, {status: 500})}),
	);

	await tasklistProcessesPage.gotoStartForm(processDefinitionKey);
	await expect(tasklistProcessesPage.startProcessFormError).toBeVisible();
	await expect(page).toHaveScreenshot('start-process-form-load-error.png');

	network.use(
		mockGetProcessStartFormEndpoint({
			successResponse: HttpResponse.json(createProcessStartFormResponse({schema: '{ invalid schema'})),
		}),
	);
	await page.reload();
	await expect(tasklistProcessesPage.startProcessFormError).toContainText('We were not able to render the form.');
	await expect(page).toHaveScreenshot('start-process-form-render-error.png');
});

test('should match the start-process form validation and submission-error notification snapshots', async ({
	network,
	tasklistProcessesPage,
	page,
}) => {
	const processDefinitionKey = '2251799813685279';
	network.use(
		mockGetProcessDefinitionEndpoint({
			successResponse: HttpResponse.json(createGetProcessDefinitionResponse({processDefinitionKey})),
		}),
		mockGetProcessStartFormEndpoint({
			successResponse: HttpResponse.json(createProcessStartFormResponse()),
		}),
		mockCreateProcessInstanceEndpoint({successResponse: new HttpResponse(null, {status: 500})}),
	);

	await tasklistProcessesPage.gotoStartForm(processDefinitionKey);
	await tasklistProcessesPage.startProcessFormButton.click();
	await expect(tasklistProcessesPage.startProcessDialog.getByRole('alert')).toBeVisible();
	await expect(page).toHaveScreenshot('start-process-form-validation.png');

	await tasklistProcessesPage.startProcessDialog.getByRole('textbox', {name: 'Customer name'}).fill('Jane Doe');
	await tasklistProcessesPage.startProcessFormButton.click();
	await expect(tasklistProcessesPage.header.notifications.getByNotificationTitle('Process start failed')).toBeVisible();
	await expect(page).toHaveScreenshot('start-process-form-submission-error.png');
});

test('should match the unpublished-processes empty-state snapshot', async ({tasklistProcessesPage, page}) => {
	await tasklistProcessesPage.goto();
	await expect(tasklistProcessesPage.unpublishedProcessesHeading).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the filtered empty-state snapshot', async ({tasklistProcessesPage, page}) => {
	await tasklistProcessesPage.goto('?search=missing');
	await expect(tasklistProcessesPage.noMatchingProcessesHeading).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the multi-tenant processes page snapshot', async ({network, tasklistProcessesPage, page}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(
				createCurrentUser({
					tenants: [
						{tenantId: '<default>', name: 'Default', description: null},
						{tenantId: 'tenant-a', name: 'Tenant A', description: null},
					],
				}),
			),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(
				createSystemConfiguration({
					components: {active: ['tasklist']},
					deployment: {isMultiTenancyEnabled: true, maxRequestSize: 0},
				}),
			),
		}),
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({
					items: [createProcessDefinition({name: 'Tenant process', processDefinitionKey: '1'})],
				}),
			),
		}),
	);

	await tasklistProcessesPage.goto();
	await expect(tasklistProcessesPage.tenantFilter).toBeVisible();
	await expect(tasklistProcessesPage.processHeading('Tenant process')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the forbidden processes page snapshot', async ({
	network,
	tasklistProcessesPage,
	forbiddenPage,
	page,
}) => {
	network.use(mockQueryProcessDefinitionsEndpoint({successResponse: new HttpResponse(null, {status: 403})}));

	await tasklistProcessesPage.goto();
	await expect(forbiddenPage.heading).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the generic processes error page snapshot', async ({network, tasklistProcessesPage, page}) => {
	network.use(mockQueryProcessDefinitionsEndpoint({successResponse: new HttpResponse(null, {status: 500})}));

	await tasklistProcessesPage.goto();
	await expect(tasklistProcessesPage.genericErrorHeading).toBeVisible();

	await expect(page).toHaveScreenshot();
});
