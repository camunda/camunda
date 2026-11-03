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
	mockGetProcessInstanceCallHierarchyEndpoint,
	mockGetProcessInstanceEndpoint,
	mockGetProcessInstanceWaitStateStatisticsEndpoint,
	mockLicenseEndpoint,
	mockOperateClientConfigEndpoint,
	mockQueryAuditLogsEndpoint,
	mockQueryJobsEndpoint,
	mockQueryProcessInstanceIncidentsEndpoint,
	mockQueryVariablesEndpoint,
	mockGetVariableEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createOperateClientConfigScript} from '#/shared-test-modules/api-mocks/operate-client-config';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createIncident} from '#/shared-test-modules/api-mocks/incidents';
import {createAuditLog, createQueryAuditLogsResponse} from '#/shared-test-modules/api-mocks/audit-logs';
import {createListenerJob} from '#/shared-test-modules/api-mocks/jobs';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {createQueryVariablesByUserTaskResponse, createVariable} from '#/shared-test-modules/api-mocks/variables';
import {createCallHierarchy} from '#/shared-test-modules/api-mocks/call-hierarchy';
import {processInstanceHeaderHandlers} from '#/shared-test-modules/process-instance-header-handlers';
import {processInstanceDiagramHandlers} from '#/shared-test-modules/process-instance-diagram-handlers';

const PROCESS_INSTANCE_ID = '2251799813685280';

test('should have no accessibility violations in the process instance diagram shell', async ({
	network,
	operatePreviewProcessInstancePage,
	makeAxeBuilder,
}) => {
	network.use(
		...processInstanceDiagramHandlers(),
		...processInstanceHeaderHandlers(createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})),
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser({authorizedComponents: ['operate']})),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
		}),
		mockOperateClientConfigEndpoint({
			successResponse: HttpResponse.text(createOperateClientConfigScript()),
		}),
		mockLicenseEndpoint({
			successResponse: HttpResponse.json(createLicense()),
		}),
		mockGetProcessInstanceEndpoint({
			successResponse: HttpResponse.json(createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})),
		}),
		mockGetProcessInstanceWaitStateStatisticsEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse({items: []})),
		}),
	);

	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
	await expect(operatePreviewProcessInstancePage.header).toBeVisible();
	await expect(operatePreviewProcessInstancePage.stateOverlay('Review invoice, Active: 1')).toBeVisible();

	const results = await makeAxeBuilder().analyze();
	expect(results.violations).toEqual([]);
});

test('should have no accessibility violations in the process instance diagram with a selected element', async ({
	network,
	page,
	operatePreviewProcessInstancePage,
	makeAxeBuilder,
}) => {
	network.use(
		...processInstanceDiagramHandlers(),
		...processInstanceHeaderHandlers(createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})),
	);

	await page.goto(`/operate-preview/processes/${PROCESS_INSTANCE_ID}/variables?elementId=task-1`);
	await expect(operatePreviewProcessInstancePage.stateOverlay('Review invoice, Incidents: 1')).toBeVisible();

	const results = await makeAxeBuilder().analyze();
	expect(results.violations).toEqual([]);
});

test('should have no accessibility violations with the breadcrumb overflow menu open', async ({
	network,
	page,
	operatePreviewProcessInstancePage,
	makeAxeBuilder,
}) => {
	const hierarchy = ['1', '2', '3', '4', '5', PROCESS_INSTANCE_ID].map((processInstanceKey) =>
		createCallHierarchy({processInstanceKey, processDefinitionName: `Process ${processInstanceKey}`}),
	);
	network.use(
		mockGetProcessInstanceCallHierarchyEndpoint({successResponse: HttpResponse.json(hierarchy)}),
		...processInstanceHeaderHandlers(createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})),
	);

	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
	await page.getByRole('button', {name: 'More'}).click();
	await expect(page.getByRole('menuitem', {name: 'Process 3'})).toBeVisible();

	const results = await makeAxeBuilder().include('[role="menu"]').analyze();
	expect(results.violations).toEqual([]);
});

