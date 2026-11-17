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
import {useState} from 'react';
import {useRouterState} from '@tanstack/react-router';
import {HttpResponse, http} from 'msw';
import type {SetupWorker} from 'msw/browser';
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {
	createElementInstance,
	createQueryElementInstancesResponse,
} from '#/shared-test-modules/api-mocks/element-instances';
import {
	mockGetElementInstanceEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {ProcessInstancePageProvider} from './ProcessInstancePageProvider';
import {processInstanceSearchSchema} from './processInstanceSearch';
import {initialModificationState} from './modifications/modificationReducer';
import {InstanceHistory} from './InstanceHistory';

const instance = createProcessInstance();
const path = '/operate/processes/$processInstanceId/variables';
const task = createElementInstance({
	elementId: 'task',
	elementInstanceKey: '2251799813800201',
	elementName: 'Ship order',
	endDate: '2026-01-15T11:00:00.000Z',
});
const treeChild = createElementInstance({
	elementId: 'tree-child',
	elementInstanceKey: '2251799813800202',
	elementName: 'Tree child',
});

const scopeSearches: SearchRequest[] = [];

type SearchRequest = {
	filter: Record<string, unknown>;
	sort: {field: string; order: string}[];
	page: {from: number; limit: number};
};

function Harness({modification = false}: {modification?: boolean}) {
	const search = useRouterState({select: (state) => state.location.search});
	const [id, setId] = useState(instance.processInstanceKey);
	return (
		<ProcessInstancePageProvider
			processInstanceId={id}
			processInstance={createProcessInstance({processInstanceKey: id})}
			search={processInstanceSearchSchema.parse(search)}
			modification={
				modification
					? {state: initialModificationState, isModificationModeEnabled: true, dispatch: () => {}}
					: undefined
			}
		>
			<div style={{height: 400}}>
				<InstanceHistory />
			</div>
			<output data-testid="url-search">{JSON.stringify(search)}</output>
			<button type="button" onClick={() => setId('2251799813699999')}>
				Switch instance
			</button>
		</ProcessInstancePageProvider>
	);
}

function mockHistory(
	worker: SetupWorker,
	{
		results = [task],
		total = results.length,
		status = 200,
	}: {results?: ElementInstance[]; total?: number; status?: number} = {},
) {
	const searches: SearchRequest[] = [];
	worker.use(
		mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text('')}),
		mockQueryBatchOperationItemsEndpoint({successResponse: HttpResponse.json(createQueryElementInstancesResponse())}),
		mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(task)}),
		http.post('/v2/element-instances/search', async ({request}) => {
			const body = (await request.json()) as SearchRequest;
			if (body.filter.elementInstanceScopeKey !== undefined) {
				scopeSearches.push(body);
				return HttpResponse.json(createQueryElementInstancesResponse([treeChild]));
			}
			searches.push(body);
			return status === 200
				? HttpResponse.json(createQueryElementInstancesResponse(results, total))
				: new HttpResponse(null, {status});
		}),
	);
	return searches;
}

function urlSearch(screen: Awaited<ReturnType<typeof render>>) {
	return () => JSON.parse(screen.getByTestId('url-search').element().textContent ?? '{}') as Record<string, unknown>;
}

function row(offset: number) {
	return createElementInstance({elementInstanceKey: String(1000 + offset), elementName: `Row ${offset}`});
}

function mockSearch(worker: SetupWorker, respond: (page: SearchRequest['page']) => Response | Promise<Response>) {
	const offsets: number[] = [];
	worker.use(
		mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text('')}),
		mockQueryBatchOperationItemsEndpoint({successResponse: HttpResponse.json(createQueryElementInstancesResponse())}),
		http.post('/v2/element-instances/search', async ({request}) => {
			const {page} = (await request.json()) as SearchRequest;
			offsets.push(page.from);
			return respond(page);
		}),
	);
	return offsets;
}

