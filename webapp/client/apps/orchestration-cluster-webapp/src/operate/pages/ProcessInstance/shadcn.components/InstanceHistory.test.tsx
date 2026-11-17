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
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import type {SetupWorker} from 'msw/browser';
import {z} from 'zod';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {
	createElementInstance,
	createQueryElementInstancesResponse,
} from '#/shared-test-modules/api-mocks/element-instances';
import {
	mockGetElementInstanceEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockQueryElementInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {ProcessInstancePageProvider} from '../ProcessInstancePageProvider';
import {processInstanceSearchSchema} from '../processInstanceSearch';
import {useRouterState} from '@tanstack/react-router';
import {InstanceHistory} from './InstanceHistory';

const instance = createProcessInstance({processDefinitionName: 'Order process'});
const path = '/operate-preview/processes/$processInstanceId/variables';
const subprocess = createElementInstance({
	elementId: 'subprocess',
	elementInstanceKey: '2251799813800101',
	elementName: 'Subprocess',
	type: 'SUB_PROCESS',
	state: 'COMPLETED',
	endDate: '2026-01-15T11:00:00.000Z',
});
const leaf = createElementInstance({
	elementId: 'leaf',
	elementInstanceKey: '2251799813800102',
	elementName: 'Leaf task',
	state: 'ACTIVE',
});
const nested = createElementInstance({
	elementId: 'nested',
	elementInstanceKey: '2251799813800103',
	elementName: 'Nested task',
	hasIncident: true,
});

afterEach(async () => {
	await cleanup();
	localStorage.clear();
});

function PagedHarness() {
	return (
		<div style={{height: '320px'}} className="flex flex-col">
			<Harness />
		</div>
	);
}

function Harness() {
	const search = useRouterState({select: (state) => state.location.search});
	return (
		<ProcessInstancePageProvider
			processInstanceId={instance.processInstanceKey}
			processInstance={instance}
			search={processInstanceSearchSchema.parse(search)}
		>
			<InstanceHistory />
		</ProcessInstancePageProvider>
	);
}

type RenderOptions = {
	query?: string;
	batchItems?: object;
	handlers?: ReturnType<typeof mockQueryElementInstancesEndpoint>[];
};

function scopedElementInstances(
	scopeKey: string,
	scopeItems: ElementInstance[],
	otherItems: ElementInstance[] = [nested],
) {
	return mockQueryElementInstancesEndpoint({
		schema: z.object({filter: z.object({elementInstanceScopeKey: z.literal(scopeKey)})}),
		successResponse: HttpResponse.json(createQueryElementInstancesResponse(scopeItems)),
		failureResponse: HttpResponse.json(createQueryElementInstancesResponse(otherItems)),
	});
}

function renderHistory(
	worker: SetupWorker,
	{query = '', batchItems = createQueryElementInstancesResponse(), handlers = []}: RenderOptions = {},
) {
	worker.use(
		...handlers,
		mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text('')}),
		mockQueryBatchOperationItemsEndpoint({successResponse: HttpResponse.json(batchItems)}),
		mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(leaf)}),
		scopedElementInstances(instance.processInstanceKey, [subprocess, leaf]),
	);
	return renderWithRouter(Harness, {
		path,
		initialEntry: `/operate-preview/processes/${instance.processInstanceKey}/variables${query}`,
	});
}

const pagedItems = Array.from({length: 150}, (_, index) =>
	createElementInstance({
		elementId: `task-${index}`,
		elementInstanceKey: String(2251799813900000 + index),
		elementName: `Task ${index}`,
	}),
);

function renderPagedHistory(
	worker: SetupWorker,
	{pages, query = ''}: {pages: [[number, number], [number, number]]; query?: string},
) {
	worker.use(
		mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text('')}),
		mockQueryBatchOperationItemsEndpoint({successResponse: HttpResponse.json(createQueryElementInstancesResponse())}),
		mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(pagedItems[0])}),
		mockQueryElementInstancesEndpoint({
			schema: z.object({page: z.object({from: z.literal(0)})}),
			successResponse: HttpResponse.json(createQueryElementInstancesResponse(pagedItems.slice(...pages[0]), 150)),
			failureResponse: HttpResponse.json(createQueryElementInstancesResponse(pagedItems.slice(...pages[1]), 150)),
		}),
	);
	return renderWithRouter(PagedHarness, {
		path,
		initialEntry: `/operate-preview/processes/${instance.processInstanceKey}/variables${query}`,
	});
}

function scrollToLoadingRow(screen: Awaited<ReturnType<typeof renderPagedHistory>>) {
	screen.getByRole('treeitem', {name: 'Loading more elements'}).element().scrollIntoView();
}

