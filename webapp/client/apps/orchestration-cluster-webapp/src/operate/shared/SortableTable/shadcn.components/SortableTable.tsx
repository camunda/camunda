/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {useNavigate, useSearch} from '@tanstack/react-router';
import {Checkbox, DataTable, type DataTableColumn, type SortingConfig} from '@camunda/design-system';
import {cn} from '#/shared/cn';

type SortingState = NonNullable<SortingConfig['sortState']>;
type SortOrder = 'asc' | 'desc';
type TableSize = 'xs' | 'sm' | 'md' | 'lg' | 'xl';

type Column<TRow> = {
	key: string;
	label: string;
	sortKey?: string;
	isDefault?: boolean;
	defaultOrder?: SortOrder;
	render: (row: TRow) => React.ReactNode;
};

type BaseProps<TRow> = {
	columns: Column<TRow>[];
	rows: TRow[];
	rowKey: (row: TRow) => string;
	size?: TableSize;
	isFetching?: boolean;
	emptyState?: React.ReactNode;
	// Renders the empty state on its own, without the table/column headers, instead of inside a
	// full-width table row. Opt-in because most tables keep their headers visible when empty.
	hideHeaderWhenEmpty?: boolean;
	onSort?: (sortKey: string, order: SortOrder) => void;
	onVerticalScrollStartReach?: (scrollDown: (distance: number) => void) => void;
	onVerticalScrollEndReach?: (scrollUp: (distance: number) => void) => void;
	'data-testid'?: string;
};

type SelectionProps = {
	selectionType: 'checkbox';
	selectAllLabel: string;
	selectRowLabel: (rowId: string) => string;
	checkIsAllSelected: () => boolean;
	checkIsIndeterminate: () => boolean;
	checkIsRowSelected: (rowId: string) => boolean;
	onSelectAll: () => void;
	onSelect: (rowId: string) => void;
};

// Selection is all-or-nothing: passing `selectionType: 'checkbox'` requires every other
// selection prop too, so a caller can't accidentally render a checkbox with an empty aria-label.
type Props<TRow> = BaseProps<TRow> & ({selectionType?: undefined} | SelectionProps);

// The DS `Table` only renders a `sm` | `md` | `lg` density; Carbon's 5-step scale collapses onto
// it by rounding to the nearest supported size rather than dropping the two extremes silently.
const DS_TABLE_SIZE: Record<TableSize, 'sm' | 'md' | 'lg'> = {
	xs: 'sm',
	sm: 'sm',
	md: 'md',
	lg: 'lg',
	xl: 'lg',
};

/**
 * Observes two sentinel elements placed before/after the table and reports when the viewport
 * scrolls past them, mirroring `InfiniteScroller`'s contract (`onVerticalScrollStartReach` /
 * `onVerticalScrollEndReach`, each handed a `scrollBy`-style adjuster).
 *
 * `InfiniteScroller` itself clones its child and observes that child's first/last DOM children —
 * it assumes the caller owns the row markup directly (true for the Carbon `TableBody`). The DS
 * `DataTable` renders its own `<tbody>` internally with no render-prop hook to attach a ref to a
 * specific row, so there is nothing for `InfiniteScroller` to clone onto here. Sentinel elements
 * placed around the opaque `DataTable` output sidestep that gap entirely — the same technique the
 * Dashboard's `ExpandableList` already uses for its DS-backed lists.
 */
