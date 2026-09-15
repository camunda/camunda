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
	mockLicenseEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryProcessInstancesEndpoint,
	mockSystemConfigurationEndpoint,
	mockCreateCancellationBatchOperationEndpoint,
	mockGetBatchOperationEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockGetProcessInstanceCallHierarchyEndpoint,
	mockGetProcessInstanceEndpoint,
	mockGetProcessInstanceWaitStateStatisticsEndpoint,
	mockQueryProcessInstanceIncidentsEndpoint,
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
import {createCallHierarchy} from '#/shared-test-modules/api-mocks/call-hierarchy';
import {
	createBatchOperationItem,
	createBatchOperation,
	createQueryBatchOperationItemsResponse,
} from '#/shared-test-modules/api-mocks/batch-operations';
import {createPaginatedResponse, createProblemDetails} from '#/shared-test-modules/api-mocks/shared';

const PROCESS_INSTANCE_XML =
	'<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="order-process"><callActivity id="call-activity" /></process></definitions>';

function getProcessInstanceShellHandlers({
	processInstance = createProcessInstance({
		processInstanceKey: '1001',
		processDefinitionName: 'Order Process',
		hasIncident: false,
	}),
	callHierarchy = [],
}: {
	processInstance?: ReturnType<typeof createProcessInstance>;
	callHierarchy?: ReturnType<typeof createCallHierarchy>[];
} = {}) {
	return [
		mockGetProcessInstanceEndpoint({
			successResponse: HttpResponse.json(processInstance),
		}),
		mockGetProcessInstanceCallHierarchyEndpoint({
			successResponse: HttpResponse.json(callHierarchy),
		}),
		mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(PROCESS_INSTANCE_XML)}),
	] as const;
}

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser()),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
		}),
		mockLicenseEndpoint({
			successResponse: HttpResponse.json(createLicense()),
		}),
		mockGetProcessDefinitionInstanceStatisticsEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
		mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({
					items: [createProcessDefinition({name: 'Order Process', processDefinitionId: 'order-process'})],
				}),
			),
		}),
		mockQueryBatchOperationItemsEndpoint({
			successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse()),
		}),
		mockQueryProcessInstancesEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessInstancesResponse({
					items: [
						createProcessInstance({processInstanceKey: '1001', processDefinitionName: 'Order Process'}),
						createProcessInstance({processInstanceKey: '1002', processDefinitionName: 'Payment Process'}),
					],
				}),
			),
		}),
		mockGetProcessInstanceWaitStateStatisticsEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
	);
});

