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

test('should have no accessibility violations in the populated processes page', async ({
	network,
	shadcnTasklistProcessesPage,
	makeAxeBuilder,
}) => {
	network.use(
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({
					items: [
						createProcessDefinition({name: 'Invoice review', processDefinitionKey: '1', hasStartForm: true}),
						createProcessDefinition({name: null, processDefinitionId: 'order-approval', processDefinitionKey: '2'}),
					],
					page: {endCursor: 'next-page', hasMoreTotalItems: true},
				}),
			),
		}),
	);

	await shadcnTasklistProcessesPage.goto();
	await expect(shadcnTasklistProcessesPage.processHeading('Invoice review')).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the unpublished-processes empty state', async ({
	shadcnTasklistProcessesPage,
	makeAxeBuilder,
}) => {
	await shadcnTasklistProcessesPage.goto();
	await expect(shadcnTasklistProcessesPage.unpublishedProcessesHeading).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the filtered empty state with tenant filtering enabled', async ({
	network,
	shadcnTasklistProcessesPage,
	makeAxeBuilder,
}) => {
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
	);

	await shadcnTasklistProcessesPage.goto('?search=missing');
	await expect(shadcnTasklistProcessesPage.noMatchingProcessesHeading).toBeVisible();
	await expect(shadcnTasklistProcessesPage.tenantFilter).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});

// Will be fixed with #60223
test.skip('should have no accessibility violations in the start-process form modal', async ({
	network,
	shadcnTasklistProcessesPage,
	makeAxeBuilder,
}) => {
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

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the start-process form error states', async ({
	network,
	shadcnTasklistProcessesPage,
	makeAxeBuilder,
}) => {
	const processDefinitionKey = '2251799813685279';
	network.use(
		mockGetProcessDefinitionEndpoint({
			successResponse: HttpResponse.json(createGetProcessDefinitionResponse({processDefinitionKey})),
		}),
		mockGetProcessStartFormEndpoint({successResponse: new HttpResponse(null, {status: 500})}),
	);

	await shadcnTasklistProcessesPage.gotoStartForm(processDefinitionKey);
	await expect(shadcnTasklistProcessesPage.startProcessFormError).toBeVisible();

	let accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);

	network.use(
		mockGetProcessStartFormEndpoint({
			successResponse: HttpResponse.json(createProcessStartFormResponse({schema: '{ invalid schema'})),
		}),
	);
	await shadcnTasklistProcessesPage.startProcessDialog.getByRole('button', {name: 'Try again'}).click();
	await expect(shadcnTasklistProcessesPage.startProcessFormError).toContainText('We were not able to render the form.');

	accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations on the forbidden processes page', async ({
	network,
	shadcnTasklistProcessesPage,
	forbiddenPage,
	makeAxeBuilder,
}) => {
	network.use(mockQueryProcessDefinitionsEndpoint({successResponse: new HttpResponse(null, {status: 403})}));
	await shadcnTasklistProcessesPage.goto();
	await expect(forbiddenPage.heading).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations on the generic processes error page', async ({
	network,
	shadcnTasklistProcessesPage,
	makeAxeBuilder,
}) => {
	network.use(mockQueryProcessDefinitionsEndpoint({successResponse: new HttpResponse(null, {status: 500})}));
	await shadcnTasklistProcessesPage.goto();
	await expect(shadcnTasklistProcessesPage.genericErrorHeading).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});
