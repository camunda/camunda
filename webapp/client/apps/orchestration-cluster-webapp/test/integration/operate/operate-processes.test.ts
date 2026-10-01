/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {delay, http, HttpResponse} from 'msw';
import {
	endpoints as apiEndpoints,
	queryProcessDefinitionsRequestBodySchema,
} from '@camunda/camunda-api-zod-schemas/8.11';
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
	mockGetProcessDefinitionStatisticsEndpoint,
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
import {createGetProcessDefinitionStatisticsResponse} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {BPMN_XML} from '#/shared-test-modules/api-mocks/process-definition-xmls';

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

	test('should synchronize the element combobox and diagram with URL history and instance requests', async ({
		network,
		page,
		operateProcessesPage,
	}) => {
		network.use(
			mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
			mockGetProcessDefinitionStatisticsEndpoint({
				successResponse: HttpResponse.json(createGetProcessDefinitionStatisticsResponse([])),
			}),
		);
		await operateProcessesPage.goto('?process=order-process&version=1');
		await expect(operateProcessesPage.elementCombobox).toBeEnabled();

		const selectedRequest = page.waitForRequest(
			(request) =>
				request.method() === 'POST' &&
				request.url().endsWith('/v2/process-instances/search') &&
				JSON.stringify(request.postDataJSON()).includes('"elementId"'),
		);
		await operateProcessesPage.elementCombobox.fill('Review invoice');
		await operateProcessesPage.elementCombobox.press('Enter');
		expect((await selectedRequest).postDataJSON().filter).toEqual(
			expect.objectContaining({
				processDefinitionId: {$eq: 'order-process'},
				processDefinitionVersion: 1,
				elementId: {$eq: 'task-1'},
				elementInstanceState: {$eq: 'ACTIVE'},
			}),
		);
		await expect(operateProcessesPage.elementCombobox).toHaveValue('Review invoice');
		await expect(operateProcessesPage.diagramElement('task-1')).toHaveClass(/op-selected/);
		expect(new URL(page.url()).searchParams.get('elementId')).toBe('task-1');

		await page.reload();
		await expect(operateProcessesPage.elementCombobox).toHaveValue('Review invoice');
		await expect(operateProcessesPage.diagramElement('task-1')).toHaveClass(/op-selected/);
		await operateProcessesPage.diagramElement('end_event').click();
		await expect(operateProcessesPage.elementCombobox).toHaveValue('end_event');
		await expect(operateProcessesPage.diagramElement('end_event')).toHaveClass(/op-selected/);
		await page.goBack();
		await expect(operateProcessesPage.elementCombobox).toHaveValue('Review invoice');
		await page.goForward();
		await expect(operateProcessesPage.elementCombobox).toHaveValue('end_event');

		await page.getByRole('button', {name: 'Clear selected item'}).last().click();
		await expect(operateProcessesPage.elementCombobox).toHaveValue('');
		await expect.poll(() => new URL(page.url()).searchParams.get('elementId')).toBeNull();
	});

	test('should keep a bookmarked element filter clearable when XML fails without hiding instances', async ({
		network,
		page,
		operateProcessesPage,
	}) => {
		network.use(mockGetProcessDefinitionXmlEndpoint({successResponse: new HttpResponse(null, {status: 503})}));
		await operateProcessesPage.goto('?process=order-process&version=1&elementId=task-1');
		await expect(operateProcessesPage.instancesTable).toBeVisible();
		await expect(operateProcessesPage.elementCombobox).toHaveValue('task-1');
		await expect(operateProcessesPage.filtersPanel.getByRole('button', {name: 'Retry loading elements'})).toBeVisible({
			timeout: 15000,
		});
		await operateProcessesPage.filtersPanel.getByRole('button', {name: 'Clear element filter'}).click();
		await expect.poll(() => new URL(page.url()).searchParams.get('elementId')).toBeNull();
		await expect(operateProcessesPage.instancesTable).toBeVisible();
	});

	test('should clear the element and diagram selection when the process version changes', async ({
		network,
		page,
		operateProcessesPage,
	}) => {
		network.use(
			mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
			mockGetProcessDefinitionStatisticsEndpoint({
				successResponse: HttpResponse.json(createGetProcessDefinitionStatisticsResponse([])),
			}),
		);
		await operateProcessesPage.goto('?process=order-process&version=1&elementId=task-1');
		await expect(operateProcessesPage.diagramElement('task-1')).toHaveClass(/op-selected/);
		await operateProcessesPage.versionCombobox.click();
		await page.getByRole('option', {name: 'All versions'}).click();
		await expect.poll(() => new URL(page.url()).searchParams.get('elementId')).toBeNull();
		await expect(operateProcessesPage.elementCombobox).toBeDisabled();
		await expect(page.getByText('There is more than one Version selected for Process "Order Process"')).toBeVisible();
	});

	test('should wait for a later tenant on the selected process before showing elements', async ({
		network,
		page,
		operateProcessesPage,
	}) => {
		const tenantAVersions = Array.from({length: 1000}, (_, index) =>
			createProcessDefinition({
				name: 'Orders',
				processDefinitionId: 'orders',
				processDefinitionKey: `tenant-a-${index + 1}`,
				version: index + 1,
				tenantId: '<tenant-A>',
			}),
		);
		const otherDefinitions = Array.from({length: 999}, (_, index) =>
			createProcessDefinition({processDefinitionId: `other-${index}`, processDefinitionKey: `other-${index}`}),
		);
		network.use(
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(
					createSystemConfiguration({
						components: {active: ['operate']},
						deployment: {isMultiTenancyEnabled: true, isTenantsApiEnabled: true, maxRequestSize: 0},
					}),
				),
			}),
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(
					createCurrentUser({
						tenants: [
							{tenantId: '<tenant-A>', name: 'Tenant A', description: null},
							{tenantId: '<tenant-B>', name: 'Tenant B', description: null},
						],
					}),
				),
			}),
			http.post(apiEndpoints.queryProcessDefinitions.getUrl(), async ({request}) => {
				const body = queryProcessDefinitionsRequestBodySchema.parse(await request.json());
				const filter = body.filter?.processDefinitionId;
				if ((typeof filter === 'string' ? filter : filter?.$eq) === 'orders') {
					if (body.page?.after === 'orders-next') {
						await delay(1500);
						return HttpResponse.json(
							createQueryProcessDefinitionsResponse({
								items: [
									createProcessDefinition({
										name: 'Orders',
										processDefinitionId: 'orders',
										processDefinitionKey: 'tenant-b-1',
										version: 1,
										tenantId: '<tenant-B>',
									}),
									createProcessDefinition({
										name: 'Orders',
										processDefinitionId: 'orders',
										processDefinitionKey: 'tenant-b-1001',
										version: 1001,
										tenantId: '<tenant-B>',
									}),
								],
								page: {totalItems: 1002, hasMoreTotalItems: false},
							}),
						);
					}
					return HttpResponse.json(
						createQueryProcessDefinitionsResponse({
							items: tenantAVersions,
							page: {totalItems: 1002, hasMoreTotalItems: true, endCursor: 'orders-next'},
						}),
					);
				}
				return HttpResponse.json(
					createQueryProcessDefinitionsResponse({
						items: [tenantAVersions[0]!, ...otherDefinitions],
						page: {totalItems: 2000, hasMoreTotalItems: true, endCursor: 'global-next'},
					}),
				);
			}),
		);
		await operateProcessesPage.goto('?tenantId=all&process=orders&version=1');
		await expect(operateProcessesPage.elementCombobox).toBeDisabled();
		await expect(page.getByTestId('diagram-spinner')).toBeVisible();
		await expect(page.getByText('Process "Orders" exists in more than one Tenant')).toBeVisible();
		await expect(operateProcessesPage.elementCombobox).toBeDisabled();
		await operateProcessesPage.versionCombobox.click();
		await expect(page.getByRole('option', {name: '1', exact: true})).toHaveCount(1);
		await expect(operateProcessesPage.instancesTable).toBeVisible();
	});

	test('should load a bookmarked process missing from the first 1,000 global definitions', async ({
		network,
		page,
		operateProcessesPage,
	}) => {
		const otherDefinitions = Array.from({length: 1000}, (_, index) =>
			createProcessDefinition({processDefinitionId: `other-${index}`, processDefinitionKey: `other-${index}`}),
		);
		network.use(
			http.post(apiEndpoints.queryProcessDefinitions.getUrl(), async ({request}) => {
				const body = queryProcessDefinitionsRequestBodySchema.parse(await request.json());
				const filter = body.filter?.processDefinitionId;
				return HttpResponse.json(
					(typeof filter === 'string' ? filter : filter?.$eq) === 'unique'
						? createQueryProcessDefinitionsResponse({
								items: [
									createProcessDefinition({
										name: 'Unique Process',
										processDefinitionId: 'unique',
										processDefinitionKey: 'unique-1',
										version: 1,
									}),
								],
							})
						: createQueryProcessDefinitionsResponse({
								items: otherDefinitions,
								page: {totalItems: 1001, hasMoreTotalItems: true, endCursor: 'global-next'},
							}),
				);
			}),
			mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
			mockGetProcessDefinitionStatisticsEndpoint({
				successResponse: HttpResponse.json(createGetProcessDefinitionStatisticsResponse([])),
			}),
		);
		await operateProcessesPage.goto('?process=unique&version=1');
		await expect(operateProcessesPage.processCombobox).toHaveValue('Unique Process');
		await expect(operateProcessesPage.elementCombobox).toBeEnabled();
		await expect(operateProcessesPage.diagramElement('task-1')).toBeAttached();
		expect(new URL(page.url()).searchParams.get('version')).toBe('1');
	});

	test('should preserve the primary list through selected-definition lookup failure and recovery', async ({
		network,
		page,
		operateProcessesPage,
	}) => {
		let lookupFailureStatus: 503 | 403 | undefined = 503;
		network.use(
			http.post(apiEndpoints.queryProcessDefinitions.getUrl(), async ({request}) => {
				const body = queryProcessDefinitionsRequestBodySchema.parse(await request.json());
				const filter = body.filter?.processDefinitionId;
				return (typeof filter === 'string' ? filter : filter?.$eq) === 'order-process' &&
					lookupFailureStatus !== undefined
					? new HttpResponse(null, {status: lookupFailureStatus})
					: HttpResponse.json(
							createQueryProcessDefinitionsResponse({
								items: [createProcessDefinition({name: 'Order Process', processDefinitionId: 'order-process'})],
							}),
						);
			}),
			mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
			mockGetProcessDefinitionStatisticsEndpoint({
				successResponse: HttpResponse.json(createGetProcessDefinitionStatisticsResponse([])),
			}),
		);
		await operateProcessesPage.goto('?process=order-process&version=1&elementId=task-1');
		await expect(operateProcessesPage.instancesTable).toBeVisible();
		await expect(operateProcessesPage.elementCombobox).toBeDisabled();
		await expect(operateProcessesPage.elementCombobox).toHaveValue('task-1');
		await expect(operateProcessesPage.filtersPanel.getByRole('button', {name: 'Retry loading elements'})).toBeVisible();

		lookupFailureStatus = undefined;
		await operateProcessesPage.filtersPanel.getByRole('button', {name: 'Retry loading elements'}).click();
		await expect(operateProcessesPage.elementCombobox).toHaveValue('Review invoice');
		await expect(operateProcessesPage.elementCombobox).toBeEnabled();

		lookupFailureStatus = 503;
		await page.getByRole('link', {name: 'Processes', exact: true}).click();
		await page.goBack();
		await expect(operateProcessesPage.filtersPanel.getByRole('button', {name: 'Retry loading elements'})).toBeVisible();
		await expect(operateProcessesPage.elementCombobox).toBeDisabled();
		await expect(operateProcessesPage.elementCombobox).toHaveValue('task-1');
		await expect(operateProcessesPage.instancesTable).toBeVisible();

		lookupFailureStatus = undefined;
		await operateProcessesPage.filtersPanel.getByRole('button', {name: 'Retry loading elements'}).click();
		await expect(operateProcessesPage.elementCombobox).toBeEnabled();

		lookupFailureStatus = 403;
		await page.getByRole('link', {name: 'Processes', exact: true}).click();
		await page.goBack();
		await expect(page.getByText('Missing permissions to view the Definition')).toBeVisible();
		await expect(operateProcessesPage.filtersPanel.getByRole('button', {name: 'Retry loading elements'})).toHaveCount(
			0,
		);
		await expect(operateProcessesPage.filtersPanel.getByRole('button', {name: 'Clear element filter'})).toBeVisible();
		await expect(operateProcessesPage.instancesTable).toBeVisible();
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

	test('should recover waiting statistics before choosing the default tab', async ({
		network,
		page,
		operateProcessesPage,
	}) => {
		network.use(
			...getProcessInstanceShellHandlers({
				processInstance: createProcessInstance({
					processInstanceKey: '1001',
					processDefinitionId: 'order-process',
					processDefinitionName: 'Order Process',
					hasIncident: false,
				}),
			}),
		);

		for (const isCached of [false, true]) {
			network.use(
				mockGetProcessInstanceWaitStateStatisticsEndpoint({
					successResponse: HttpResponse.json(createProblemDetails({status: 503}), {status: 503}),
				}),
			);
			if (isCached) {
				await page.getByRole('link', {name: 'Processes', exact: true}).click();
				await operateProcessesPage.instanceLink('1001').click();
			} else {
				await page.goto('/operate/processes/1001');
			}
			await expect(page.getByRole('heading', {name: 'Something went wrong'})).toBeVisible({timeout: 15000});
			await expect(page).toHaveURL('/operate/processes/1001');
			await expect(page.getByText('Waiting', {exact: true})).toHaveCount(0);
			network.use(
				mockGetProcessInstanceWaitStateStatisticsEndpoint({
					successResponse: HttpResponse.json(
						createPaginatedResponse({items: [{elementId: 'order-process', waitingCount: 1}]}),
					),
				}),
			);
			await page.getByRole('button', {name: 'Try again'}).click();
			await expect(page).toHaveURL('/operate/processes/1001/details');
			await expect(page.getByText('Waiting', {exact: true})).toBeVisible();
		}
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
