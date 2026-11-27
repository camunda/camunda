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
import type {Job} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockGetElementInstanceEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockQueryElementInstancesEndpoint,
	mockQueryJobsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {
	createElementInstance,
	createQueryElementInstancesResponse,
} from '#/shared-test-modules/api-mocks/element-instances';
import {createListenerJob} from '#/shared-test-modules/api-mocks/jobs';
import {createPaginatedResponse, createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {ProcessInstancePageProvider} from '../ProcessInstancePageProvider';
import {InstanceHistory} from './InstanceHistory';
import {ListenersTab} from './ListenersTab';
import {processInstanceSearchSchema} from '../processInstanceSearch';

const PROCESS_INSTANCE_ID = '2251799813685280';
const ELEMENT_INSTANCE_KEY = '2251799813685299';
const BOTH_KINDS = z.object({
	$in: z.tuple([z.literal('BPMN_ELEMENT'), z.literal('EXECUTION_LISTENER'), z.literal('TASK_LISTENER')]),
});

afterEach(async () => {
	await cleanup();
});

function jobsResponse(items: Job[]) {
	return HttpResponse.json(
		createPaginatedResponse({
			items,
			page: {totalItems: items.length, startCursor: null, endCursor: null, hasMoreTotalItems: false},
		}),
	);
}

function renderListeners(search = '') {
	function Listeners() {
		const routeSearch = useRouterState({select: (state) => state.location.search});
		return (
			<div style={{height: '400px', display: 'flex', flexDirection: 'column'}}>
				<ProcessInstancePageProvider
					processInstanceId={PROCESS_INSTANCE_ID}
					processInstance={createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})}
					search={processInstanceSearchSchema.parse(routeSearch)}
				>
					<ListenersTab />
				</ProcessInstancePageProvider>
			</div>
		);
	}

	return renderWithRouter(Listeners, {
		path: '/operate-preview/processes/$processInstanceId/listeners',
		initialEntry: `/operate-preview/processes/${PROCESS_INSTANCE_ID}/listeners${search}`,
	});
}

function renderListenersWithHistory(search: string) {
	function ListenersWithHistory() {
		const routeSearch = useRouterState({select: (state) => state.location.search});
		return (
			<ProcessInstancePageProvider
				processInstanceId={PROCESS_INSTANCE_ID}
				processInstance={createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})}
				search={processInstanceSearchSchema.parse(routeSearch)}
			>
				<div style={{height: '300px', display: 'flex', flexDirection: 'column'}}>
					<InstanceHistory />
				</div>
				<section aria-label="Listeners" style={{height: '400px', display: 'flex', flexDirection: 'column'}}>
					<ListenersTab />
				</section>
			</ProcessInstancePageProvider>
		);
	}

	return renderWithRouter(ListenersWithHistory, {
		path: '/operate-preview/processes/$processInstanceId/listeners',
		initialEntry: `/operate-preview/processes/${PROCESS_INSTANCE_ID}/listeners${search}`,
	});
}

function selectedElementSearch(type: 'USER_TASK' | 'SERVICE_TASK') {
	return {
		search: `?elementId=task&elementInstanceKey=${ELEMENT_INSTANCE_KEY}`,
		elementInstance: createElementInstance({elementInstanceKey: ELEMENT_INSTANCE_KEY, elementId: 'task', type}),
	};
}