function useEdgeScrollDetection({
	scrollContainer,
	topSentinel,
	bottomSentinel,
	onVerticalScrollStartReach,
	onVerticalScrollEndReach,
}: {
	scrollContainer: HTMLElement | null;
	topSentinel: HTMLElement | null;
	bottomSentinel: HTMLElement | null;
	onVerticalScrollStartReach?: (scrollDown: (distance: number) => void) => void;
	onVerticalScrollEndReach?: (scrollUp: (distance: number) => void) => void;
}) {
	useEffect(() => {
		if (scrollContainer === null || (topSentinel === null && bottomSentinel === null)) {
			return;
		}

		const scrollDown = (distance: number) => scrollContainer.scrollTo(0, scrollContainer.scrollTop + distance);
		const scrollUp = (distance: number) => scrollContainer.scrollTo(0, scrollContainer.scrollTop - distance);

		let prevScrollTop = scrollContainer.scrollTop;
		const observer = new IntersectionObserver(
			(entries) => {
				const scrollTop = scrollContainer.scrollTop;

				entries
					.filter((entry) => entry.isIntersecting)
					.forEach(({target}) => {
						if (scrollTop > prevScrollTop && target === bottomSentinel) {
							onVerticalScrollEndReach?.(scrollUp);
						} else if (scrollTop < prevScrollTop && target === topSentinel) {
							onVerticalScrollStartReach?.(scrollDown);
						}
					});

				prevScrollTop = scrollTop;
			},
			{root: scrollContainer, threshold: 0.5},
		);

		if (topSentinel !== null) {
			observer.observe(topSentinel);
		}

		if (bottomSentinel !== null) {
			observer.observe(bottomSentinel);
		}

		return () => observer.disconnect();
	}, [scrollContainer, topSentinel, bottomSentinel, onVerticalScrollStartReach, onVerticalScrollEndReach]);
}

