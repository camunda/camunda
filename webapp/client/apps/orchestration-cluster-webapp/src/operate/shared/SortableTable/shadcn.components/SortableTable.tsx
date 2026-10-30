/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useLayoutEffect, useMemo, useRef} from 'react';
import {useNavigate, useSearch} from '@tanstack/react-router';
import {z} from 'zod';
import {DataTable, type DataTableColumn, type SortingConfig} from '@camunda/design-system';
import type {QuerySortOrder} from '@camunda/camunda-api-zod-schemas/8.11';
import {cn} from '#/shared/cn';
import {createSortSearchParamSchema} from '#/shared/sortSearchParam';

type SortingState = NonNullable<SortingConfig['sortState']>;

const sortSearchParamSchema = createSortSearchParamSchema(z.string());
type TableSize = 'xs' | 'sm' | 'md' | 'lg' | 'xl';

type Column<TRow> = {
	key: string;
	label: string;
	sortKey?: string;
	isDefault?: boolean;
	defaultOrder?: QuerySortOrder;
	render: (row: TRow) => React.ReactNode;
};

type BaseProps<TRow> = {
	columns: Column<TRow>[];
	rows: TRow[];
	rowKey: (row: TRow) => string;
	/**
	 * Accessible name for the underlying `<table>` (forwarded as `DataTable`'s `aria-label`).
	 * Required so every caller supplies a localized string — a table with no name is
	 * indistinguishable from any other table in a screen reader's table list.
	 */
	ariaLabel: string;
	size?: TableSize;
	/**
	 * Whether the full result set is being (re)fetched — e.g. the initial load, or a
	 * refetch triggered by a filter/sort change. Not meant for incremental pagination
	 * (`onVerticalScrollStartReach`/`onVerticalScrollEndReach`), which already tracks its
	 * own in-flight state and should feel incremental rather than block the whole table.
	 * Renders the design system's built-in skeleton rows when there is no data yet
	 * (initial load), or a dimming overlay over the existing rows otherwise (refetch).
	 */
	isFetching?: boolean;
	/**
	 * Whether a previous page is currently being fetched via `onVerticalScrollStartReach`.
	 * The design system renders a single skeleton row above the existing rows.
	 */
	isFetchingPreviousPage?: boolean;
	/** Same as `isFetchingPreviousPage`, for a next page fetched via `onVerticalScrollEndReach`. */
	isFetchingNextPage?: boolean;
	/** Whether a previous page is available to fetch via `onVerticalScrollStartReach`. Defaults to `true`. */
	hasPreviousPage?: boolean;
	/** Same as `hasPreviousPage`, for a next page fetched via `onVerticalScrollEndReach`. */
	hasNextPage?: boolean;
	emptyState?: React.ReactNode;
	hideHeaderWhenEmpty?: boolean;
	/**
	 * Extra classes for the outer wrapper. Use descendant selectors (e.g.
	 * `[&_[data-slot=table-container]]:rounded-none`) to restyle the design system's own table
	 * container, which `DataTable` does not expose.
	 */
	className?: string;
	onSort?: (sortKey: string, order: QuerySortOrder) => void;
	onVerticalScrollStartReach?: () => void;
	onVerticalScrollEndReach?: () => void;
	'data-testid'?: string;
};

type SelectionProps = {
	selectionType: 'checkbox';
	selectAllLabel: string;
	selectRowLabel: (rowId: string) => string;
	checkIsAllSelected: () => boolean;
	checkIsIndeterminate: () => boolean;
	checkIsRowSelected: (rowId: string) => boolean;
	// Includes selected rows that are not loaded (e.g. evicted pages or "select all")
	selectedCount: number;
	onSelectAll: () => void;
	onSelect: (rowId: string) => void;
	onReset: () => void;
	batchActions?: BatchAction[];
	selectionCountLabel?: string;
	batchActionsLabel?: string;
	batchActionsCancelLabel?: string;
};

