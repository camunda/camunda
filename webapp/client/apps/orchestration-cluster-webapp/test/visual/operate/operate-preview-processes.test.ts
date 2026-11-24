/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';
import {queryProcessDefinitionsRequestBodySchema} from '@camunda/camunda-api-zod-schemas/8.11';
import {
	mockCurrentUserEndpoint,
	mockLicenseEndpoint,
	mockGetProcessDefinitionStatisticsEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
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
import {
	createProcessInstance,
	createQueryProcessInstancesResponse,
} from '#/shared-test-modules/api-mocks/process-instances';
import {
	createGetProcessDefinitionStatisticsResponse,
	createProcessDefinitionStatistic,
} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {BPMN_XML as MOVE_BPMN_XML} from '#/shared-test-modules/api-mocks/process-definition-xmls';
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
		mockQueryBatchOperationItemsEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse({items: []})),
		}),
	);
});

test('should match the instances table snapshot with active, incident, and completed instances', async ({
	network,
	operatePreviewProcessesPage,
	page,
}) => {
	network.use(
		mockQueryProcessInstancesEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessInstancesResponse({
					items: [
						createProcessInstance({processInstanceKey: '2251799813700001', processDefinitionName: 'Order process'}),
						createProcessInstance({
							processInstanceKey: '2251799813700002',
							processDefinitionName: 'Shipping process',
							hasIncident: true,
							parentProcessInstanceKey: '2251799813700001',
						}),
						createProcessInstance({
							processInstanceKey: '2251799813700003',
							processDefinitionName: 'Invoice process',
							state: 'COMPLETED',
							endDate: '2026-01-16T10:00:00.000Z',
						}),
					],
				}),
			),
		}),
	);
	await page.goto(`${operatePreviewProcessesPage.processesUrl}?active=true&incidents=true&completed=true`);

	await expect(
		operatePreviewProcessesPage.instancesTable.getByRole('link', {name: 'View instance 2251799813700001'}),
	).toBeVisible();
	await expect(operatePreviewProcessesPage.instancesTable).toHaveScreenshot();
});

test('should match the instances table snapshot when no instances match', async ({
	network,
	operatePreviewProcessesPage,
	page,
}) => {
	network.use(
		mockQueryProcessInstancesEndpoint({
			successResponse: HttpResponse.json(createQueryProcessInstancesResponse({items: []})),
		}),
	);
	await page.goto(`${operatePreviewProcessesPage.processesUrl}?active=true`);

	await expect(operatePreviewProcessesPage.instancesTable.getByText(/No instances match these filters/)).toBeVisible();
	await expect(operatePreviewProcessesPage.instancesTable).toHaveScreenshot();
});

test('should match the instances table snapshot with a selected instance and the bulk actions bar', async ({
	network,
	operatePreviewProcessesPage,
	page,
}) => {
	network.use(
		mockQueryProcessInstancesEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessInstancesResponse({
					items: [
						createProcessInstance({processInstanceKey: '2251799813700001', processDefinitionName: 'Order process'}),
						createProcessInstance({
							processInstanceKey: '2251799813700002',
							processDefinitionName: 'Shipping process',
							hasIncident: true,
						}),
					],
				}),
			),
		}),
	);
	await page.goto(`${operatePreviewProcessesPage.processesUrl}?active=true&incidents=true`);

	await operatePreviewProcessesPage.instancesTable
		.getByRole('checkbox', {name: 'Select instance 2251799813700002'})
		.click({force: true});

	await expect(operatePreviewProcessesPage.instancesTable.getByText('1 instance selected')).toBeVisible();
	await expect(operatePreviewProcessesPage.instancesTable).toHaveScreenshot();
});

test('should match the diagram panel header snapshot with a long process name and the delete action', async ({
	network,
	operatePreviewProcessesPage,
	page,
}) => {
	network.use(
		mockQueryProcessDefinitionsEndpoint({
			schema: queryProcessDefinitionsRequestBodySchema.refine(({filter}) => filter?.state !== 'DRAINING'),
			successResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({
					items: [
						createProcessDefinition({
							processDefinitionId: 'order',
							name: 'Order fulfilment and shipment coordination process with an exceptionally long display name',
							version: 1,
						}),
					],
				}),
			),
			failureResponse: HttpResponse.json(createQueryProcessDefinitionsResponse({items: []})),
		}),
		mockQueryProcessInstancesEndpoint({
			successResponse: HttpResponse.json(createQueryProcessInstancesResponse({items: []})),
		}),
		mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
		mockGetProcessDefinitionStatisticsEndpoint({
			successResponse: HttpResponse.json(createGetProcessDefinitionStatisticsResponse([])),
		}),
	);
	await page.goto(`${operatePreviewProcessesPage.processesUrl}?active=true&process=order&version=1`);

	const panel = operatePreviewProcessesPage.diagramPanel;
	await expect(panel.getByRole('region', {name: 'Diagram', exact: true})).toBeVisible();
	await expect(panel.getByRole('button', {name: /Delete Process Definition/})).toBeVisible();
	await expect(panel.locator('header')).toHaveScreenshot();
});