describe('<ListenersTab /> (design system)', () => {
	it('should list the listeners of the process instance with their details', async ({worker}) => {
		worker.use(
			mockQueryJobsEndpoint({
				schema: z.object({
					filter: z.object({
						processInstanceKey: z.literal(PROCESS_INSTANCE_ID),
						elementInstanceKey: z.literal(PROCESS_INSTANCE_ID),
						kind: BOTH_KINDS,
					}),
				}),
				successResponse: jobsResponse([
					createListenerJob({
						jobKey: '100',
						kind: 'TASK_LISTENER',
						state: 'FAILED',
						type: 'audit-listener',
						listenerEventType: 'COMPLETING',
						endTime: '2026-01-15T10:05:00.000Z',
					}),
				]),
				failureResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);
		const screen = await renderListeners();

		const row = screen.getByRole('row', {name: /\b100\b/});
		await expect.element(row.getByText('Task listener')).toBeVisible();
		await expect.element(row.getByText('Failed')).toBeVisible();
		await expect.element(row.getByText('audit-listener')).toBeVisible();
		await expect.element(row.getByText('Completing')).toBeVisible();
		await expect.element(row.getByText(/^2026-01-1[56] \d\d:\d\d:\d\d$/)).toBeVisible();
		await expect.element(screen.getByRole('combobox')).not.toBeInTheDocument();
	});

	it('should scope the listeners to the selected element instance', async ({worker}) => {
		const {search, elementInstance} = selectedElementSearch('SERVICE_TASK');
		worker.use(
			mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(elementInstance)}),
			mockQueryJobsEndpoint({
				schema: z.object({
					filter: z.object({
						processInstanceKey: z.literal(PROCESS_INSTANCE_ID),
						elementId: z.literal('task'),
						elementInstanceKey: z.literal(ELEMENT_INSTANCE_KEY),
					}),
				}),
				successResponse: jobsResponse([createListenerJob({jobKey: '101'})]),
				failureResponse: jobsResponse([]),
			}),
		);
		const screen = await renderListeners(search);

		await expect.element(screen.getByRole('cell', {name: '101', exact: true})).toBeVisible();
	});

	it('should list the listeners of every instance when the selected element has several', async ({worker}) => {
		worker.use(
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryElementInstancesResponse([], 3)),
			}),
			mockQueryJobsEndpoint({
				schema: z.object({
					filter: z.object({
						processInstanceKey: z.literal(PROCESS_INSTANCE_ID),
						elementId: z.literal('task'),
						kind: BOTH_KINDS,
					}),
				}),
				successResponse: jobsResponse([createListenerJob({jobKey: '102'})]),
				failureResponse: jobsResponse([]),
			}),
		);
		const screen = await renderListeners('?elementId=task');

		await expect.element(screen.getByRole('cell', {name: '102', exact: true})).toBeVisible();
	});

	it('should not list the listeners of every instance when the selected element cannot be resolved', async ({
		worker,
	}) => {
		worker.use(
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
			mockQueryJobsEndpoint({successResponse: jobsResponse([createListenerJob({jobKey: '103'})])}),
		);
		const screen = await renderListeners('?elementId=task');

		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: '103', exact: true})).not.toBeInTheDocument();
	});

	it('should drop the previous element listeners when the next selection cannot be resolved', async ({worker}) => {
		const {search, elementInstance} = selectedElementSearch('SERVICE_TASK');
		const otherElementInstance = createElementInstance({
			elementInstanceKey: '2251799813685400',
			elementId: 'other',
			elementName: 'Other task',
			type: 'SERVICE_TASK',
		});
		worker.use(
			mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text('')}),
			mockQueryBatchOperationItemsEndpoint({successResponse: HttpResponse.json(createPaginatedResponse())}),
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryElementInstancesResponse([{...elementInstance, elementName: 'Task'}, otherElementInstance]),
				),
			}),
			mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(elementInstance)}),
			mockQueryJobsEndpoint({successResponse: jobsResponse([createListenerJob({jobKey: '501'})])}),
		);
		const screen = await renderListenersWithHistory(search);
		const listeners = screen.getByRole('region', {name: 'Listeners'});
		await expect.element(listeners.getByRole('cell', {name: '501', exact: true})).toBeVisible();

		worker.use(
			mockGetElementInstanceEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);
		await userEvent.click(screen.getByRole('treeitem', {name: /Other task/}));

		await expect.element(listeners.getByText("Couldn't fetch data")).toBeVisible();
		await expect.element(listeners.getByRole('cell', {name: '501', exact: true})).not.toBeInTheDocument();
	});

	it('should show an error message when the listeners cannot be fetched', async ({worker}) => {
		const {search, elementInstance} = selectedElementSearch('SERVICE_TASK');
		worker.use(
			mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(elementInstance)}),
			mockQueryJobsEndpoint({successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500})}),
		);
		const screen = await renderListeners(search);

		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
		await expect.element(screen.getByText('No execution listeners on this element')).not.toBeInTheDocument();
	});

	it('should show an empty message for an element without listeners', async ({worker}) => {
		const {search, elementInstance} = selectedElementSearch('SERVICE_TASK');
		worker.use(
			mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(elementInstance)}),
			mockQueryJobsEndpoint({successResponse: jobsResponse([])}),
		);
		const screen = await renderListeners(search);

		await expect.element(screen.getByText('No execution listeners on this element')).toBeVisible();
	});

	it('should filter the listeners of a user task by type', async ({worker}) => {
		const {search, elementInstance} = selectedElementSearch('USER_TASK');
		worker.use(
			mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(elementInstance)}),
			mockQueryJobsEndpoint({
				successResponse: jobsResponse([
					createListenerJob({jobKey: '201', kind: 'EXECUTION_LISTENER'}),
					createListenerJob({jobKey: '202', kind: 'TASK_LISTENER'}),
				]),
			}),
		);
		const screen = await renderListeners(search);
		await expect.element(screen.getByRole('cell', {name: '202', exact: true})).toBeVisible();

		worker.use(
			mockQueryJobsEndpoint({
				schema: z.object({filter: z.object({kind: z.literal('TASK_LISTENER')})}),
				successResponse: jobsResponse([createListenerJob({jobKey: '202', kind: 'TASK_LISTENER'})]),
				failureResponse: jobsResponse([
					createListenerJob({jobKey: '201'}),
					createListenerJob({jobKey: '202', kind: 'TASK_LISTENER'}),
				]),
			}),
		);
		await userEvent.click(screen.getByRole('combobox'));
		await userEvent.click(screen.getByRole('option', {name: 'User task listeners'}));

		await expect.element(screen.getByRole('cell', {name: '201', exact: true})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('cell', {name: '202', exact: true})).toBeVisible();
	});

	it('should explain which listeners a user task is missing', async ({worker}) => {
		const {search, elementInstance} = selectedElementSearch('USER_TASK');
		worker.use(
			mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(elementInstance)}),
			mockQueryJobsEndpoint({successResponse: jobsResponse([])}),
		);
		const screen = await renderListeners(search);

		await expect.element(screen.getByText('No listeners on this element')).toBeVisible();
		await expect.element(screen.getByRole('combobox')).toBeDisabled();
	});

	it('should explain when a user task has no listeners of the filtered type', async ({worker}) => {
		const {search, elementInstance} = selectedElementSearch('USER_TASK');
		worker.use(
			mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(elementInstance)}),
			mockQueryJobsEndpoint({successResponse: jobsResponse([createListenerJob({jobKey: '301'})])}),
		);
		const screen = await renderListeners(search);
		await expect.element(screen.getByRole('cell', {name: '301', exact: true})).toBeVisible();

		worker.use(mockQueryJobsEndpoint({successResponse: jobsResponse([])}));
		await userEvent.click(screen.getByRole('combobox'));
		await userEvent.click(screen.getByRole('option', {name: 'User task listeners'}));

		await expect.element(screen.getByText('No user task listeners on this element')).toBeVisible();

		await userEvent.click(screen.getByRole('combobox'));
		await userEvent.click(screen.getByRole('option', {name: 'Execution listeners'}));

		await expect.element(screen.getByText('No execution listeners on this element')).toBeVisible();
	});
});
