/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import {
	mockCreateDocumentsEndpoint,
	mockCreateProcessInstanceEndpoint,
	mockCurrentUserEndpoint,
	mockGetProcessDefinitionEndpoint,
	mockGetProcessStartFormEndpoint,
	mockGetUserTaskEndpoint,
	mockLicenseEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryUserTasksEndpoint,
	mockQueryVariablesByUserTaskEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {
	createGetProcessDefinitionResponse,
	createProcessDefinition,
	createProcessStartFormResponse,
	createQueryProcessDefinitionsResponse,
	START_PROCESS_FORM_WITH_DOCUMENT_SCHEMA,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {createQueryUserTasksResponse, createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';
import {createProcessInstanceResponse} from '#/shared-test-modules/api-mocks/process-instances';
import {createQueryVariablesByUserTaskResponse} from '#/shared-test-modules/api-mocks/variables';

function createProcessDefinitionsRequestSchema(filter: Record<string, z.ZodType> = {}) {
	return z.strictObject({
		filter: z.strictObject({isLatestVersion: z.literal(true), ...filter}),
		page: z.strictObject({limit: z.literal(12)}),
	});
}

function createNewProcessInstanceTasksRequestSchema(processInstanceKey: string) {
	return z.strictObject({
		filter: z.strictObject({
			processInstanceKey: z.literal(processInstanceKey),
			state: z.literal('CREATED'),
		}),
		page: z.strictObject({limit: z.literal(10)}),
	});
}

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
			schema: createProcessDefinitionsRequestSchema(),
			successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
		mockQueryUserTasksEndpoint({
			successResponse: HttpResponse.json(createQueryUserTasksResponse()),
		}),
	);
});

