/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';
import {mockQueryVariablesEndpoint} from '#/shared-test-modules/mock-handlers';
import {createQueryVariablesByUserTaskResponse, createVariable} from '#/shared-test-modules/api-mocks/variables';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {
	createElementInstance,
	createQueryElementInstancesResponse,
} from '#/shared-test-modules/api-mocks/element-instances';
import {mockQueryElementInstancesEndpoint} from '#/shared-test-modules/mock-handlers';
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
						createVariable({
							name: 'password',
							value: 'secret',
							variableKey: '3',
							metadata: {protectionModes: ['REDACT']},
						}),
					],
				}),
			),
		}),
	);

	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

	await expect(operatePreviewProcessInstancePage.variableRow('password')).toContainText('[REDACTED]');
	await expect(operatePreviewProcessInstancePage.variablesList).toHaveScreenshot();
});

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
