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
	mockCreateProcessInstanceEndpoint,
	mockLicenseEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryUserTasksEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {createQueryUserTasksResponse} from '#/shared-test-modules/api-mocks/user-tasks';
import {createProcessInstanceResponse} from '#/shared-test-modules/api-mocks/process-instances';
import {z} from 'zod';

function createProcessDefinitionsRequestSchema(filter: Record<string, z.ZodType> = {}) {
	return z.strictObject({
		filter: z.strictObject({isLatestVersion: z.literal(true), ...filter}),
		page: z.strictObject({limit: z.literal(12)}),
	});
}

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser()),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['tasklist']}})),
		}),
		mockLicenseEndpoint({
			successResponse: HttpResponse.json(createLicense()),
		}),
		mockQueryProcessDefinitionsEndpoint({
			schema: createProcessDefinitionsRequestSchema(),
			successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
	);
});

test.describe('Tasklist processes page', () => {
	test('should start a process without a form using the selected tenant', async ({network, tasklistProcessesPage}) => {
		const processDefinitionKey = '2251799813685279';
		const tenants = [
			{tenantId: '<default>', name: 'Default', description: null},
			{tenantId: 'tenant-a', name: 'Tenant A', description: null},
		];
		network.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser({tenants}))}),
			mockQueryProcessDefinitionsEndpoint({
				schema: createProcessDefinitionsRequestSchema({tenantId: z.literal('tenant-a')}),
				successResponse: HttpResponse.json(
					createQueryProcessDefinitionsResponse({
						items: [
							createProcessDefinition({
								name: 'Invoice review',
								processDefinitionKey,
								tenantId: 'tenant-a',
							}),
						],
					}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
			mockCreateProcessInstanceEndpoint({
				schema: z.strictObject({
					processDefinitionKey: z.literal(processDefinitionKey),
					tenantId: z.literal('tenant-a'),
				}),
				successResponse: HttpResponse.json(createProcessInstanceResponse({processDefinitionKey, tenantId: 'tenant-a'})),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await tasklistProcessesPage.goto('?tenantId=tenant-a');
		await tasklistProcessesPage.startProcessButton.click();

		await expect(
			tasklistProcessesPage.header.notifications.getByNotificationTitle('Process has started'),
		).toBeVisible();
	});

	test('should notify the user when starting a process fails', async ({network, tasklistProcessesPage}) => {
		const processDefinitionKey = '2251799813685279';
		network.use(
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(
					createCurrentUser({
						tenants: [{tenantId: '<default>', name: 'Default', description: null}],
					}),
				),
			}),
			mockQueryProcessDefinitionsEndpoint({
				schema: createProcessDefinitionsRequestSchema({tenantId: z.literal('<default>')}),
				successResponse: HttpResponse.json(
					createQueryProcessDefinitionsResponse({
						items: [
							createProcessDefinition({
								name: 'Invoice review',
								processDefinitionKey,
							}),
						],
					}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
			mockCreateProcessInstanceEndpoint({
				schema: z.strictObject({
					processDefinitionKey: z.literal(processDefinitionKey),
					tenantId: z.literal('<default>'),
				}),
				successResponse: new HttpResponse(null, {status: 500}),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await tasklistProcessesPage.goto();
		await tasklistProcessesPage.startProcessButton.click();

		const notification = tasklistProcessesPage.header.notifications.getByNotificationTitle('Process start failed');
		await expect(notification).toBeVisible();
		await expect(notification).toContainText('Invoice review');
	});

	test('should notify the user when they do not have permission to start a process', async ({
		network,
		tasklistProcessesPage,
	}) => {
		const processDefinitionKey = '2251799813685279';
		network.use(
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(
					createCurrentUser({
						tenants: [{tenantId: '<default>', name: 'Default', description: null}],
					}),
				),
			}),
			mockQueryProcessDefinitionsEndpoint({
				schema: createProcessDefinitionsRequestSchema({tenantId: z.literal('<default>')}),
				successResponse: HttpResponse.json(
					createQueryProcessDefinitionsResponse({
						items: [
							createProcessDefinition({
								name: 'Invoice review',
								processDefinitionKey,
							}),
						],
					}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
			mockCreateProcessInstanceEndpoint({
				schema: z.strictObject({
					processDefinitionKey: z.literal(processDefinitionKey),
					tenantId: z.literal('<default>'),
				}),
				successResponse: new HttpResponse(null, {status: 403}),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await tasklistProcessesPage.goto();
		await tasklistProcessesPage.startProcessButton.click();

		const notification = tasklistProcessesPage.header.notifications.getByNotificationTitle('Process start failed');
		await expect(notification).toContainText(
			"You don't have the necessary permissions. Contact your admin to request access.",
		);
		await expect(notification).not.toContainText('Invoice review');
		await expect(tasklistProcessesPage.startProcessButton).toBeEnabled();
	});

	test('should render Tasklist Processes page with navigation', async ({tasklistProcessesPage}) => {
		await tasklistProcessesPage.goto();

		await expect(tasklistProcessesPage.heading).toBeVisible();
		await expect(tasklistProcessesPage.tasksNavItem).toBeVisible();
		await expect(tasklistProcessesPage.processesNavItem).toBeVisible();
	});

	test('should display available processes with their names, IDs, form requirements, start buttons, and more-results control', async ({
		network,
		tasklistProcessesPage,
		page,
	}) => {
		network.use(
			mockQueryProcessDefinitionsEndpoint({
				schema: createProcessDefinitionsRequestSchema(),
				successResponse: HttpResponse.json(
					createQueryProcessDefinitionsResponse({
						items: [
							createProcessDefinition({
								name: 'Invoice review',
								processDefinitionId: 'invoice-review',
								processDefinitionKey: '1',
								hasStartForm: true,
							}),
							createProcessDefinition({
								name: null,
								processDefinitionId: 'order-approval',
								processDefinitionKey: '2',
							}),
						],
						page: {endCursor: 'next-page', hasMoreTotalItems: true},
					}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await tasklistProcessesPage.goto();

		await expect(tasklistProcessesPage.processHeading('Invoice review')).toBeVisible();
		await expect(page.getByText('invoice-review', {exact: true})).toBeVisible();
		await expect(tasklistProcessesPage.processHeading('order-approval')).toBeVisible();
		await expect(page.getByText('Requires form input')).toBeVisible();
		await expect(tasklistProcessesPage.startProcessButton).toHaveCount(2);
		await expect(tasklistProcessesPage.loadMoreButton).toBeVisible();
	});

	test('should display the appropriate empty state for unfiltered and filtered process lists', async ({
		network,
		tasklistProcessesPage,
	}) => {
		await tasklistProcessesPage.goto();
		await expect(tasklistProcessesPage.unpublishedProcessesHeading).toBeVisible();

		network.use(
			mockQueryProcessDefinitionsEndpoint({
				schema: createProcessDefinitionsRequestSchema({
					processDefinitionId: z.strictObject({$like: z.literal('*missing*')}),
				}),
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);
		await tasklistProcessesPage.searchInput.fill('missing');
		await expect(tasklistProcessesPage.noMatchingProcessesHeading).toBeVisible();
	});

	test('should search and filter processes while preserving the selected filters', async ({
		network,
		tasklistProcessesPage,
		page,
	}) => {
		network.use(
			mockQueryProcessDefinitionsEndpoint({
				schema: createProcessDefinitionsRequestSchema({hasStartForm: z.literal(true)}),
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await tasklistProcessesPage.goto('?hasStartForm=yes');
		await expect(tasklistProcessesPage.unpublishedProcessesHeading).toBeVisible();

		network.use(
			mockQueryProcessDefinitionsEndpoint({
				schema: createProcessDefinitionsRequestSchema({
					hasStartForm: z.literal(true),
					processDefinitionId: z.strictObject({$like: z.literal('*invoice*')}),
				}),
				successResponse: HttpResponse.json(
					createQueryProcessDefinitionsResponse({
						items: [createProcessDefinition({name: 'Invoice review', processDefinitionKey: '1'})],
					}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await tasklistProcessesPage.searchInput.fill('invoice');

		await expect(tasklistProcessesPage.processHeading('Invoice review')).toBeVisible();
		await expect(page).toHaveURL(/hasStartForm=yes/);
		await expect(page).toHaveURL(/search=invoice/);

		network.use(
			mockQueryProcessDefinitionsEndpoint({
				schema: createProcessDefinitionsRequestSchema({hasStartForm: z.literal(true)}),
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await tasklistProcessesPage.searchInput.fill('');
		await expect(page).not.toHaveURL(/search=/);
		await expect(tasklistProcessesPage.unpublishedProcessesHeading).toBeVisible();

		network.use(
			mockQueryProcessDefinitionsEndpoint({
				schema: createProcessDefinitionsRequestSchema({hasStartForm: z.literal(false)}),
				successResponse: HttpResponse.json(
					createQueryProcessDefinitionsResponse({
						items: [createProcessDefinition({name: 'Order approval', processDefinitionKey: '2'})],
					}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await tasklistProcessesPage.processFilter.click();
		await page.getByRole('option', {name: 'Does not require form input to start'}).click();

		await expect(tasklistProcessesPage.processHeading('Order approval')).toBeVisible();
		await expect(page).toHaveURL(/hasStartForm=no/);
	});

	test('should use the default tenant and update the process list when another tenant is selected', async ({
		network,
		tasklistProcessesPage,
		page,
	}) => {
		const tenants = [
			{tenantId: '<default>', name: 'Default', description: null},
			{tenantId: 'tenant-a', name: 'Tenant A', description: null},
		];
		network.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser({tenants}))}),
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(
					createSystemConfiguration({
						components: {active: ['tasklist']},
						deployment: {isMultiTenancyEnabled: true, maxRequestSize: 0},
					}),
				),
			}),
			mockQueryProcessDefinitionsEndpoint({
				schema: createProcessDefinitionsRequestSchema({tenantId: z.literal('<default>')}),
				successResponse: HttpResponse.json(
					createQueryProcessDefinitionsResponse({
						items: [createProcessDefinition({name: 'Default tenant process', processDefinitionKey: '1'})],
					}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await tasklistProcessesPage.goto();
		await expect(tasklistProcessesPage.processHeading('Default tenant process')).toBeVisible();

		network.use(
			mockQueryProcessDefinitionsEndpoint({
				schema: createProcessDefinitionsRequestSchema({tenantId: z.literal('tenant-a')}),
				successResponse: HttpResponse.json(
					createQueryProcessDefinitionsResponse({
						items: [createProcessDefinition({name: 'Tenant A process', processDefinitionKey: '2'})],
					}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await tasklistProcessesPage.tenantFilter.click();
		await page.getByRole('option', {name: 'Tenant A - tenant-a'}).click();

		await expect(tasklistProcessesPage.processHeading('Tenant A process')).toBeVisible();
		await expect(page).toHaveURL(/tenantId=tenant-a/);
	});

	test('should display the forbidden page when process access is denied', async ({
		network,
		tasklistProcessesPage,
		forbiddenPage,
	}) => {
		network.use(mockQueryProcessDefinitionsEndpoint({successResponse: new HttpResponse(null, {status: 403})}));

		await tasklistProcessesPage.goto();

		await expect(forbiddenPage.heading).toBeVisible();
	});

	test('should display the generic error page when processes cannot be loaded', async ({
		network,
		tasklistProcessesPage,
	}) => {
		network.use(mockQueryProcessDefinitionsEndpoint({successResponse: new HttpResponse(null, {status: 500})}));

		await tasklistProcessesPage.goto();

		await expect(tasklistProcessesPage.genericErrorHeading).toBeVisible();
	});

	test('should navigate from Processes to Tasks', async ({network, tasklistProcessesPage, page}) => {
		network.use(
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(createQueryUserTasksResponse()),
			}),
		);
		await tasklistProcessesPage.goto();

		await tasklistProcessesPage.tasksNavItem.click();

		await expect(page).toHaveURL('/tasklist');
	});
});
