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
import {HttpResponse, http, type RequestHandler} from 'msw';
import type {SetupWorker} from 'msw/browser';
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
} from '#/shared-test-modules/mock-handlers';
import {endpoints} from '#/shared/http/endpoints';
import {notificationsStore} from '#/shared/notifications/notifications.store';
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
	notificationsStore.reset();
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
	rootStatus?: number;
	batchItems?: object;
	handlers?: RequestHandler[];
};

function renderHistory(
	worker: SetupWorker,
	{query = '', rootStatus = 200, batchItems = createQueryElementInstancesResponse(), handlers = []}: RenderOptions = {},
) {
	worker.use(
		...handlers,
		mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text('')}),
		mockQueryBatchOperationItemsEndpoint({successResponse: HttpResponse.json(batchItems)}),
		mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(leaf)}),
		http.post(endpoints.queryElementInstances({}).url, async ({request}) => {
			const {filter} = (await request.json()) as {filter: {elementInstanceScopeKey?: string}};
			if (filter.elementInstanceScopeKey === instance.processInstanceKey) {
				return rootStatus === 200
					? HttpResponse.json(createQueryElementInstancesResponse([subprocess, leaf]))
					: new HttpResponse(null, {status: rootStatus});
			}
			return HttpResponse.json(createQueryElementInstancesResponse([nested]));
		}),
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

function renderPagedHistory(worker: SetupWorker) {
	worker.use(
		mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text('')}),
		mockQueryBatchOperationItemsEndpoint({successResponse: HttpResponse.json(createQueryElementInstancesResponse())}),
		mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(pagedItems[0])}),
		http.post(endpoints.queryElementInstances({}).url, async ({request}) => {
			const {page} = (await request.json()) as {page: {from: number}};
			return HttpResponse.json(
				createQueryElementInstancesResponse(pagedItems.slice(page.from, page.from + 100), pagedItems.length),
			);
		}),
	);
	return renderWithRouter(PagedHarness, {
		path,
		initialEntry: `/operate-preview/processes/${instance.processInstanceKey}/variables`,
	});
}