function SortableTable<TRow>(props: Props<TRow>) {
	const {
		columns,
		rows,
		rowKey,
		size = 'md',
		isFetching = false,
		emptyState,
		hideHeaderWhenEmpty = false,
		onSort,
		onVerticalScrollStartReach,
		onVerticalScrollEndReach,
		'data-testid': dataTestId,
	} = props;
	const selection = props.selectionType === 'checkbox' ? props : undefined;
	const hasScrollHandlers = onVerticalScrollStartReach !== undefined || onVerticalScrollEndReach !== undefined;

	const navigate = useNavigate();
	const search = useSearch({strict: false}) as {sort?: string};
	const [currentSortKey, currentSortOrderRaw] = (search.sort ?? '').split('+') as [string, SortOrder | undefined];

	const defaultColumn = columns.find((column) => column.isDefault);
	const activeColumn =
		currentSortKey === '' ? defaultColumn : columns.find((column) => column.sortKey === currentSortKey);
	const activeOrder: SortOrder =
		(currentSortKey === activeColumn?.sortKey ? currentSortOrderRaw : undefined) ??
		activeColumn?.defaultOrder ??
		'desc';
	const sortState: SortingState =
		activeColumn?.sortKey !== undefined ? [{id: activeColumn.sortKey, desc: activeOrder === 'desc'}] : [];
	const hasSortableColumns = columns.some((column) => column.sortKey !== undefined);

	const handleSortingChange = (next: SortingState) => {
		// Manual mode: TanStack still computes a "next" state from its own 3-way (asc/desc/none)
		// toggle rules, but only the column identity in it is trustworthy here — the direction is
		// recomputed below from our own 2-way (asc/desc, never "none") per-column rule instead, to
		// match the original `ColumnHeader` behaviour exactly.
		const clickedKey = next[0]?.id ?? sortState[0]?.id;
		const clickedColumn = columns.find((column) => column.sortKey === clickedKey);

		if (clickedColumn?.sortKey === undefined) {
			return;
		}

		const isActive = clickedColumn.sortKey === activeColumn?.sortKey;
		const order: SortOrder = isActive
			? activeOrder === 'asc'
				? 'desc'
				: 'asc'
			: (clickedColumn.defaultOrder ?? 'desc');

		onSort?.(clickedColumn.sortKey, order);
		void navigate({to: '.', search: (prev) => ({...prev, sort: `${clickedColumn.sortKey}+${order}`})});
	};

	const dataColumns: DataTableColumn<TRow>[] = useMemo(
		() =>
			columns.map((column) => ({
				id: column.sortKey ?? column.key,
				header: column.label,
				enableSorting: column.sortKey !== undefined,
				// `getCanSort()` (TanStack) only reports sortable when the column has an accessor — sort
				// order is server/URL-driven here, so the accessed "value" itself is never used, only its
				// presence is needed to satisfy that check.
				accessorFn: column.sortKey !== undefined ? () => null : undefined,
				cell: (info) => column.render(info.row.original),
			})),
		[columns],
	);

	// Row selection supports cross-page semantics (`checkIsAllSelected`/`checkIsIndeterminate` can
	// stay true while instances outside the currently-loaded page are implicitly selected, e.g. a
	// "select all N matching instances" action). `DataTable`'s own `rowSelection` config derives the
	// header checkbox's checked/indeterminate state purely from the rows in `data`, which can't
	// represent that. A manual leading column wired straight to the caller's predicates — exactly
	// like Carbon's `TableSelectAll`/`TableSelectRow` were — preserves that behaviour exactly.
	const selectionColumn: DataTableColumn<TRow> | undefined = useMemo(() => {
		if (selection === undefined) {
			return undefined;
		}

		return {
			id: 'select',
			enableSorting: false,
			header: () => (
				// `data-interactive` mirrors `DataTable`'s own built-in select column: it keeps the
				// checkbox click from also triggering a future `onRowClick`/`rowHref`, should one ever
				// be added to a sortable table.
				<div data-interactive className="flex items-center">
					<Checkbox
						checked={selection.checkIsIndeterminate() ? 'indeterminate' : selection.checkIsAllSelected()}
						onCheckedChange={() => selection.onSelectAll()}
						aria-label={selection.selectAllLabel}
					/>
				</div>
			),
			cell: (info) => {
				const id = rowKey(info.row.original);
				return (
					<div data-interactive className="flex items-center">
						<Checkbox
							checked={selection.checkIsRowSelected(id)}
							onCheckedChange={() => selection.onSelect(id)}
							aria-label={selection.selectRowLabel(id)}
						/>
					</div>
				);
			},
		};
	}, [selection, rowKey]);

	const tableColumns = selectionColumn !== undefined ? [selectionColumn, ...dataColumns] : dataColumns;

	const [scrollContainer, setScrollContainerState] = useState<HTMLDivElement | null>(null);
	const scrollContainerRef = useRef<HTMLDivElement | null>(null);
	const setScrollContainer = useCallback((node: HTMLDivElement | null) => {
		scrollContainerRef.current = node;
		setScrollContainerState(node);
	}, []);
	const [topSentinel, setTopSentinel] = useState<HTMLDivElement | null>(null);
	const [bottomSentinel, setBottomSentinel] = useState<HTMLDivElement | null>(null);

	useEdgeScrollDetection({
		scrollContainer,
		topSentinel,
		bottomSentinel,
		onVerticalScrollStartReach,
		onVerticalScrollEndReach,
	});

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

	return (
		<div
			ref={hasScrollHandlers ? setScrollContainer : undefined}
			className={cn('relative', hasScrollHandlers && 'h-full flex-1 overflow-y-auto')}
			data-testid={dataTestId}
		>
			{isFetching && (
				<div
					aria-hidden
					className="absolute inset-0 z-10 bg-neutral-background-subtle opacity-50"
					data-testid={dataTestId !== undefined ? `${dataTestId}-loading-overlay` : undefined}
				/>
			)}
			{hasScrollHandlers && <div ref={setTopSentinel} data-testid="sortable-table-top-sentinel" />}
			<DataTable<TRow>
				columns={tableColumns}
				data={rows}
				getRowId={rowKey}
				size={DS_TABLE_SIZE[size]}
				emptyState={emptyState}
				sorting={hasSortableColumns ? {manual: true, sortState, onSortingChange: handleSortingChange} : undefined}
			/>
			{hasScrollHandlers && <div ref={setBottomSentinel} data-testid="sortable-table-bottom-sentinel" />}
		</div>
	);
}

export {SortableTable};
