/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import type {Locator} from '@playwright/test';
import {HttpResponse} from 'msw';
import {
	mockCurrentUserEndpoint,
	mockGetProcessInstanceCallHierarchyEndpoint,
	mockGetProcessInstanceEndpoint,
	mockGetProcessInstanceWaitStateStatisticsEndpoint,
	mockLicenseEndpoint,
	mockGetElementInstanceEndpoint,
	mockQueryElementInstancesEndpoint,
	mockQueryProcessInstanceIncidentsEndpoint,
	mockQueryElementInstanceIncidentsEndpoint,
	mockQueryElementInstanceInspectionEndpoint,
	mockQueryVariablesEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createCallHierarchy} from '#/shared-test-modules/api-mocks/call-hierarchy';
import {createIncident} from '#/shared-test-modules/api-mocks/incidents';
import {
	createElementInstance,
	createQueryElementInstancesResponse,
} from '#/shared-test-modules/api-mocks/element-instances';
import {
	createElementInstanceInspection,
	createQueryElementInstanceInspectionResponse,
} from '#/shared-test-modules/api-mocks/element-instance-inspection';
import {createPaginatedResponse, createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {createQueryVariablesByUserTaskResponse, createVariable} from '#/shared-test-modules/api-mocks/variables';
import {processInstanceHeaderHandlers} from '#/shared-test-modules/process-instance-header-handlers';
import {processInstanceDiagramHandlers} from '#/shared-test-modules/process-instance-diagram-handlers';

const PROCESS_INSTANCE_ID = '2251799813685280';

async function isHitTestable(locator: Locator) {
	return locator.evaluate((element) => {
		const bounds = element.getBoundingClientRect();
		const x = (bounds.left + bounds.right) / 2;
		const y = (bounds.top + bounds.bottom) / 2;
		const pointerEvents = element.style.pointerEvents;
		element.style.pointerEvents = 'all';
		const target = document.elementFromPoint(x, y);
		element.style.pointerEvents = pointerEvents;
		return target !== null && (element === target || element.contains(target));
	});
}

test.beforeEach(async ({network}) => {
	network.use(
		...processInstanceDiagramHandlers(),
		...processInstanceHeaderHandlers(createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})),
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser({authorizedComponents: ['operate']})),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
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

		test('should select a multi-instance body incident as the body', async ({
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
							type: 'MULTI_INSTANCE_BODY',
							hasIncident: true,
						}),
					),
				}),
			);
			await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
			await operatePreviewProcessInstancePage.incidentsTab.click();

			await operatePreviewProcessInstancePage.incidentsTable
				.getByRole('row')
				.filter({hasText: 'Payment failed'})
				.getByRole('button', {name: 'Payment failed'})
				.press('Enter');

			await expect(page).toHaveURL(/isMultiInstanceBody=true/);
		});

		test('should put the sort of the clicked column in the URL', async ({
			network,
			operatePreviewProcessInstancePage,
			page,
		}) => {
			network.use(
				mockQueryProcessInstanceIncidentsEndpoint({
					successResponse: HttpResponse.json(
						createPaginatedResponse({
							items: [createIncident({errorMessage: 'Payment failed'})],
							page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
						}),
					),
				}),
			);
			await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
			await operatePreviewProcessInstancePage.incidentsTab.click();

			await operatePreviewProcessInstancePage.incidentsTable.getByRole('button', {name: 'Type'}).click();

			await expect(page).toHaveURL(/sort=errorType/);
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

	test('should open the Incidents tab for an instance with incidents and switch tabs through the URL', async ({
		network,
		operatePreviewProcessInstancePage,
		page,
	}) => {
		network.use(
			mockGetProcessInstanceEndpoint({
				successResponse: HttpResponse.json(
					createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID, hasIncident: true}),
				),
			}),
			mockQueryProcessInstanceIncidentsEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({
						items: [createIncident()],
						page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
		);
		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

		await expect(page).toHaveURL(`/operate-preview/processes/${PROCESS_INSTANCE_ID}/incidents`);

		await page.getByRole('tab', {name: 'Variables'}).click();

		await expect(page).toHaveURL(`/operate-preview/processes/${PROCESS_INSTANCE_ID}/variables`);
		await expect(page.getByRole('tab', {name: 'Variables'})).toHaveAttribute('aria-selected', 'true');
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
			mockQueryElementInstanceInspectionEndpoint({
				successResponse: HttpResponse.json(
					createQueryElementInstanceInspectionResponse([
						createElementInstanceInspection({elementInstanceKey: PROCESS_INSTANCE_ID}),
					]),
				),
			}),
		);

		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

		await expect(page).toHaveURL(`/operate-preview/processes/${PROCESS_INSTANCE_ID}/details`);
		await expect(
			operatePreviewProcessInstancePage.tabPanel.getByText('Waiting for message: payment-received'),
		).toBeVisible();
	});

	test('should not request wait states and should redirect to variables when the flag is off', async ({
		network,
		operatePreviewProcessInstancePage,
		page,
	}) => {
		network.use(
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(
					createSystemConfiguration({
						components: {active: ['operate']},
						deployment: {
							isMultiTenancyEnabled: false,
							isTenantsApiEnabled: false,
							isWaitStatesEnabled: false,
							maxRequestSize: 0,
						},
					}),
				),
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

	test('should leave the mapping tab when the selection is cleared and the flag is off', async ({
		network,
		operatePreviewProcessInstancePage,
		page,
	}) => {
		network.use(
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(
					createSystemConfiguration({
						components: {active: ['operate']},
						deployment: {
							isMultiTenancyEnabled: false,
							isTenantsApiEnabled: false,
							isWaitStatesEnabled: false,
							maxRequestSize: 0,
						},
					}),
				),
			}),
		);
		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
		await operatePreviewProcessInstancePage.diagramElement('task-1').click();
		await operatePreviewProcessInstancePage.tab('Input Mappings').click();
		await expect(operatePreviewProcessInstancePage.tab('Input Mappings')).toHaveAttribute('aria-selected', 'true');

		await operatePreviewProcessInstancePage.diagram.click({position: {x: 5, y: 5}});

		await expect(page).toHaveURL(`/operate-preview/processes/${PROCESS_INSTANCE_ID}/variables`);
		await expect(operatePreviewProcessInstancePage.variablesTab).toHaveAttribute('aria-selected', 'true');
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

	test('should show the instance history tree and select an element from it', async ({
		network,
		operatePreviewProcessInstancePage,
		page,
	}) => {
		const task = createElementInstance({
			elementId: 'task-1',
			elementInstanceKey: '2251799813685290',
			elementName: 'Task 1',
		});
		network.use(
			mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(task)}),
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryElementInstancesResponse([
						createElementInstance({elementId: 'task-1', elementInstanceKey: '2251799813685290', elementName: 'Task 1'}),
					]),
				),
			}),
		);

		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
		await operatePreviewProcessInstancePage.historyItem('Task 1').click();

		await expect(page).toHaveURL(/elementInstanceKey=%222251799813685290%22/);
		await expect(operatePreviewProcessInstancePage.historyItem('Task 1')).toHaveAttribute('aria-selected', 'true');

		const rootStateIcon = operatePreviewProcessInstancePage.historyTree
			.locator('[role="treeitem"][aria-level="1"]')
			.locator('[data-testid$="-icon"]');
		const taskStateIcon = operatePreviewProcessInstancePage.historyItem('Task 1').locator('[data-testid$="-icon"]');
		await rootStateIcon.click();

		await expect(page).not.toHaveURL(/elementInstanceKey=/);
		await expect(
			operatePreviewProcessInstancePage.historyTree.getByRole('treeitem', {name: /My Process/}),
		).toHaveAttribute('aria-selected', 'true');

		await taskStateIcon.click();
		await expect(page).toHaveURL(/elementInstanceKey=%222251799813685290%22/);
		await expect(operatePreviewProcessInstancePage.historyItem('Task 1')).toHaveAttribute('aria-selected', 'true');
	});

	test('should show one full-name tooltip for a truncated history row on hover and focus', async ({
		network,
		operatePreviewProcessInstancePage,
		page,
	}) => {
		const longElementName = `Task ${'with an exceptionally long history label '.repeat(32)}`.trim();
		const longElementNamePattern = /Task with an exceptionally long history label/;
		const longTask = createElementInstance({
			elementId: 'task-long',
			elementInstanceKey: '2251799813685291',
			elementName: longElementName,
		});
		network.use(
			mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(longTask)}),
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryElementInstancesResponse([longTask])),
			}),
		);

		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
		await page.addStyleTag({
			content: '[data-slot="tooltip-trigger"] { max-width: 110px !important; display: block !important; }',
		});
		const historyTree = operatePreviewProcessInstancePage.historyTree.first();
		const historyRow = historyTree.getByRole('treeitem', {name: longElementNamePattern}).first();
		await expect(historyRow).toBeVisible();
		const historyRowLabel = historyRow.locator('[data-slot="tooltip-trigger"]').first();
		await expect(historyRowLabel).toContainText(longElementName);
		await historyRowLabel.hover({force: true});
		const tooltip = page.getByRole('tooltip').filter({hasText: longElementNamePattern});
		await expect(tooltip).toHaveCount(1);
		await expect(tooltip).toContainText(longElementName);
		await expect(tooltip.locator('svg, img')).toHaveCount(0);

		await page.mouse.move(0, 0);
		await page.keyboard.press('Escape');
		await expect(tooltip).toHaveCount(0);
		await historyRow.focus();
		await expect(tooltip).toHaveCount(1);
		await expect(tooltip).toContainText(longElementName);
		await expect(tooltip.locator('svg, img')).toHaveCount(0);
	});

	test('should keep history state icons in a fixed outer-left gutter without clipping', async ({
		network,
		operatePreviewProcessInstancePage,
		page,
	}) => {
		const subprocess = createElementInstance({
			elementId: 'start',
			elementName: 'Start',
			elementInstanceKey: '2251799813800101',
			type: 'SUB_PROCESS',
			state: 'COMPLETED',
		});
		const incidentTask = createElementInstance({
			elementId: 'task-1',
			elementName: 'Task 1',
			elementInstanceKey: '2251799813685290',
			state: 'ACTIVE',
			hasIncident: true,
		});
		network.use(
			mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(subprocess)}),
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryElementInstancesResponse([subprocess, incidentTask])),
			}),
		);
		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
		const historyPanel = page.getByRole('region', {name: 'Instance History'});
		const historyTree = page.getByRole('tree', {name: 'Instance History'});
		const rootRow = historyTree.locator('[role="treeitem"][aria-level="1"]').first();
		const incidentRow = historyTree.getByRole('treeitem', {name: /Task 1/});
		await expect(incidentRow).toBeVisible();

		const activeStateIcon = rootRow.getByTestId('ACTIVE-icon');
		const incidentStateIcon = incidentRow.getByTestId('INCIDENT-icon');
		const rootElementIcon = rootRow.locator('[aria-hidden="true"][data-icon] svg, img[aria-hidden="true"]').first();
		const incidentElementIcon = incidentRow
			.locator('[aria-hidden="true"][data-icon] svg, img[aria-hidden="true"]')
			.first();
		const rootToggle = rootRow.locator('button').first();

		await expect(activeStateIcon).toBeInViewport({ratio: 1});
		await expect(incidentStateIcon).toBeInViewport({ratio: 1});
		await expect(rootElementIcon).toBeInViewport({ratio: 1});
		await expect(incidentElementIcon).toBeInViewport({ratio: 1});

		const historyPanelBounds = await historyPanel.evaluate((panel) => panel.getBoundingClientRect());
		const historyTreeBounds = await historyTree.evaluate((tree) => tree.getBoundingClientRect());
		const activeStateIconBounds = await activeStateIcon.evaluate((icon) => icon.getBoundingClientRect());
		const incidentStateIconBounds = await incidentStateIcon.evaluate((icon) => icon.getBoundingClientRect());
		const rootRowBounds = await rootRow.evaluate((row) => row.getBoundingClientRect());
		const incidentRowBounds = await incidentRow.evaluate((row) => row.getBoundingClientRect());
		const rootToggleLeft = await rootToggle.evaluate((toggle) => toggle.getBoundingClientRect().left);
		const activeStateIconRight = activeStateIconBounds.right;
		const rootElementIconLeft = await rootElementIcon.evaluate((icon) => icon.getBoundingClientRect().left);
		const rootToggleRight = await rootToggle.evaluate((toggle) => toggle.getBoundingClientRect().right);
		const activeStateIconHitTest = await isHitTestable(activeStateIcon);
		const incidentStateIconHitTest = await isHitTestable(incidentStateIcon);
		const rootElementIconHitTest = await isHitTestable(rootElementIcon);
		const incidentElementIconHitTest = await isHitTestable(incidentElementIcon);

		expect(activeStateIconBounds.left).toBeGreaterThanOrEqual(historyPanelBounds.left);
		expect(activeStateIconBounds.right).toBeLessThanOrEqual(historyPanelBounds.right);
		expect(activeStateIconBounds.top).toBeGreaterThanOrEqual(historyPanelBounds.top);
		expect(activeStateIconBounds.bottom).toBeLessThanOrEqual(historyPanelBounds.bottom);
		expect(incidentStateIconBounds.left).toBeGreaterThanOrEqual(historyPanelBounds.left);
		expect(incidentStateIconBounds.right).toBeLessThanOrEqual(historyPanelBounds.right);
		expect(incidentStateIconBounds.top).toBeGreaterThanOrEqual(historyPanelBounds.top);
		expect(incidentStateIconBounds.bottom).toBeLessThanOrEqual(historyPanelBounds.bottom);
		expect(Math.abs(rootRowBounds.left - historyTreeBounds.left)).toBeLessThanOrEqual(1);
		expect(Math.abs(incidentRowBounds.left - historyTreeBounds.left)).toBeLessThanOrEqual(1);
		expect(activeStateIconRight).toBeLessThanOrEqual(rootToggleLeft);
		expect(rootElementIconLeft).toBeGreaterThan(rootToggleRight);
		expect(activeStateIconHitTest).toBe(true);
		expect(incidentStateIconHitTest).toBe(true);
		expect(rootElementIconHitTest).toBe(true);
		expect(incidentElementIconHitTest).toBe(true);
	});

	test('should show the variables tab when the history path is opened on a wide viewport', async ({
		operatePreviewProcessInstancePage,
	}) => {
		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID, '/history');

		await expect(operatePreviewProcessInstancePage.tab('Variables')).toHaveAttribute('aria-selected', 'true');
	});

	test.describe('on a narrow viewport', () => {
		test.beforeEach(async ({page}) => {
			await page.setViewportSize({width: 800, height: 900});
		});

		test('should leave the history tab for the details tab when an element is selected', async ({
			network,
			operatePreviewProcessInstancePage,
			page,
		}) => {
			const task = createElementInstance({elementInstanceKey: '2251799813685290', elementName: 'Task 1'});
			network.use(
				mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(task)}),
				mockQueryElementInstancesEndpoint({
					successResponse: HttpResponse.json(createQueryElementInstancesResponse([task])),
				}),
			);
			await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID, '/history');

			await page.getByRole('treeitem', {name: /Task 1/}).click();

			await expect(operatePreviewProcessInstancePage.tab('Details')).toHaveAttribute('aria-selected', 'true');
		});

		test('should leave the history tab for the incidents tab when an element with an incident is selected', async ({
			network,
			operatePreviewProcessInstancePage,
			page,
		}) => {
			const task = createElementInstance({
				elementInstanceKey: '2251799813685290',
				elementName: 'Task 1',
				hasIncident: true,
			});
			const incidents = HttpResponse.json(createPaginatedResponse({items: [createIncident()]}));
			network.use(
				mockGetProcessInstanceEndpoint({
					successResponse: HttpResponse.json(
						createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID, hasIncident: true}),
					),
				}),
				mockQueryProcessInstanceIncidentsEndpoint({successResponse: incidents}),
				mockQueryElementInstanceIncidentsEndpoint({successResponse: incidents}),
				mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(task)}),
				mockQueryElementInstancesEndpoint({
					successResponse: HttpResponse.json(createQueryElementInstancesResponse([task])),
				}),
			);
			await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID, '/history');

			await page.getByRole('treeitem', {name: /Task 1/}).click();

			await expect(operatePreviewProcessInstancePage.tab('Incidents')).toHaveAttribute('aria-selected', 'true');
		});
	});

	test('should show the permission message when access to the instance is forbidden', async ({
		network,
		operatePreviewProcessInstancePage,
		page,
	}) => {
		network.use(
			mockGetProcessInstanceEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 403}), {status: 403}),
			}),
		);

		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

		await expect(
			page.getByRole('heading', {name: 'You do not have permission to view this information'}),
		).toBeVisible();
		await expect(operatePreviewProcessInstancePage.header).not.toBeVisible();
	});

	test('should notify and redirect to the process list when the instance does not exist', async ({
		network,
		operatePreviewProcessInstancePage,
		page,
	}) => {
		network.use(
			mockGetProcessInstanceEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 404}), {status: 404}),
			}),
		);

		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

		await expect(page).toHaveURL(/\/operate-preview\/processes\?/);
		await expect(page.getByText(`Instance ${PROCESS_INSTANCE_ID} could not be found`)).toBeVisible();
	});

	test('should offer a retry that recovers from a generic error', async ({
		network,
		operatePreviewProcessInstancePage,
		page,
	}) => {
		network.use(mockGetProcessInstanceEndpoint({successResponse: new HttpResponse(null, {status: 500})}));
		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
		await expect(page.getByRole('button', {name: 'Try again'})).toBeVisible();

		network.use(
			mockGetProcessInstanceEndpoint({
				successResponse: HttpResponse.json(createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})),
			}),
		);
		await page.getByRole('button', {name: 'Try again'}).click();

		await expect(operatePreviewProcessInstancePage.header).toBeVisible();
	});
});