type BatchAction = {
	id: string;
	label: string;
	variant?: 'default' | 'destructive';
	disabled?: boolean;
	onClick: () => void;
};

type Props<TRow> = BaseProps<TRow> & ({selectionType?: undefined} | SelectionProps);

// Overrides that depend on DS DataTable internals (`data-slot` hooks). Remove once the DS exposes props.
function getDataTableOverrides({
	isEmpty,
	hasSelectionColumn,
	hasScrollHandlers,
}: {
	isEmpty: boolean;
	hasSelectionColumn: boolean;
	hasScrollHandlers: boolean;
}) {
	return cn(
		// data-table: fill the panel so the DS scroll container gets a bounded height and pins the header
		hasScrollHandlers && 'h-full [&>div]:h-full',
		// data-table, data-table > div, table-container, table: stretch the empty state to the panel height;
		// table-header tr: keep the header row at 40px while stretched
		isEmpty &&
			'[&>div]:h-full [&_[data-slot=data-table]]:h-full [&_[data-slot=data-table]>div]:h-full [&_[data-slot=table-container]]:h-full [&_[data-slot=table]]:h-full [&_[data-slot=table-header]_tr]:h-10',
		hasSelectionColumn &&
			cn(
				// data-table-skeleton-row: the DS draws the same wide bar in every column, including the checkbox one
				'[&_[data-slot=data-table-skeleton-row]_td:first-child>div]:w-4 [&_[data-slot=data-table-skeleton-row]_td:first-child>div]:rounded-sm',
				// tbody tr: selection lives outside the DS table, so rows with a checked checkbox get the token the DS
				// uses for `data-state=selected`
				'[&_tbody_tr:has([aria-checked=true])]:bg-[var(--accent-background-subtle)]',
				// skeleton row: the DS selection column renders a checkbox in every row, including loading skeletons
				'[&_[data-slot=data-table-skeleton-row]_[role=checkbox]]:hidden',
			),
	);
}

const DS_TABLE_SIZE: Record<TableSize, 'sm' | 'md' | 'lg'> = {
	xs: 'sm',
	sm: 'sm',
	md: 'md',
	lg: 'lg',
	xl: 'lg',
};