test('should display available processes with their names, IDs, form requirements, start buttons, and more-results control', async ({
	network,
	shadcnTasklistProcessesPage,
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

	await shadcnTasklistProcessesPage.goto();

	await expect(shadcnTasklistProcessesPage.processHeading('Invoice review')).toBeVisible();
	await expect(shadcnTasklistProcessesPage.processDefinitionId('1', 'invoice-review')).toBeVisible();
	await expect(shadcnTasklistProcessesPage.processHeading('order-approval')).toBeVisible();
	await expect(shadcnTasklistProcessesPage.requiresFormPill('1')).toBeVisible();
	await expect(shadcnTasklistProcessesPage.startProcessButton).toHaveCount(2);
	await expect(shadcnTasklistProcessesPage.loadMoreButton).toBeVisible();
});

test('should display the appropriate empty state for unfiltered and filtered process lists', async ({
	network,
	shadcnTasklistProcessesPage,
}) => {
	await shadcnTasklistProcessesPage.goto();
	await expect(shadcnTasklistProcessesPage.unpublishedProcessesHeading).toBeVisible();

	network.use(
		mockQueryProcessDefinitionsEndpoint({
			schema: createProcessDefinitionsRequestSchema({
				processDefinitionId: z.strictObject({$like: z.literal('*missing*')}),
			}),
			successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
	);
	await shadcnTasklistProcessesPage.searchInput.fill('missing');
	await expect(shadcnTasklistProcessesPage.noMatchingProcessesHeading).toBeVisible();
});

test('should search and filter processes while preserving the selected filters', async ({
	network,
	shadcnTasklistProcessesPage,
	page,
}) => {
	network.use(
		mockQueryProcessDefinitionsEndpoint({
			schema: createProcessDefinitionsRequestSchema({hasStartForm: z.literal(true)}),
			successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
	);

	await shadcnTasklistProcessesPage.goto('?hasStartForm=yes');
	await expect(shadcnTasklistProcessesPage.unpublishedProcessesHeading).toBeVisible();

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

	await shadcnTasklistProcessesPage.searchInput.fill('invoice');

	await expect(shadcnTasklistProcessesPage.processHeading('Invoice review')).toBeVisible();
	await expect(page).toHaveURL(/hasStartForm=yes/);
	await expect(page).toHaveURL(/search=invoice/);

	network.use(
		mockQueryProcessDefinitionsEndpoint({
			schema: createProcessDefinitionsRequestSchema({hasStartForm: z.literal(true)}),
			successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
	);

	await shadcnTasklistProcessesPage.searchInput.fill('');
	await expect(page).not.toHaveURL(/search=/);
	await expect(shadcnTasklistProcessesPage.unpublishedProcessesHeading).toBeVisible();

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

	await shadcnTasklistProcessesPage.selectProcessFilter('Does not require form input to start');

	await expect(shadcnTasklistProcessesPage.processHeading('Order approval')).toBeVisible();
	await expect(page).toHaveURL(/hasStartForm=no/);
});

test('should use the default tenant and update the process list when another tenant is selected', async ({
	network,
	shadcnTasklistProcessesPage,
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

	await shadcnTasklistProcessesPage.goto();
	await expect(shadcnTasklistProcessesPage.processHeading('Default tenant process')).toBeVisible();

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

	await shadcnTasklistProcessesPage.selectTenant('Tenant A - tenant-a');

	await expect(shadcnTasklistProcessesPage.processHeading('Tenant A process')).toBeVisible();
	await expect(page).toHaveURL(/tenantId=tenant-a/);
});

test('should open and close a start form while preserving process filters in the URL', async ({
	network,
	shadcnTasklistProcessesPage,
	page,
}) => {
	const processDefinitionKey = '2251799813685279';
	network.use(
		mockQueryProcessDefinitionsEndpoint({
			schema: createProcessDefinitionsRequestSchema({
				hasStartForm: z.literal(true),
				processDefinitionId: z.strictObject({$like: z.literal('*invoice*')}),
			}),
			successResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({
					items: [
						createProcessDefinition({
							name: 'Invoice review',
							processDefinitionKey,
							hasStartForm: true,
						}),
					],
				}),
			),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
		mockGetProcessDefinitionEndpoint({
			successResponse: HttpResponse.json(
				createGetProcessDefinitionResponse({name: 'Invoice review', processDefinitionKey}),
			),
		}),
		mockGetProcessStartFormEndpoint({
			successResponse: HttpResponse.json(createProcessStartFormResponse()),
		}),
	);

	await shadcnTasklistProcessesPage.goto('?search=invoice&hasStartForm=yes');
	await shadcnTasklistProcessesPage.startProcessButton.click();

	await expect(shadcnTasklistProcessesPage.startProcessDialog).toBeVisible();
	await expect(page).toHaveURL(
		`/shadcn/tasklist/processes/${processDefinitionKey}/start?search=invoice&hasStartForm=yes`,
	);

	await shadcnTasklistProcessesPage.cancelStartProcessButton.click();

	await expect(page).toHaveURL('/shadcn/tasklist/processes?search=invoice&hasStartForm=yes');
});

test('should load a start form directly from its URL', async ({network, shadcnTasklistProcessesPage}) => {
	const processDefinitionKey = '2251799813685279';
	network.use(
		mockGetProcessDefinitionEndpoint({
			successResponse: HttpResponse.json(
				createGetProcessDefinitionResponse({name: null, processDefinitionId: 'invoice-review', processDefinitionKey}),
			),
		}),
		mockGetProcessStartFormEndpoint({
			successResponse: HttpResponse.json(createProcessStartFormResponse()),
		}),
	);

	await shadcnTasklistProcessesPage.gotoStartForm(processDefinitionKey);

	await expect(shadcnTasklistProcessesPage.startProcessDialog).toHaveAccessibleName('Start process invoice-review');
	await expect(
		shadcnTasklistProcessesPage.startProcessDialog.getByRole('textbox', {name: 'Customer name'}),
	).toBeVisible();
});

test('should start a process with a form and selected tenant', async ({network, shadcnTasklistProcessesPage, page}) => {
	const processDefinitionKey = '2251799813685279';
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
					deployment: {isMultiTenancyEnabled: true, maxRequestSize: 4_194_304},
				}),
			),
		}),
		mockQueryProcessDefinitionsEndpoint({
			schema: createProcessDefinitionsRequestSchema({tenantId: z.literal('tenant-a')}),
			successResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({
					items: [
						createProcessDefinition({
							name: 'Invoice review',
							processDefinitionKey,
							hasStartForm: true,
							tenantId: 'tenant-a',
						}),
					],
				}),
			),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
		mockGetProcessDefinitionEndpoint({
			successResponse: HttpResponse.json(
				createGetProcessDefinitionResponse({name: 'Invoice review', processDefinitionKey, tenantId: 'tenant-a'}),
			),
		}),
		mockGetProcessStartFormEndpoint({
			successResponse: HttpResponse.json(
				createProcessStartFormResponse({schema: START_PROCESS_FORM_WITH_DOCUMENT_SCHEMA, tenantId: 'tenant-a'}),
			),
		}),
		mockCreateDocumentsEndpoint({
			successResponse: HttpResponse.json({createdDocuments: [], failedDocuments: []}),
		}),
		mockCreateProcessInstanceEndpoint({
			schema: z.strictObject({
				processDefinitionKey: z.literal(processDefinitionKey),
				tenantId: z.literal('tenant-a'),
				variables: z.object({
					customerName: z.literal('Jane Doe'),
					invoiceAmount: z.literal(125.5),
				}),
			}),
			successResponse: HttpResponse.json(createProcessInstanceResponse({processDefinitionKey, tenantId: 'tenant-a'})),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
	);

	await shadcnTasklistProcessesPage.goto('?tenantId=tenant-a');
	await shadcnTasklistProcessesPage.startProcessButton.click();
	await shadcnTasklistProcessesPage.startProcessDialog.getByRole('textbox', {name: 'Customer name'}).fill('Jane Doe');
	await shadcnTasklistProcessesPage.startProcessDialog.getByRole('textbox', {name: 'Invoice amount'}).fill('125.5');
	await shadcnTasklistProcessesPage.startProcessDialog.getByLabel('Supporting document').setInputFiles({
		name: 'supporting.txt',
		mimeType: 'text/plain',
		buffer: Buffer.from('supporting document'),
	});
	await shadcnTasklistProcessesPage.startProcessFormButton.click();

	await expect(
		shadcnTasklistProcessesPage.header.notifications.getByNotificationTitle('Process has started'),
	).toBeVisible();
	await expect(page).toHaveURL('/shadcn/tasklist/processes?tenantId=tenant-a');
	await expect(shadcnTasklistProcessesPage.startProcessDialog).not.toBeVisible();
});

test('should prevent process creation when form validation fails', async ({network, shadcnTasklistProcessesPage}) => {
	const processDefinitionKey = '2251799813685279';
	network.use(
		mockGetProcessDefinitionEndpoint({
			successResponse: HttpResponse.json(createGetProcessDefinitionResponse({processDefinitionKey})),
		}),
		mockGetProcessStartFormEndpoint({
			successResponse: HttpResponse.json(createProcessStartFormResponse()),
		}),
	);

	await shadcnTasklistProcessesPage.gotoStartForm(processDefinitionKey);
	await shadcnTasklistProcessesPage.startProcessFormButton.click();

	await expect(shadcnTasklistProcessesPage.startProcessDialog.getByRole('alert')).toContainText(
		'Please review 1 field: Customer name',
	);
});

test('should close the form and notify after submission failure', async ({
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
		mockCreateProcessInstanceEndpoint({
			successResponse: new HttpResponse(null, {status: 500}),
		}),
	);

	await shadcnTasklistProcessesPage.gotoStartForm(processDefinitionKey);
	await shadcnTasklistProcessesPage.startProcessDialog.getByRole('textbox', {name: 'Customer name'}).fill('Jane Doe');
	await shadcnTasklistProcessesPage.startProcessFormButton.click();

	await expect(page).toHaveURL('/shadcn/tasklist/processes');
	await expect(shadcnTasklistProcessesPage.startProcessDialog).not.toBeVisible();
	await expect(
		shadcnTasklistProcessesPage.header.notifications.getByNotificationTitle('Process start failed'),
	).toBeVisible();
});

test('should show non-retryable errors for missing, form-less, forbidden, and invalid-schema processes', async ({
	network,
	shadcnTasklistProcessesPage,
	page,
}) => {
	const processDefinitionKey = '2251799813685279';
	network.use(mockGetProcessDefinitionEndpoint({successResponse: new HttpResponse(null, {status: 404})}));

	await shadcnTasklistProcessesPage.gotoStartForm(processDefinitionKey);
	await expect(shadcnTasklistProcessesPage.startProcessFormError).toContainText(
		`Process ${processDefinitionKey} does not exist or has no start form`,
	);
	await expect(shadcnTasklistProcessesPage.startProcessDialog.getByRole('button', {name: 'Try again'})).toHaveCount(0);

	network.use(
		mockGetProcessDefinitionEndpoint({
			successResponse: HttpResponse.json(
				createGetProcessDefinitionResponse({processDefinitionKey, hasStartForm: false}),
			),
		}),
	);
	await page.reload();
	await expect(shadcnTasklistProcessesPage.startProcessFormError).toContainText(
		`Process ${processDefinitionKey} does not exist or has no start form`,
	);
	await expect(shadcnTasklistProcessesPage.startProcessDialog.getByRole('button', {name: 'Try again'})).toHaveCount(0);

	network.use(mockGetProcessDefinitionEndpoint({successResponse: new HttpResponse(null, {status: 403})}));
	await page.reload();
	await expect(shadcnTasklistProcessesPage.startProcessFormError).toContainText(
		"You don't have the necessary permissions.",
	);
	await expect(shadcnTasklistProcessesPage.startProcessDialog.getByRole('button', {name: 'Try again'})).toHaveCount(0);

	network.use(
		mockGetProcessDefinitionEndpoint({
			successResponse: HttpResponse.json(createGetProcessDefinitionResponse({processDefinitionKey})),
		}),
		mockGetProcessStartFormEndpoint({
			successResponse: HttpResponse.json(createProcessStartFormResponse({schema: '{ invalid schema'})),
		}),
	);
	await page.reload();
	await expect(shadcnTasklistProcessesPage.startProcessFormError).toContainText('We were not able to render the form.');
	await expect(page).toHaveURL(`/shadcn/tasklist/processes/${processDefinitionKey}/start`);
});

test('should retry after a transient start-form loading failure', async ({network, shadcnTasklistProcessesPage}) => {
	const processDefinitionKey = '2251799813685279';
	network.use(
		mockGetProcessDefinitionEndpoint({
			successResponse: HttpResponse.json(createGetProcessDefinitionResponse({processDefinitionKey})),
		}),
		mockGetProcessStartFormEndpoint({successResponse: new HttpResponse(null, {status: 500})}),
	);

	await shadcnTasklistProcessesPage.gotoStartForm(processDefinitionKey);
	await expect(shadcnTasklistProcessesPage.startProcessFormError).toContainText('We were not able to load the form.');

	network.use(
		mockGetProcessStartFormEndpoint({
			successResponse: HttpResponse.json(createProcessStartFormResponse()),
		}),
	);
	await shadcnTasklistProcessesPage.startProcessDialog.getByRole('button', {name: 'Try again'}).click();

	await expect(
		shadcnTasklistProcessesPage.startProcessDialog.getByRole('textbox', {name: 'Customer name'}),
	).toBeVisible();
});

test('should start a process without a form using the selected tenant and open its task', async ({
	network,
	shadcnTasklistProcessesPage,
	shadcnTaskDetailPage,
	page,
}) => {
	const processDefinitionKey = '2251799813685279';
	const processInstanceKey = '2251799813685280';
	const userTaskKey = '2251799813685281';
	const task = createUserTask({
		userTaskKey,
		processInstanceKey,
		name: 'Review invoice',
		processName: 'Invoice review',
	});
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
		mockQueryUserTasksEndpoint({
			schema: createNewProcessInstanceTasksRequestSchema(processInstanceKey),
			successResponse: HttpResponse.json(createQueryUserTasksResponse({items: [task]})),
			failureResponse: new HttpResponse(null, {status: 400}),
			delay: 500,
		}),
		mockGetUserTaskEndpoint({successResponse: HttpResponse.json(task)}),
		mockQueryVariablesByUserTaskEndpoint({
			successResponse: HttpResponse.json(createQueryVariablesByUserTaskResponse()),
		}),
	);

	await shadcnTasklistProcessesPage.goto('?tenantId=tenant-a');
	await shadcnTasklistProcessesPage.startProcessButton.click();

	await expect(
		shadcnTasklistProcessesPage.header.notifications.getByNotificationTitle('Process has started'),
	).toBeVisible();
	network.use(
		mockQueryUserTasksEndpoint({
			successResponse: HttpResponse.json(createQueryUserTasksResponse({items: [task]})),
		}),
	);
	await expect(shadcnTasklistProcessesPage.waitingForTasksStatus).toBeVisible();
	await expect(shadcnTasklistProcessesPage.startProcessButton).toHaveCount(0);
	await expect(page).toHaveURL(`/shadcn/tasklist/${userTaskKey}`);
	await expect(shadcnTaskDetailPage.detailsInfo).toBeVisible();
});

test('should notify about multiple new tasks and open a selected task', async ({
	network,
	shadcnTasklistProcessesPage,
	shadcnTaskDetailPage,
	page,
}) => {
	const processDefinitionKey = '2251799813685279';
	const processInstanceKey = '2251799813685280';
	const reviewTask = createUserTask({
		userTaskKey: '2251799813685281',
		processInstanceKey,
		name: 'Review invoice',
		processName: 'Invoice review',
	});
	const approveTask = createUserTask({
		userTaskKey: '2251799813685282',
		processInstanceKey,
		name: null,
		elementId: 'approve-invoice',
		processName: null,
		processDefinitionId: 'invoice-review',
	});
	network.use(
		mockQueryProcessDefinitionsEndpoint({
			schema: createProcessDefinitionsRequestSchema(),
			successResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({
					items: [createProcessDefinition({name: 'Invoice review', processDefinitionKey})],
				}),
			),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
		mockCreateProcessInstanceEndpoint({
			successResponse: HttpResponse.json(createProcessInstanceResponse({processDefinitionKey, processInstanceKey})),
		}),
		mockQueryUserTasksEndpoint({
			schema: createNewProcessInstanceTasksRequestSchema(processInstanceKey),
			successResponse: HttpResponse.json(createQueryUserTasksResponse({items: [reviewTask, approveTask]})),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
		mockGetUserTaskEndpoint({successResponse: HttpResponse.json(approveTask)}),
		mockQueryVariablesByUserTaskEndpoint({
			successResponse: HttpResponse.json(createQueryVariablesByUserTaskResponse()),
		}),
	);

	await shadcnTasklistProcessesPage.goto();
	await shadcnTasklistProcessesPage.startProcessButton.click();

	const reviewNotificationTitle = 'Process "Invoice review" reached task "Review invoice"';
	const approveNotificationTitle = 'Process "invoice-review" reached task "approve-invoice"';
	await expect(
		shadcnTasklistProcessesPage.header.notifications.getByNotificationTitle(reviewNotificationTitle),
	).toBeVisible();
	await expect(
		shadcnTasklistProcessesPage.header.notifications.getByNotificationTitle(approveNotificationTitle),
	).toBeVisible();
	await expect(page).toHaveURL('/shadcn/tasklist/processes');

	network.use(
		mockQueryUserTasksEndpoint({
			successResponse: HttpResponse.json(createQueryUserTasksResponse({items: [reviewTask, approveTask]})),
		}),
	);
	await shadcnTasklistProcessesPage.header.notifications.getActionButton(approveNotificationTitle, 'Open task').click();

	await expect(page).toHaveURL(`/shadcn/tasklist/${approveTask.userTaskKey}`);
	await expect(shadcnTaskDetailPage.detailsInfo).toBeVisible();
});

test('should notify the user when starting a process fails', async ({network, shadcnTasklistProcessesPage}) => {
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

	await shadcnTasklistProcessesPage.goto();
	await shadcnTasklistProcessesPage.startProcessButton.click();

	const notification = shadcnTasklistProcessesPage.header.notifications.getByNotificationTitle('Process start failed');
	await expect(notification).toBeVisible();
	await expect(notification).toContainText('Invoice review');
});

test('should notify the user when they do not have permission to start a process', async ({
	network,
	shadcnTasklistProcessesPage,
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

	await shadcnTasklistProcessesPage.goto();
	await shadcnTasklistProcessesPage.startProcessButton.click();

	const notification = shadcnTasklistProcessesPage.header.notifications.getByNotificationTitle('Process start failed');
	await expect(notification).toContainText(
		"You don't have the necessary permissions. Contact your admin to request access.",
	);
	await expect(notification).not.toContainText('Invoice review');
	await expect(shadcnTasklistProcessesPage.startProcessButton).toBeEnabled();
});

test('should display the forbidden page when process access is denied', async ({
	network,
	shadcnTasklistProcessesPage,
	forbiddenPage,
}) => {
	network.use(mockQueryProcessDefinitionsEndpoint({successResponse: new HttpResponse(null, {status: 403})}));

	await shadcnTasklistProcessesPage.goto();

	await expect(forbiddenPage.heading).toBeVisible();
});

test('should display the generic error page when processes cannot be loaded', async ({
	network,
	shadcnTasklistProcessesPage,
}) => {
	network.use(mockQueryProcessDefinitionsEndpoint({successResponse: new HttpResponse(null, {status: 500})}));

	await shadcnTasklistProcessesPage.goto();

	await expect(shadcnTasklistProcessesPage.genericErrorHeading).toBeVisible();
});
