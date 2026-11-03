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
	mockGetElementInstanceEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockGetProcessInstanceEndpoint,
	mockQueryAuditLogsEndpoint,
	mockQueryElementInstancesEndpoint,
	mockQueryElementInstanceIncidentsEndpoint,
	mockQueryJobsEndpoint,
	mockQueryProcessInstanceIncidentsEndpoint,
	mockQueryVariablesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {BPMN_XML} from '#/shared-test-modules/api-mocks/process-definition-xmls';
import {createAuditLog, createQueryAuditLogsResponse} from '#/shared-test-modules/api-mocks/audit-logs';
import {createListenerJob} from '#/shared-test-modules/api-mocks/jobs';
import {createIncident} from '#/shared-test-modules/api-mocks/incidents';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {createQueryVariablesByUserTaskResponse, createVariable} from '#/shared-test-modules/api-mocks/variables';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {
	createElementInstance,
	createQueryElementInstancesResponse,
} from '#/shared-test-modules/api-mocks/element-instances';
import {processInstanceHeaderHandlers} from '#/shared-test-modules/process-instance-header-handlers';
import {processInstanceDiagramHandlers} from '#/shared-test-modules/process-instance-diagram-handlers';

const PROCESS_INSTANCE_ID = '2251799813685280';

const HISTORY_ITEMS = [
	createElementInstance({
		elementId: 'start',
		elementInstanceKey: '2251799813685281',
		elementName: 'Start',
		type: 'START_EVENT',
		state: 'COMPLETED',
	}),
	createElementInstance({
		elementId: 'task-1',
		elementInstanceKey: '2251799813685290',
		elementName: 'Task 1',
		type: 'SERVICE_TASK',
		state: 'ACTIVE',
	}),
];

test.beforeEach(({network}) => {
	network.use(
		mockQueryElementInstancesEndpoint({
			successResponse: HttpResponse.json(createQueryElementInstancesResponse(HISTORY_ITEMS)),
		}),
		...processInstanceDiagramHandlers(),
		...processInstanceHeaderHandlers(createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})),
	);
});

test('should match the process instance diagram panel snapshot with state overlays', async ({
	operatePreviewProcessInstancePage,
}) => {
	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

	await expect(operatePreviewProcessInstancePage.stateOverlay('Review invoice, Active: 1')).toBeVisible();
	await expect(operatePreviewProcessInstancePage.diagramPanel).toHaveScreenshot();
});

test('should match the process instance diagram panel snapshot with a selected element', async ({
	operatePreviewProcessInstancePage,
	page,
}) => {
	await page.goto(`/operate-preview/processes/${PROCESS_INSTANCE_ID}/variables?elementId=task-1`);

	await expect(operatePreviewProcessInstancePage.stateOverlay('Review invoice, Active: 1')).toBeVisible();
	await expect(operatePreviewProcessInstancePage.diagramPanel).toHaveScreenshot();
});

test('should match the process instance variables tab snapshot', async ({
	network,
	operatePreviewProcessInstancePage,
}) => {
	network.use(
		mockQueryVariablesEndpoint({
			successResponse: HttpResponse.json(
				createQueryVariablesByUserTaskResponse({
					items: [
						createVariable({name: 'amount', value: '100', variableKey: '1'}),
						createVariable({name: 'customer', value: '{"name":"Jane","address":{"city":"Berlin"}}', variableKey: '2'}),
					],
				}),
			),
		}),
	);

	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

	await expect(operatePreviewProcessInstancePage.variableRow('customer')).toBeVisible();
	await expect(operatePreviewProcessInstancePage.variablesList).toHaveScreenshot();
});

test.describe('incidents tab', () => {
	const LONG_ERROR_MESSAGE =
		"Expected result of the expression '=orderTotal * (1 + taxRate)' to be 'NUMBER', but was 'STRING'. The evaluation reported: failed to evaluate expression 'orderTotal * (1 + taxRate)': no variable found for name 'taxRate' while resolving the tax for the order.";
	const LONG_ELEMENT_ID = 'calculate-order-total-including-regional-taxes-and-shipping';
	const incident = createIncident({
		errorType: 'EXTRACT_VALUE_ERROR',
		errorMessage: LONG_ERROR_MESSAGE,
		elementId: LONG_ELEMENT_ID,
	});

	test.beforeEach(async ({network, operatePreviewProcessInstancePage}) => {
		const incidents = HttpResponse.json(
			createPaginatedResponse({
				items: [
					incident,
					createIncident({
						incidentKey: '2251799813685292',
						elementInstanceKey: '2251799813685293',
						elementId: 'task-2',
						errorMessage: 'No more retries left.',
					}),
				],
				page: {totalItems: 2, startCursor: null, endCursor: null, hasMoreTotalItems: false},
			}),
		);
		network.use(
			mockGetProcessInstanceEndpoint({
				successResponse: HttpResponse.json(
					createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID, hasIncident: true}),
				),
			}),
			mockQueryProcessInstanceIncidentsEndpoint({successResponse: incidents}),
			mockQueryElementInstanceIncidentsEndpoint({successResponse: incidents}),
			mockGetElementInstanceEndpoint({
				successResponse: HttpResponse.json(
					createElementInstance({
						elementInstanceKey: incident.elementInstanceKey,
						elementId: incident.elementId,
						processInstanceKey: PROCESS_INSTANCE_ID,
					}),
				),
			}),
		);

		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
		await operatePreviewProcessInstancePage.incidentsTab.click();
		await expect(operatePreviewProcessInstancePage.incidentsTable.getByRole('row')).toHaveCount(3);
	});

	test('should match the incidents tab snapshot with a selected row', async ({operatePreviewProcessInstancePage}) => {
		const row = operatePreviewProcessInstancePage.incidentsTable.getByRole('row').filter({hasText: 'Expected result'});

		await row.click();

		await expect(row).toContainText('Selected');
		await expect(operatePreviewProcessInstancePage.incidentsList).toHaveScreenshot();
	});

	test('should match the incidents tab snapshot with an expanded row', async ({operatePreviewProcessInstancePage}) => {
		const row = operatePreviewProcessInstancePage.incidentsTable.getByRole('row').filter({hasText: 'Expected result'});

		await row.getByRole('button', {name: 'Expand row'}).click();

		await expect(operatePreviewProcessInstancePage.incidentsTable.getByText('Job ID')).toBeVisible();
		await expect(operatePreviewProcessInstancePage.incidentsList).toHaveScreenshot();
	});
});

