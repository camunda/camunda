/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {test, expect} from '#/pw-modules/test-extend';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createCallHierarchy} from '#/shared-test-modules/api-mocks/call-hierarchy';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {createGetProcessDefinitionStatisticsResponse} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {processInstanceHeaderHandlers} from '#/shared-test-modules/process-instance-header-handlers';
import {
	mockGetProcessInstanceEndpoint,
	mockResolveProcessInstanceIncidentsEndpoint,
	mockCancelProcessInstanceEndpoint,
	mockDeleteProcessInstanceEndpoint,
	mockSuspendProcessInstanceEndpoint,
	mockResumeProcessInstanceEndpoint,
	mockGetProcessInstanceCallHierarchyEndpoint,
	mockGetProcessDefinitionStatisticsEndpoint,
	mockQueryProcessDefinitionsEndpoint,
} from '#/shared-test-modules/mock-handlers';

test('should retry, suspend, resume and cancel from a direct tenant-aware instance URL', async ({
	network,
	page,
	operateProcessInstancePage,
}) => {
	const instance = createProcessInstance({
		processInstanceKey: '2251799813691001',
		tenantId: 'tenant-a',
		hasIncident: true,
	});
	const key = instance.processInstanceKey;
	network.use(
		...processInstanceHeaderHandlers(instance),
		mockResolveProcessInstanceIncidentsEndpoint({
			successResponse: HttpResponse.json({batchOperationKey: 'retry-batch'}, {status: 202}),
		}),
	);
	await operateProcessInstancePage.goto(key, '/variables?tenantId=tenant-a&elementId=task-1');
	await expect(operateProcessInstancePage.action('Retry', key)).toBeEnabled();
	await expect(page).toHaveTitle(`Operate: Process Instance ${key} of My Process`);
	await expect(page.getByText('Tenant A', {exact: true})).toBeVisible();
	await operateProcessInstancePage.action('Retry', key).click();
	await expect(page.getByText('Incidents are scheduled for retry')).toBeVisible();
	await operateProcessInstancePage.notifications
		.getByNotificationTitle('Incidents are scheduled for retry')
		.getByRole('button', {name: 'close notification'})
		.click();

	network.use(
		mockSuspendProcessInstanceEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		mockGetProcessInstanceEndpoint({successResponse: HttpResponse.json({...instance, state: 'SUSPENDED'})}),
	);
	await operateProcessInstancePage.action('Suspend', key).click();
	await expect(page.getByText('Instance suspended')).toBeVisible();
	await operateProcessInstancePage.notifications
		.getByNotificationTitle('Instance suspended')
		.getByRole('button', {name: 'close notification'})
		.click();
	await expect(operateProcessInstancePage.action('Resume', key)).toBeEnabled();
	network.use(
		mockResumeProcessInstanceEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		mockGetProcessInstanceEndpoint({successResponse: HttpResponse.json({...instance, hasIncident: false})}),
	);
	await operateProcessInstancePage.action('Resume', key).click();
	await expect(page.getByText('Instance resumed')).toBeVisible();
	await operateProcessInstancePage.notifications
		.getByNotificationTitle('Instance resumed')
		.getByRole('button', {name: 'close notification'})
		.click();
	await expect(operateProcessInstancePage.action('Retry', key)).not.toBeVisible();
	await expect(page).toHaveURL(new RegExp(`/operate/processes/${key}/variables\\?tenantId=tenant-a&elementId=task-1$`));

	network.use(mockCancelProcessInstanceEndpoint({successResponse: new HttpResponse(null, {status: 204})}));
	await operateProcessInstancePage.action('Cancel', key).click();
	await operateProcessInstancePage.apply.click();
	await expect(page.getByText('Instance is scheduled for cancellation')).toBeVisible();
});

test('should delete a finished instance within a deployment prefix and replace its URL', async ({
	network,
	page,
	operateProcessInstancePage,
}) => {
	const instance = createProcessInstance({
		processInstanceKey: '2251799813691002',
		state: 'COMPLETED',
		tenantId: 'tenant-a',
	});
	network.use(
		...processInstanceHeaderHandlers(instance),
		mockDeleteProcessInstanceEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
	);
	await page.route('**/deployment/operate/processes/**', async (route) => {
		const response = await route.fetch();
		await route.fulfill({
			response,
			body: (await response.text()).replace('<html', '<html data-base-name="/deployment"'),
		});
	});
	await operateProcessInstancePage.goto(instance.processInstanceKey, '/variables?tenantId=tenant-a', '/deployment');
	await operateProcessInstancePage.action('Delete', instance.processInstanceKey).click();
	await operateProcessInstancePage.delete.click();
	await expect(page.getByText('Instance is scheduled for deletion')).toBeVisible();
	await expect(page).toHaveURL(
		/\/deployment\/operate\/processes\?active=true&incidents=true&suspended=true&completed=false&canceled=false$/,
	);
});