test('should match the instances table snapshot when the search fails', async ({
	network,
	operatePreviewProcessesPage,
	page,
}) => {
	network.use(
		mockQueryProcessInstancesEndpoint({
			successResponse: new HttpResponse(null, {status: 500}),
		}),
	);
	await page.goto(`${operatePreviewProcessesPage.processesUrl}?active=true`);

	await expect(operatePreviewProcessesPage.instancesTable.getByText("Couldn't fetch data")).toBeVisible({
		timeout: 15000,
	});
	await expect(operatePreviewProcessesPage.instancesTable).toHaveScreenshot();
});

test('should match the instances table snapshot when the search is forbidden', async ({
	network,
	operatePreviewProcessesPage,
	page,
}) => {
	network.use(
		mockQueryProcessInstancesEndpoint({
			successResponse: HttpResponse.json({}, {status: 403}),
		}),
	);
	await page.goto(`${operatePreviewProcessesPage.processesUrl}?active=true`);

	await expect(operatePreviewProcessesPage.instancesTable.getByText('You need permission')).toBeVisible({
		timeout: 15000,
	});
	await expect(operatePreviewProcessesPage.instancesTable).toHaveScreenshot();
});

test.describe('batch move mode', () => {
	test.beforeEach(async ({network, operatePreviewProcessesPage, page}) => {
		network.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessDefinitionsResponse({
						items: [
							createProcessDefinition({
								processDefinitionId: 'my_simple_process',
								tenantId: 'tenant-a',
								name: 'Invoice process',
								version: 1,
							}),
						],
					}),
				),
			}),
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [createProcessInstance({processInstanceKey: '2251799813700001', state: 'ACTIVE'})],
					}),
				),
			}),
			mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(MOVE_BPMN_XML)}),
			mockGetProcessDefinitionStatisticsEndpoint({
				successResponse: HttpResponse.json(
					createGetProcessDefinitionStatisticsResponse([
						createProcessDefinitionStatistic({elementId: 'task-1', active: 1}),
					]),
				),
			}),
		);
		await page.goto(
			`${operatePreviewProcessesPage.processesUrl}?process=my_simple_process&version=1&tenantId=tenant-a&elementId=task-1`,
		);
		await operatePreviewProcessesPage.instancesTable
			.getByRole('checkbox', {name: 'Select instance 2251799813700001'})
			.click();
		await page.getByRole('button', {name: 'Move'}).click();
	});

	test('should match the move helper dialog snapshot', async ({page}) => {
		const dialog = page.getByRole('dialog');

		await expect(dialog.getByRole('button', {name: 'Continue'})).toBeVisible();
		await expect(dialog).toHaveScreenshot();
	});

	test('should match the batch modification mode snapshot with a target selected', async ({
		operatePreviewProcessesPage,
		page,
	}) => {
		await page.getByRole('dialog').getByRole('button', {name: 'Continue'}).click();
		await page.locator('[data-element-id="end_event"]').click();

		await expect(page.getByRole('button', {name: 'Review Modification'})).toBeEnabled();
		await expect(
			operatePreviewProcessesPage.diagramPanel.getByRole('img', {name: 'end_event, 1 added', exact: true}),
		).toBeVisible();
		await expect(page).toHaveScreenshot();
	});

	test('should match the exit confirmation dialog snapshot', async ({page}) => {
		await page.getByRole('dialog').getByRole('button', {name: 'Continue'}).click();
		await page.getByRole('button', {name: 'Exit'}).click();
		const dialog = page.getByRole('alertdialog');

		await expect(dialog.getByRole('button', {name: 'Exit'})).toBeVisible();
		await expect(dialog).toHaveScreenshot();
	});
});
