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
	mockGetIncidentEndpoint,
	mockQueryAuditLogsEndpoint,
	mockQueryJobsEndpoint,
	mockQueryProcessInstanceIncidentsEndpoint,
	mockQueryElementInstanceIncidentsEndpoint,
	mockGetElementInstanceEndpoint,
	mockResolveIncidentEndpoint,
	mockQueryVariablesEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createOperateClientConfigScript} from '#/shared-test-modules/api-mocks/operate-client-config';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createCallHierarchy} from '#/shared-test-modules/api-mocks/call-hierarchy';
import {createIncident} from '#/shared-test-modules/api-mocks/incidents';
import {createElementInstance} from '#/shared-test-modules/api-mocks/element-instances';
import {createAuditLog, createQueryAuditLogsResponse} from '#/shared-test-modules/api-mocks/audit-logs';
import {createListenerJob} from '#/shared-test-modules/api-mocks/jobs';
import {createPaginatedResponse, createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {createQueryVariablesByUserTaskResponse, createVariable} from '#/shared-test-modules/api-mocks/variables';
import {processInstanceHeaderHandlers} from '#/shared-test-modules/process-instance-header-handlers';
import {processInstanceDiagramHandlers} from '#/shared-test-modules/process-instance-diagram-handlers';

const PROCESS_INSTANCE_ID = '2251799813685280';

test.beforeEach(async ({network, page}) => {
	await page.route('**/operate/client-config.js', (route) =>
		route.fulfill({
			contentType: 'text/javascript',
			body: createOperateClientConfigScript(),
		}),
	);
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
});

test.describe('Operate Process Instance DS preview (/operate-preview/processes/:processInstanceId)', () => {
	test('should render the instance diagram with state overlays', async ({operatePreviewProcessInstancePage}) => {
		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

		await expect(operatePreviewProcessInstancePage.heading).toBeAttached();
		await expect(operatePreviewProcessInstancePage.header).toBeVisible();
		await expect(operatePreviewProcessInstancePage.diagram).toBeVisible();
		await expect(operatePreviewProcessInstancePage.stateOverlay('Review invoice, Active: 1')).toBeVisible();
		await expect(operatePreviewProcessInstancePage.stateOverlay('Review invoice, Incidents: 1')).toBeVisible();
		await expect(operatePreviewProcessInstancePage.variablesTab).toBeVisible();
	});

	test('should list the variables of the process instance in the Variables tab', async ({
		network,
		operatePreviewProcessInstancePage,
	}) => {
		network.use(
			mockQueryVariablesEndpoint({
				successResponse: HttpResponse.json(
					createQueryVariablesByUserTaskResponse({
						items: [createVariable({name: 'amount', value: '100', variableKey: '1'})],
					}),
				),
			}),
		);

		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

		await expect(operatePreviewProcessInstancePage.variableRow('amount')).toBeVisible();
		await expect(operatePreviewProcessInstancePage.variableRow('amount')).toContainText('100');
	});

	test('should show [REDACTED] without actions for a redacted variable', async ({
		network,
		operatePreviewProcessInstancePage,
	}) => {
		network.use(
			mockQueryVariablesEndpoint({
				successResponse: HttpResponse.json(
					createQueryVariablesByUserTaskResponse({
						items: [
							createVariable({name: 'amount', value: '100', variableKey: '1'}),
							createVariable({
								name: 'password',
								value: 'secret',
								variableKey: '2',
								metadata: {protectionModes: ['REDACT']},
							}),
						],
					}),
				),
			}),
		);

		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

		const redactedRow = operatePreviewProcessInstancePage.variableRow('password');
		await expect(redactedRow).toContainText('[REDACTED]');
		await expect(redactedRow).not.toContainText('secret');
		await expect(redactedRow.getByRole('button')).toHaveCount(0);
		await expect(
			operatePreviewProcessInstancePage.variableRow('amount').getByRole('button', {name: 'Open'}),
		).toBeVisible();
	});

	test.describe('Incidents tab', () => {
		test.beforeEach(({network}) => {
			network.use(
				mockGetProcessInstanceEndpoint({
					successResponse: HttpResponse.json(
						createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID, hasIncident: true}),
					),
				}),
			);
		});

		test('should select the failing element on row click and clear it on a second click', async ({
			network,
			operatePreviewProcessInstancePage,
			page,
		}) => {
			const incident = createIncident({errorMessage: 'Payment failed'});
			const incidents = HttpResponse.json(
				createPaginatedResponse({
					items: [incident],
					page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
				}),
			);
			network.use(
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
			const row = operatePreviewProcessInstancePage.incidentsTable.getByRole('row').filter({hasText: 'Payment failed'});
			await expect(row).toBeVisible();
			await expect(row).not.toContainText('Selected');

			await row.click();

			await expect(page).toHaveURL(/elementId=/);
			await expect(row).toContainText('Selected');

			await row.click();

			await expect(page).not.toHaveURL(/elementId=/);
			await expect(row).not.toContainText('Selected');
		});

		test('should select the failing element with the keyboard', async ({
			network,
			operatePreviewProcessInstancePage,
			page,
		}) => {
			const incident = createIncident({errorMessage: 'Payment failed'});
			const incidents = HttpResponse.json(
				createPaginatedResponse({
					items: [incident],
					page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
				}),
			);
			network.use(
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
			const row = operatePreviewProcessInstancePage.incidentsTable.getByRole('row').filter({hasText: 'Payment failed'});

			await row.getByRole('button', {name: 'Payment failed'}).press('Enter');

			await expect(page).toHaveURL(/elementId=/);
			await expect(row).toContainText('Selected');
		});

		test('should retry an incident and remove it from the list once resolved', async ({
			network,
			operatePreviewProcessInstancePage,
			page,
		}) => {
			network.use(
				mockQueryProcessInstanceIncidentsEndpoint({
					successResponse: HttpResponse.json(
						createPaginatedResponse({
							items: [createIncident({errorMessage: 'Payment failed', jobKey: null})],
							page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
						}),
					),
				}),
				mockResolveIncidentEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
				mockGetIncidentEndpoint({successResponse: HttpResponse.json(createIncident({state: 'RESOLVED'}))}),
			);
			await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
			await operatePreviewProcessInstancePage.incidentsTab.click();
			const retryButton = operatePreviewProcessInstancePage.incidentsTable.getByRole('button', {
				name: 'Retry Incident',
			});
			await expect(retryButton).toBeVisible();
			network.use(
				mockQueryProcessInstanceIncidentsEndpoint({
					successResponse: HttpResponse.json(
						createPaginatedResponse({
							items: [],
							page: {totalItems: 0, startCursor: null, endCursor: null, hasMoreTotalItems: false},
						}),
					),
				}),
			);

			await retryButton.click();

			await expect(page.getByText('There are no incidents matching this filter set')).toBeVisible();
		});

		test('should show an error when the incidents cannot be loaded', async ({
			network,
			operatePreviewProcessInstancePage,
			page,
		}) => {
			network.use(
				mockQueryProcessInstanceIncidentsEndpoint({
					successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
				}),
			);

			await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
			await operatePreviewProcessInstancePage.incidentsTab.click();

			await expect(page.getByText("Couldn't fetch data")).toBeVisible({timeout: 15000});
			await expect(operatePreviewProcessInstancePage.incidentsTable).toBeHidden();
		});
	});

	test('should keep the selected element in the URL and clear it on a second canvas click', async ({
		operatePreviewProcessInstancePage,
		page,
	}) => {
		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
		await expect(operatePreviewProcessInstancePage.diagram).toBeVisible();

		await operatePreviewProcessInstancePage.diagramElement('task-1').click();

		await expect(page).toHaveURL(/elementId=task-1/);

		await operatePreviewProcessInstancePage.diagram.click({position: {x: 5, y: 5}});

		await expect(page).not.toHaveURL(/elementId=/);
	});

	test('should list the listeners of the process instance in the Listeners tab', async ({
		network,
		operatePreviewProcessInstancePage,
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

		await expect(operatePreviewProcessInstancePage.listenersList.getByText('audit-listener')).toBeVisible();
	});

	test('should list the operations of the process instance in the Operations log tab', async ({
		network,
		operatePreviewProcessInstancePage,
		page,
	}) => {
		network.use(
			mockQueryAuditLogsEndpoint({
				successResponse: HttpResponse.json(createQueryAuditLogsResponse({items: [createAuditLog()]})),
			}),
		);

		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
		await operatePreviewProcessInstancePage.tab('Operations Log').click();

		await expect(page).toHaveURL(`/operate-preview/processes/${PROCESS_INSTANCE_ID}/operations-log`);
		await expect(
			operatePreviewProcessInstancePage.operationsLogTable.getByRole('cell', {name: 'Create'}),
		).toBeVisible();
	});

	test('should only offer the mapping tabs when an element is selected', async ({
		operatePreviewProcessInstancePage,
	}) => {
		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
		await expect(operatePreviewProcessInstancePage.variablesTab).toBeVisible();

		await expect(operatePreviewProcessInstancePage.tab('Input Mappings')).toBeHidden();

		await operatePreviewProcessInstancePage.diagramElement('task-1').click();

		await expect(operatePreviewProcessInstancePage.tab('Input Mappings')).toBeVisible();
		await expect(operatePreviewProcessInstancePage.tab('Output Mappings')).toBeVisible();
	});

	test('should redirect to the default tab and switch tabs through the URL', async ({
		operatePreviewProcessInstancePage,
		page,
	}) => {
		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

		await expect(page).toHaveURL(`/operate-preview/processes/${PROCESS_INSTANCE_ID}/variables`);

		await operatePreviewProcessInstancePage.incidentsTab.click();

		await expect(page).toHaveURL(`/operate-preview/processes/${PROCESS_INSTANCE_ID}/incidents`);
		await expect(operatePreviewProcessInstancePage.incidentsTab).toHaveAttribute('aria-selected', 'true');
	});

	test('should redirect to the details tab when the instance waits at process level', async ({
		network,
		operatePreviewProcessInstancePage,
		page,
	}) => {
		network.use(
			mockGetProcessInstanceWaitStateStatisticsEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({items: [{elementId: 'my-process', waitingCount: 1}]}),
				),
			}),
		);

		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

		await expect(page).toHaveURL(`/operate-preview/processes/${PROCESS_INSTANCE_ID}/details`);
	});

	test('should not request wait states and should redirect to variables when the flag is off', async ({
		network,
		operatePreviewProcessInstancePage,
		page,
	}) => {
		await page.route('**/operate/client-config.js', (route) =>
			route.fulfill({
				contentType: 'text/javascript',
				body: createOperateClientConfigScript({waitStatesEnabled: false}),
			}),
		);
		const statisticsRequests: string[] = [];
		page.on('request', (request) => {
			if (request.url().endsWith('/statistics/wait-states')) {
				statisticsRequests.push(request.url());
			}
		});
		network.use(
			mockGetProcessInstanceWaitStateStatisticsEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({items: [{elementId: 'my-process', waitingCount: 1}]}),
				),
			}),
		);

		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

		await expect(page).toHaveURL(`/operate-preview/processes/${PROCESS_INSTANCE_ID}/variables`);
		expect(statisticsRequests).toHaveLength(0);
	});

	test('should not render a breadcrumb for a root instance', async ({network, operatePreviewProcessInstancePage}) => {
		network.use(mockGetProcessInstanceCallHierarchyEndpoint({successResponse: HttpResponse.json([])}));

		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

		await expect(operatePreviewProcessInstancePage.header).toBeVisible();
		await expect(operatePreviewProcessInstancePage.breadcrumb).toHaveCount(0);
	});

	test('should render the ancestors in the breadcrumb of a called instance', async ({
		network,
		operatePreviewProcessInstancePage,
	}) => {
		network.use(
			mockGetProcessInstanceCallHierarchyEndpoint({
				successResponse: HttpResponse.json([
					createCallHierarchy({processInstanceKey: '1', processDefinitionName: 'Parent Process'}),
					createCallHierarchy({processInstanceKey: PROCESS_INSTANCE_ID, processDefinitionName: 'Child Process'}),
				]),
			}),
		);

		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

		await expect(
			operatePreviewProcessInstancePage.breadcrumb.getByRole('link', {
				name: 'View Process Parent Process - Instance 1',
			}),
		).toBeVisible();
	});
});