test('should recover an initial read failure and preserve usable controls on cached read errors', async ({
	network,
	page,
	operateProcessInstancePage,
}) => {
	const instance = createProcessInstance({processInstanceKey: '2251799813691003', tenantId: 'tenant-a'});
	network.use(...processInstanceHeaderHandlers(instance));
	network.use(mockGetProcessInstanceEndpoint({successResponse: new HttpResponse(null, {status: 500})}));
	const initialFailure = page.waitForResponse(
		(response) =>
			response.url().endsWith(`/process-instances/${instance.processInstanceKey}`) && response.status() === 500,
	);
	await operateProcessInstancePage.goto(instance.processInstanceKey);
	await initialFailure;
	await expect(operateProcessInstancePage.action('Suspend', instance.processInstanceKey)).not.toBeVisible();
	network.use(mockGetProcessInstanceEndpoint({successResponse: HttpResponse.json(instance)}));
	await expect(operateProcessInstancePage.action('Suspend', instance.processInstanceKey)).toBeEnabled();
	network.use(mockGetProcessInstanceEndpoint({successResponse: new HttpResponse(null, {status: 500})}));
	await page.waitForResponse(
		(response) =>
			response.url().endsWith(`/process-instances/${instance.processInstanceKey}`) && response.status() === 500,
	);
	await expect(page.getByRole('heading', {name: 'Operate Process Instance'})).toBeAttached();
	await expect(operateProcessInstancePage.action('Cancel', instance.processInstanceKey)).toBeEnabled();
});

test('should retain root cancellation guidance and collapsed keyboard focus', async ({
	network,
	page,
	operateProcessInstancePage,
}) => {
	const instance = createProcessInstance({
		processInstanceKey: '2251799813691004',
		tenantId: 'tenant-a',
		state: 'SUSPENDED',
	});
	network.use(...processInstanceHeaderHandlers(instance));
	network.use(
		mockGetProcessInstanceCallHierarchyEndpoint({
			successResponse: HttpResponse.json([createCallHierarchy({processInstanceKey: '2251799813691005'})]),
		}),
	);
	await page.setViewportSize({width: 1024, height: 768});
	await operateProcessInstancePage.goto(instance.processInstanceKey);
	await operateProcessInstancePage.actionsMenu.focus();
	await page.keyboard.press('Enter');
	await expect(page.getByRole('menuitem', {name: 'Resume'})).toBeEnabled();
	await page.keyboard.press('Escape');
	await expect(operateProcessInstancePage.actionsMenu).toBeFocused();
	await operateProcessInstancePage.actionsMenu.click();
	await page.getByRole('menuitem', {name: 'Cancel'}).click();
	await expect(operateProcessInstancePage.confirmation.getByRole('link', {name: '2251799813691005'})).toHaveAttribute(
		'href',
		'/operate/processes/2251799813691005',
	);
	await expect(operateProcessInstancePage.apply).not.toBeVisible();
});

test('should hand an active instance to the migration wizard without keeping it across exit or reload', async ({
	network,
	page,
	operateProcessInstancePage,
	operateProcessesPage,
}) => {
	const instance = createProcessInstance({
		processInstanceKey: '2251799813691006',
		processDefinitionKey: '123',
		processDefinitionId: 'my_simple_process',
		processDefinitionName: 'Invoice process',
		processDefinitionVersion: 1,
		tenantId: 'tenant-a',
	});
	const key = instance.processInstanceKey;
	const migrationStep = (step: string) => page.getByText(`Migration step ${step}`);
	network.use(...processInstanceHeaderHandlers(instance));
	network.use(
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({
					items: [1, 2].map((version) =>
						createProcessDefinition({
							processDefinitionId: 'my_simple_process',
							processDefinitionKey: String(122 + version),
							name: 'Invoice process',
							version,
							tenantId: 'tenant-a',
						}),
					),
				}),
			),
		}),
		mockGetProcessDefinitionStatisticsEndpoint({
			successResponse: HttpResponse.json(createGetProcessDefinitionStatisticsResponse([])),
		}),
	);
	page.on('dialog', (dialog) => void dialog.accept());

	await test.step('enter the wizard from the instance and stay when navigating back', async () => {
		await operateProcessInstancePage.goto(key);
		await operateProcessInstancePage.action('Migrate', key).click();
		await operateProcessInstancePage.confirmation.getByRole('button', {name: 'Continue'}).click();
		await expect(migrationStep('1 - mapping elements')).toBeVisible();
		await expect(page).toHaveURL(
			/\/operate\/processes\?active=true&incidents=true&suspended=true&completed=false&canceled=false&process=my_simple_process&version=1&tenantId=tenant-a$/,
		);
		await page.goBack();
		await page.getByRole('dialog', {name: 'Leave Migration Mode'}).getByRole('button', {name: 'Stay'}).click();
		await expect(migrationStep('1 - mapping elements')).toBeVisible();
	});

	await test.step('exit to the source list', async () => {
		await page.getByRole('button', {name: 'Exit migration'}).click();
		await page.getByRole('dialog', {name: 'Exit migration'}).getByRole('button', {name: 'Exit'}).click();
		await expect(operateProcessesPage.processCombobox).toHaveValue('Invoice process');
		await expect(migrationStep('1 - mapping elements')).not.toBeVisible();
	});

	await test.step('reload the summary into the source list', async () => {
		await page.goBack();
		await operateProcessInstancePage.action('Migrate', key).click();
		await operateProcessInstancePage.confirmation.getByRole('button', {name: 'Continue'}).click();
		await page.getByRole('button', {name: 'Next'}).click();
		await expect(migrationStep('2 - confirm')).toBeVisible();
		await page.reload();
		await expect(operateProcessesPage.processCombobox).toHaveValue('Invoice process');
		await expect(migrationStep('2 - confirm')).not.toBeVisible();
	});
});