test('should match the process instance listeners tab snapshot', async ({
	network,
	operatePreviewProcessInstancePage,
}) => {
	network.use(
		mockQueryJobsEndpoint({
			successResponse: HttpResponse.json(
				createPaginatedResponse({
					items: [
						createListenerJob({jobKey: '100', type: 'audit-listener'}),
						createListenerJob({jobKey: '101', type: 'notify-listener', state: 'FAILED'}),
					],
					page: {totalItems: 2, startCursor: null, endCursor: null, hasMoreTotalItems: false},
				}),
			),
		}),
	);

	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
	await operatePreviewProcessInstancePage.tab('Listeners').click();

	await expect(operatePreviewProcessInstancePage.listenersList.getByText('audit-listener')).toBeVisible();
	await expect(operatePreviewProcessInstancePage.tabPanel).toHaveScreenshot();
});

test('should match the process instance operations log tab snapshot', async ({
	network,
	operatePreviewProcessInstancePage,
}) => {
	network.use(
		mockQueryAuditLogsEndpoint({
			successResponse: HttpResponse.json(createQueryAuditLogsResponse({items: [createAuditLog()]})),
		}),
	);

	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
	await operatePreviewProcessInstancePage.tab('Operations Log').click();

	await expect(operatePreviewProcessInstancePage.operationsLogTable.getByRole('cell', {name: 'Create'})).toBeVisible();
	await expect(operatePreviewProcessInstancePage.tabPanel).toHaveScreenshot();
});

test('should match the process instance input mappings tab snapshot', async ({
	network,
	page,
	operatePreviewProcessInstancePage,
}) => {
	network.use(
		mockGetProcessDefinitionXmlEndpoint({
			successResponse: HttpResponse.text(
				BPMN_XML.replace(
					'<zeebe:userTask />',
					'<zeebe:userTask /><zeebe:ioMapping><zeebe:input source="=orderId" target="localOrderId" /><zeebe:input source="=amount * 2" target="doubledAmount" /></zeebe:ioMapping>',
				),
			),
		}),
	);

	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID, '/input-mappings?elementId=task-1');

	await expect(page.getByRole('cell', {name: 'localOrderId'})).toBeVisible();
	await expect(operatePreviewProcessInstancePage.tabPanel).toHaveScreenshot();

test('should match the instance history panel snapshot', async ({operatePreviewProcessInstancePage}) => {
	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

	await expect(operatePreviewProcessInstancePage.historyItem('Task 1')).toBeVisible();
	await expect(operatePreviewProcessInstancePage.historyPanel).toHaveScreenshot();
});

test('should match the filtered instance history panel snapshot', async ({operatePreviewProcessInstancePage, page}) => {
	await page.goto(`/operate-preview/processes/${PROCESS_INSTANCE_ID}/variables?elementSearch=Task`);

	await expect(operatePreviewProcessInstancePage.historyItem('Task 1')).toBeVisible();
	await expect(operatePreviewProcessInstancePage.historyPanel).toHaveScreenshot();
});

test('should match the mobile instance history tab snapshot', async ({page}) => {
	await page.setViewportSize({width: 800, height: 900});
	await page.goto(`/operate-preview/processes/${PROCESS_INSTANCE_ID}/history`);

	await expect(
		page.getByRole('tree', {name: 'Instance History'}).getByRole('treeitem', {name: 'Task 1'}),
	).toBeVisible();
	await expect(page.getByRole('main')).toHaveScreenshot();
});

test('should match the process instance output mappings tab snapshot', async ({
	network,
	page,
	operatePreviewProcessInstancePage,
}) => {
	network.use(
		mockGetProcessDefinitionXmlEndpoint({
			successResponse: HttpResponse.text(
				BPMN_XML.replace(
					'<zeebe:userTask />',
					'<zeebe:userTask /><zeebe:ioMapping><zeebe:output source="=result" target="processResult" /><zeebe:output source="=total * 2" target="doubledTotal" /></zeebe:ioMapping>',
				),
			),
		}),
	);

	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID, '/output-mappings?elementId=task-1');

	await expect(page.getByRole('cell', {name: 'processResult'})).toBeVisible();
	await expect(operatePreviewProcessInstancePage.tabPanel).toHaveScreenshot();
});