function SortableTable<TRow>(props: Props<TRow>) {
	const {
		columns,
		rows,
		rowKey,
		ariaLabel,
		size = 'md',
		isFetching = false,
		isFetchingPreviousPage = false,
		isFetchingNextPage = false,
		hasPreviousPage = true,
		hasNextPage = true,
		emptyState,
		hideHeaderWhenEmpty = false,
		className,
		onSort,
		onVerticalScrollStartReach,
		onVerticalScrollEndReach,
		'data-testid': dataTestId,
	} = props;
	const selection = props.selectionType === 'checkbox' ? props : undefined;
	const hasScrollHandlers = onVerticalScrollStartReach !== undefined || onVerticalScrollEndReach !== undefined;

	const navigate = useNavigate();
	const search = useSearch({strict: false}) as {sort?: string};
	const {field: currentSortKey, order: currentSortOrder} = useMemo(
		() => sortSearchParamSchema.safeParse(search.sort).data ?? {field: '', order: undefined},
		[search.sort],
	);

	const defaultColumn = columns.find((column) => column.isDefault);
	const activeColumn =
		currentSortKey === '' ? defaultColumn : columns.find((column) => column.sortKey === currentSortKey);
	const activeOrder: QuerySortOrder =
		(currentSortKey === activeColumn?.sortKey ? currentSortOrder : undefined) ?? activeColumn?.defaultOrder ?? 'DESC';
	const sortState: SortingState =
		activeColumn?.sortKey !== undefined ? [{id: activeColumn.sortKey, desc: activeOrder === 'DESC'}] : [];
	const hasSortableColumns = columns.some((column) => column.sortKey !== undefined);

	const handleSortingChange = (next: SortingState) => {
		const clickedKey = next[0]?.id ?? sortState[0]?.id;
		const clickedColumn = columns.find((column) => column.sortKey === clickedKey);

		if (clickedColumn?.sortKey === undefined) {
			return;
		}

		const isActive = clickedColumn.sortKey === activeColumn?.sortKey;
		const order: QuerySortOrder = isActive
			? activeOrder === 'ASC'
				? 'DESC'
				: 'ASC'
			: (clickedColumn.defaultOrder ?? 'DESC');

		onSort?.(clickedColumn.sortKey, order);
		void navigate({to: '.', search: (prev) => ({...prev, sort: `${clickedColumn.sortKey}+${order}`})});
	};

	const hasSelectionColumn = selection !== undefined;

	const dataColumns: DataTableColumn<TRow>[] = useMemo(
		() =>
			columns.map((column) => ({
				id: column.sortKey ?? `col-${column.key}`,
				header: column.label,
				enableSorting: column.sortKey !== undefined,
				accessorFn: column.sortKey !== undefined ? () => null : undefined,
				cell: (info) => column.render(info.row.original),
			})),
		[columns],
	);

	const loadedRowIds = useMemo(() => rows.map(rowKey), [rows, rowKey]);
	const checkIsRowSelected = selection?.checkIsRowSelected;
	const selectedRowIds = useMemo(() => {
		if (checkIsRowSelected === undefined) {
			return undefined;
		}
		return Object.fromEntries(loadedRowIds.filter(checkIsRowSelected).map((id) => [id, true]));
	}, [loadedRowIds, checkIsRowSelected]);

	// The DS reports a full next selection without saying which control changed it, so remember what was clicked
	const lastCheckboxClickRef = useRef<'header' | 'row' | null>(null);
	const trackCheckboxClick = (event: React.MouseEvent) => {
		const checkbox = (event.target as Element).closest('[role=checkbox]');
		if (checkbox === null) {
			lastCheckboxClickRef.current = null;
			return;
		}
		lastCheckboxClickRef.current = checkbox.closest('[data-slot=table-header]') === null ? 'row' : 'header';
	};

	// The DS reports a full next selection; translate it into the callbacks of the selection model
	const handleSelectedRowsChange = (next: Record<string, boolean>) => {
		if (selection === undefined || selectedRowIds === undefined) {
			return;
		}
		const nextIds = loadedRowIds.filter((id) => next[id]);
		const changedIds = loadedRowIds.filter((id) => (next[id] ?? false) !== (selectedRowIds[id] ?? false));
		const [onlyChangedId] = changedIds;

		if (lastCheckboxClickRef.current === 'row' && changedIds.length === 1 && onlyChangedId !== undefined) {
			selection.onSelect(onlyChangedId);
			return;
		}

		if (lastCheckboxClickRef.current === 'header' && selection.checkIsAllSelected()) {
			selection.onReset();
			return;
		}

		// A fully selected loaded window with matches outside it shows a partial header, which selects everything
		const isPartialHeaderClick = lastCheckboxClickRef.current === 'header' && !selection.checkIsAllSelected();

		if (nextIds.length === 0 && !isPartialHeaderClick) {
			// Also what the batch bar's cancel button reports, so it must clear rows that are not loaded too
			selection.onReset();
		} else if (!selection.checkIsAllSelected()) {
			// `onSelectAll` toggles: from a partial selection the first call clears it, the second selects everything
			if (selection.checkIsIndeterminate()) {
				selection.onSelectAll();
			}
			selection.onSelectAll();
		}
	};

	const batchActions = selection?.batchActions;

	// The DS shows the batch bar from the resolved rows, so pad with placeholders for selected rows that are not loaded
	const getSelectedRows = () => {
		if (selection === undefined) {
			return [];
		}
		const loadedSelected = rows.filter((row) => selection.checkIsRowSelected(rowKey(row)));
		const missingCount = Math.max(selection.selectedCount - loadedSelected.length, 0);
		// Only the length matters for the batch bar; extending it leaves holes instead of allocating one entry per
		// unloaded selected row, which can be the whole result set
		loadedSelected.length += missingCount;
		return loadedSelected;
	};

	const tableRootRef = useRef<HTMLDivElement | null>(null);

	// The DS derives the header checkbox from the loaded rows only, so a window that is fully selected looks like
	// "all selected" even when matches outside it are not. Show it as partial until the whole result set is selected.
	useLayoutEffect(() => {
		if (selection === undefined || loadedRowIds.length === 0) {
			return;
		}
		const headerCheckbox = tableRootRef.current?.querySelector('[data-slot=table-header] [role=checkbox]');
		const allLoadedSelected = loadedRowIds.every(selection.checkIsRowSelected);
		if (headerCheckbox && allLoadedSelected) {
			// The DS prop does not change between the two states, so React never rewrites what was patched before
			const isPartial = !selection.checkIsAllSelected();
			headerCheckbox.setAttribute('aria-checked', isPartial ? 'mixed' : 'true');
			headerCheckbox.setAttribute('data-state', isPartial ? 'indeterminate' : 'checked');
		}
	});

	const getRowId = useCallback((row: TRow) => rowKey(row), [rowKey]);

	if (rows.length === 0 && emptyState !== undefined && hideHeaderWhenEmpty) {
		return (
			<div
				className={cn('flex h-full items-center justify-center', hasScrollHandlers && 'flex-1 overflow-y-auto')}
				data-testid={dataTestId}
			>
				{emptyState}
			</div>
		);
	}

	const isInitialLoad = isFetching && rows.length === 0;

	return (
		<div className={cn('relative', hasScrollHandlers && 'h-full min-h-0 flex-1', className)}>
			{isFetching && !isInitialLoad && (
				<div
					aria-hidden
					className="pointer-events-none absolute inset-0 z-20 bg-neutral-background-subtle opacity-50"
					data-testid={dataTestId !== undefined ? `${dataTestId}-loading-overlay` : undefined}
				/>
			)}
			<div
				ref={tableRootRef}
				onClickCapture={hasSelectionColumn ? trackCheckboxClick : undefined}
				className={cn(getDataTableOverrides({isEmpty: rows.length === 0, hasSelectionColumn, hasScrollHandlers}))}
				data-testid={dataTestId}
			>
				<DataTable<TRow>
					columns={dataColumns}
					data={rows}
					getRowId={getRowId}
					aria-label={ariaLabel}
					size={DS_TABLE_SIZE[size]}
					emptyState={emptyState}
					loading={isInitialLoad}
					infiniteScroll={
						hasScrollHandlers
							? {
									root: 'self',
									hasPreviousPage: onVerticalScrollStartReach !== undefined && hasPreviousPage,
									hasNextPage: onVerticalScrollEndReach !== undefined && hasNextPage,
									isFetchingPreviousPage,
									isFetchingNextPage,
									onLoadPrevious: onVerticalScrollStartReach,
									onLoadNext: onVerticalScrollEndReach,
								}
							: undefined
					}
					rowSelection={
						selectedRowIds === undefined ? undefined : {selectedRowIds, onSelectedRowsChange: handleSelectedRowsChange}
					}
					selectAllRowsLabel={selection?.selectAllLabel}
					getRowSelectionLabel={selection === undefined ? undefined : (row) => selection.selectRowLabel(rowKey(row))}
					batchActions={batchActions}
					batchActionsLabel={selection?.batchActionsLabel}
					batchActionsCancelLabel={selection?.batchActionsCancelLabel}
					selectionCountLabel={selection?.selectionCountLabel}
					getSelectedRows={selection === undefined ? undefined : getSelectedRows}
					sorting={hasSortableColumns ? {manual: true, sortState, onSortingChange: handleSortingChange} : undefined}
				/>
			</div>
		</div>
	);
}

export {SortableTable};
