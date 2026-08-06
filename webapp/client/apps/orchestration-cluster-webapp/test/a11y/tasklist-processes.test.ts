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
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {
	mockCurrentUserEndpoint,
	mockLicenseEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {HttpResponse} from 'msw';

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
	);
});

test('should have no accessibility violations in the populated processes page', async ({
	network,
	tasklistProcessesPage,
	makeAxeBuilder,
}) => {
	network.use(
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({
					items: [
						createProcessDefinition({
							name: 'Invoice review',
							processDefinitionKey: '1',
							hasStartForm: true,
						}),
						createProcessDefinition({name: null, processDefinitionId: 'order-approval', processDefinitionKey: '2'}),
					],
					page: {endCursor: 'next-page', hasMoreTotalItems: true},
				}),
			),
		}),
	);

	await tasklistProcessesPage.goto();
	await expect(tasklistProcessesPage.processHeading('Invoice review')).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the unpublished-processes empty state', async ({
	tasklistProcessesPage,
	makeAxeBuilder,
}) => {
	await tasklistProcessesPage.goto();
	await expect(tasklistProcessesPage.unpublishedProcessesHeading).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the filtered empty state with tenant filtering enabled', async ({
	network,
	tasklistProcessesPage,
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

	await tasklistProcessesPage.goto('?search=missing');
	await expect(tasklistProcessesPage.noMatchingProcessesHeading).toBeVisible();
	await expect(tasklistProcessesPage.tenantFilter).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations on the forbidden processes page', async ({
	network,
	tasklistProcessesPage,
	forbiddenPage,
	makeAxeBuilder,
}) => {
	network.use(mockQueryProcessDefinitionsEndpoint({successResponse: new HttpResponse(null, {status: 403})}));
	await tasklistProcessesPage.goto();
	await expect(forbiddenPage.heading).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations on the generic processes error page', async ({
	network,
	tasklistProcessesPage,
	makeAxeBuilder,
}) => {
	network.use(mockQueryProcessDefinitionsEndpoint({successResponse: new HttpResponse(null, {status: 500})}));
	await tasklistProcessesPage.goto();
	await expect(tasklistProcessesPage.genericErrorHeading).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});
