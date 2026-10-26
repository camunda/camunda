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
	{key: 'name', label: 'Name', sortKey: 'name', isDefault: true, defaultOrder: 'ASC', render: (row) => row.name},
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
		expect(screen.router.state.location.search).toEqual({sort: 'name+DESC'});

		// when
		await userEvent.click(nameSortButton);

		// then
		await expect.element(screen.getByRole('cell', {name: 'Name'})).toHaveAttribute('aria-sort', 'ascending');
		expect(screen.router.state.location.search).toEqual({sort: 'name+ASC'});
	});

	it('should call onSort with the resolved key and order when a column header is clicked', async () => {
		// given
		const onSort = vi.fn();
		const screen = await renderTable({onSort});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Name'}));

		// then
		expect(onSort).toHaveBeenCalledWith('name', 'DESC');
	});

	it('should start a non-default sortable column at its own default order on first click', async () => {
		// given
		const onSort = vi.fn();
		const columns: RenderOverrides['columns'] = [
			{key: 'name', label: 'Name', sortKey: 'name', isDefault: true, defaultOrder: 'ASC', render: (row) => row.name},
			{key: 'id', label: 'Id', sortKey: 'id', defaultOrder: 'DESC', render: (row) => row.id},
		];
		const screen = await renderTable({columns, onSort});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Id'}));

		// then
		expect(onSort).toHaveBeenCalledWith('id', 'DESC');
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
		const screen = await renderTable({columns}, {initialEntry: '/?sort=id%2BASC'});

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
				selectedCount: 0,
				onSelectAll: vi.fn(),
				onSelect: vi.fn(),
				onReset: vi.fn(),
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
	});

	describe('batch actions', () => {
		function renderWithBatchActions(overrides: RenderOverrides = {}) {
			return renderTable({
				selectionType: 'checkbox',
				selectAllLabel: 'Select all rows',
				selectRowLabel: (rowId) => `Select row ${rowId}`,
				checkIsAllSelected: () => false,
				checkIsIndeterminate: () => false,
				checkIsRowSelected: (rowId) => rowId === 'row-1',
				selectedCount: 1,
				onSelectAll: vi.fn(),
				onSelect: vi.fn(),
				onReset: vi.fn(),
				selectionCountLabel: '12 items selected',
				batchActionsLabel: 'Bulk actions',
				batchActionsCancelLabel: 'Discard',
				batchActions: [{id: 'delete', label: 'Delete', variant: 'destructive', onClick: vi.fn()}],
				...overrides,
			});
		}

		it('should show the caller-provided count and the actions while rows are selected', async () => {
			// given / when
			const screen = await renderWithBatchActions();

			// then
			await expect.element(screen.getByRole('toolbar', {name: 'Bulk actions'})).toBeVisible();
			await expect.element(screen.getByText('12 items selected')).toBeVisible();
			await expect.element(screen.getByRole('button', {name: 'Delete'})).toBeVisible();
		});

		it('should not show the batch bar when no row is selected', async () => {
			// given / when
			const screen = await renderWithBatchActions({checkIsRowSelected: () => false, selectedCount: 0});

			// then
			await expect.element(screen.getByRole('toolbar', {name: 'Bulk actions'})).not.toBeInTheDocument();
		});

		it('should call the action when it is clicked', async () => {
			// given
			const onClick = vi.fn();
			const screen = await renderWithBatchActions({batchActions: [{id: 'delete', label: 'Delete', onClick}]});

			// when
			await userEvent.click(screen.getByRole('button', {name: 'Delete'}));

			// then
			expect(onClick).toHaveBeenCalledOnce();
		});

		it('should show the select-all checkbox as partial when every loaded row is selected but matches outside are not', async () => {
			// given / when
			const screen = await renderWithBatchActions({
				checkIsRowSelected: () => true,
				checkIsAllSelected: () => false,
				checkIsIndeterminate: () => true,
				selectedCount: 2,
			});

			// then
			await expect
				.element(screen.getByRole('checkbox', {name: 'Select all rows'}))
				.toHaveAttribute('aria-checked', 'mixed');
		});

		it('should show the select-all checkbox as checked once a partial selection becomes the whole result set', async () => {
			// given
			let isAllSelected = false;
			const screen = await renderWithBatchActions({
				checkIsRowSelected: () => true,
				checkIsAllSelected: () => isAllSelected,
				checkIsIndeterminate: () => !isAllSelected,
				selectedCount: 2,
				onSelectAll: () => {
					isAllSelected = true;
				},
			});
			await expect
				.element(screen.getByRole('checkbox', {name: 'Select all rows'}))
				.toHaveAttribute('aria-checked', 'mixed');

			// when
			await userEvent.click(screen.getByRole('checkbox', {name: 'Select all rows'}));

			screen.setRows([...ROWS]);

			// then
			await expect.element(screen.getByRole('checkbox', {name: 'Select all rows'})).toBeChecked();
		});

		it('should show the select-all checkbox as checked when the whole result set is selected', async () => {
			// given / when
			const screen = await renderWithBatchActions({
				checkIsRowSelected: () => true,
				checkIsAllSelected: () => true,
				checkIsIndeterminate: () => false,
				selectedCount: 2,
			});

			// then
			await expect.element(screen.getByRole('checkbox', {name: 'Select all rows'})).toBeChecked();
		});

		it('should only toggle the row when the last unselected loaded row is checked', async () => {
			// given
			const onSelectAll = vi.fn();
			const onSelect = vi.fn();
			const screen = await renderWithBatchActions({
				checkIsRowSelected: (rowId) => rowId === 'row-1',
				checkIsIndeterminate: () => true,
				selectedCount: 1,
				onSelectAll,
				onSelect,
			});

			// when
			await userEvent.click(screen.getByRole('checkbox', {name: 'Select row row-2'}));

			// then
			expect(onSelect).toHaveBeenCalledWith('row-2');
			expect(onSelectAll).not.toHaveBeenCalled();
		});

		it('should select all matching rows when the partial select-all is clicked with every loaded row selected', async () => {
			// given
			const onSelectAll = vi.fn();
			const onReset = vi.fn();
			const screen = await renderWithBatchActions({
				checkIsRowSelected: () => true,
				checkIsAllSelected: () => false,
				checkIsIndeterminate: () => true,
				selectedCount: 2,
				onSelectAll,
				onReset,
			});

			// when
			await userEvent.click(screen.getByRole('checkbox', {name: 'Select all rows'}));

			// then
			expect(onSelectAll).toHaveBeenCalled();
			expect(onReset).not.toHaveBeenCalled();
		});

		it('should select all matching rows when select-all is clicked with one loaded row left unselected', async () => {
			// given
			const onSelectAll = vi.fn();
			const onSelect = vi.fn();
			const screen = await renderWithBatchActions({
				checkIsRowSelected: (rowId) => rowId === 'row-1',
				checkIsIndeterminate: () => true,
				selectedCount: 1,
				onSelectAll,
				onSelect,
			});

			// when
			await userEvent.click(screen.getByRole('checkbox', {name: 'Select all rows'}));

			// then
			expect(onSelectAll).toHaveBeenCalled();
			expect(onSelect).not.toHaveBeenCalled();
		});

		it('should keep selected rows that are not loaded when the last selected loaded row is unchecked', async () => {
			// given
			const onReset = vi.fn();
			const onSelect = vi.fn();
			const screen = await renderWithBatchActions({
				checkIsRowSelected: (rowId) => rowId === 'row-1',
				selectedCount: 5,
				onReset,
				onSelect,
			});

			// when
			await userEvent.click(screen.getByRole('checkbox', {name: 'Select row row-1'}));

			// then
			expect(onSelect).toHaveBeenCalledWith('row-1');
			expect(onReset).not.toHaveBeenCalled();
		});

		it('should clear a global selection when select-all is clicked while a next page is loading', async () => {
			// given
			const onSelectAll = vi.fn();
			const onReset = vi.fn();
			const screen = await renderWithBatchActions({
				isFetchingNextPage: true,
				checkIsRowSelected: () => true,
				checkIsAllSelected: () => true,
				selectedCount: 100,
				onSelectAll,
				onReset,
			});

			// when
			await userEvent.click(screen.getByRole('checkbox', {name: 'Select all rows'}));

			// then
			expect(onReset).toHaveBeenCalledOnce();
			expect(onSelectAll).not.toHaveBeenCalled();
		});

		it('should reset the whole selection when discard is clicked', async () => {
			// given
			const onReset = vi.fn();
			const onSelect = vi.fn();
			const screen = await renderWithBatchActions({
				checkIsRowSelected: (rowId) => rowId === 'row-1',
				selectedCount: 1,
				onReset,
				onSelect,
			});

			// when
			await userEvent.click(screen.getByRole('button', {name: 'Discard'}));

			// then
			expect(onReset).toHaveBeenCalledOnce();
			expect(onSelect).not.toHaveBeenCalled();
		});

		it('should keep the batch bar visible while selected rows are not loaded', async () => {
			// given / when
			const screen = await renderWithBatchActions({checkIsRowSelected: () => false, selectedCount: 5});

			// then
			await expect.element(screen.getByRole('toolbar', {name: 'Bulk actions'})).toBeVisible();
		});
	});

	describe('infinite scroll', () => {
		const {getObserver} = setUpFakeIntersectionObserver();

		const MANY_ROWS: Row[] = Array.from({length: 60}, (_, index) => ({id: `row-${index}`, name: `Process ${index}`}));

		function getScrollContainer() {
			return document.querySelector<HTMLElement>('[data-slot="data-table-scroll-container"]')!;
		}

		it('should keep the header fixed at the top of the scroll container while the rows scroll', async () => {
			// given
			await renderTable(
				{rows: MANY_ROWS, onVerticalScrollStartReach: vi.fn(), onVerticalScrollEndReach: vi.fn(), className: 'h-80'},
				{},
			);
			const container = getScrollContainer();
			const header = document.querySelector<HTMLElement>('[data-slot="table-header"]')!;
			const headerTopBefore = header.getBoundingClientRect().top;

			// when
			container.scrollTop = 400;

			// then
			await expect.poll(() => container.scrollTop).toBeGreaterThan(0);
			expect(header.getBoundingClientRect().top).toBe(headerTopBefore);
		});

		it('should not render scroll sentinels when no scroll handlers are given', async () => {
			// given / when
			await renderTable();

			// then
			expect(document.querySelectorAll('[data-slot^="data-table-"][data-slot$="-sentinel"]')).toHaveLength(0);
		});

		it('should call onVerticalScrollStartReach when the top sentinel intersects', async () => {
			// given
			const onVerticalScrollStartReach = vi.fn();
			await renderTable({onVerticalScrollStartReach, onVerticalScrollEndReach: vi.fn()});

			// when
			getObserver().intersect(document.querySelector('[data-slot="data-table-top-sentinel"]')!);

			// then
			expect(onVerticalScrollStartReach).toHaveBeenCalledOnce();
		});

		it('should call onVerticalScrollEndReach when the bottom sentinel intersects', async () => {
			// given
			const onVerticalScrollEndReach = vi.fn();
			await renderTable({onVerticalScrollStartReach: vi.fn(), onVerticalScrollEndReach});

			// when
			getObserver().intersect(document.querySelector('[data-slot="data-table-bottom-sentinel"]')!);

			// then
			expect(onVerticalScrollEndReach).toHaveBeenCalledOnce();
		});

		it('should not call onVerticalScrollEndReach when there is no next page', async () => {
			// given
			const onVerticalScrollEndReach = vi.fn();
			await renderTable({onVerticalScrollStartReach: vi.fn(), onVerticalScrollEndReach, hasNextPage: false});

			// when
			getObserver().intersect(document.querySelector('[data-slot="data-table-bottom-sentinel"]')!);

			// then
			expect(onVerticalScrollEndReach).not.toHaveBeenCalled();
		});

		it('should render a single skeleton row instead of the whole-table overlay while fetching the next page', async () => {
			// given
			const screen = await renderTable({onVerticalScrollEndReach: vi.fn()});

			// when
			screen.setIsFetchingNextPage(true);

			// then
			await expect.poll(() => document.querySelectorAll('[data-slot="data-table-skeleton-row"]').length).toBe(1);
			expect(screen.getByTestId('table-loading-overlay').elements()).toHaveLength(0);
		});
	});
});
