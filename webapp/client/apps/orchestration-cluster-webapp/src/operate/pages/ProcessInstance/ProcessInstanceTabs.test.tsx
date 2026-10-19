/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup} from 'vitest-browser-react';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import {useRouterState} from '@tanstack/react-router';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockGetElementInstanceEndpoint,
	mockGetProcessInstanceWaitStateStatisticsEndpoint,
	mockQueryElementInstanceIncidentsEndpoint,
	mockQueryElementInstancesEndpoint,
	mockQueryProcessInstanceIncidentsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {
	createElementInstance,
	createQueryElementInstancesResponse,
} from '#/shared-test-modules/api-mocks/element-instances';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {ProcessInstancePageProvider} from './ProcessInstancePageProvider';
import {ProcessInstanceTabs} from './ProcessInstanceTabs';
import {processInstanceSearchSchema} from './processInstanceSearch';

const PROCESS_INSTANCE_ID = '2251799813685280';

afterEach(async () => {
	await cleanup();
});

function incidentsCount(totalItems: number) {
	return HttpResponse.json(
		createPaginatedResponse({
			items: [],
			page: {totalItems, hasMoreTotalItems: false, startCursor: null, endCursor: null},
		}),
	);
}

function waitStates(items: {elementId: string; waitingCount: number}[] = []) {
	return mockGetProcessInstanceWaitStateStatisticsEndpoint({
		successResponse: HttpResponse.json(createPaginatedResponse({items})),
	});
}

function renderTabs({
	initialEntry,
	processInstance = createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID, hasIncident: false}),
	isHistoryTabVisible = false,
}: {
	initialEntry: string;
	processInstance?: ReturnType<typeof createProcessInstance>;
	isHistoryTabVisible?: boolean;
}) {
	function Tabs() {
		const search = useRouterState({select: (state) => state.location.search});
		return (
			<ProcessInstancePageProvider
				processInstanceId={PROCESS_INSTANCE_ID}
				processInstance={processInstance}
				search={processInstanceSearchSchema.parse(search)}
			>
				<ProcessInstanceTabs isHistoryTabVisible={isHistoryTabVisible} />
			</ProcessInstancePageProvider>
		);
	}

	return renderWithRouter(Tabs, {
		path: '/operate/processes/$processInstanceId/$',
		initialEntry: `/operate/processes/${PROCESS_INSTANCE_ID}/${initialEntry}`,
	});
}

describe('<ProcessInstanceTabs />', () => {
	it('should only show Variables for a process without incidents, selection or wait states', async ({worker}) => {
		worker.use(waitStates());
		const screen = await renderTabs({initialEntry: 'variables'});

		await expect.element(screen.getByRole('link', {name: 'Variables'})).toHaveAttribute('aria-current', 'page');
		await expect.element(screen.getByRole('link', {name: 'Incidents'})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('link', {name: 'Details'})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('link', {name: 'Instance History'})).not.toBeInTheDocument();
	});

	it('should show Instance History when the layout requests it', async ({worker}) => {
		worker.use(waitStates());
		const screen = await renderTabs({initialEntry: 'history', isHistoryTabVisible: true});

		await expect.element(screen.getByRole('link', {name: 'Instance History'})).toHaveAttribute('aria-current', 'page');
		expect(screen.router.state.location.pathname).toBe(`/operate/processes/${PROCESS_INSTANCE_ID}/history`);
	});

	it('should show the process incidents count and keep the selected tab while navigating', async ({worker}) => {
		worker.use(waitStates(), mockQueryProcessInstanceIncidentsEndpoint({successResponse: incidentsCount(3)}));
		const screen = await renderTabs({
			initialEntry: 'incidents',
			processInstance: createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID, hasIncident: true}),
		});

		await expect.element(screen.getByRole('link', {name: 'Incidents 3'})).toHaveAttribute('aria-current', 'page');

		await userEvent.click(screen.getByRole('link', {name: 'Variables'}));

		await expect
			.poll(() => screen.router.state.location.pathname)
			.toBe(`/operate/processes/${PROCESS_INSTANCE_ID}/variables`);
		await expect.element(screen.getByRole('link', {name: 'Variables'})).toHaveAttribute('aria-current', 'page');
	});

	it('should redirect a hidden Incidents tab to Details when the selected element has no incidents', async ({
		worker,
	}) => {
		worker.use(
			waitStates(),
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryElementInstancesResponse([], 2)),
			}),
			mockQueryProcessInstanceIncidentsEndpoint({
				schema: z.object({filter: z.object({state: z.literal('ACTIVE'), elementId: z.literal('user-task')})}),
				successResponse: incidentsCount(0),
				failureResponse: incidentsCount(5),
			}),
		);
		const screen = await renderTabs({
			initialEntry: 'incidents?elementId=user-task&tenant=tenant-a',
			processInstance: createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID, hasIncident: true}),
		});

		await expect
			.poll(() => screen.router.state.location.pathname)
			.toBe(`/operate/processes/${PROCESS_INSTANCE_ID}/details`);
		expect(screen.router.state.location.search).toEqual({elementId: 'user-task', tenant: 'tenant-a'});
		await expect.element(screen.getByRole('link', {name: /Incidents/})).not.toBeInTheDocument();
	});

	it('should count incidents of a selected element instance', async ({worker}) => {
		const elementInstance = createElementInstance({elementInstanceKey: '2251799813685299', hasIncident: true});
		worker.use(
			waitStates(),
			mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(elementInstance)}),
			mockQueryElementInstanceIncidentsEndpoint({successResponse: incidentsCount(1)}),
			mockQueryProcessInstanceIncidentsEndpoint({successResponse: incidentsCount(5)}),
		);
		const screen = await renderTabs({
			initialEntry: `incidents?elementId=user-task&elementInstanceKey=${elementInstance.elementInstanceKey}`,
			processInstance: createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID, hasIncident: true}),
		});

		await expect.element(screen.getByRole('link', {name: 'Incidents 1'})).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'Details'})).toBeVisible();
	});

	it('should redirect a hidden Details tab to Variables', async ({worker}) => {
		worker.use(waitStates());
		const screen = await renderTabs({initialEntry: 'details?tenant=tenant-a'});

		await expect
			.poll(() => screen.router.state.location.pathname)
			.toBe(`/operate/processes/${PROCESS_INSTANCE_ID}/variables`);
		expect(screen.router.state.location.search).toEqual({tenant: 'tenant-a'});
	});

	it('should redirect a hidden Incidents tab to Details when the process is waiting', async ({worker}) => {
		const processInstance = createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID, hasIncident: true});
		worker.use(
			waitStates([{elementId: processInstance.processDefinitionId, waitingCount: 1}]),
			mockQueryProcessInstanceIncidentsEndpoint({successResponse: incidentsCount(0)}),
		);
		const screen = await renderTabs({initialEntry: 'incidents', processInstance});

		await expect
			.poll(() => screen.router.state.location.pathname)
			.toBe(`/operate/processes/${PROCESS_INSTANCE_ID}/details`);
	});
});
