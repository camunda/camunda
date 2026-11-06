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
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createOperateClientConfigScript} from '#/shared-test-modules/api-mocks/operate-client-config';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createCallHierarchy} from '#/shared-test-modules/api-mocks/call-hierarchy';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
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
		await expect(operatePreviewProcessInstancePage.bottomPanelPlaceholder).toBeVisible();
		await expect(operatePreviewProcessInstancePage.variablesTab).toBeVisible();
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
