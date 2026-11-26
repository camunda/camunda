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
	mockQueryProcessDefinitionsEndpoint,
	mockQueryProcessInstancesEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
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
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import type {Page} from '@playwright/test';
import type {OperatePreviewProcessesPage} from '#/pages/operate/OperatePreviewProcesses.page';
import {batchMoveScenarioHandlers, defaultOperatePreviewProcessesHandlers} from './operate-preview-processes.handlers';

const BATCH_MOVE_SCHEDULED_TEXT =
	'Move 1 instance from “Review invoice” to “end_event”. Review the modification to confirm it.';

type PlanBatchMoveArgs = {
	page: Page;
	operatePreviewProcessesPage: OperatePreviewProcessesPage;
};

async function planBatchMove({page, operatePreviewProcessesPage}: PlanBatchMoveArgs) {
	await operatePreviewProcessesPage.instancesTable.getByRole('checkbox', {name: 'Select instance 1'}).click();
	await page.getByRole('button', {name: 'Move'}).click();
	await page.getByRole('dialog').getByRole('button', {name: 'Continue'}).click();
	await page.locator('[data-element-id="end_event"]').click();
	await expect(page.getByRole('button', {name: 'Review Modification'})).toBeEnabled();
	await expect(page.getByText(BATCH_MOVE_SCHEDULED_TEXT, {exact: true})).toBeVisible();
}

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

test.use({handlers: [defaultOperatePreviewProcessesHandlers, {scope: 'test'}]});