function fullPage(from: number) {
	return HttpResponse.json(
		createQueryElementInstancesResponse(
			Array.from({length: 50}, (_, index) => row(from + index)),
			400,
		),
	);
}

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

async function scrollToEnd(scroller: () => HTMLElement) {
	scroller().scrollTop = scroller().scrollHeight / 2;
	await wait(200);
	scroller().scrollTop = scroller().scrollHeight;
}

async function scrollToStart(scroller: () => HTMLElement) {
	scroller().scrollTop = scroller().scrollHeight / 2;
	await wait(200);
	scroller().scrollTop = 0;
}

function render(search = '', modification = false) {
	return renderWithRouter(() => <Harness modification={modification} />, {
		path,
		initialEntry: `/operate/processes/${instance.processInstanceKey}/variables${search ? `?${search}` : ''}`,
	});
}

describe('InstanceHistory search and filters', () => {
	beforeEach(() => {
		localStorage.clear();
		scopeSearches.length = 0;
	});
	afterEach(async () => {
		await cleanup();
		vi.restoreAllMocks();
	});

	it('should show the filtered list on direct entry with escaped search and status payload', async ({worker}) => {
		// given
		const searches = mockHistory(worker);

		// when
		const screen = await render('elementSearch=a*b&elementStatus=active');

		// then
		await expect.element(screen.getByRole('treeitem', {name: 'Ship order'})).toBeVisible();
		expect(searches[0]).toMatchObject({
			filter: {
				processInstanceKey: instance.processInstanceKey,
				$or: [{elementName: {$like: '*a\\*b*'}}, {elementId: {$like: '*a\\*b*'}}],
				state: 'ACTIVE',
				hasIncident: false,
			},
			sort: [
				{field: 'startDate', order: 'DESC'},
				{field: 'elementInstanceKey', order: 'DESC'},
			],
			page: {from: 0, limit: 50},
		});
		await expect.element(screen.getByRole('searchbox', {name: 'Search instance history'})).toHaveValue('a*b');
		await expect.element(screen.getByRole('button', {name: 'Active'})).toHaveAttribute('aria-pressed', 'true');
	});

	it('should filter incidents without a search term', async ({worker}) => {
		// given
		const searches = mockHistory(worker);

		// when
		await render('elementStatus=incidents');

		// then
		await expect
			.poll(() => searches[0]?.filter)
			.toEqual({
				processInstanceKey: instance.processInstanceKey,
				hasIncident: true,
			});
	});

	it('should fall back to all elements for an unknown status', async ({worker}) => {
		// given
		mockHistory(worker);

		// when
		const screen = await render('elementStatus=bogus');

		// then
		await expect.element(screen.getByRole('button', {name: 'All'})).toHaveAttribute('aria-pressed', 'true');
		await expect.element(screen.getByRole('treeitem', {name: 'Tree child'})).toBeVisible();
	});

	it('should persist the typed search in the URL and remove it when cleared', async ({worker}) => {
		// given
		mockHistory(worker);
		const screen = await render('tab=keep');

		// when
		await userEvent.type(screen.getByRole('searchbox', {name: 'Search instance history'}), '  ship  ');

		// then
		await expect.poll(urlSearch(screen)).toMatchObject({elementSearch: 'ship', tab: 'keep'});

		// when
		await userEvent.click(screen.getByRole('button', {name: /clear search/i}));

		// then
		await expect.poll(urlSearch(screen)).not.toHaveProperty('elementSearch');
		await expect.element(screen.getByRole('treeitem', {name: 'Tree child'})).toBeVisible();
	});

	it.for([
		['elementStatus=active', 'No active elements'],
		['elementStatus=incidents', 'No elements with incidents'],
		['elementSearch=zzz', 'No matching elements'],
	])('should show the empty state for %s', async ([search, heading], {worker}) => {
		// given
		mockHistory(worker, {results: []});

		// when
		const screen = await render(search);

		// then
		await expect.element(screen.getByRole('heading', {name: heading})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'All'})).toBeVisible();
	});

	it('should announce the matching count in a live region', async ({worker}) => {
		// given
		mockHistory(worker, {results: [task], total: 1});

		// when
		const screen = await render('elementSearch=ship');

		// then
		await expect.element(screen.getByText('1 matching element', {exact: true})).toHaveAttribute('aria-live', 'polite');
	});

	it('should announce an empty result in the live region', async ({worker}) => {
		// given
		mockHistory(worker, {results: [], total: 0});

		// when
		const screen = await render('elementSearch=ship');

		// then
		await expect.element(screen.getByText('0 matching elements', {exact: true})).toHaveAttribute('aria-live', 'polite');
	});

	it('should show the forbidden notification when the search is forbidden', async ({worker}) => {
		// given
		mockHistory(worker, {status: 403});

		// when
		const screen = await render('elementSearch=ship');

		// then
		await expect.element(screen.getByText('You do not have permission to view instance history')).toBeVisible();
		await expect.element(screen.getByRole('searchbox', {name: 'Search instance history'})).not.toBeInTheDocument();
	});

	it('should show the error message when the search fails', async ({worker}) => {
		// given
		mockHistory(worker, {status: 500});

		// when
		const screen = await render('elementSearch=ship');

		// then
		await expect.element(screen.getByText('Search results could not be fetched')).toBeVisible();
		await expect.element(screen.getByText('Refresh the page to try again')).toBeVisible();
	});

	it('should keep the selection across filtered and tree modes', async ({worker}) => {
		// given
		mockHistory(worker);
		const screen = await render(`elementSearch=ship&elementInstanceKey=${task.elementInstanceKey}`);
		await expect.element(screen.getByRole('treeitem', {name: 'Ship order'})).toHaveAttribute('aria-selected', 'true');

		// when
		await userEvent.click(screen.getByRole('button', {name: /clear search/i}));
		await userEvent.click(screen.getByRole('button', {name: 'Incidents'}));

		// then
		await expect
			.poll(urlSearch(screen))
			.toMatchObject({elementInstanceKey: Number(task.elementInstanceKey), elementStatus: 'incidents'});
		await expect.element(screen.getByRole('treeitem', {name: 'Ship order'})).toHaveAttribute('aria-selected', 'true');
	});

	it('should select a result through the URL', async ({worker}) => {
		// given
		mockHistory(worker);
		const screen = await render('elementSearch=ship');

		// when
		await userEvent.click(screen.getByRole('treeitem', {name: 'Ship order'}));

		// then
		await expect.poll(urlSearch(screen)).toMatchObject({elementInstanceKey: task.elementInstanceKey});
	});

	it('should sort oldest first, persist the preference and refetch', async ({worker}) => {
		// given
		const searches = mockHistory(worker);
		const screen = await render('elementSearch=ship');
		await expect.element(screen.getByRole('button', {name: 'Latest first'})).toBeVisible();

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Latest first'}));

		// then
		await expect.element(screen.getByRole('button', {name: 'Oldest first'})).toBeVisible();
		await expect
			.poll(() => searches.at(-1)?.sort)
			.toEqual([
				{field: 'startDate', order: 'ASC'},
				{field: 'elementInstanceKey', order: 'ASC'},
			]);
		expect(localStorage.getItem('operate.instanceHistorySortOrder')).toContain('asc');
	});

	it.for([0, 1])(
		'should return to the first page when a retained later window shrinks to %i rows',
		async (rows, {worker}) => {
			// given
			let shrunk = false;
			const offsets: number[] = [];
			worker.use(
				mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text('')}),
				mockQueryBatchOperationItemsEndpoint({
					successResponse: HttpResponse.json(createQueryElementInstancesResponse()),
				}),
				http.post('/v2/element-instances/search', async ({request}) => {
					const {page} = (await request.json()) as SearchRequest;
					offsets.push(page.from);
					if (shrunk) {
						return HttpResponse.json(
							createQueryElementInstancesResponse(
								page.from === 0
									? [task]
									: Array.from({length: page.from === 50 ? rows : 0}, (_, index) =>
											createElementInstance({elementInstanceKey: String(9000 + index)}),
										),
								51,
							),
						);
					}
					const items = Array.from({length: 50}, (_, index) =>
						createElementInstance({
							elementInstanceKey: String(1000 + page.from + index),
							elementName: `Row ${page.from + index}`,
						}),
					);
					return HttpResponse.json(createQueryElementInstancesResponse(items, 400));
				}),
			);
			const screen = await render('elementStatus=active');
			await expect.element(screen.getByRole('treeitem', {name: 'Row 0'})).toBeVisible();
			const scroller = () => screen.getByRole('tree', {name: 'Search results'}).element().parentElement!;
			for (const offset of [50, 100, 150, 200, 250]) {
				scroller().scrollTop = scroller().scrollHeight / 2;
				await new Promise((resolve) => setTimeout(resolve, 200));
				scroller().scrollTop = scroller().scrollHeight;
				await expect.poll(() => offsets.includes(offset), {timeout: 5000}).toBe(true);
			}

			// when
			await userEvent.click(screen.getByRole('button', {name: 'All'}));
			await expect.element(screen.getByRole('tree', {name: 'Search results'})).not.toBeInTheDocument();
			shrunk = true;
			await userEvent.click(screen.getByRole('button', {name: 'Active'}));
			await expect.poll(() => offsets.at(-1), {timeout: 15000}).toBe(0);

			// then
			await expect.element(screen.getByRole('treeitem', {name: 'Ship order'})).toBeVisible();
		},
	);

	it('should follow a URL search change after committing a draft with trailing spaces', async ({worker}) => {
		// given
		mockHistory(worker);
		const screen = await render();
		const input = screen.getByRole('searchbox', {name: 'Search instance history'});
		await userEvent.type(input, 'foo ');
		await expect.poll(urlSearch(screen)).toMatchObject({elementSearch: 'foo'});

		// when
		await screen.router.navigate({
			to: '.',
			search: (current: Record<string, unknown>) => ({...current, elementSearch: 'bar'}),
			replace: true,
		});
		await wait(1200);

		// then
		await expect.element(input).toHaveValue('bar');
		expect(urlSearch(screen)()).toMatchObject({elementSearch: 'bar'});
	});

	it('should not carry an unsent search draft over to another instance', async ({worker}) => {
		// given
		mockHistory(worker);
		const screen = await render();
		const input = screen.getByRole('searchbox', {name: 'Search instance history'});
		await userEvent.type(input, 'foo');

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Switch instance'}));
		await wait(1200);

		// then
		await expect.element(screen.getByRole('searchbox', {name: 'Search instance history'})).toHaveValue('');
		expect(urlSearch(screen)()).not.toHaveProperty('elementSearch');
	});

	it('should keep the search controls usable when the history tree fails', async ({worker}) => {
		// given
		mockSearch(worker, () => new HttpResponse(null, {status: 500}));

		// when
		const screen = await render();

		// then
		await expect.element(screen.getByText('Instance history could not be fetched. Please refresh.')).toBeVisible();
		await expect.element(screen.getByRole('searchbox', {name: 'Search instance history'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Incidents'})).toBeEnabled();
	});

	it('should keep the previously first row in place when a previous page is prepended', async ({worker}) => {
		// given
		const offsets = mockSearch(worker, (page) => fullPage(page.from));
		const screen = await render('elementStatus=active');
		await expect.element(screen.getByRole('treeitem', {name: 'Row 0'})).toBeVisible();
		const scroller = () => screen.getByRole('tree', {name: 'Search results'}).element().parentElement!;
		for (const offset of [50, 100, 150, 200, 250]) {
			await scrollToEnd(scroller);
			await expect.poll(() => offsets.includes(offset), {timeout: 5000}).toBe(true);
		}
		await expect.element(screen.getByRole('treeitem', {name: 'Row 0'})).not.toBeInTheDocument();
		await wait(300);
		const rowHeight = screen.getByRole('treeitem', {name: 'Row 50'}).element().getBoundingClientRect().height;

		// when
		await scrollToStart(scroller);
		await expect.element(screen.getByRole('treeitem', {name: 'Row 0'})).toBeInTheDocument();

		// then
		await expect.poll(() => scroller().scrollTop).toBeGreaterThanOrEqual(49 * rowHeight);
		expect(scroller().scrollTop).toBeLessThanOrEqual(51 * rowHeight);
	});

	it('should keep the last loaded rows in view when paging forward evicts the first page', async ({worker}) => {
		// given
		const offsets = mockSearch(worker, (page) => fullPage(page.from));
		const screen = await render('elementStatus=active');
		await expect.element(screen.getByRole('treeitem', {name: 'Row 0'})).toBeVisible();
		const scroller = () => screen.getByRole('tree', {name: 'Search results'}).element().parentElement!;
		for (const offset of [50, 100, 150, 200]) {
			await scrollToEnd(scroller);
			await expect.poll(() => offsets.includes(offset), {timeout: 5000}).toBe(true);
		}
		await expect.element(screen.getByRole('treeitem', {name: 'Row 249'})).toBeInTheDocument();

		// when
		await scrollToEnd(scroller);
		await expect.poll(() => offsets.includes(250), {timeout: 5000}).toBe(true);
		await expect.element(screen.getByRole('treeitem', {name: 'Row 0', exact: true})).not.toBeInTheDocument();
		await wait(300);

		// then
		const viewport = scroller().getBoundingClientRect();
		const lastLoaded = screen.getByRole('treeitem', {name: 'Row 249'}).element().getBoundingClientRect();
		expect(lastLoaded.bottom).toBeGreaterThan(viewport.top);
		expect(lastLoaded.top).toBeLessThan(viewport.bottom);
	});

	it('should keep rows in place when overlapping pages are evicted and prepended', async ({worker}) => {
		// given
		const delayed = new Set<number>();
		const offsets = mockSearch(worker, async (page) => {
			if (delayed.has(page.from)) {
				await wait(500);
			}
			return HttpResponse.json(
				createQueryElementInstancesResponse(
					Array.from({length: 50}, (_, index) => row(page.from === 0 ? index : page.from - 10 + index)),
					400,
				),
			);
		});
		const screen = await render('elementStatus=active');
		await expect.element(screen.getByRole('treeitem', {name: 'Row 0', exact: true})).toBeVisible();
		const scroller = () => screen.getByRole('tree', {name: 'Search results'}).element().parentElement!;
		const top = (name: string) =>
			screen.getByRole('treeitem', {name, exact: true}).element().getBoundingClientRect().top -
			scroller().getBoundingClientRect().top;
		for (const offset of [50, 100, 150, 200]) {
			await scrollToEnd(scroller);
			await expect.poll(() => offsets.includes(offset), {timeout: 5000}).toBe(true);
		}
		await expect.element(screen.getByRole('treeitem', {name: 'Row 239', exact: true})).toBeInTheDocument();
		const rowHeight = screen
			.getByRole('treeitem', {name: 'Row 50', exact: true})
			.element()
			.getBoundingClientRect().height;

		// when
		delayed.add(250);
		await scrollToEnd(scroller);
		await wait(100);
		const beforeEviction = top('Row 239');
		await expect.element(screen.getByRole('treeitem', {name: 'Row 0', exact: true})).not.toBeInTheDocument();
		await wait(300);

		// then
		expect(Math.abs(top('Row 239') - beforeEviction)).toBeLessThan(rowHeight / 2);

		// when
		delayed.add(0);
		await scrollToStart(scroller);
		await wait(100);
		const beforePrepend = top('Row 40');
		await expect.element(screen.getByRole('treeitem', {name: 'Row 0', exact: true})).toBeInTheDocument();
		await wait(300);

		// then
		expect(Math.abs(top('Row 40') - beforePrepend)).toBeLessThan(rowHeight / 2);
	});

	it('should not render a row twice when live data shifts it across pages', async ({worker}) => {
		// given
		const offsets = mockSearch(worker, (page) =>
			HttpResponse.json(
				createQueryElementInstancesResponse(
					Array.from({length: 50}, (_, index) => row(page.from === 0 ? index : 49 + index)),
					400,
				),
			),
		);
		const screen = await render('elementStatus=active');
		await expect.element(screen.getByRole('treeitem', {name: 'Row 0'})).toBeVisible();
		const scroller = () => screen.getByRole('tree', {name: 'Search results'}).element().parentElement!;

		// when
		await scrollToEnd(scroller);
		await expect.poll(() => offsets.includes(50), {timeout: 5000}).toBe(true);
		await expect.element(screen.getByRole('treeitem', {name: 'Row 98'})).toBeInTheDocument();

		// then
		expect(screen.getByRole('treeitem', {name: 'Row 49', exact: true}).elements()).toHaveLength(1);
	});

	it('should stay at the top and show rows a poll inserts above the first result', async ({worker}) => {
		// given
		let items = Array.from({length: 30}, (_, index) => row(index + 1));
		mockSearch(worker, () => HttpResponse.json(createQueryElementInstancesResponse(items)));
		const screen = await render('elementSearch=row');
		await expect.element(screen.getByRole('treeitem', {name: 'Row 1', exact: true})).toBeVisible();
		const scroller = () => screen.getByRole('tree', {name: 'Search results'}).element().parentElement!;

		// when
		items = [row(0), ...items];
		await expect.element(screen.getByRole('treeitem', {name: 'Row 0', exact: true}), {timeout: 15000}).toBeVisible();

		// then
		expect(scroller().scrollTop).toBe(0);
	});

	it('should not start a next-page fetch while a previous-page fetch is pending', async ({worker}) => {
		// given
		let delayFirstPage = false;
		const offsets: number[] = [];
		worker.use(
			mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text('')}),
			mockQueryBatchOperationItemsEndpoint({successResponse: HttpResponse.json(createQueryElementInstancesResponse())}),
			http.post('/v2/element-instances/search', async ({request}) => {
				const {page} = (await request.json()) as SearchRequest;
				offsets.push(page.from);
				if (delayFirstPage && page.from === 0) {
					await new Promise((resolve) => setTimeout(resolve, 2000));
				}
				const items = Array.from({length: 50}, (_, index) =>
					createElementInstance({
						elementInstanceKey: String(1000 + page.from + index),
						elementName: `Row ${page.from + index}`,
					}),
				);
				return HttpResponse.json(createQueryElementInstancesResponse(items, 400));
			}),
		);
		const screen = await render('elementStatus=active');
		await expect.element(screen.getByRole('treeitem', {name: 'Row 0'})).toBeVisible();
		const scroller = () => screen.getByRole('tree', {name: 'Search results'}).element().parentElement!;
		const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));
		for (const offset of [50, 100, 150, 200, 250]) {
			scroller().scrollTop = scroller().scrollHeight / 2;
			await wait(200);
			scroller().scrollTop = scroller().scrollHeight;
			await expect.poll(() => offsets.includes(offset), {timeout: 5000}).toBe(true);
		}
		await wait(300);

		// when
		delayFirstPage = true;
		scroller().scrollTop = scroller().scrollHeight / 2;
		await wait(200);
		scroller().scrollTop = 0;
		await expect.poll(() => offsets.at(-1), {timeout: 3000}).toBe(0);
		scroller().scrollTop = scroller().scrollHeight / 2;
		await wait(200);
		scroller().scrollTop = scroller().scrollHeight;
		await wait(800);

		// then
		expect(offsets).not.toContain(300);
	});

	it('should always show end dates in filtered rows regardless of the timestamps switch', async ({worker}) => {
		// given
		mockHistory(worker);
		const screen = await render('elementSearch=ship');
		const timestamp = () => screen.getByRole('treeitem', {name: 'Ship order'}).element().textContent;
		await expect.element(screen.getByRole('treeitem', {name: 'Ship order'})).toBeVisible();
		expect(timestamp()).toMatch(/2026/);

		// when
		(screen.getByRole('switch', {name: 'End date'}).element() as HTMLElement).click();
		await wait(300);

		// then
		expect(timestamp()).toMatch(/2026/);
	});

	it('should trim the draft on blur and keep typed spaces while editing', async ({worker}) => {
		// given
		mockHistory(worker);
		const screen = await render();
		const input = screen.getByRole('searchbox', {name: 'Search instance history'});

		// when
		await userEvent.type(input, 'foo ');
		await expect.poll(urlSearch(screen)).toMatchObject({elementSearch: 'foo'});
		await userEvent.type(input, 'bar');

		// then
		await expect.element(input).toHaveValue('foo bar');
		await expect.poll(urlSearch(screen)).toMatchObject({elementSearch: 'foo bar'});

		// when
		await userEvent.type(input, '  ');
		await userEvent.tab();

		// then
		await expect.element(input).toHaveValue('foo bar');
	});

	it('should replace the results with the error message when a poll fails', async ({worker}) => {
		// given
		let calls = 0;
		mockSearch(worker, () =>
			++calls === 1
				? HttpResponse.json(createQueryElementInstancesResponse([task]))
				: new HttpResponse(null, {status: 500}),
		);
		const screen = await render('elementSearch=ship');
		await expect.element(screen.getByRole('treeitem', {name: 'Ship order'})).toBeVisible();

		// when
		await expect.element(screen.getByText('Search results could not be fetched'), {timeout: 15000}).toBeVisible();

		// then
		await expect.element(screen.getByText('Refresh the page to try again')).toBeVisible();
		await expect.element(screen.getByRole('treeitem', {name: 'Ship order'})).not.toBeInTheDocument();
	});

	it('should anchor an ad-hoc inner instance result using the configured sort order', async ({worker}) => {
		// given
		localStorage.setItem('operate.instanceHistorySortOrder', JSON.stringify('asc'));
		const adHoc = createElementInstance({
			elementId: 'ad-hoc',
			elementInstanceKey: '2251799813800301',
			elementName: 'Ad hoc inner',
			type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
		});
		mockHistory(worker, {results: [adHoc]});
		const screen = await render('elementSearch=ad');

		// when
		await userEvent.click(screen.getByRole('treeitem', {name: 'Ad hoc inner'}));

		// then
		await expect
			.poll(() => scopeSearches.find(({page}) => page.limit === 1)?.sort)
			.toEqual([
				{field: 'startDate', order: 'ASC'},
				{field: 'elementInstanceKey', order: 'ASC'},
			]);
	});

	it('should force the tree and hide the controls in modification mode', async ({worker}) => {
		// given
		const searches = mockHistory(worker);

		// when
		const screen = await render('elementSearch=ship&elementStatus=active', true);

		// then
		await expect.element(screen.getByRole('treeitem', {name: 'Tree child'})).toBeVisible();
		expect(screen.getByRole('searchbox', {name: 'Search instance history'}).elements()).toHaveLength(0);
		expect(searches).toHaveLength(0);
		await expect.poll(urlSearch(screen)).toMatchObject({elementSearch: 'ship', elementStatus: 'active'});
	});
});
