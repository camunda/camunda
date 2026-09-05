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
	mockCreateProcessInstanceEndpoint,
	mockCurrentUserEndpoint,
	mockGetProcessDefinitionEndpoint,
	mockGetProcessStartFormEndpoint,
	mockLicenseEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryUserTasksEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createQueryUserTasksResponse} from '#/shared-test-modules/api-mocks/user-tasks';
import {
	createGetProcessDefinitionResponse,
	createProcessDefinition,
	createProcessStartFormResponse,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';

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

test('should match the populated processes page snapshot', async ({network, shadcnTasklistProcessesPage, page}) => {
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

	await shadcnTasklistProcessesPage.goto();
	await expect(shadcnTasklistProcessesPage.processHeading('Invoice review')).toBeVisible();
	await expect(shadcnTasklistProcessesPage.loadMoreButton).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the start-process form modal snapshot', async ({network, shadcnTasklistProcessesPage, page}) => {
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

	await shadcnTasklistProcessesPage.gotoStartForm(processDefinitionKey);
	await expect(
		shadcnTasklistProcessesPage.startProcessDialog.getByRole('textbox', {name: 'Customer name'}),
	).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the start-process form error snapshots', async ({network, shadcnTasklistProcessesPage, page}) => {
	const processDefinitionKey = '2251799813685279';
	network.use(
		mockGetProcessDefinitionEndpoint({
			successResponse: HttpResponse.json(createGetProcessDefinitionResponse({processDefinitionKey})),
		}),
		mockGetProcessStartFormEndpoint({successResponse: new HttpResponse(null, {status: 500})}),
	);

	await shadcnTasklistProcessesPage.gotoStartForm(processDefinitionKey);
	await expect(shadcnTasklistProcessesPage.startProcessFormError).toBeVisible();
	await expect(page).toHaveScreenshot('start-process-form-load-error.png');

	network.use(
		mockGetProcessStartFormEndpoint({
			successResponse: HttpResponse.json(createProcessStartFormResponse({schema: '{ invalid schema'})),
		}),
	);
	await page.reload();
	await expect(shadcnTasklistProcessesPage.startProcessFormError).toContainText('We were not able to render the form.');
	await expect(page).toHaveScreenshot('start-process-form-render-error.png');
});

test('should match the start-process form validation and submission-error notification snapshots', async ({
	network,
	shadcnTasklistProcessesPage,
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

	await shadcnTasklistProcessesPage.gotoStartForm(processDefinitionKey);
	await shadcnTasklistProcessesPage.startProcessFormButton.click();
	await expect(shadcnTasklistProcessesPage.startProcessDialog.getByRole('alert')).toBeVisible();
	await expect(page).toHaveScreenshot('start-process-form-validation.png');

	await shadcnTasklistProcessesPage.startProcessDialog.getByRole('textbox', {name: 'Customer name'}).fill('Jane Doe');
	await shadcnTasklistProcessesPage.startProcessFormButton.click();
	await expect(
		shadcnTasklistProcessesPage.header.notifications.getByNotificationTitle('Process start failed'),
	).toBeVisible();
	await expect(page).toHaveScreenshot('start-process-form-submission-error.png');
});

test('should match the unpublished-processes empty-state snapshot', async ({shadcnTasklistProcessesPage, page}) => {
	await shadcnTasklistProcessesPage.goto();
	await expect(shadcnTasklistProcessesPage.unpublishedProcessesHeading).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the filtered empty-state snapshot', async ({shadcnTasklistProcessesPage, page}) => {
	await shadcnTasklistProcessesPage.goto('?search=missing');
	await expect(shadcnTasklistProcessesPage.noMatchingProcessesHeading).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the multi-tenant processes page snapshot', async ({network, shadcnTasklistProcessesPage, page}) => {
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

	await shadcnTasklistProcessesPage.goto();
	await expect(shadcnTasklistProcessesPage.tenantFilter).toBeVisible();
	await expect(shadcnTasklistProcessesPage.processHeading('Tenant process')).toBeVisible();

	await expect(page).toHaveScreenshot();
});
