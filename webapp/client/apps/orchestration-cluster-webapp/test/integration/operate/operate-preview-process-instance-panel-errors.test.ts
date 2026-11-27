/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import type {Request} from '@playwright/test';
import {HttpResponse} from 'msw';
import {
	mockGetProcessDefinitionXmlEndpoint,
	mockGetProcessInstanceEndpoint,
	mockQueryElementInstancesEndpoint,
	mockQueryProcessInstanceIncidentsEndpoint,
	mockQueryVariablesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {createQueryVariablesByUserTaskResponse, createVariable} from '#/shared-test-modules/api-mocks/variables';
import {createIncident} from '#/shared-test-modules/api-mocks/incidents';
import {
	createElementInstance,
	createQueryElementInstancesResponse,
} from '#/shared-test-modules/api-mocks/element-instances';
import {BPMN_XML} from '#/shared-test-modules/api-mocks/process-definition-xmls';
import {
	PROCESS_INSTANCE_ID,
	processInstancePanelErrorBaseHandlers,
} from '../../fixtures/operate/processInstancePanelErrors';

test.use({handlers: [processInstancePanelErrorBaseHandlers, {scope: 'test'}]});

function isIncidentsListRequest(request: Request) {
	const payload = request.postDataJSON() as {page?: {limit?: number}; filter?: {state?: string}};
	return (
		request.method() === 'POST' &&
		request.url().includes(`/v2/process-instances/${PROCESS_INSTANCE_ID}/incidents`) &&
		payload.page?.limit !== 0
	);
}

test.describe('Operate Process Instance DS preview panel errors', () => {
	test('should recover the diagram panel when retrying after a failed load', async ({
		network,
		operatePreviewProcessInstancePage,
	}) => {
		network.use(mockGetProcessDefinitionXmlEndpoint({successResponse: new HttpResponse(null, {status: 500})}));

		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
		await expect(operatePreviewProcessInstancePage.diagramPanel.getByText("Couldn't fetch data")).toBeVisible({
			timeout: 20_000,
		});
		const retryButton = operatePreviewProcessInstancePage.diagramPanel.getByRole('button', {name: 'Try again'});

		network.use(mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}));
		await retryButton.click();

		await expect(operatePreviewProcessInstancePage.diagram).toBeVisible();
	});

	test('should recover the VariablesError panel when retrying after a failed load', async ({
		network,
		operatePreviewProcessInstancePage,
	}) => {
		network.use(mockQueryVariablesEndpoint({successResponse: new HttpResponse(null, {status: 500})}));

		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
		await expect(operatePreviewProcessInstancePage.tabPanel.getByText('Variables could not be fetched')).toBeVisible({
			timeout: 20_000,
		});
		const retryButton = operatePreviewProcessInstancePage.tabPanel.getByRole('button', {name: 'Try again'});

		network.use(
			mockQueryVariablesEndpoint({
				successResponse: HttpResponse.json(
					createQueryVariablesByUserTaskResponse({
						items: [createVariable({name: 'amount', value: '100', variableKey: '1'})],
					}),
				),
			}),
		);
		await retryButton.click();

		await expect(operatePreviewProcessInstancePage.variableRow('amount')).toContainText('100');
	});

	test('should recover the incidents panel when retrying after a failed load', async ({
		network,
		page,
		operatePreviewProcessInstancePage,
	}) => {
		const incident = createIncident({errorMessage: 'Payment failed'});
		const incidents = HttpResponse.json(
			createPaginatedResponse({
				items: [incident],
				page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
			}),
		);

		network.use(
			mockGetProcessInstanceEndpoint({
				successResponse: HttpResponse.json(
					createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID, hasIncident: true}),
				),
			}),
			mockQueryProcessInstanceIncidentsEndpoint({successResponse: new HttpResponse(null, {status: 500})}),
		);
		await page.clock.install();

		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
		await expect(operatePreviewProcessInstancePage.incidentsTab).toBeVisible();
		await operatePreviewProcessInstancePage.incidentsTab.click();
		await expect(operatePreviewProcessInstancePage.incidentsTab).toHaveAttribute('aria-selected', 'true');
		await expect(operatePreviewProcessInstancePage.incidentsList.getByText("Couldn't fetch data")).toBeVisible({
			timeout: 20_000,
		});
		await page.clock.pauseAt(new Date());
		const retryButton = operatePreviewProcessInstancePage.incidentsList.getByRole('button', {name: 'Try again'});
		const retryRequest = page.waitForRequest(isIncidentsListRequest);
		network.use(mockQueryProcessInstanceIncidentsEndpoint({successResponse: incidents}));
		await retryButton.click();
		await retryRequest;
		await page.clock.fastForward('00:00:04');

		await expect(
			operatePreviewProcessInstancePage.incidentsTable.getByRole('row').filter({hasText: 'Payment failed'}),
		).toBeVisible();
	});

	test('should recover the element history panel when retrying after a failed load', async ({
		network,
		operatePreviewProcessInstancePage,
	}) => {
		network.use(
			mockGetProcessInstanceEndpoint({
				successResponse: HttpResponse.json(
					createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID, state: 'COMPLETED'}),
				),
			}),
			mockQueryElementInstancesEndpoint({successResponse: new HttpResponse(null, {status: 500})}),
		);

		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

		const retryButton = operatePreviewProcessInstancePage.historyPanel.getByRole('button', {name: 'Try again'});
		await expect(
			operatePreviewProcessInstancePage.historyPanel.getByText(
				'Instance history could not be fetched. Please refresh.',
			),
		).toBeVisible();

		network.use(
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryElementInstancesResponse([createElementInstance({elementId: 'task-1', elementName: 'Task 1'})]),
				),
			}),
		);
		await retryButton.click();

		await expect(operatePreviewProcessInstancePage.historyItem('Task 1')).toBeVisible();
	});
});