test.describe('Operate processes page', () => {
	test('should cancel all matching instances and discard selection after acceptance', async ({
		network,
		page,
		operateProcessesPage,
	}) => {
		network.use(
			mockCreateCancellationBatchOperationEndpoint({
				successResponse: HttpResponse.json(
					{batchOperationKey: 'batch-op-1', batchOperationType: 'CANCEL_PROCESS_INSTANCE'},
					{status: 202},
				),
			}),
			mockGetBatchOperationEndpoint({successResponse: HttpResponse.json(createBatchOperation())}),
		);
		await operateProcessesPage.goto();
		await page.getByRole('checkbox', {name: 'Select all items'}).check({force: true});
		await page.getByRole('button', {name: 'Cancel', exact: true}).click();
		await expect(page.getByRole('dialog')).toContainText(
			'In case there are called instances, these will be canceled too.',
		);
		const submitted = page.waitForRequest(
			(request) => request.method() === 'POST' && request.url().endsWith('/v2/process-instances/cancellation'),
		);
		await page.getByRole('dialog').getByRole('button', {name: 'Apply'}).click();
		expect((await submitted).postDataJSON()).toEqual({
			filter: {
				$or: [
					{state: {$eq: 'ACTIVE'}, hasIncident: false},
					{state: {$eq: 'SUSPENDED'}},
					{hasIncident: true, state: {$neq: 'SUSPENDED'}},
				],
			},
		});
		await expect(page.getByText('The batch operation "Cancel Process Instance" has been started')).toBeVisible();
		await expect(page.getByRole('checkbox', {name: 'Select all items'})).not.toBeChecked();
	});

	test('should render the filters panel with the process combobox', async ({operateProcessesPage}) => {
		await operateProcessesPage.goto();

		await expect(operateProcessesPage.filtersPanel).toBeVisible();
		await expect(operateProcessesPage.processCombobox).toBeVisible();
	});

	test('should render the reset filters button disabled by default', async ({operateProcessesPage}) => {
		await operateProcessesPage.goto();

		await expect(operateProcessesPage.resetFiltersButton).toBeDisabled();
	});

	test('should list the matching process instances and link each one to its details page', async ({
		network,
		page,
		operateProcessesPage,
	}) => {
		network.use(...getProcessInstanceShellHandlers());
		await operateProcessesPage.goto();

		await expect(operateProcessesPage.instancesTable).toBeVisible();
		await expect(operateProcessesPage.instanceLink('1001')).toHaveAttribute('href', '/operate/processes/1001');
		await expect(operateProcessesPage.instanceLink('1002')).toHaveAttribute('href', '/operate/processes/1002');
		await operateProcessesPage.instanceLink('1001').click();
		await expect(page).toHaveURL('/operate/processes/1001/variables');
		await expect(page.getByRole('heading', {name: 'Operate Process Instance'})).toBeAttached();
		await expect(page.getByRole('cell', {name: '1001'})).toBeVisible();
	});

	test('should show operation states when filtering by a batch operation', async ({network, operateProcessesPage}) => {
		network.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(
					createQueryBatchOperationItemsResponse({
						items: [createBatchOperationItem({processInstanceKey: '1001', state: 'ACTIVE'})],
					}),
				),
			}),
		);

		await operateProcessesPage.goto('?batchOperationKey=2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee');

		await expect(operateProcessesPage.operationStateColumn).toBeVisible();
		await expect(operateProcessesPage.operationState('ACTIVE')).toBeVisible();
	});

	test('should render forbidden process-instance content when the instance request returns 403', async ({
		network,
		page,
	}) => {
		network.use(
			mockGetProcessInstanceEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 403}), {status: 403}),
			}),
		);

		await page.goto('/operate/processes/1001');

		await expect(page).toHaveURL('/operate/processes/1001');
		await expect(page.getByText('403 - You do not have permission to view this information')).toBeVisible({
			timeout: 15000,
		});
		await expect(page.getByText('Contact your administrator to get access.')).toBeVisible();
		await expect(page.getByRole('link', {name: 'Learn more about permissions'})).toHaveAttribute(
			'href',
			'https://docs.camunda.io/docs/self-managed/operate-deployment/operate-authentication/#resource-based-permissions',
		);
	});

	test('should show the incident count fetched from the incidents endpoint', async ({
		network,
		page,
		operateProcessesPage,
	}) => {
		network.use(
			mockQueryProcessInstanceIncidentsEndpoint({
				successResponse: HttpResponse.json({
					items: [],
					page: {totalItems: 4, startCursor: null, endCursor: null, hasMoreTotalItems: false},
				}),
			}),
			...getProcessInstanceShellHandlers({
				processInstance: createProcessInstance({
					processInstanceKey: '1001',
					processDefinitionName: 'Order Process',
					hasIncident: true,
				}),
			}),
		);

		await operateProcessesPage.goto();
		await operateProcessesPage.instanceLink('1001').click();

		await expect(page).toHaveURL('/operate/processes/1001/incidents');
		await expect(page.getByText('4 incidents')).toBeVisible();
	});

	test('should redirect unknown process-instance child paths to the default tab while preserving search params', async ({
		network,
		page,
	}) => {
		network.use(...getProcessInstanceShellHandlers());

		await page.goto('/operate/processes/1001/custom-tab?elementId=call-activity&isPlaceholder=true');

		await expect(page).toHaveURL('/operate/processes/1001/details?elementId=call-activity&isPlaceholder=true');
	});

	test('should show call-hierarchy overflow in a More menu and keep the current process breadcrumb', async ({
		network,
		page,
		operateProcessesPage,
	}) => {
		network.use(
			...getProcessInstanceShellHandlers({
				callHierarchy: [
					createCallHierarchy({processInstanceKey: '10', processDefinitionName: 'Root'}),
					createCallHierarchy({processInstanceKey: '11', processDefinitionName: 'Step 1'}),
					createCallHierarchy({processInstanceKey: '12', processDefinitionName: 'Step 2'}),
					createCallHierarchy({processInstanceKey: '13', processDefinitionName: 'Step 3'}),
					createCallHierarchy({processInstanceKey: '14', processDefinitionName: 'Step 4'}),
					createCallHierarchy({processInstanceKey: '15', processDefinitionName: 'Step 5'}),
					createCallHierarchy({processInstanceKey: '1001', processDefinitionName: 'Order Process'}),
				],
			}),
		);

		await operateProcessesPage.goto();
		await operateProcessesPage.instanceLink('1001').click();

		await expect(page.getByRole('button', {name: 'More'})).toBeVisible();
		await page.getByRole('button', {name: 'More'}).click();
		await expect(page.getByRole('menuitem', {name: 'Step 2'})).toBeVisible();
		await expect(page.getByLabel('Breadcrumb').getByText('Order Process')).toBeVisible();
	});

	test('should preserve search params when redirecting from process-instance shell to the default tab', async ({
		network,
		page,
	}) => {
		network.use(...getProcessInstanceShellHandlers());

		await page.goto('/operate/processes/1001?elementId=call-activity&isPlaceholder=true&customHint=focus');

		await expect(page).toHaveURL(/\/operate\/processes\/1001\/details\?/);
		const {searchParams} = new URL(page.url());
		expect(searchParams.get('elementId')).toBe('call-activity');
		expect(searchParams.get('isPlaceholder')).toBe('true');
		expect(searchParams.get('customHint')).toBe('focus');
		await expect(page.getByRole('heading', {name: 'Operate Process Instance'})).toBeAttached();
	});

	test('should redirect to details by default when wait states are enabled and present at process scope', async ({
		network,
		page,
	}) => {
		network.use(
			mockGetProcessInstanceWaitStateStatisticsEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({
						items: [{elementId: 'order-process', waitingCount: 1}],
					}),
				),
			}),
			...getProcessInstanceShellHandlers({
				processInstance: createProcessInstance({
					processInstanceKey: '1001',
					processDefinitionId: 'order-process',
					processDefinitionName: 'Order Process',
					hasIncident: false,
				}),
			}),
		);

		await page.goto('/operate/processes/1001');

		await expect(page).toHaveURL('/operate/processes/1001/details');
	});

	test('should hide start, end and called instances columns on reduced layouts', async ({network, page}) => {
		await page.setViewportSize({width: 1000, height: 900});
		network.use(
			...getProcessInstanceShellHandlers({
				processInstance: createProcessInstance({
					processInstanceKey: '1001',
					processDefinitionName: 'Order Process',
					endDate: '2026-01-15T12:00:00.000Z',
					hasIncident: false,
				}),
			}),
		);

		await page.goto('/operate/processes/1001');

		await expect(page).toHaveURL('/operate/processes/1001/variables');
		await expect(page.getByRole('columnheader', {name: 'Start Date'})).toHaveCount(0);
		await expect(page.getByRole('columnheader', {name: 'End Date'})).toHaveCount(0);
		await expect(page.getByRole('columnheader', {name: 'Called Instances'})).toHaveCount(0);
	});
});