test.describe('Operate Processes DS preview (/operate-preview/processes)', () => {
	test('should render the filters and the process instances table', async ({operatePreviewProcessesPage}) => {
		await operatePreviewProcessesPage.goto('?active=true');

		await expect(operatePreviewProcessesPage.heading).toBeAttached();
		await expect(operatePreviewProcessesPage.nameFilter).toBeVisible();
		await expect(
			operatePreviewProcessesPage.diagramPanel.getByRole('heading', {name: 'Select a process'}),
		).toBeVisible();
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
		await expect(panel.getByRole('region', {name: 'Diagram', exact: true})).toBeVisible();
		await expect(panel.getByRole('heading', {name: 'Select a process'})).toBeHidden();
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

	test('should show an error state in the table when the search fails and recover on try again', async ({
		network,
		operatePreviewProcessesPage,
	}) => {
		network.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: new HttpResponse(null, {status: 500}),
			}),
		);
		await operatePreviewProcessesPage.goto('?active=true');
		await expect(operatePreviewProcessesPage.instancesTable.getByText("Couldn't fetch data")).toBeVisible({
			timeout: 15000,
		});

		network.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [
							createProcessInstance({processInstanceKey: '2251799813685249', processDefinitionName: 'Order process'}),
						],
					}),
				),
			}),
		);
		await operatePreviewProcessesPage.instancesTable.getByRole('button', {name: 'Try again'}).click();

		await expect(operatePreviewProcessesPage.instancesTable.getByText('Order process')).toBeVisible();
	});

	test('should show a permission state in the table when the search is forbidden', async ({
		network,
		operatePreviewProcessesPage,
	}) => {
		network.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json({}, {status: 403}),
			}),
		);
		await operatePreviewProcessesPage.goto('?active=true');

		await expect(operatePreviewProcessesPage.instancesTable.getByText('You need permission')).toBeVisible({
			timeout: 15000,
		});
		await expect(operatePreviewProcessesPage.instancesTable.getByRole('button', {name: 'Try again'})).toBeVisible();
	});

	test.describe('batch modification mode', () => {
		test.use({
			handlers: [[...batchMoveScenarioHandlers, ...defaultOperatePreviewProcessesHandlers], {scope: 'test'}],
		});

		test('should enter batch modification mode, select a target, and exit', async ({
			operatePreviewProcessesPage,
			page,
		}) => {
			await operatePreviewProcessesPage.goto('?process=my_simple_process&version=1&tenantId=tenant-a&elementId=task-1');

			await operatePreviewProcessesPage.instancesTable.getByRole('checkbox', {name: 'Select instance 1'}).click();
			await page.getByRole('button', {name: 'Move'}).click();
			await page.getByRole('dialog').getByRole('button', {name: 'Continue'}).click();

			await expect(page.getByText('Batch modification mode', {exact: true})).toBeVisible();
			await expect(page.getByRole('button', {name: 'Review Modification'})).toBeDisabled();

			await page.locator('[data-element-id="end_event"]').click();
			await expect(page.getByText(BATCH_MOVE_SCHEDULED_TEXT, {exact: true})).toBeVisible();
			await expect(page.getByRole('button', {name: 'Review Modification'})).toBeEnabled();

			await page.getByRole('button', {name: 'Exit'}).click();
			await page.getByRole('alertdialog').getByRole('button', {name: 'Exit'}).click();

			await expect(page.getByText('Batch modification mode', {exact: true})).toBeHidden();
		});

		test('should keep the planned move when canceling browser back in batch modification mode', async ({
			operatePreviewProcessesPage,
			page,
		}) => {
			await operatePreviewProcessesPage.goto('?process=my_simple_process&version=1&tenantId=tenant-a');
			await page.getByRole('combobox', {name: 'Element', exact: true}).click();
			await page.getByRole('option', {name: 'Review invoice', exact: true}).click();
			await expect.poll(() => new URL(page.url()).searchParams.get('elementId')).toBe('task-1');

			await planBatchMove({page, operatePreviewProcessesPage});

			await page.goBack();
			await expect(page.getByRole('alertdialog')).toBeVisible();
			await page.getByRole('alertdialog').getByRole('button', {name: 'Cancel'}).click();

			await expect(page.getByText('Batch modification mode', {exact: true})).toBeVisible();
			await expect(page.getByRole('button', {name: 'Review Modification'})).toBeEnabled();
			await expect.poll(() => new URL(page.url()).searchParams.get('elementId')).toBe('task-1');
			await expect(page.getByText(BATCH_MOVE_SCHEDULED_TEXT, {exact: true})).toBeVisible();
		});

		test('should discard the planned move when confirming browser back in batch modification mode', async ({
			operatePreviewProcessesPage,
			page,
		}) => {
			await operatePreviewProcessesPage.goto('?process=my_simple_process&version=1&tenantId=tenant-a');
			await page.getByRole('combobox', {name: 'Element', exact: true}).click();
			await page.getByRole('option', {name: 'Review invoice', exact: true}).click();
			await expect.poll(() => new URL(page.url()).searchParams.get('elementId')).toBe('task-1');

			await planBatchMove({page, operatePreviewProcessesPage});

			await page.goBack();
			await expect(page.getByRole('alertdialog')).toBeVisible();
			await page.getByRole('alertdialog').getByRole('button', {name: 'Exit'}).click();

			await expect(page.getByText('Batch modification mode', {exact: true})).toBeHidden();
			await expect(page.getByRole('button', {name: 'Review Modification'})).toBeHidden();
			await expect.poll(() => new URL(page.url()).searchParams.get('elementId')).toBe(null);
			await expect(page.getByText(BATCH_MOVE_SCHEDULED_TEXT, {exact: true})).toBeHidden();
		});

		test('should show exit confirmation when navigating to another page and keep the plan on cancel', async ({
			operatePreviewProcessesPage,
			page,
		}) => {
			await operatePreviewProcessesPage.goto('?process=my_simple_process&version=1&tenantId=tenant-a&elementId=task-1');
			await planBatchMove({page, operatePreviewProcessesPage});

			await page.getByRole('link', {name: 'Dashboard', exact: true}).click();
			await expect(page.getByRole('alertdialog')).toBeVisible();
			await page.getByRole('alertdialog').getByRole('button', {name: 'Cancel'}).click();

			await expect(page.getByText('Batch modification mode', {exact: true})).toBeVisible();
			await expect(page.getByRole('button', {name: 'Review Modification'})).toBeEnabled();
			await expect(page.getByText(BATCH_MOVE_SCHEDULED_TEXT, {exact: true})).toBeVisible();
		});

		test('should keep batch mode active when changing an unlocked state filter', async ({
			operatePreviewProcessesPage,
			page,
		}) => {
			await operatePreviewProcessesPage.goto('?process=my_simple_process&version=1&tenantId=tenant-a&elementId=task-1');
			await planBatchMove({page, operatePreviewProcessesPage});

			await page.getByRole('checkbox', {name: 'Completed'}).click();

			await expect(page.getByRole('alertdialog')).toHaveCount(0);
			await expect(page.getByRole('checkbox', {name: 'Completed'})).toBeChecked();
			await expect(page.getByText('Batch modification mode', {exact: true})).toBeVisible();
			await expect.poll(() => new URL(page.url()).searchParams.get('elementId')).toBe('task-1');
		});

		test('should keep batch mode active when sorting instances table', async ({operatePreviewProcessesPage, page}) => {
			await operatePreviewProcessesPage.goto('?process=my_simple_process&version=1&tenantId=tenant-a&elementId=task-1');
			await planBatchMove({page, operatePreviewProcessesPage});

			await page.getByRole('button', {name: 'Name', exact: true}).click();

			await expect(page.getByRole('alertdialog')).toHaveCount(0);
			await expect(page.getByText('Batch modification mode', {exact: true})).toBeVisible();
			await expect(page.getByRole('button', {name: 'Review Modification'})).toBeEnabled();
			await expect.poll(() => new URL(page.url()).searchParams.get('elementId')).toBe('task-1');
			await expect(page.getByText(BATCH_MOVE_SCHEDULED_TEXT, {exact: true})).toBeVisible();
		});

		test('should keep the planned move when sorting from legacy aliases and going back', async ({
			operatePreviewProcessesPage,
			page,
		}) => {
			await operatePreviewProcessesPage.goto(
				'?processDefinitionId=my_simple_process&processDefinitionVersion=1&tenantId=tenant-a&elementId=task-1',
			);
			await planBatchMove({page, operatePreviewProcessesPage});

			await page.getByRole('button', {name: 'Name', exact: true}).click();
			await page.goBack();

			await expect(page.getByRole('alertdialog')).toHaveCount(0);
			await expect(page.getByText('Batch modification mode', {exact: true})).toBeVisible();
			await expect(page.getByRole('button', {name: 'Review Modification'})).toBeEnabled();
			await expect(page.getByText(BATCH_MOVE_SCHEDULED_TEXT, {exact: true})).toBeVisible();
		});

		test('should keep the planned move for quoted boolean legacy aliases when sorting and going back', async ({
			operatePreviewProcessesPage,
			page,
		}) => {
			await operatePreviewProcessesPage.goto(
				'?processDefinitionId=%22true%22&processDefinitionVersion=1&tenantId=tenant-a&elementId=task-1',
			);
			await planBatchMove({page, operatePreviewProcessesPage});

			await page.getByRole('button', {name: 'Name', exact: true}).click();
			await page.goBack();

			await expect(page.getByRole('alertdialog')).toHaveCount(0);
			await expect(page.getByText('Batch modification mode', {exact: true})).toBeVisible();
			await expect(page.getByRole('button', {name: 'Review Modification'})).toBeEnabled();
			await expect(page.getByText(BATCH_MOVE_SCHEDULED_TEXT, {exact: true})).toBeVisible();
		});

		test('should keep tenant filter enabled and stay in mode when tenant changes', async ({
			network,
			operatePreviewProcessesPage,
			page,
		}) => {
			network.use(
				mockCurrentUserEndpoint({
					successResponse: HttpResponse.json(
						createCurrentUser({
							tenants: [
								{tenantId: 'tenant-a', name: 'Tenant A', description: null},
								{tenantId: 'tenant-b', name: 'Tenant B', description: null},
							],
						}),
					),
				}),
				mockSystemConfigurationEndpoint({
					successResponse: HttpResponse.json(
						createSystemConfiguration({
							components: {active: ['operate']},
							deployment: {
								isMultiTenancyEnabled: true,
								isTenantsApiEnabled: true,
								isWaitStatesEnabled: true,
								maxRequestSize: 0,
							},
						}),
					),
				}),
			);
			await operatePreviewProcessesPage.goto('?process=my_simple_process&version=1&tenantId=tenant-a&elementId=task-1');
			await planBatchMove({page, operatePreviewProcessesPage});

			await expect(page.getByRole('combobox', {name: 'Select a tenant'})).toBeEnabled();
			await page.getByRole('combobox', {name: 'Select a tenant'}).click();
			await page.getByRole('option', {name: 'Tenant B'}).click();

			await expect(page.getByRole('alertdialog')).toHaveCount(0);
			await expect(page.getByText('Batch modification mode', {exact: true})).toBeVisible();
			await expect.poll(() => new URL(page.url()).searchParams.get('tenantId')).toBe('tenant-b');
			await expect(page.getByRole('button', {name: 'Review Modification'})).toBeDisabled();
			await expect(page.getByText(BATCH_MOVE_SCHEDULED_TEXT, {exact: true})).toHaveCount(0);
			await expect.poll(() => new URL(page.url()).searchParams.get('process')).toBe(null);
			await expect.poll(() => new URL(page.url()).searchParams.get('version')).toBe(null);
			await expect.poll(() => new URL(page.url()).searchParams.get('elementId')).toBe(null);
		});
	});
});
