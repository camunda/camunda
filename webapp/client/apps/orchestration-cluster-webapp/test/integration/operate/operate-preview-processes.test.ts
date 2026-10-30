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
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockGetProcessDefinitionStatisticsEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockLicenseEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryProcessInstancesEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {createGetProcessDefinitionStatisticsResponse} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {
	createProcessInstance,
	createQueryProcessInstancesResponse,
} from '#/shared-test-modules/api-mocks/process-instances';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';

const BPMN_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" id="d" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="order" isExecutable="true">
    <bpmn:startEvent id="start" name="Start" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="order">
      <bpmndi:BPMNShape id="start_di" bpmnElement="start">
        <dc:Bounds x="152" y="82" width="36" height="36" />
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`;

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser({authorizedComponents: ['operate']})),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
		}),
		mockLicenseEndpoint({
			successResponse: HttpResponse.json(createLicense()),
		}),
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
		}),
		mockQueryProcessInstancesEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessInstancesResponse({
					items: [
						createProcessInstance({processInstanceKey: '2251799813685249', processDefinitionName: 'Order process'}),
					],
				}),
			),
		}),
		mockQueryBatchOperationItemsEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse({items: []})),
		}),
	);
});

test.describe('Operate Processes DS preview (/operate-preview/processes)', () => {
	test('should render the filters and the process instances table', async ({operatePreviewProcessesPage}) => {
		await operatePreviewProcessesPage.goto('?active=true');

		await expect(operatePreviewProcessesPage.heading).toBeAttached();
		await expect(operatePreviewProcessesPage.nameFilter).toBeVisible();
		await expect(operatePreviewProcessesPage.diagramPanel.getByText('There is no Process selected')).toBeVisible();
		await expect(operatePreviewProcessesPage.instancesTable).toBeVisible();
		await expect(operatePreviewProcessesPage.instancesTable.getByText('Order process')).toBeVisible();
	});

	test('should render the diagram panel header and diagram for a selected process version', async ({
		network,
		operatePreviewProcessesPage,
	}) => {
		network.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessDefinitionsResponse({
						items: [createProcessDefinition({processDefinitionId: 'order', name: 'Order process', version: 1})],
					}),
				),
			}),
			mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
			mockGetProcessDefinitionStatisticsEndpoint({
				successResponse: HttpResponse.json(createGetProcessDefinitionStatisticsResponse([])),
			}),
		);
		await operatePreviewProcessesPage.goto('?active=true&process=order&version=1');

		const panel = operatePreviewProcessesPage.diagramPanel;
		await expect(panel.getByText('Order process')).toBeVisible();
		await expect(panel.getByRole('button', {name: /Process ID/})).toBeVisible();
		await expect(panel.getByTestId('diagram-canvas')).toBeVisible();
		await expect(panel.getByText('There is no Process selected')).toBeHidden();
	});

	test('should keep the Carbon Processes route working', async ({network, operateProcessesPage, page}) => {
		network.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
			}),
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryProcessInstancesResponse()),
			}),
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				successResponse: HttpResponse.json(createPaginatedResponse({items: []})),
			}),
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				successResponse: HttpResponse.json(createPaginatedResponse({items: []})),
			}),
		);
		await operateProcessesPage.goto();

		await expect(page.getByRole('combobox', {name: 'Name', exact: true})).toBeVisible();
	});
});
