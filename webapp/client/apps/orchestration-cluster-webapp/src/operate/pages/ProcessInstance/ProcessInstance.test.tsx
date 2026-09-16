/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup} from 'vitest-browser-react';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockCurrentUserEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockGetProcessInstanceCallHierarchyEndpoint,
	mockGetProcessInstanceEndpoint,
	mockGetProcessInstanceWaitStateStatisticsEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryProcessInstanceIncidentsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createCallHierarchy} from '#/shared-test-modules/api-mocks/call-hierarchy';
import {createPaginatedResponse, createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {ProcessInstance} from './ProcessInstance';

const PROCESS_INSTANCE_ID = '2251799813685280';
const PROCESS_XML =
	'<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="my-process"><callActivity id="call-activity" /></process></definitions>';

function renderPage() {
	return renderWithRouter(() => <ProcessInstance processInstanceId={PROCESS_INSTANCE_ID} search={{}} />, {
		path: '/operate/processes/$processInstanceId/variables',
		initialEntry: `/operate/processes/${PROCESS_INSTANCE_ID}/variables`,
	});
}

function getProcessInstancePageHandlers({
	processInstance = createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID}),
	callHierarchy = [],
	processDefinitions = createQueryProcessDefinitionsResponse(),
	waitStateItems = [],
	currentUser = createCurrentUser(),
}: {
	processInstance?: ReturnType<typeof createProcessInstance>;
	callHierarchy?: ReturnType<typeof createCallHierarchy>[];
	processDefinitions?: ReturnType<typeof createQueryProcessDefinitionsResponse>;
	waitStateItems?: {elementId: string; waitingCount: number}[];
	currentUser?: ReturnType<typeof createCurrentUser>;
} = {}) {
	return [
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(currentUser)}),
		mockGetProcessInstanceEndpoint({successResponse: HttpResponse.json(processInstance)}),
		mockQueryProcessDefinitionsEndpoint({successResponse: HttpResponse.json(processDefinitions)}),
		mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(PROCESS_XML)}),
		mockGetProcessInstanceCallHierarchyEndpoint({
			successResponse: HttpResponse.json(callHierarchy),
		}),
		mockGetProcessInstanceWaitStateStatisticsEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse({items: waitStateItems})),
		}),
	] as const;
}

describe('<ProcessInstance />', () => {
	beforeEach(() => {
		vi.spyOn(window, 'matchMedia').mockImplementation((query) => ({
			matches: false,
			media: query,
			onchange: null,
			addEventListener: vi.fn(),
			removeEventListener: vi.fn(),
			addListener: vi.fn(),
			removeListener: vi.fn(),
			dispatchEvent: vi.fn(),
		}));
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(async () => {
		await cleanup();
		vi.restoreAllMocks();
		sessionStorage.clear();
		notificationsStore.reset();
	});

	it('should render the shell with header and breadcrumb', async ({worker}) => {
		sessionStorage.setItem(
			'clientConfig',
			JSON.stringify(createSystemConfiguration({deployment: {isMultiTenancyEnabled: true, maxRequestSize: 0}})),
		);
		const processInstance = createProcessInstance({
			processInstanceKey: PROCESS_INSTANCE_ID,
			processDefinitionName: 'Order',
			tenantId: 'tenant-a',
		});
		worker.use(
			...getProcessInstancePageHandlers({
				processInstance,
				currentUser: createCurrentUser({tenants: [{tenantId: 'tenant-a', name: 'Tenant A', description: null}]}),
				waitStateItems: [{elementId: processInstance.processDefinitionId, waitingCount: 2}],
				callHierarchy: [
					createCallHierarchy({processInstanceKey: '1', processDefinitionName: 'Root Process'}),
					createCallHierarchy({processInstanceKey: '2', processDefinitionName: 'Parent Process'}),
					createCallHierarchy({processInstanceKey: PROCESS_INSTANCE_ID, processDefinitionName: 'Order'}),
				],
			}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByRole('heading', {name: 'Operate Process Instance'})).toBeInTheDocument();
		await expect
			.element(screen.getByRole('link', {name: 'Root Process'}))
			.toHaveAttribute('href', '/operate/processes/1');
		await expect
			.element(screen.getByRole('link', {name: 'Parent Process'}))
			.toHaveAttribute('href', '/operate/processes/2');
		await expect.element(screen.getByText('Start Date')).toBeVisible();
		await expect.element(screen.getByText('2 waiting')).toBeVisible();
		await expect.element(screen.getByText('Tenant', {exact: true})).toBeVisible();
		await expect.element(screen.getByText('Tenant A', {exact: true})).toBeVisible();
		await expect
			.element(screen.getByRole('link', {name: /View process .*Tenant A/}))
			.toHaveAttribute('href', expect.stringContaining('tenantId=tenant-a'));
	});

	it('should redirect to processes and notify when the process instance is not found', async ({worker}) => {
		worker.use(
			mockGetProcessInstanceEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 404}), {status: 404}),
			}),
		);

		const screen = await renderPage();

		await expect.poll(() => screen.router.state.location.pathname).toBe('/operate/processes');
		expect(screen.router.state.location.search).toMatchObject({
			active: true,
			incidents: true,
			suspended: true,
			completed: false,
			canceled: false,
		});
		await expect
			.poll(() => notificationsStore.notifications.map((notification) => notification.title))
			.toContain(`Instance ${PROCESS_INSTANCE_ID} could not be found`);
	});

	it.for([false, true])(
		'should recover from a generic instance read error (cached: %s)',
		async (isCached, {worker}) => {
			worker.use(...getProcessInstancePageHandlers());
			const failure = mockGetProcessInstanceEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			});
			if (!isCached) {
				worker.use(failure);
			}

			const screen = await renderPage();
			if (isCached) {
				await expect.element(screen.getByTestId('instance-header')).toBeVisible();
				worker.use(failure);
				await screen.queryClient.invalidateQueries({queryKey: ['processInstance', PROCESS_INSTANCE_ID]});
			}
			await expect.element(screen.getByRole('heading', {name: 'Something went wrong'})).toBeVisible();

			worker.use(...getProcessInstancePageHandlers());

			await userEvent.click(screen.getByRole('button', {name: 'Try again'}));

			await expect.element(screen.getByTestId('instance-header')).toBeVisible();
			await expect.element(screen.getByRole('heading', {name: 'Something went wrong'})).not.toBeInTheDocument();
		},
	);

	it.for([true, false])(
		'should scope draining metadata to this version (matching key: %s)',
		async (isMatching, {worker}) => {
			const processInstance = createProcessInstance({
				processInstanceKey: PROCESS_INSTANCE_ID,
				processDefinitionId: 'order-process',
				processDefinitionName: 'Order',
				hasIncident: true,
			});
			worker.use(
				mockQueryProcessInstanceIncidentsEndpoint({
					successResponse: HttpResponse.json({
						items: [],
						page: {totalItems: 3, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				}),
				...getProcessInstancePageHandlers({
					processInstance,
					processDefinitions: createQueryProcessDefinitionsResponse({
						items: [
							createProcessDefinition({
								processDefinitionId: 'order-process',
								processDefinitionKey: isMatching ? processInstance.processDefinitionKey : '999',
								state: 'DRAINING',
							}),
						],
					}),
				}),
			);

			const screen = await renderPage();

			await expect.element(screen.getByText('3 incidents')).toBeVisible();
			if (isMatching) {
				await expect.element(screen.getByTestId('draining-tag')).toBeVisible();
			} else {
				await expect.element(screen.getByTestId('draining-tag')).not.toBeInTheDocument();
			}
		},
	);
});