describe('InstanceHistory (design system)', () => {
	it('should render the process root with its top-level elements', async ({worker}) => {
		const screen = await renderHistory(worker);

		await expect.element(screen.getByRole('treeitem', {name: /Order process/})).toBeVisible();
		await expect.element(screen.getByRole('treeitem', {name: /Subprocess/})).toBeVisible();
		await expect.element(screen.getByRole('treeitem', {name: /Leaf task/})).toBeVisible();
	});

	it('should load and show the children of a sub process when it is expanded', async ({worker}) => {
		const screen = await renderHistory(worker);

		await userEvent.click(screen.getByRole('treeitem', {name: /Subprocess/}));
		await userEvent.keyboard('{ArrowRight}');

		await expect.element(screen.getByRole('treeitem', {name: /Nested task/})).toBeVisible();
	});

	it('should name the loading placeholder of a sub process and keep it out of keyboard navigation', async ({
		worker,
	}) => {
		const screen = await renderHistory(worker);
		await expect.element(screen.getByRole('treeitem', {name: /Subprocess/})).toBeVisible();
		worker.use(mockQueryElementInstancesEndpoint({successResponse: HttpResponse.json({}), delay: 'infinite'}));

		await userEvent.click(screen.getByRole('treeitem', {name: /Subprocess/}));
		await userEvent.keyboard('{ArrowRight}');

		await expect
			.element(screen.getByRole('treeitem', {name: 'Loading more elements'}))
			.toHaveAttribute('aria-disabled', 'true');
	});

	it('should select the clicked element', async ({worker}) => {
		const screen = await renderHistory(worker);

		await userEvent.click(screen.getByRole('treeitem', {name: /Leaf task/}));

		await expect.element(screen.getByRole('treeitem', {name: /Leaf task/})).toHaveAttribute('aria-selected', 'true');
	});

	it('should show end dates only when the timestamps switch is on', async ({worker}) => {
		const screen = await renderHistory(worker);
		await expect.element(screen.getByRole('treeitem', {name: /Subprocess/})).toBeVisible();
		await expect.element(screen.getByText('2026-01-15', {exact: false})).not.toBeInTheDocument();

		await userEvent.click(screen.getByRole('switch', {name: 'End date'}));

		await expect.element(screen.getByText('2026-01-15', {exact: false})).toBeVisible();
	});

	it('should list only matching elements when a status filter is applied', async ({worker}) => {
		const screen = await renderHistory(worker, {query: '?elementStatus=incidents'});

		await expect.element(screen.getByText('1 matching element')).toBeVisible();
		await expect.element(screen.getByRole('treeitem', {name: /Nested task/})).toBeVisible();
	});

	it('should offer a retry when the history cannot be fetched', async ({worker}) => {
		const screen = await renderHistory(worker, {
			handlers: [mockQueryElementInstancesEndpoint({successResponse: new HttpResponse(null, {status: 500})})],
		});

		await expect.element(screen.getByText('Instance history could not be fetched. Please refresh.')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Try again'})).toBeVisible();
	});

	it('should switch to filtered results after typing in the search field', async ({worker}) => {
		const screen = await renderHistory(worker);
		await expect.element(screen.getByRole('treeitem', {name: /Leaf task/})).toBeVisible();

		await userEvent.fill(screen.getByRole('searchbox', {name: 'Search instance history'}), 'nested');

		await expect.element(screen.getByText('1 matching element'), {timeout: 5000}).toBeVisible();
		await expect.element(screen.getByRole('treeitem', {name: /Leaf task/})).not.toBeInTheDocument();
	});

	it('should filter by status through the status filter', async ({worker}) => {
		const screen = await renderHistory(worker);
		await expect.element(screen.getByRole('radio', {name: 'All'})).toBeChecked();

		await userEvent.click(screen.getByRole('radio', {name: 'Incidents'}));

		await expect.element(screen.getByRole('radio', {name: 'Incidents'})).toBeChecked();
		await expect.element(screen.getByText('1 matching element')).toBeVisible();
	});

	it('should load the next page and keep the previous position when scrolled to the end', async ({worker}) => {
		const screen = await renderPagedHistory(worker, {
			pages: [
				[0, 100],
				[50, 150],
			],
		});
		await expect.element(screen.getByRole('treeitem', {name: /Task 0/})).toBeVisible();

		scrollToLoadingRow(screen);

		await expect.element(screen.getByRole('treeitem', {name: /Task 149/})).toBeInTheDocument();
		await expect.element(screen.getByRole('treeitem', {name: /Task 0/})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('treeitem', {name: /Task 99/})).toBeInViewport();
	});

	it('should load the previous page when scrolled back to the top after paging forward', async ({worker}) => {
		const screen = await renderPagedHistory(worker, {
			pages: [
				[0, 100],
				[50, 150],
			],
		});
		await expect.element(screen.getByRole('treeitem', {name: /Task 0/})).toBeVisible();
		scrollToLoadingRow(screen);
		await expect.element(screen.getByRole('treeitem', {name: /Task 149/})).toBeInTheDocument();

		scrollToLoadingRow(screen);

		await expect.element(screen.getByRole('treeitem', {name: /Task 0/})).toBeInTheDocument();
		await expect.element(screen.getByRole('treeitem', {name: /Task 149/})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('treeitem', {name: /Task 50/})).toBeInViewport();
	});

	it('should keep the last result in view when the next page of filtered results is loaded', async ({worker}) => {
		const screen = await renderPagedHistory(worker, {
			pages: [
				[0, 50],
				[50, 100],
			],
			query: '?elementStatus=incidents',
		});
		await expect.element(screen.getByRole('treeitem', {name: /Task 0/})).toBeVisible();

		scrollToLoadingRow(screen);

		await expect.element(screen.getByRole('treeitem', {name: /Task 99/})).toBeInTheDocument();
		await expect.element(screen.getByRole('treeitem', {name: /Task 49/})).toBeInViewport();
	});

	it('should not let keyboard users land on the paging sentinel', async ({worker}) => {
		const screen = await renderPagedHistory(worker, {
			pages: [
				[0, 100],
				[50, 150],
			],
		});
		const sentinel = screen.getByRole('treeitem', {name: 'Loading more elements'});
		await expect.element(sentinel).toHaveAttribute('aria-disabled', 'true');

		await userEvent.click(screen.getByRole('treeitem', {name: /Task 0/}));
		await userEvent.keyboard('{End}');

		await expect.element(screen.getByRole('treeitem', {name: /Task 99/})).toHaveFocus();
		await expect.element(sentinel).not.toHaveFocus();
	});

	it('should show a forbidden message when the history is not accessible', async ({worker}) => {
		const screen = await renderHistory(worker, {
			handlers: [mockQueryElementInstancesEndpoint({successResponse: new HttpResponse(null, {status: 403})})],
		});

		await expect.element(screen.getByText('You do not have permission to view instance history')).toBeVisible();
		await expect.element(screen.getByRole('searchbox', {name: 'Search instance history'})).not.toBeInTheDocument();
	});

	it('should toggle the sort order and reload the history', async ({worker}) => {
		const screen = await renderHistory(worker);
		await expect.element(screen.getByRole('treeitem', {name: /Subprocess/})).toBeVisible();
		worker.use(
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryElementInstancesResponse([leaf])),
			}),
		);

		await userEvent.click(screen.getByRole('button', {name: 'Latest first'}));

		await expect.element(screen.getByRole('button', {name: 'Oldest first'})).toBeVisible();
		await expect.element(screen.getByRole('treeitem', {name: /Subprocess/})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('treeitem', {name: /Leaf task/})).toBeVisible();
	});

	it('should show the migrated badge on the process root', async ({worker}) => {
		const screen = await renderHistory(worker, {
			batchItems: {
				items: [{processedDate: '2026-02-01T12:00:00.000Z'}],
				page: {totalItems: 1, hasMoreTotalItems: false, startCursor: null, endCursor: null},
			},
		});

		await expect.element(screen.getByText('Migrated 2026-02-01', {exact: false})).toBeVisible();
	});

	it('should not show the migrated badge when the instance was never migrated', async ({worker}) => {
		const screen = await renderHistory(worker);
		await expect.element(screen.getByRole('treeitem', {name: /Order process/})).toBeVisible();

		await expect.element(screen.getByText('Migrated', {exact: false})).not.toBeInTheDocument();
	});

	it('should select a multi instance body', async ({worker}) => {
		const multiInstance = createElementInstance({
			elementId: 'multi',
			elementInstanceKey: '2251799813800104',
			elementName: 'Multi task',
			type: 'MULTI_INSTANCE_BODY',
		});
		const screen = await renderHistory(worker, {
			handlers: [scopedElementInstances(instance.processInstanceKey, [multiInstance])],
		});

		await userEvent.click(screen.getByRole('treeitem', {name: 'active state Multi task (Multi Instance)'}));

		await expect
			.element(screen.getByRole('treeitem', {name: 'active state Multi task (Multi Instance)'}))
			.toHaveAttribute('aria-selected', 'true');
	});

	it('should explain a failed search and retry it on request', async ({worker}) => {
		const screen = await renderHistory(worker, {
			query: '?elementStatus=incidents',
			handlers: [
				mockQueryElementInstancesEndpoint({successResponse: new HttpResponse(null, {status: 500}), once: true}),
			],
		});
		await expect.element(screen.getByText('Search results could not be fetched')).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Try again'}));

		await expect.element(screen.getByText('1 matching element')).toBeVisible();
		await expect.element(screen.getByRole('treeitem', {name: /Nested task/})).toBeVisible();
	});
});