function scrollTreeTo(screen: Awaited<ReturnType<typeof renderPagedHistory>>, top: number) {
	const scroller = screen.getByRole('tree').element().parentElement?.parentElement as HTMLElement;
	scroller.scrollTo({top});
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
		const screen = await renderHistory(worker, {rootStatus: 500});

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

	it('should filter by status through the status toggle', async ({worker}) => {
		const screen = await renderHistory(worker);

		await userEvent.click(screen.getByRole('radio', {name: 'Incidents'}));

		await expect.element(screen.getByText('1 matching element')).toBeVisible();
	});

	it('should load the next page and keep the previous position when scrolled to the end', async ({worker}) => {
		const screen = await renderPagedHistory(worker);
		await expect.element(screen.getByRole('treeitem', {name: /Task 0/})).toBeVisible();

		scrollTreeTo(screen, 100000);

		await expect.element(screen.getByRole('treeitem', {name: /Task 149/})).toBeInTheDocument();
		await expect.element(screen.getByRole('treeitem', {name: /Task 0/})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('treeitem', {name: /Task 99/})).toBeInViewport();
	});

	it('should load the previous page when scrolled back to the top after paging forward', async ({worker}) => {
		const screen = await renderPagedHistory(worker);
		await expect.element(screen.getByRole('treeitem', {name: /Task 0/})).toBeVisible();
		scrollTreeTo(screen, 100000);
		await expect.element(screen.getByRole('treeitem', {name: /Task 149/})).toBeInTheDocument();

		scrollTreeTo(screen, 0);

		await expect.element(screen.getByRole('treeitem', {name: /Task 0/})).toBeInTheDocument();
		await expect.element(screen.getByRole('treeitem', {name: /Task 149/})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('treeitem', {name: /Task 50/})).toBeInViewport();
	});

	it('should not let keyboard users land on the paging sentinel', async ({worker}) => {
		const screen = await renderPagedHistory(worker);
		const sentinel = screen.getByRole('treeitem', {name: 'Loading more elements'});
		await expect.element(sentinel).toHaveAttribute('aria-disabled', 'true');

		await userEvent.click(screen.getByRole('treeitem', {name: /Task 0/}));
		await userEvent.keyboard('{End}');

		await expect.element(screen.getByRole('treeitem', {name: /Task 99/})).toHaveFocus();
		await expect.element(sentinel).not.toHaveFocus();
	});

	it('should show a forbidden message when the history is not accessible', async ({worker}) => {
		const screen = await renderHistory(worker, {rootStatus: 403});

		await expect.element(screen.getByText('You do not have permission to view instance history')).toBeVisible();
		await expect.element(screen.getByRole('searchbox', {name: 'Search instance history'})).not.toBeInTheDocument();
	});

	it('should toggle the sort order and reload the history', async ({worker}) => {
		const screen = await renderHistory(worker);
		await expect.element(screen.getByRole('treeitem', {name: /Subprocess/})).toBeVisible();
		worker.use(
			http.post(endpoints.queryElementInstances({}).url, () =>
				HttpResponse.json(createQueryElementInstancesResponse([leaf])),
			),
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

	it('should select a multi instance body together with its flag', async ({worker}) => {
		const multiInstance = createElementInstance({
			elementId: 'multi',
			elementInstanceKey: '2251799813800104',
			elementName: 'Multi task',
			type: 'MULTI_INSTANCE_BODY',
		});
		const screen = await renderHistory(worker, {
			handlers: [
				http.post(endpoints.queryElementInstances({}).url, () =>
					HttpResponse.json(createQueryElementInstancesResponse([multiInstance])),
				),
			],
		});

		await userEvent.click(screen.getByRole('treeitem', {name: 'Multi task (Multi Instance)'}));

		await expect
			.poll(() => screen.router.state.location.search)
			.toMatchObject({elementId: 'multi', elementInstanceKey: '2251799813800104', isMultiInstanceBody: true});
		await expect
			.element(screen.getByRole('treeitem', {name: 'Multi task (Multi Instance)'}))
			.toHaveAttribute('aria-selected', 'true');
	});

	it('should anchor the selection of an ad-hoc inner instance on its first child', async ({worker}) => {
		const innerInstance = createElementInstance({
			elementId: 'ad-hoc',
			elementInstanceKey: '2251799813800105',
			elementName: 'Ad-hoc inner',
			type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
		});
		const screen = await renderHistory(worker, {
			handlers: [
				http.post(endpoints.queryElementInstances({}).url, async ({request}) => {
					const {filter} = (await request.json()) as {filter: {elementInstanceScopeKey?: string}};
					return HttpResponse.json(
						createQueryElementInstancesResponse(
							filter.elementInstanceScopeKey === instance.processInstanceKey ? [innerInstance] : [nested],
						),
					);
				}),
			],
		});

		await userEvent.click(screen.getByRole('treeitem', {name: /Ad-hoc inner/}));

		await expect
			.poll(() => screen.router.state.location.search)
			.toMatchObject({elementInstanceKey: '2251799813800105', anchorElementId: 'nested'});
	});

	it('should follow the element search through browser back and forward navigation', async ({worker}) => {
		const screen = await renderHistory(worker);
		const searchbox = screen.getByRole('searchbox', {name: 'Search instance history'});
		await expect.element(searchbox).toHaveValue('');

		screen.router.history.push(
			`/operate-preview/processes/${instance.processInstanceKey}/variables?elementSearch=nested`,
		);
		await expect.element(searchbox).toHaveValue('nested');
		await expect.element(screen.getByText('1 matching element')).toBeVisible();

		screen.router.history.back();
		await expect.element(searchbox).toHaveValue('');
		await expect.element(screen.getByRole('treeitem', {name: /Leaf task/})).toBeVisible();

		screen.router.history.forward();
		await expect.element(searchbox).toHaveValue('nested');
		await expect.element(screen.getByText('1 matching element')).toBeVisible();
	});

	it('should explain a failed search and retry it on request', async ({worker}) => {
		const screen = await renderHistory(worker, {
			query: '?elementStatus=incidents',
			handlers: [
				http.post(endpoints.queryElementInstances({}).url, () => new HttpResponse(null, {status: 500}), {once: true}),
			],
		});
		await expect.element(screen.getByText('Search results could not be fetched')).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Try again'}));

		await expect.element(screen.getByText('1 matching element')).toBeVisible();
		await expect.element(screen.getByRole('treeitem', {name: /Nested task/})).toBeVisible();
	});
});
