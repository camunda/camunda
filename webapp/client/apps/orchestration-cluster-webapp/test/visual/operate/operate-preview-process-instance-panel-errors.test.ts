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
	mockGetProcessDefinitionXmlEndpoint,
	mockGetProcessInstanceEndpoint,
	mockQueryElementInstancesEndpoint,
	mockQueryProcessInstanceIncidentsEndpoint,
	mockQueryVariablesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {
	PROCESS_INSTANCE_ID,
	processInstancePanelErrorBaseHandlers,
} from '../../fixtures/operate/processInstancePanelErrors';

test.use({handlers: [processInstancePanelErrorBaseHandlers, {scope: 'test'}]});

test('should match the diagram panel snapshot when the diagram fails to load', async ({
	network,
	operatePreviewProcessInstancePage,
}) => {
	network.use(mockGetProcessDefinitionXmlEndpoint({successResponse: new HttpResponse(null, {status: 500})}));

	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

	await expect(operatePreviewProcessInstancePage.diagramPanel.getByText("Couldn't fetch data")).toBeVisible({
		timeout: 20_000,
	});
	await expect(operatePreviewProcessInstancePage.diagramPanel).toHaveScreenshot();
});

test('should match the VariablesError snapshot when variables fail to load', async ({
	network,
	operatePreviewProcessInstancePage,
}) => {
	network.use(mockQueryVariablesEndpoint({successResponse: new HttpResponse(null, {status: 500})}));

	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

	await expect(operatePreviewProcessInstancePage.tabPanel.getByText('Variables could not be fetched')).toBeVisible({
		timeout: 20_000,
	});
	await expect(operatePreviewProcessInstancePage.tabPanel).toHaveScreenshot();
});

test('should match the incidents panel snapshot when incidents fail to load', async ({
	network,
	operatePreviewProcessInstancePage,
}) => {
	network.use(
		mockGetProcessInstanceEndpoint({
			successResponse: HttpResponse.json(
				createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID, hasIncident: true, state: 'COMPLETED'}),
			),
		}),
		mockQueryProcessInstanceIncidentsEndpoint({successResponse: new HttpResponse(null, {status: 500})}),
	);

	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
	await operatePreviewProcessInstancePage.incidentsTab.click();

	await expect(operatePreviewProcessInstancePage.incidentsTab).toHaveAttribute('aria-selected', 'true');
	await expect(operatePreviewProcessInstancePage.incidentsList.getByText("Couldn't fetch data")).toBeVisible({
		timeout: 20_000,
	});
	await expect(operatePreviewProcessInstancePage.incidentsList).toHaveScreenshot();
});

test(
	'should match the element history panel snapshot when history fails to load',
	{tag: '@desktop'},
	async ({network, operatePreviewProcessInstancePage}) => {
		network.use(mockQueryElementInstancesEndpoint({successResponse: new HttpResponse(null, {status: 500})}));

		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

		await expect(
			operatePreviewProcessInstancePage.historyPanel.getByText(
				'Instance history could not be fetched. Please refresh.',
			),
		).toBeVisible();
		await expect(operatePreviewProcessInstancePage.historyPanel).toHaveScreenshot();
	},
);