test.describe('Variables tab', () => {
	test.beforeEach(({network}) => {
		network.use(...processInstanceHeaderHandlers(createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})));
		network.use(
			mockQueryVariablesEndpoint({
				successResponse: HttpResponse.json(
					createQueryVariablesByUserTaskResponse({
						items: [
							createVariable({name: 'amount', value: '100', variableKey: '1'}),
							createVariable({name: 'customer', value: '{"name": "Ada"}', variableKey: '2'}),
						],
					}),
				),
			}),
			mockGetVariableEndpoint({
				successResponse: HttpResponse.json(createVariable({name: 'amount', value: '100', variableKey: '1'})),
			}),
		);
	});

	test('should have no accessibility violations with variables listed', async ({
		operatePreviewProcessInstancePage,
		makeAxeBuilder,
	}) => {
		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
		await expect(operatePreviewProcessInstancePage.variableRow('amount')).toBeVisible();

		const results = await makeAxeBuilder().analyze();
		expect(results.violations).toEqual([]);
	});

	test('should have no accessibility violations while adding a variable', async ({
		operatePreviewProcessInstancePage,
		page,
		makeAxeBuilder,
	}) => {
		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
		await page.getByRole('button', {name: 'Add Variable'}).click();
		await expect(page.getByRole('textbox', {name: 'Name', exact: true})).toBeVisible();

		const results = await makeAxeBuilder().analyze();
		expect(results.violations).toEqual([]);
	});

	test('should have no accessibility violations with the full value dialog open', async ({
		operatePreviewProcessInstancePage,
		page,
		makeAxeBuilder,
	}) => {
		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
		await operatePreviewProcessInstancePage.variableRow('amount').getByRole('button', {name: 'Open'}).click();
		await expect(page.getByRole('dialog')).toBeVisible();

		const results = await makeAxeBuilder().analyze();
		expect(results.violations).toEqual([]);
	});
});

const DS_EXPAND_COLUMN_HEADER = 'thead th:first-child';

test.describe('Incidents tab', () => {
	test.beforeEach(({network}) => {
		network.use(
			...processInstanceHeaderHandlers(
				createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID, hasIncident: true}),
			),
		);
		network.use(
			mockQueryProcessInstanceIncidentsEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({
						items: [createIncident({errorMessage: 'Payment failed'})],
						page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
		);
	});

	test('should have no accessibility violations with incidents listed', async ({
		operatePreviewProcessInstancePage,
		makeAxeBuilder,
	}) => {
		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
		await operatePreviewProcessInstancePage.incidentsTab.click();
		await expect(operatePreviewProcessInstancePage.incidentsTable).toBeVisible();

		const results = await makeAxeBuilder().exclude(DS_EXPAND_COLUMN_HEADER).analyze();
		expect(results.violations).toEqual([]);
	});

	test('should have no accessibility violations with an incident expanded', async ({
		operatePreviewProcessInstancePage,
		makeAxeBuilder,
	}) => {
		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
		await operatePreviewProcessInstancePage.incidentsTab.click();
		await operatePreviewProcessInstancePage.incidentsTable.getByRole('button', {name: 'Expand row'}).click();

		const results = await makeAxeBuilder().exclude(DS_EXPAND_COLUMN_HEADER).analyze();
		expect(results.violations).toEqual([]);
	});
});

test.describe('Listeners tab', () => {
	test.beforeEach(({network}) => {
		network.use(...processInstanceHeaderHandlers(createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})));
	});

	test('should have no accessibility violations with listeners listed', async ({
		network,
		operatePreviewProcessInstancePage,
		makeAxeBuilder,
	}) => {
		network.use(
			mockQueryJobsEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({
						items: [createListenerJob({jobKey: '100', type: 'audit-listener'})],
						page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
		);
		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
		await operatePreviewProcessInstancePage.tab('Listeners').click();
		await expect(operatePreviewProcessInstancePage.listenersList).toBeVisible();

		const results = await makeAxeBuilder().analyze();
		expect(results.violations).toEqual([]);
	});
});

test.describe('Operations log tab', () => {
	test.beforeEach(({network}) => {
		network.use(...processInstanceHeaderHandlers(createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})));
	});

	test('should have no accessibility violations with operations listed', async ({
		network,
		operatePreviewProcessInstancePage,
		makeAxeBuilder,
	}) => {
		network.use(
			mockQueryAuditLogsEndpoint({
				successResponse: HttpResponse.json(createQueryAuditLogsResponse({items: [createAuditLog()]})),
			}),
		);
		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
		await operatePreviewProcessInstancePage.tab('Operations Log').click();
		await expect(operatePreviewProcessInstancePage.operationsLogTable).toBeVisible();

		const results = await makeAxeBuilder().analyze();
		expect(results.violations).toEqual([]);
	});
});
