/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

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

// A plain `Partial` collapses the selection-props union down to its common members — distribute it
// over each union member instead, so the `selectionType: 'checkbox'` discriminant survives.
type DistributivePartial<T> = T extends unknown ? Partial<T> : never;
type RenderOverrides = DistributivePartial<React.ComponentProps<typeof SortableTable<Row>>>;

async function renderTable(overrides: RenderOverrides = {}, {initialEntry = '/'}: {initialEntry?: string} = {}) {
	// A plain object-spread of a discriminated union into JSX attributes doesn't preserve the
	// discriminant for TS's attribute checker; build the merged props object explicitly instead.
	const props = {
		columns: COLUMNS,
		rows: ROWS,
		rowKey: (row: Row) => row.id,
		'data-testid': 'table',
		...overrides,
	} as React.ComponentProps<typeof SortableTable<Row>>;
	const rootRoute = createRootRoute();
	const testRoute = createRoute({
		getParentRoute: () => rootRoute,
		path: '/',
		component: () => <SortableTable<Row> {...props} />,
	});
	const router = createRouter({
		routeTree: rootRoute.addChildren([testRoute]),
		history: createMemoryHistory({initialEntries: [initialEntry]}),
		defaultPendingMinMs: 0,
	});

	await router.load();

	const screen = await render(<RouterProvider router={router} />);

	return {...screen, router};
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

	it('should show a loading overlay while fetching', async () => {
		// given / when
		const screen = await renderTable({isFetching: true});

		// then
		await expect.element(screen.getByTestId('table-loading-overlay')).toBeVisible();
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
			// given / when — a cross-page "select all matching filter" state can report both as true;
			// the indeterminate predicate must still win, since `DataTable`'s own derived header state
			// (from only the loaded rows) can't represent that case.
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

		it('should call onVerticalScrollStartReach with a scrollDown adjuster when the top sentinel intersects while scrolling up', async () => {
			// given
			const onVerticalScrollStartReach = vi.fn();
			const screen = await renderTable({onVerticalScrollStartReach, onVerticalScrollEndReach: vi.fn()});
			const container = screen.getByTestId('table').element() as HTMLElement;
			const scrollToSpy = vi.spyOn(container, 'scrollTo').mockImplementation(() => {});
			const observer = getObserver();
			const topSentinel = screen.getByTestId('sortable-table-top-sentinel').element();

			// when — simulate scrolling down then back up past the top sentinel. `scrollTop` is
			// redefined as a plain data property because a real (unscrolled, two-row) container can't
			// be driven to an arbitrary scroll offset directly, and `scrollTo` doesn't move it either.
			Object.defineProperty(container, 'scrollTop', {value: 100, configurable: true});
			observer.intersect(topSentinel);
			Object.defineProperty(container, 'scrollTop', {value: 20, configurable: true});
			observer.intersect(topSentinel);

			// then
			expect(onVerticalScrollStartReach).toHaveBeenCalledWith(expect.any(Function));
			expect(onVerticalScrollStartReach).toHaveBeenCalledTimes(1);

			// and when the exposed adjuster is invoked, it scrolls the container down by the given distance
			const scrollDown = onVerticalScrollStartReach.mock.calls[0]?.[0] as (distance: number) => void;
			scrollDown(100);
			expect(scrollToSpy).toHaveBeenCalledWith(0, 120);
		});

		it('should call onVerticalScrollEndReach with a scrollUp adjuster when the bottom sentinel intersects while scrolling down', async () => {
			// given
			const onVerticalScrollEndReach = vi.fn();
			const screen = await renderTable({onVerticalScrollStartReach: vi.fn(), onVerticalScrollEndReach});
			const container = screen.getByTestId('table').element() as HTMLElement;
			const scrollToSpy = vi.spyOn(container, 'scrollTo').mockImplementation(() => {});
			const observer = getObserver();
			const bottomSentinel = screen.getByTestId('sortable-table-bottom-sentinel').element();

			// when — simulate scrolling up then down past the bottom sentinel
			Object.defineProperty(container, 'scrollTop', {value: 20, configurable: true});
			observer.intersect(bottomSentinel);
			Object.defineProperty(container, 'scrollTop', {value: 100, configurable: true});
			observer.intersect(bottomSentinel);

			// then
			expect(onVerticalScrollEndReach).toHaveBeenCalledWith(expect.any(Function));

			// and when the exposed adjuster is invoked, it scrolls the container up by the given distance
			const scrollUp = onVerticalScrollEndReach.mock.calls[0]?.[0] as (distance: number) => void;
			scrollUp(20);
			expect(scrollToSpy).toHaveBeenCalledWith(0, 80);
		});
	});
});
