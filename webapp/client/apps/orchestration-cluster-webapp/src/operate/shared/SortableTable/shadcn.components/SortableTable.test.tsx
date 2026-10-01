/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {describe, expect, vi} from 'vitest';
import {render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {createMemoryHistory, createRootRoute, createRoute, createRouter, RouterProvider} from '@tanstack/react-router';
import {it} from '#/vitest-modules/test-extend';
import {setUpFakeIntersectionObserver} from '#/vitest-modules/fake-intersection-observer';
import {SortableTable} from './SortableTable';

type Row = {id: string; name: string};

const ROWS: Row[] = [
	{id: 'row-1', name: 'Order process'},
	{id: 'row-2', name: 'Shipping process'},
];

const COLUMNS: React.ComponentProps<typeof SortableTable<Row>>['columns'] = [
	{key: 'name', label: 'Name', sortKey: 'name', isDefault: true, defaultOrder: 'asc', render: (row) => row.name},
	{key: 'id', label: 'Id', render: (row) => row.id},
];

type DistributivePartial<T> = T extends unknown ? Partial<T> : never;
type RenderOverrides = DistributivePartial<React.ComponentProps<typeof SortableTable<Row>>>;

async function renderTable(overrides: RenderOverrides = {}, {initialEntry = '/'}: {initialEntry?: string} = {}) {
	const props = {
		columns: COLUMNS,
		rows: ROWS,
		rowKey: (row: Row) => row.id,
		ariaLabel: 'Process instances',
		'data-testid': 'table',
		loadingPreviousPageLabel: 'Loading previous rows',
		loadingNextPageLabel: 'Loading more rows',
		...overrides,
	} as React.ComponentProps<typeof SortableTable<Row>>;
	let setRowsState: ((rows: Row[]) => void) | undefined;
	let setIsFetchingState: ((isFetching: boolean) => void) | undefined;
	let setIsFetchingPreviousPageState: ((isFetchingPreviousPage: boolean) => void) | undefined;
	let setIsFetchingNextPageState: ((isFetchingNextPage: boolean) => void) | undefined;
	function TestRouteComponent() {
		const [rows, setRows] = useState(props.rows);
		const [isFetching, setIsFetching] = useState(props.isFetching ?? false);
		const [isFetchingPreviousPage, setIsFetchingPreviousPage] = useState(props.isFetchingPreviousPage ?? false);
		const [isFetchingNextPage, setIsFetchingNextPage] = useState(props.isFetchingNextPage ?? false);
		setRowsState = setRows;
		setIsFetchingState = setIsFetching;
		setIsFetchingPreviousPageState = setIsFetchingPreviousPage;
		setIsFetchingNextPageState = setIsFetchingNextPage;
		return (
			<SortableTable<Row>
				{...props}
				rows={rows}
				isFetching={isFetching}
				isFetchingPreviousPage={isFetchingPreviousPage}
				isFetchingNextPage={isFetchingNextPage}
			/>
		);
	}
	const rootRoute = createRootRoute();
	const testRoute = createRoute({
		getParentRoute: () => rootRoute,
		path: '/',
		component: TestRouteComponent,
	});
	const router = createRouter({
		routeTree: rootRoute.addChildren([testRoute]),
		history: createMemoryHistory({initialEntries: [initialEntry]}),
		defaultPendingMinMs: 0,
	});

	await router.load();

	const screen = await render(<RouterProvider router={router} />);

	function setRows(rows: Row[]) {
		setRowsState?.(rows);
	}

	function setIsFetchingPreviousPage(isFetchingPreviousPage: boolean) {
		setIsFetchingPreviousPageState?.(isFetchingPreviousPage);
	}

	function setIsFetchingNextPage(isFetchingNextPage: boolean) {
		setIsFetchingNextPageState?.(isFetchingNextPage);
	}

	function setIsFetching(isFetching: boolean) {
		setIsFetchingState?.(isFetching);
	}

	return {...screen, router, setRows, setIsFetchingPreviousPage, setIsFetchingNextPage, setIsFetching};
}

describe('<SortableTable />', () => {
	it('should render every row and column', async () => {
		// given / when
		const screen = await renderTable();

		// then
		await expect.element(screen.getByText('Order process')).toBeVisible();
		await expect.element(screen.getByText('Shipping process')).toBeVisible();
		await expect.element(screen.getByText('row-1')).toBeVisible();
	});

	it('should expose ariaLabel as an accessible name on the table', async () => {
		// given / when
		const screen = await renderTable({ariaLabel: 'Process instances'});

		// then
		await expect.element(screen.getByRole('table', {name: 'Process instances'})).toBeVisible();
	});

	it('should render the caller-provided empty state inside the table when there are no rows', async () => {
		// given / when
		const screen = await renderTable({rows: [], emptyState: <div data-testid="empty-state">Nothing here</div>});

		// then
		await expect.element(screen.getByTestId('empty-state')).toBeVisible();
		await expect.element(screen.getByText('Name')).toBeVisible();
	});

	it('should hide the table header when hideHeaderWhenEmpty is set and there are no rows', async () => {
		// given / when
		const screen = await renderTable({
			rows: [],
			emptyState: <div data-testid="empty-state">Nothing here</div>,
			hideHeaderWhenEmpty: true,
		});

		// then
		await expect.element(screen.getByTestId('empty-state')).toBeVisible();
		expect(screen.getByText('Name').elements()).toHaveLength(0);
	});

	it('should show a loading overlay while refetching with existing rows', async () => {
		// given / when
		const screen = await renderTable({isFetching: true});

		// then
		await expect.element(screen.getByTestId('table-loading-overlay')).toBeVisible();
	});

	it('should show the design system skeleton rows instead of the overlay on the initial load (no rows yet)', async () => {
		// given / when
		const screen = await renderTable({isFetching: true, rows: []});

		// then
		expect(screen.getByTestId('table-loading-overlay').elements()).toHaveLength(0);
		expect(document.querySelectorAll('[data-slot="data-table-skeleton-row"]').length).toBeGreaterThan(0);
	});

	describe('pagination loading skeleton', () => {
		it('should render a single skeleton row below the existing rows while fetching the next page', async () => {
			// given / when
			const screen = await renderTable({isFetchingNextPage: true});

			// then
			await expect.element(screen.getByTestId('table-loading-next')).toBeVisible();
			expect(screen.getByTestId('table-loading-overlay').elements()).toHaveLength(0);
			const rows = document.querySelectorAll('[data-slot="table-body"] [data-slot="table-row"]');
			expect(rows[rows.length - 1]?.querySelector('[data-testid="table-loading-next"]')).not.toBeNull();
		});

		it('should render a single skeleton row above the existing rows while fetching the previous page', async () => {
			// given / when
			const screen = await renderTable({isFetchingPreviousPage: true});

			// then
			await expect.element(screen.getByTestId('table-loading-previous')).toBeVisible();
			const rows = document.querySelectorAll('[data-slot="table-body"] [data-slot="table-row"]');
			expect(rows[0]?.querySelector('[data-testid="table-loading-previous"]')).not.toBeNull();
		});

		it('should not render the whole-table overlay for pagination loading, only for the top-level isFetching flag', async () => {
			// given / when
			const screen = await renderTable({isFetchingPreviousPage: true, isFetchingNextPage: true});

			// then
			expect(screen.getByTestId('table-loading-overlay').elements()).toHaveLength(0);
		});

		it('should not render a checkbox for the skeleton row in a selectable table', async () => {
			// given / when
			const screen = await renderTable({
				isFetchingNextPage: true,
				selectionType: 'checkbox',
				selectAllLabel: 'Select all rows',
				selectRowLabel: (rowId: string) => `Select row ${rowId}`,
				checkIsAllSelected: () => false,
				checkIsIndeterminate: () => false,
				checkIsRowSelected: () => false,
				onSelectAll: vi.fn(),
				onSelect: vi.fn(),
			});

			// then
			const skeletonRow = screen.getByTestId('table-loading-next').element().closest('[data-slot="table-row"]');
			expect(skeletonRow?.querySelector('[role="checkbox"]')).toBeNull();
		});

		it('should scroll down by the skeleton row height on mount, so inserting it above the rows does not shift them', async () => {
			// given
			const scrollTopSetterSpy = vi.spyOn(Element.prototype, 'scrollTop', 'set');

			// when
			const screen = await renderTable({
				isFetchingPreviousPage: true,
				onVerticalScrollStartReach: vi.fn(),
				onVerticalScrollEndReach: vi.fn(),
			});
			const skeletonRow = screen.getByTestId('table-loading-previous').element().closest('[data-slot="table-row"]')!;
			const skeletonHeight = skeletonRow.getBoundingClientRect().height;

			// then
			expect(skeletonHeight).toBeGreaterThan(0);
			expect(scrollTopSetterSpy).toHaveBeenCalledWith(skeletonHeight);
			scrollTopSetterSpy.mockRestore();
		});

		it('should still compensate for the previous-page skeleton row when the caller passes no data-testid', async () => {
			// given
			const scrollTopSetterSpy = vi.spyOn(Element.prototype, 'scrollTop', 'set');

			// when
			await renderTable({
				isFetchingPreviousPage: true,
				onVerticalScrollStartReach: vi.fn(),
				onVerticalScrollEndReach: vi.fn(),
				'data-testid': undefined,
			});
			const skeletonRow = document
				.querySelector('[data-skeleton-edge="previous"]')!
				.closest('[data-slot="table-row"]')!;
			const skeletonHeight = skeletonRow.getBoundingClientRect().height;

			// then
			expect(skeletonHeight).toBeGreaterThan(0);
			expect(scrollTopSetterSpy).toHaveBeenCalledWith(skeletonHeight);
			scrollTopSetterSpy.mockRestore();
		});

		it('should undo the pending scroll compensation if the previous-page fetch ends without new rows arriving (e.g. a failed or aborted fetch)', async () => {
			// given
			const scrollTopSetterSpy = vi.spyOn(Element.prototype, 'scrollTop', 'set');
			const screen = await renderTable({
				isFetchingPreviousPage: true,
				onVerticalScrollStartReach: vi.fn(),
				onVerticalScrollEndReach: vi.fn(),
			});
			const skeletonRow = screen.getByTestId('table-loading-previous').element().closest('[data-slot="table-row"]')!;
			const skeletonHeight = skeletonRow.getBoundingClientRect().height;
			expect(scrollTopSetterSpy).toHaveBeenCalledWith(skeletonHeight);
			scrollTopSetterSpy.mockClear();

			// when: the fetch ends without the row set ever changing, as happens on failure/abort
			screen.setIsFetchingPreviousPage(false);

			// then: the earlier downward adjustment is reversed
			await expect.poll(() => scrollTopSetterSpy.mock.calls.length).toBeGreaterThan(0);
			expect(scrollTopSetterSpy).toHaveBeenCalledWith(-skeletonHeight);
			scrollTopSetterSpy.mockClear();

			// and: a subsequent fetch can still compensate (the ref wasn't left permanently non-zero)
			screen.setIsFetchingPreviousPage(true);
			await expect.poll(() => scrollTopSetterSpy.mock.calls.length).toBeGreaterThan(0);
			expect(scrollTopSetterSpy).toHaveBeenCalledWith(skeletonHeight);
			scrollTopSetterSpy.mockRestore();
		});

		it('should not misread a later, unrelated rows change (e.g. sorting) as the completion of an abandoned previous-page fetch', async () => {
			// given: a previous-page fetch starts, then ends without new rows arriving (failure/abort)
			const scrollTopSetterSpy = vi.spyOn(Element.prototype, 'scrollTop', 'set');
			const screen = await renderTable({
				isFetchingPreviousPage: true,
				onVerticalScrollStartReach: vi.fn(),
				onVerticalScrollEndReach: vi.fn(),
			});
			await expect.poll(() => scrollTopSetterSpy.mock.calls.length).toBeGreaterThan(0);
			screen.setIsFetchingPreviousPage(false);
			await expect.poll(() => scrollTopSetterSpy.mock.calls.length).toBeGreaterThan(1);
			scrollTopSetterSpy.mockClear();

			// when: an unrelated rows change happens afterwards (e.g. a sort/filter result)
			screen.setRows([{id: 'row-3', name: 'Reordered process'}]);
			await expect.element(screen.getByText('Reordered process')).toBeVisible();

			// then: no stale scrollDown compensation is applied for the abandoned fetch
			expect(scrollTopSetterSpy).not.toHaveBeenCalled();
			scrollTopSetterSpy.mockRestore();
		});
	});

	it('should mark the default column as sorted ascending with no sort param in the URL', async () => {
		// given / when
		const screen = await renderTable();

		// then
		const nameHeaderCell = screen.getByRole('cell', {name: 'Name'});
		await expect.element(nameHeaderCell).toHaveAttribute('aria-sort', 'ascending');
	});

	it('should toggle the active column between ascending and descending on repeated clicks', async () => {
		// given
		const screen = await renderTable();
		const nameSortButton = screen.getByRole('button', {name: 'Name'});

		// when
		await userEvent.click(nameSortButton);

		// then
		await expect.element(screen.getByRole('cell', {name: 'Name'})).toHaveAttribute('aria-sort', 'descending');
		expect(screen.router.state.location.search).toEqual({sort: 'name+desc'});

		// when
		await userEvent.click(nameSortButton);

		// then
		await expect.element(screen.getByRole('cell', {name: 'Name'})).toHaveAttribute('aria-sort', 'ascending');
		expect(screen.router.state.location.search).toEqual({sort: 'name+asc'});
	});

	it('should call onSort with the resolved key and order when a column header is clicked', async () => {
		// given
		const onSort = vi.fn();
		const screen = await renderTable({onSort});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Name'}));

		// then
		expect(onSort).toHaveBeenCalledWith('name', 'desc');
	});

	it('should start a non-default sortable column at its own default order on first click', async () => {
		// given
		const onSort = vi.fn();
		const columns: RenderOverrides['columns'] = [
			{key: 'name', label: 'Name', sortKey: 'name', isDefault: true, defaultOrder: 'asc', render: (row) => row.name},
			{key: 'id', label: 'Id', sortKey: 'id', defaultOrder: 'desc', render: (row) => row.id},
		];
		const screen = await renderTable({columns, onSort});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Id'}));

		// then
		expect(onSort).toHaveBeenCalledWith('id', 'desc');
	});

	it('should not render a sort button for a column without a sortKey', async () => {
		// given / when
		const screen = await renderTable();

		// then
		expect(screen.getByRole('button', {name: 'Id'}).elements()).toHaveLength(0);
	});

	it('should reflect the URL sort param for a non-default column', async () => {
		// given / when
		const columns: RenderOverrides['columns'] = [
			{key: 'name', label: 'Name', sortKey: 'name', isDefault: true, render: (row) => row.name},
			{key: 'id', label: 'Id', sortKey: 'id', render: (row) => row.id},
		];
		const screen = await renderTable({columns}, {initialEntry: '/?sort=id%2Basc'});

		// then
		await expect.element(screen.getByRole('cell', {name: 'Id'})).toHaveAttribute('aria-sort', 'ascending');
		await expect.element(screen.getByRole('cell', {name: 'Name'})).toHaveAttribute('aria-sort', 'none');
	});

	describe('row selection', () => {
		function renderWithSelection(overrides: RenderOverrides = {}) {
			return renderTable({
				selectionType: 'checkbox',
				selectAllLabel: 'Select all rows',
				selectRowLabel: (rowId) => `Select row ${rowId}`,
				checkIsAllSelected: () => false,
				checkIsIndeterminate: () => false,
				checkIsRowSelected: () => false,
				onSelectAll: vi.fn(),
				onSelect: vi.fn(),
				...overrides,
			});
		}

		it('should render a checkbox per row plus a select-all checkbox', async () => {
			// given / when
			const screen = await renderWithSelection();

			// then
			await expect.element(screen.getByRole('checkbox', {name: 'Select all rows'})).toBeVisible();
			await expect.element(screen.getByRole('checkbox', {name: 'Select row row-1'})).toBeVisible();
			await expect.element(screen.getByRole('checkbox', {name: 'Select row row-2'})).toBeVisible();
		});

		it('should call onSelect with the row id when a row checkbox is clicked', async () => {
			// given
			const onSelect = vi.fn();
			const screen = await renderWithSelection({onSelect});

			// when
			await userEvent.click(screen.getByRole('checkbox', {name: 'Select row row-1'}));

			// then
			expect(onSelect).toHaveBeenCalledWith('row-1');
		});

		it('should call onSelectAll when the select-all checkbox is clicked', async () => {
			// given
			const onSelectAll = vi.fn();
			const screen = await renderWithSelection({onSelectAll});

			// when
			await userEvent.click(screen.getByRole('checkbox', {name: 'Select all rows'}));

			// then
			expect(onSelectAll).toHaveBeenCalled();
		});

		it("should reflect checkIsRowSelected's value per row independently of checkIsAllSelected", async () => {
			// given / when
			const screen = await renderWithSelection({checkIsRowSelected: (rowId) => rowId === 'row-1'});

			// then
			await expect.element(screen.getByRole('checkbox', {name: 'Select row row-1'})).toBeChecked();
			await expect.element(screen.getByRole('checkbox', {name: 'Select row row-2'})).not.toBeChecked();
		});

		it('should render the select-all checkbox as indeterminate when checkIsIndeterminate returns true even if checkIsAllSelected is also true', async () => {
			// given / when
			const screen = await renderWithSelection({checkIsAllSelected: () => true, checkIsIndeterminate: () => true});

			// then
			const checkbox = screen.getByRole('checkbox', {name: 'Select all rows'});
			await expect.element(checkbox).toHaveAttribute('data-state', 'indeterminate');
		});
	});

	describe('infinite scroll', () => {
		const {getObserver} = setUpFakeIntersectionObserver();

		it('should not render scroll sentinels when no scroll handlers are given', async () => {
			// given / when
			const screen = await renderTable();

			// then
			expect(screen.getByTestId('sortable-table-top-sentinel').elements()).toHaveLength(0);
			expect(screen.getByTestId('sortable-table-bottom-sentinel').elements()).toHaveLength(0);
		});

		it('should root the intersection observer on the scrollable container, not the viewport', async () => {
			// given / when: the scroll container is attached in the same commit as the observer is
			// created, so using the wrong root (e.g. falling back to the viewport) would make
			// sentinel intersections fire at the wrong scroll offsets
			const screen = await renderTable({onVerticalScrollStartReach: vi.fn(), onVerticalScrollEndReach: vi.fn()});
			const container = screen.getByTestId('table').element();

			// then
			expect(getObserver().root).toBe(container);
		});

		it('should call onVerticalScrollStartReach when the top sentinel intersects while scrolling up', async () => {
			// given
			const onVerticalScrollStartReach = vi.fn();
			const screen = await renderTable({onVerticalScrollStartReach, onVerticalScrollEndReach: vi.fn()});
			const container = screen.getByTestId('table').element() as HTMLElement;
			const observer = getObserver();
			const topSentinel = screen.getByTestId('sortable-table-top-sentinel').element();

			// when
			Object.defineProperty(container, 'scrollTop', {value: 100, writable: true, configurable: true});
			observer.intersect(topSentinel);
			Object.defineProperty(container, 'scrollTop', {value: 20, writable: true, configurable: true});
			observer.intersect(topSentinel);

			// then
			expect(onVerticalScrollStartReach).toHaveBeenCalledWith();
			expect(onVerticalScrollStartReach).toHaveBeenCalledTimes(1);
		});

		it('should call onVerticalScrollEndReach when the bottom sentinel intersects while scrolling down', async () => {
			// given
			const onVerticalScrollEndReach = vi.fn();
			const screen = await renderTable({onVerticalScrollStartReach: vi.fn(), onVerticalScrollEndReach});
			const container = screen.getByTestId('table').element() as HTMLElement;
			const observer = getObserver();
			const bottomSentinel = screen.getByTestId('sortable-table-bottom-sentinel').element();

			// when
			Object.defineProperty(container, 'scrollTop', {value: 20, writable: true, configurable: true});
			observer.intersect(bottomSentinel);
			Object.defineProperty(container, 'scrollTop', {value: 100, writable: true, configurable: true});
			observer.intersect(bottomSentinel);

			// then
			expect(onVerticalScrollEndReach).toHaveBeenCalledWith();
		});

		it('should not arm prepend compensation when onVerticalScrollStartReach is not configured, even though the top sentinel still renders', async () => {
			// given: only the next-page callback is configured, but the top sentinel still renders
			// as part of the pair whenever any scroll handler is given
			const screen = await renderTable({
				rows: [
					{id: 'row-1', name: 'Order process'},
					{id: 'row-2', name: 'Shipping process'},
				],
				onVerticalScrollEndReach: vi.fn(),
			});
			const container = screen.getByTestId('table').element() as HTMLElement;
			const scrollToSpy = vi.spyOn(container, 'scrollTo').mockImplementation(() => {});
			const observer = getObserver();
			const topSentinel = screen.getByTestId('sortable-table-top-sentinel').element();

			// when: the top sentinel intersects while scrolling up - there is no previous-page
			// fetch to start since the caller never configured one for this direction
			Object.defineProperty(container, 'scrollTop', {value: 100, writable: true, configurable: true});
			observer.intersect(topSentinel);
			Object.defineProperty(container, 'scrollTop', {value: 20, writable: true, configurable: true});
			observer.intersect(topSentinel);

			// and: rows change afterwards as if a new row were genuinely prepended, which would
			// have been (wrongly) compensated for had a baseline been armed despite no callback
			// being configured for this direction
			screen.setRows([
				{id: 'row-9', name: 'New process'},
				{id: 'row-1', name: 'Order process'},
				{id: 'row-2', name: 'Shipping process'},
			]);
			await expect.element(screen.getByText('New process')).toBeVisible();

			// then: no baseline was armed by the unreachable top sentinel to misfire against this
			expect(scrollToSpy).not.toHaveBeenCalled();
		});

		it('should not arm append compensation when onVerticalScrollEndReach is not configured, even though the bottom sentinel still renders', async () => {
			// given: only the previous-page callback is configured, but the bottom sentinel still
			// renders as part of the pair whenever any scroll handler is given
			const scrollToSpy = vi.fn();
			const screen = await renderTable({
				rows: [
					{id: 'row-1', name: 'Order process'},
					{id: 'row-2', name: 'Shipping process'},
				],
				onVerticalScrollStartReach: vi.fn(),
			});
			const container = screen.getByTestId('table').element() as HTMLElement;
			vi.spyOn(container, 'scrollTo').mockImplementation(scrollToSpy);
			const observer = getObserver();
			const bottomSentinel = screen.getByTestId('sortable-table-bottom-sentinel').element();

			// when: the bottom sentinel intersects, as if the user scrolled down - there is no
			// next-page fetch to start since the caller never configured one
			Object.defineProperty(container, 'scrollTop', {value: 20, writable: true, configurable: true});
			observer.intersect(bottomSentinel);

			// and: rows change afterwards as if the top row were genuinely evicted and a new one
			// appended, which would have been (wrongly) compensated for had a baseline been armed
			// despite no callback being configured for this direction
			screen.setRows([
				{id: 'row-2', name: 'Shipping process'},
				{id: 'row-9', name: 'New process'},
			]);
			await expect.element(screen.getByText('New process')).toBeVisible();

			// then: no baseline was armed by the unreachable bottom sentinel to misfire against this
			expect(scrollToSpy).not.toHaveBeenCalled();
		});

		it('should restore the scroll offset using the actual measured height of the prepended rows', async () => {
			// given
			const screen = await renderTable({
				onVerticalScrollStartReach: vi.fn(),
				onVerticalScrollEndReach: vi.fn(),
			});
			const container = screen.getByTestId('table').element() as HTMLElement;
			const scrollToSpy = vi.spyOn(container, 'scrollTo').mockImplementation(() => {});
			const observer = getObserver();
			const topSentinel = screen.getByTestId('sortable-table-top-sentinel').element();

			// when
			Object.defineProperty(container, 'scrollTop', {value: 100, writable: true, configurable: true});
			observer.intersect(topSentinel);
			Object.defineProperty(container, 'scrollTop', {value: 20, writable: true, configurable: true});
			observer.intersect(topSentinel);
			screen.setIsFetchingPreviousPage(true);
			await expect.element(screen.getByText('Loading previous rows')).toBeVisible();
			screen.setIsFetchingPreviousPage(false);
			screen.setRows([
				{id: 'row-0', name: 'Prepended process'},
				{id: 'row-1', name: 'Order process'},
				{id: 'row-2', name: 'Shipping process'},
			]);

			// then
			await expect.poll(() => scrollToSpy.mock.calls.length).toBeGreaterThan(0);
			const prependedRowHeight = screen
				.getByText('Prepended process')
				.element()
				.closest('[data-slot="table-row"]')!
				.getBoundingClientRect().height;
			expect(scrollToSpy).toHaveBeenCalledWith(0, 20 + prependedRowHeight);
		});

		it('should still restore the scroll offset correctly when a page is evicted from the opposite end at the same time (e.g. an infinite query capped by maxPages)', async () => {
			// given
			const screen = await renderTable({
				rows: [
					{id: 'row-1', name: 'Order process'},
					{id: 'row-2', name: 'Shipping process'},
					{id: 'row-3', name: 'Billing process'},
				],
				onVerticalScrollStartReach: vi.fn(),
				onVerticalScrollEndReach: vi.fn(),
			});
			const container = screen.getByTestId('table').element() as HTMLElement;
			const scrollToSpy = vi.spyOn(container, 'scrollTo').mockImplementation(() => {});
			const observer = getObserver();
			const topSentinel = screen.getByTestId('sortable-table-top-sentinel').element();

			// when
			Object.defineProperty(container, 'scrollTop', {value: 100, writable: true, configurable: true});
			observer.intersect(topSentinel);
			Object.defineProperty(container, 'scrollTop', {value: 20, writable: true, configurable: true});
			observer.intersect(topSentinel);
			screen.setIsFetchingPreviousPage(true);
			await expect.element(screen.getByText('Loading previous rows')).toBeVisible();
			screen.setIsFetchingPreviousPage(false);
			screen.setRows([
				{id: 'row-0', name: 'Prepended process'},
				{id: 'row-1', name: 'Order process'},
				{id: 'row-2', name: 'Shipping process'},
			]);

			// then
			await expect.poll(() => scrollToSpy.mock.calls.length).toBeGreaterThan(0);
			const prependedRowHeight = screen
				.getByText('Prepended process')
				.element()
				.closest('[data-slot="table-row"]')!
				.getBoundingClientRect().height;
			expect(scrollToSpy).toHaveBeenCalledWith(0, 20 + prependedRowHeight);
		});

		it('should compensate when rows are evicted from the top while the user has scrolled away from the bottom', async () => {
			// given
			const screen = await renderTable({
				rows: [
					{id: 'row-1', name: 'Order process'},
					{id: 'row-2', name: 'Shipping process'},
				],
				onVerticalScrollStartReach: vi.fn(),
				onVerticalScrollEndReach: vi.fn(),
			});
			const container = screen.getByTestId('table').element() as HTMLElement;
			const scrollToSpy = vi.spyOn(container, 'scrollTo').mockImplementation(() => {});
			const observer = getObserver();
			const bottomSentinel = screen.getByTestId('sortable-table-bottom-sentinel').element();
			const evictedRowHeight = screen
				.getByText('Order process')
				.element()
				.closest('[data-slot="table-row"]')!
				.getBoundingClientRect().height;

			// when
			Object.defineProperty(container, 'scrollTop', {value: 100, writable: true, configurable: true});
			observer.intersect(bottomSentinel);
			screen.setIsFetchingNextPage(true);
			await expect.element(screen.getByText('Loading more rows')).toBeVisible();
			screen.setIsFetchingNextPage(false);
			// the new page lands once the user has scrolled back up, away from the bottom edge
			Object.defineProperty(container, 'scrollTop', {value: 20, writable: true, configurable: true});
			screen.setRows([
				{id: 'row-2', name: 'Shipping process'},
				{id: 'row-3', name: 'Billing process'},
			]);

			// then
			await expect.poll(() => scrollToSpy.mock.calls.length).toBeGreaterThan(0);
			expect(scrollToSpy).toHaveBeenCalledWith(0, 20 - evictedRowHeight);
		});

		it('should also compensate when rows are evicted from the top while the user is still pinned to the bottom', async () => {
			// given
			const screen = await renderTable({
				rows: [
					{id: 'row-1', name: 'Order process'},
					{id: 'row-2', name: 'Shipping process'},
				],
				onVerticalScrollStartReach: vi.fn(),
				onVerticalScrollEndReach: vi.fn(),
			});
			const container = screen.getByTestId('table').element() as HTMLElement;
			const scrollToSpy = vi.spyOn(container, 'scrollTo').mockImplementation(() => {});
			const observer = getObserver();
			const bottomSentinel = screen.getByTestId('sortable-table-bottom-sentinel').element();
			const evictedRowHeight = screen
				.getByText('Order process')
				.element()
				.closest('[data-slot="table-row"]')!
				.getBoundingClientRect().height;

			// when
			Object.defineProperty(container, 'scrollTop', {value: 20, writable: true, configurable: true});
			observer.intersect(bottomSentinel);
			screen.setIsFetchingNextPage(true);
			await expect.element(screen.getByText('Loading more rows')).toBeVisible();
			screen.setIsFetchingNextPage(false);
			// the new page lands while the user is still pinned to the live bottom edge. Without
			// compensation, removing rows from the top (without the browser adjusting `scrollTop`)
			// would shift the surviving content upward, making the same numeric `scrollTop` land on
			// rows further down the list than the ones the user was actually looking at - a forward
			// skip equal to the evicted height. Restoring the pre-fetch view is required here too.
			screen.setRows([
				{id: 'row-2', name: 'Shipping process'},
				{id: 'row-3', name: 'Billing process'},
			]);

			// then
			await expect.poll(() => scrollToSpy.mock.calls.length).toBeGreaterThan(0);
			expect(scrollToSpy).toHaveBeenCalledWith(0, 20 - evictedRowHeight);
		});

		it('should not misread a concurrent, unrelated rows change (e.g. sorting) as top eviction while the next-page fetch is still in flight', async () => {
			// given: a next-page fetch is armed and then starts
			const scrollToSpy = vi.fn();
			const screen = await renderTable({
				rows: [
					{id: 'row-1', name: 'Order process'},
					{id: 'row-2', name: 'Shipping process'},
				],
				onVerticalScrollStartReach: vi.fn(),
				onVerticalScrollEndReach: vi.fn(),
			});
			const container = screen.getByTestId('table').element() as HTMLElement;
			vi.spyOn(container, 'scrollTo').mockImplementation(scrollToSpy);
			const observer = getObserver();
			const bottomSentinel = screen.getByTestId('sortable-table-bottom-sentinel').element();
			Object.defineProperty(container, 'scrollTop', {value: 20, writable: true, configurable: true});
			observer.intersect(bottomSentinel);
			screen.setIsFetchingNextPage(true);

			// when: an unrelated refetch (e.g. a sort/filter change) replaces the rows entirely
			// while the next-page fetch is still in flight
			screen.setRows([{id: 'row-9', name: 'Reordered process'}]);
			await expect.element(screen.getByText('Reordered process')).toBeVisible();

			// then: no compensation is applied for this unrelated change
			expect(scrollToSpy).not.toHaveBeenCalled();

			// when: the real next page then lands, ending the fetch
			screen.setIsFetchingNextPage(false);
			screen.setRows([
				{id: 'row-9', name: 'Reordered process'},
				{id: 'row-10', name: 'Appended process'},
			]);

			// then: the real completion is still correctly left uncompensated (nothing was evicted
			// from the top of this unrelated row set)
			await expect.element(screen.getByText('Appended process')).toBeVisible();
			expect(scrollToSpy).not.toHaveBeenCalled();
		});

		it('should not misread an unrelated rows change as top eviction when a next-page fetch is cancelled (isFetchingNextPage and rows change together)', async () => {
			// given: a next-page fetch is armed and then starts
			const scrollToSpy = vi.fn();
			const screen = await renderTable({
				rows: [
					{id: 'row-1', name: 'Order process'},
					{id: 'row-2', name: 'Shipping process'},
					{id: 'row-3', name: 'Invoice process'},
				],
				onVerticalScrollStartReach: vi.fn(),
				onVerticalScrollEndReach: vi.fn(),
			});
			const container = screen.getByTestId('table').element() as HTMLElement;
			vi.spyOn(container, 'scrollTo').mockImplementation(scrollToSpy);
			const observer = getObserver();
			const bottomSentinel = screen.getByTestId('sortable-table-bottom-sentinel').element();
			Object.defineProperty(container, 'scrollTop', {value: 20, writable: true, configurable: true});
			observer.intersect(bottomSentinel);
			screen.setIsFetchingNextPage(true);

			// when: the fetch is cancelled by an unrelated query-key change (e.g. a filter), which
			// lands `isFetchingNextPage: false` and the new, differently-ordered rows in the same
			// commit - row-2 is gone and the surviving rows (row-1, row-3) no longer lead in their
			// original relative order
			screen.setIsFetchingNextPage(false);
			screen.setRows([
				{id: 'row-3', name: 'Invoice process'},
				{id: 'row-1', name: 'Order process'},
				{id: 'row-9', name: 'New filtered process'},
			]);
			await expect.element(screen.getByText('New filtered process')).toBeVisible();

			// then: this is not misread as the next page evicting the top of the previous one
			expect(scrollToSpy).not.toHaveBeenCalled();
		});

		it('should not misread an unrelated rows change as a prepend when a previous-page fetch is cancelled (isFetchingPreviousPage and rows change together)', async () => {
			// given: a previous-page fetch is armed and then starts
			const scrollTopSetterSpy = vi.spyOn(Element.prototype, 'scrollTop', 'set');
			const screen = await renderTable({
				rows: [
					{id: 'row-1', name: 'Order process'},
					{id: 'row-2', name: 'Shipping process'},
					{id: 'row-3', name: 'Invoice process'},
				],
				onVerticalScrollStartReach: vi.fn(),
				onVerticalScrollEndReach: vi.fn(),
			});
			const observer = getObserver();
			const topSentinel = screen.getByTestId('sortable-table-top-sentinel').element();
			observer.intersect(topSentinel);
			screen.setIsFetchingPreviousPage(true);
			await expect.poll(() => scrollTopSetterSpy.mock.calls.length).toBeGreaterThan(0);
			scrollTopSetterSpy.mockClear();

			// when: the fetch is cancelled by an unrelated query-key change (e.g. a filter), which
			// lands `isFetchingPreviousPage: false` and the new, differently-ordered rows in the
			// same commit - row-2 is gone and the surviving rows (row-1, row-3) no longer trail in
			// their original relative order
			screen.setIsFetchingPreviousPage(false);
			screen.setRows([
				{id: 'row-9', name: 'New filtered process'},
				{id: 'row-3', name: 'Invoice process'},
				{id: 'row-1', name: 'Order process'},
			]);
			await expect.element(screen.getByText('New filtered process')).toBeVisible();

			// then: this is not misread as the previous page having been prepended - only the
			// earlier skeleton-insertion compensation is reverted by the give-up effect, no
			// additional scrollDown is applied for a prepend that never happened
			expect(scrollTopSetterSpy).toHaveBeenCalledTimes(1);
			scrollTopSetterSpy.mockRestore();
		});

		it('should not misread cached data for a new query as a completed prepend when a previous-page fetch is cancelled by a sort/filter change whose cached result coincidentally shares a trailing run of row ids', async () => {
			// given: a previous-page fetch is armed and genuinely starts
			const screen = await renderTable({
				rows: [
					{id: 'row-1', name: 'Order process'},
					{id: 'row-2', name: 'Shipping process'},
					{id: 'row-3', name: 'Invoice process'},
				],
				onVerticalScrollStartReach: vi.fn(),
				onVerticalScrollEndReach: vi.fn(),
			});
			const container = screen.getByTestId('table').element() as HTMLElement;
			const scrollToSpy = vi.spyOn(container, 'scrollTo').mockImplementation(() => {});
			const observer = getObserver();
			const topSentinel = screen.getByTestId('sortable-table-top-sentinel').element();
			Object.defineProperty(container, 'scrollTop', {value: 100, writable: true, configurable: true});
			observer.intersect(topSentinel);
			Object.defineProperty(container, 'scrollTop', {value: 20, writable: true, configurable: true});
			observer.intersect(topSentinel);
			screen.setIsFetchingPreviousPage(true);
			await expect.element(screen.getByText('Loading previous rows')).toBeVisible();

			// when: a sort/filter change cancels the pagination fetch (`isFetchingPreviousPage`
			// flips back to `false`) and starts a full refetch (`isFetching`) for an entirely
			// different query, all landing in the same commit together with that new query's
			// cached rows - which happen to continue the stale baseline's surviving rows in order
			// purely by coincidence (e.g. the same rows, just re-sorted). Row-order continuity
			// alone can't tell this apart from the original fetch's genuine prepend.
			screen.setIsFetchingPreviousPage(false);
			screen.setIsFetching(true);
			screen.setRows([
				{id: 'row-9', name: 'Coincidentally ordered process'},
				{id: 'row-1', name: 'Order process'},
				{id: 'row-2', name: 'Shipping process'},
			]);
			await expect.element(screen.getByText('Coincidentally ordered process')).toBeVisible();

			// then: this is not misread as the original fetch's prepend, since the full refetch
			// disarmed the baseline - tied to query identity via `isFetching` - before this rows
			// change could be compared against it. No scrollDown is applied for a prepend that
			// never happened against this query.
			expect(scrollToSpy).not.toHaveBeenCalled();
		});

		it('should not misread cached data for a swapped query as a completed append when the armed fetch never actually started', async () => {
			// given: a next-page fetch is armed, but `isFetchingNextPage` never flips `true` -
			// nothing was ever actually fetched for this baseline
			const scrollToSpy = vi.fn();
			const screen = await renderTable({
				rows: [
					{id: 'row-1', name: 'Order process'},
					{id: 'row-2', name: 'Shipping process'},
					{id: 'row-3', name: 'Invoice process'},
				],
				onVerticalScrollStartReach: vi.fn(),
				onVerticalScrollEndReach: vi.fn(),
			});
			const container = screen.getByTestId('table').element() as HTMLElement;
			vi.spyOn(container, 'scrollTo').mockImplementation(scrollToSpy);
			const observer = getObserver();
			const bottomSentinel = screen.getByTestId('sortable-table-bottom-sentinel').element();
			Object.defineProperty(container, 'scrollTop', {value: 20, writable: true, configurable: true});
			observer.intersect(bottomSentinel);

			// when: a query-key change (e.g. a filter) instead lands cached data for a different
			// query in the same slot, and that data happens to continue the stale baseline's
			// surviving rows in order purely by coincidence (e.g. the same unfiltered rows, just
			// re-paged) - a row-order check alone can't tell this apart from a genuine append
			screen.setRows([
				{id: 'row-2', name: 'Shipping process'},
				{id: 'row-3', name: 'Invoice process'},
				{id: 'row-9', name: 'Coincidentally ordered process'},
			]);
			await expect.element(screen.getByText('Coincidentally ordered process')).toBeVisible();

			// then: this is not misread as the next page having evicted the top row, since no fetch
			// was ever observed to have started for this baseline
			expect(scrollToSpy).not.toHaveBeenCalled();
		});

		it('should not misread cached data for a swapped query as a completed prepend when the armed fetch never actually started', async () => {
			// given: a previous-page fetch is armed, but `isFetchingPreviousPage` never flips
			// `true` - nothing was ever actually fetched for this baseline
			const scrollToSpy = vi.fn();
			const screen = await renderTable({
				rows: [
					{id: 'row-1', name: 'Order process'},
					{id: 'row-2', name: 'Shipping process'},
					{id: 'row-3', name: 'Invoice process'},
				],
				onVerticalScrollStartReach: vi.fn(),
				onVerticalScrollEndReach: vi.fn(),
			});
			const container = screen.getByTestId('table').element() as HTMLElement;
			vi.spyOn(container, 'scrollTo').mockImplementation(scrollToSpy);
			const observer = getObserver();
			const topSentinel = screen.getByTestId('sortable-table-top-sentinel').element();
			Object.defineProperty(container, 'scrollTop', {value: 100, writable: true, configurable: true});
			observer.intersect(topSentinel);
			Object.defineProperty(container, 'scrollTop', {value: 20, writable: true, configurable: true});
			observer.intersect(topSentinel);

			// when: a query-key change (e.g. a filter) instead lands cached data for a different
			// query in the same slot, and that data happens to continue the stale baseline's
			// surviving rows in order purely by coincidence
			screen.setRows([
				{id: 'row-9', name: 'Coincidentally ordered process'},
				{id: 'row-1', name: 'Order process'},
				{id: 'row-2', name: 'Shipping process'},
				{id: 'row-3', name: 'Invoice process'},
			]);
			await expect.element(screen.getByText('Coincidentally ordered process')).toBeVisible();

			// then: this is not misread as a prepend, since no fetch was ever observed to have
			// started for this baseline
			expect(scrollToSpy).not.toHaveBeenCalled();
		});

		it('should not scroll up by the full previous height when a next-page fetch is cancelled by a completely disjoint rows swap', async () => {
			// given: a next-page fetch is armed and genuinely starts
			const scrollToSpy = vi.fn();
			const screen = await renderTable({
				rows: [
					{id: 'row-1', name: 'Order process'},
					{id: 'row-2', name: 'Shipping process'},
					{id: 'row-3', name: 'Invoice process'},
				],
				onVerticalScrollStartReach: vi.fn(),
				onVerticalScrollEndReach: vi.fn(),
			});
			const container = screen.getByTestId('table').element() as HTMLElement;
			vi.spyOn(container, 'scrollTo').mockImplementation(scrollToSpy);
			const observer = getObserver();
			const bottomSentinel = screen.getByTestId('sortable-table-bottom-sentinel').element();
			Object.defineProperty(container, 'scrollTop', {value: 20, writable: true, configurable: true});
			observer.intersect(bottomSentinel);
			screen.setIsFetchingNextPage(true);
			// force this to commit as its own render (instead of batching with the cancellation
			// below), so the fetch is genuinely observed to have started
			await expect.element(screen.getByTestId('table-loading-next')).toBeVisible();

			// when: the fetch is cancelled by an unrelated query-key change (e.g. a filter) whose
			// result shares zero rows with the baseline - none of row-1/row-2/row-3 survive
			screen.setIsFetchingNextPage(false);
			screen.setRows([
				{id: 'row-20', name: 'Entirely different process'},
				{id: 'row-21', name: 'Another different process'},
			]);
			await expect.element(screen.getByText('Entirely different process')).toBeVisible();

			// then: with zero surviving rows, this can't be distinguished from a genuine append at
			// all - it must not be misread as every previous row having been evicted from the top,
			// which would otherwise scroll up by their full height for no reason
			expect(scrollToSpy).not.toHaveBeenCalled();
		});

		it('should correctly compensate end-to-end when a top-sentinel-triggered prepend goes through the full skeleton lifecycle (fetch start, skeleton mount+compensate, rows arrive)', async () => {
			// given: a realistic scrollTop that reads and writes like a real scrollable container,
			// instead of the static per-assertion overrides the other tests use - this test exercises
			// the full production sequence (sentinel -> isFetchingPreviousPage -> rows) in one go, so
			// the compensation the skeleton effect applies must be visible to the later restore effect.
			let currentScrollTop = 100;
			const screen = await renderTable({
				onVerticalScrollStartReach: vi.fn(),
				onVerticalScrollEndReach: vi.fn(),
			});
			const container = screen.getByTestId('table').element() as HTMLElement;
			Object.defineProperty(container, 'scrollTop', {
				configurable: true,
				get: () => currentScrollTop,
				set: (value: number) => {
					currentScrollTop = value;
				},
			});
			const scrollToSpy = vi.spyOn(container, 'scrollTo').mockImplementation(() => {});
			const observer = getObserver();
			const topSentinel = screen.getByTestId('sortable-table-top-sentinel').element();

			// when: the user scrolls up and reaches the top sentinel, which would normally prompt
			// the consumer to start fetching the previous page
			currentScrollTop = 100;
			observer.intersect(topSentinel);
			currentScrollTop = 20;
			observer.intersect(topSentinel);

			// and: the consumer (in response to the above) flips isFetchingPreviousPage, which mounts
			// the skeleton row and should immediately compensate for its height
			screen.setIsFetchingPreviousPage(true);
			await expect.element(screen.getByTestId('table-loading-previous')).toBeVisible();
			const skeletonRow = screen.getByTestId('table-loading-previous').element().closest('[data-slot="table-row"]')!;
			const skeletonHeight = skeletonRow.getBoundingClientRect().height;
			expect(skeletonHeight).toBeGreaterThan(0);
			expect(currentScrollTop).toBe(20 + skeletonHeight);

			// and: the fetch resolves with the real previous page, replacing the skeleton
			screen.setRows([
				{id: 'row-0', name: 'Prepended process'},
				{id: 'row-1', name: 'Order process'},
				{id: 'row-2', name: 'Shipping process'},
			]);
			screen.setIsFetchingPreviousPage(false);

			// then: the net scroll adjustment is the real prepended height only - the skeleton's own
			// compensation and the final restore cancel out, landing exactly where the no-skeleton
			// case above does, with no double-compensation or leftover offset
			await expect.poll(() => scrollToSpy.mock.calls.length).toBeGreaterThan(0);
			const prependedRowHeight = screen
				.getByText('Prepended process')
				.element()
				.closest('[data-slot="table-row"]')!
				.getBoundingClientRect().height;
			expect(scrollToSpy).toHaveBeenCalledWith(0, 20 + prependedRowHeight);
		});
	});
});
