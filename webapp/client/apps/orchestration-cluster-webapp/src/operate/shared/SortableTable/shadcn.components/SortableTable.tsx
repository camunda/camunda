/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState} from 'react';
import {useNavigate, useSearch} from '@tanstack/react-router';
import {Checkbox, DataTable, type DataTableColumn, type SortingConfig} from '@camunda/design-system';
import {InfiniteScroller} from '#/operate/shared/InfiniteScroller/InfiniteScroller';
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
	 * Whether a previous (older-scrolled-past) page is currently being fetched via
	 * `onVerticalScrollStartReach`. Renders a single skeleton row above the existing rows,
	 * matching the design system's per-cell skeleton look instead of the whole-table
	 * `isFetching` overlay — the rest of the table stays fully interactive while it loads.
	 */
	isFetchingPreviousPage?: boolean;
	/** Same as `isFetchingPreviousPage`, for a next page fetched via `onVerticalScrollEndReach`. */
	isFetchingNextPage?: boolean;
	/**
	 * Whether a previous page is available to fetch via `onVerticalScrollStartReach`. Defaults
	 * to `true`. Lets scroll-compensation skip arming entirely when the consumer's callback is
	 * known to no-op (no page left, or the next page is already being fetched — pagination
	 * fetches are assumed mutually exclusive), instead of relying on `isFetchingPreviousPage` —
	 * which never flips in that case — to signal as much.
	 */
	hasPreviousPage?: boolean;
	/** Same as `hasPreviousPage`, for a next page fetched via `onVerticalScrollEndReach`. */
	hasNextPage?: boolean;
	/**
	 * Accessible label for the previous-page skeleton row. Required (rather than defaulted) so
	 * every caller supplies a localized string, matching `selectAllLabel`/`selectRowLabel` below.
	 */
	loadingPreviousPageLabel: string;
	/** Same as `loadingPreviousPageLabel`, for the next-page skeleton row. */
	loadingNextPageLabel: string;
	emptyState?: React.ReactNode;
	hideHeaderWhenEmpty?: boolean;
	/**
	 * Extra classes for the outer wrapper. Use descendant selectors (e.g.
	 * `[&_[data-slot=table-container]]:rounded-none`) to restyle the design system's own table
	 * container, which `DataTable` does not expose.
	 */
	className?: string;
	onSort?: (sortKey: string, order: SortOrder) => void;
	onVerticalScrollStartReach?: () => void;
	onVerticalScrollEndReach?: () => void;
	'data-testid'?: string;
};

type DisplayRow<TRow> = {kind: 'row'; row: TRow} | {kind: 'skeleton'; id: 'previous' | 'next'; label: string};

// Identifies the previous-page skeleton row regardless of whether the caller passed a
// `data-testid` (that prop is optional and only meant for testing — the scroll compensation
// below must work even when it's absent).
const SKELETON_EDGE_ATTRIBUTE = 'data-skeleton-edge';

function SkeletonCell({
	label,
	isFirstCell,
	edge,
	testId,
}: {
	label: string;
	isFirstCell: boolean;
	edge: 'previous' | 'next';
	testId: string | undefined;
}) {
	return (
		<div
			className="flex items-center"
			data-testid={isFirstCell ? testId : undefined}
			{...(isFirstCell ? {[SKELETON_EDGE_ATTRIBUTE]: edge} : {})}
			role={isFirstCell ? 'status' : undefined}
			aria-live={isFirstCell ? 'polite' : undefined}
		>
			{isFirstCell && <span className="sr-only">{label}</span>}
			<div aria-hidden className="h-4 w-3/4 animate-pulse rounded bg-neutral-background-strong" />
		</div>
	);
}

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

type Props<TRow> = BaseProps<TRow> & ({selectionType?: undefined} | SelectionProps);

const DS_TABLE_SIZE: Record<TableSize, 'sm' | 'md' | 'lg'> = {
	xs: 'sm',
	sm: 'sm',
	md: 'md',
	lg: 'lg',
	xl: 'lg',
};

function readRowHeights(scrollContainer: HTMLElement): number[] {
	// Excludes the loading-skeleton row (identified by the dedicated `SKELETON_EDGE_ATTRIBUTE`
	// marker, see `SkeletonCell`): it isn't part of `rows` and would otherwise skew the
	// positional height sums below, which assume index N of this array lines up with index N of
	// `rows`. Matching on `role="status"` instead would also exclude legitimate data rows whose
	// own content happens to use that role (e.g. a `BatchStateIndicator` cell).
	return Array.from(
		scrollContainer.querySelectorAll<HTMLElement>(
			`[data-slot="table-body"] [data-slot="table-row"]:not(:has([${SKELETON_EDGE_ATTRIBUTE}]))`,
		),
	).map((rowElement) => rowElement.getBoundingClientRect().height);
}

function sumRowHeights(heights: number[], count: number): number {
	return heights.slice(0, count).reduce((total, height) => total + height, 0);
}

function useEdgeScrollCompensation<TRow>({
	scrollContainer,
	scrollContainerRef,
	rows,
	rowKey,
	hasPreviousPage,
	hasNextPage,
	isFetching,
	isFetchingPreviousPage,
	isFetchingNextPage,
	onVerticalScrollStartReach,
	onVerticalScrollEndReach,
}: {
	scrollContainer: HTMLElement | null;
	scrollContainerRef: React.RefObject<HTMLElement | null>;
	rows: TRow[];
	rowKey: (row: TRow) => string;
	hasPreviousPage: boolean;
	hasNextPage: boolean;
	isFetching: boolean;
	isFetchingPreviousPage: boolean;
	isFetchingNextPage: boolean;
	onVerticalScrollStartReach?: () => void;
	onVerticalScrollEndReach?: () => void;
}) {
	const latestOnVerticalScrollStartReachRef = useRef(onVerticalScrollStartReach);
	const latestOnVerticalScrollEndReachRef = useRef(onVerticalScrollEndReach);
	const latestRowsRef = useRef(rows);
	const latestRowKeyRef = useRef(rowKey);
	useEffect(() => {
		latestOnVerticalScrollStartReachRef.current = onVerticalScrollStartReach;
		latestOnVerticalScrollEndReachRef.current = onVerticalScrollEndReach;
		latestRowsRef.current = rows;
		latestRowKeyRef.current = rowKey;
	});

	const rowIdsBeforePrependRef = useRef<Set<string> | null>(null);
	const pendingScrollDownRef = useRef<((distance: number) => void) | null>(null);
	const rowIdsBeforeAppendRef = useRef<string[] | null>(null);
	const rowHeightsBeforeAppendRef = useRef<number[] | null>(null);
	const pendingScrollUpRef = useRef<((distance: number) => void) | null>(null);
	const previousSkeletonCompensationRef = useRef(0);

	// A row-order match alone isn't proof that the armed fetch actually produced `rows`: a
	// query-key swap (sort/filter) can land cached data for an entirely different query in the
	// same slot, and that data could coincidentally share a leading or trailing run of ids with
	// the stale baseline (e.g. the same unfiltered rows, just re-paged). Require that
	// `isFetchingPreviousPage`/`isFetchingNextPage` was actually observed `true` at some point
	// since this baseline was armed, so a `rows` change can only be attributed to a fetch that
	// verifiably started. Reset when a new baseline arms; set by the tracking effects below,
	// which run before the consume effects in the same commit.
	const hasObservedPreviousPageFetchStartRef = useRef(false);
	const hasObservedNextPageFetchStartRef = useRef(false);

	// Only arm the baseline when a fetch can actually start: `hasPreviousPage`/`hasNextPage`
	// tell us upfront whether the consumer's `onVerticalScrollStartReach`/
	// `onVerticalScrollEndReach` is about to no-op (no page left, or one already in flight) —
	// unlike `isFetchingPreviousPage`/`isFetchingNextPage`, which only ever reports a fetch that
	// did start, this is known synchronously at reach time. Skipping the arm in the no-op case
	// prevents a stale baseline from surviving until an unrelated `rows` change (e.g. sorting or
	// filtering) and being misread as that fetch having completed. Both default to `true` so
	// callers that don't track pagination availability keep the previous, unconditional behavior.
	// Also skip arming while the *opposite* direction is already fetching: infinite-query pages
	// share one request pipeline, so a consumer is expected to no-op one direction's fetch while
	// the other is in flight (see `PaginatedSortableTable`) — without this, the no-op direction
	// would still arm here and misread the other direction's eventual `rows` change as its own.
	// Finally, skip entirely when the consumer never configured this direction's callback at all
	// (e.g. only `onVerticalScrollEndReach` is given): `InfiniteScroller` always receives both
	// handlers below regardless, so reaching that sentinel would otherwise still arm a baseline
	// for a fetch that can never happen.
	const handleScrollStartReach = useCallback(
		(scrollDown: (distance: number) => void) => {
			if (
				hasPreviousPage &&
				!isFetchingPreviousPage &&
				!isFetchingNextPage &&
				latestOnVerticalScrollStartReachRef.current !== undefined
			) {
				rowIdsBeforePrependRef.current = new Set(latestRowsRef.current.map(latestRowKeyRef.current));
				pendingScrollDownRef.current = scrollDown;
				hasObservedPreviousPageFetchStartRef.current = false;
			}
			latestOnVerticalScrollStartReachRef.current?.();
		},
		[hasPreviousPage, isFetchingPreviousPage, isFetchingNextPage],
	);

	const handleScrollEndReach = useCallback(
		(scrollUp: (distance: number) => void) => {
			if (
				hasNextPage &&
				!isFetchingNextPage &&
				!isFetchingPreviousPage &&
				scrollContainer !== null &&
				latestOnVerticalScrollEndReachRef.current !== undefined
			) {
				rowIdsBeforeAppendRef.current = latestRowsRef.current.map(latestRowKeyRef.current);
				rowHeightsBeforeAppendRef.current = readRowHeights(scrollContainer);
				pendingScrollUpRef.current = scrollUp;
				hasObservedNextPageFetchStartRef.current = false;
			}
			latestOnVerticalScrollEndReachRef.current?.();
		},
		[hasNextPage, isFetchingNextPage, isFetchingPreviousPage, scrollContainer],
	);

	// Record that the armed fetch genuinely started, ahead of the consume effects below so the
	// flags they read already reflect this commit. Declared as `useLayoutEffect` (not `useEffect`)
	// to run in the same phase and ordering as those effects.
	useLayoutEffect(() => {
		if (isFetchingPreviousPage) {
			hasObservedPreviousPageFetchStartRef.current = true;
		}
	}, [isFetchingPreviousPage]);

	useLayoutEffect(() => {
		if (isFetchingNextPage) {
			hasObservedNextPageFetchStartRef.current = true;
		}
	}, [isFetchingNextPage]);

	// `isFetching` means the query identity itself changed (initial load, or a sort/filter
	// change triggering a full refetch) — the previous/next page baselines above were armed for
	// the *old* query, so the cached result that eventually lands for the *new* one must never be
	// compared against them: it can coincidentally share a leading or trailing run of row ids with
	// the stale baseline and be misread as a genuine prepend/append. Disarm both baselines (and
	// any skeleton compensation already applied) the moment a full refetch starts. Declared ahead
	// of the consume effects below so this reset lands in the same commit as `isFetching` flipping
	// true, before they can read the now-stale refs.
	useLayoutEffect(() => {
		if (!isFetching) {
			return;
		}

		const container = scrollContainerRef.current;
		if (container !== null && previousSkeletonCompensationRef.current !== 0) {
			container.scrollTop -= previousSkeletonCompensationRef.current;
		}
		previousSkeletonCompensationRef.current = 0;

		rowIdsBeforePrependRef.current = null;
		pendingScrollDownRef.current = null;
		hasObservedPreviousPageFetchStartRef.current = false;

		rowIdsBeforeAppendRef.current = null;
		rowHeightsBeforeAppendRef.current = null;
		pendingScrollUpRef.current = null;
		hasObservedNextPageFetchStartRef.current = false;
	}, [isFetching, scrollContainerRef]);

	// The previous-page skeleton row adds height above the currently-visible rows the instant
	// it appears, which would otherwise shove them down the screen. Scroll down by its rendered
	// height immediately so the same rows stay in view; the prepend effect below subtracts this
	// once the real page replaces the skeleton, so the two don't double-compensate. Identifying
	// the skeleton via `SKELETON_EDGE_ATTRIBUTE` (always rendered by `SkeletonCell`, regardless
	// of whether the caller passed a `data-testid`) keeps this working even when that optional,
	// testing-only prop is omitted.
	useLayoutEffect(() => {
		const container = scrollContainerRef.current;
		if (container === null || !isFetchingPreviousPage || previousSkeletonCompensationRef.current !== 0) {
			return;
		}

		const skeletonRow = container
			.querySelector(`[${SKELETON_EDGE_ATTRIBUTE}="previous"]`)
			?.closest('[data-slot="table-row"]');
		const skeletonHeight = skeletonRow?.getBoundingClientRect().height ?? 0;

		if (skeletonHeight === 0) {
			return;
		}

		container.scrollTop += skeletonHeight;
		previousSkeletonCompensationRef.current = skeletonHeight;
	}, [isFetchingPreviousPage, scrollContainer, scrollContainerRef]);

	useLayoutEffect(() => {
		const previousRowIds = rowIdsBeforePrependRef.current;
		const scrollDown = pendingScrollDownRef.current;
		if (scrollContainer === null || previousRowIds === null || scrollDown === null) {
			return;
		}

		const currentRowIds = new Set(rows.map(rowKey));
		// `rows` can change identity for unrelated reasons (e.g. an `isFetching` flag
		// toggling an upstream, unmemoized derivation) before the actual prepended
		// page lands. Skip until the set of row ids actually differs from the
		// baseline, so a spurious render with identical content can't consume the
		// pending compensation before the real one arrives. Comparing the row count
		// alone isn't enough: a concurrent eviction on the opposite end (maxPages)
		// can leave the total count unchanged even though the content really changed.
		const isUnchanged =
			currentRowIds.size === previousRowIds.size && [...currentRowIds].every((id) => previousRowIds.has(id));
		if (isUnchanged) {
			return;
		}

		if (isFetchingPreviousPage) {
			// The tracked previous-page fetch hasn't settled yet, so this `rows` change isn't its
			// result — it's an unrelated, concurrent refetch (e.g. a sort/filter change racing the
			// pagination fetch). Re-baseline against this new state instead of consuming it:
			// comparing the eventual real page against the original, now-stale baseline would
			// misattribute this unrelated change's own row delta as part of the prepend.
			rowIdsBeforePrependRef.current = currentRowIds;
			return;
		}

		const firstExistingRowIndex = rows.findIndex((row) => previousRowIds.has(rowKey(row)));
		const prependedRowCount = firstExistingRowIndex === -1 ? rows.length : firstExistingRowIndex;

		// A genuine prepend keeps every surviving previously-known row in its original relative
		// order, trailing the newly-prepended ones — some may be missing if a concurrent eviction
		// on the opposite end (maxPages) dropped them, but the ones that remain must still line up.
		// If a previous-page fetch is instead cancelled by, say, a sort/filter change landing in the
		// same commit, `isFetchingPreviousPage` can already read `false` by the time `rows` updates,
		// which would otherwise slip past the check above and be misread as a real prepend. Verify
		// the surviving rows actually continue in the expected order before compensating; if they
		// don't, bail without consuming the baseline — the give-up effect below runs in this same
		// commit (its own `isFetchingPreviousPage` dependency just changed too) and clears it, along
		// with any skeleton compensation still pending, so a later, real prepend starts clean.
		const survivingPreviousIds = [...previousRowIds].filter((id) => currentRowIds.has(id));
		const isGenuinePrepend =
			firstExistingRowIndex !== -1 &&
			survivingPreviousIds.every((id, index) => {
				const row = rows[firstExistingRowIndex + index];
				return row !== undefined && rowKey(row) === id;
			});
		// Row-order continuity alone still isn't proof this `rows` change came from the armed
		// fetch: a query-key swap (sort/filter) can land cached data for an entirely different
		// query in the same slot, and that data could coincidentally share a leading/trailing run
		// of ids with the stale baseline (e.g. the same unfiltered rows, just re-paged). Requiring
		// that `isFetchingPreviousPage` was actually observed `true` since this baseline armed
		// rules that out — a query that never truly fetched can't have produced this change.
		if (!isGenuinePrepend || !hasObservedPreviousPageFetchStartRef.current) {
			return;
		}

		// Subtract whatever the skeleton-insertion effect above already applied: the net height
		// change from "skeleton visible" to "real rows visible" is the real prepended height minus
		// the skeleton's own height, not the full prepended height.
		scrollDown(
			sumRowHeights(readRowHeights(scrollContainer), prependedRowCount) - previousSkeletonCompensationRef.current,
		);
		previousSkeletonCompensationRef.current = 0;
		rowIdsBeforePrependRef.current = null;
		pendingScrollDownRef.current = null;
	}, [rows, rowKey, scrollContainer, isFetchingPreviousPage]);

	// If the previous-page fetch ends (error, abort, or an empty result) without the prepend
	// effect above ever consuming the baseline — which only happens once `rows` actually
	// changes — the view would stay shifted down by any applied skeleton compensation forever,
	// and the stale baseline would survive to be misread against a later, unrelated `rows`
	// change (e.g. sorting or filtering). Detect that abandoned state here and undo it.
	// Declared after the prepend effect above so that, on a successful fetch where both
	// `isFetchingPreviousPage` flipping false and `rows` changing land in the same commit, the
	// refs have already been reset by then and this is a no-op.
	useLayoutEffect(() => {
		const container = scrollContainerRef.current;
		if (isFetchingPreviousPage || container === null) {
			return;
		}

		if (previousSkeletonCompensationRef.current !== 0) {
			container.scrollTop -= previousSkeletonCompensationRef.current;
			previousSkeletonCompensationRef.current = 0;
		}
		rowIdsBeforePrependRef.current = null;
		pendingScrollDownRef.current = null;
	}, [isFetchingPreviousPage, scrollContainer, scrollContainerRef]);

	useLayoutEffect(() => {
		const previousRowIds = rowIdsBeforeAppendRef.current;
		const previousRowHeights = rowHeightsBeforeAppendRef.current;
		const scrollUp = pendingScrollUpRef.current;
		if (previousRowIds === null || previousRowHeights === null || scrollUp === null) {
			return;
		}

		const currentRowIds = new Set(rows.map(rowKey));
		// See the prepend effect above: skip until the row id set actually changes so
		// a spurious pre-fetch render doesn't consume the pending compensation.
		const previousRowIdSet = new Set(previousRowIds);
		const isUnchanged =
			currentRowIds.size === previousRowIdSet.size && [...currentRowIds].every((id) => previousRowIdSet.has(id));
		if (isUnchanged) {
			return;
		}

		if (isFetchingNextPage) {
			// See the prepend effect above: the tracked next-page fetch hasn't settled yet, so this
			// isn't its result — re-baseline against this new state instead of consuming it, so the
			// eventual real page is only compared against the latest known state.
			if (scrollContainer !== null) {
				rowIdsBeforeAppendRef.current = rows.map(rowKey);
				rowHeightsBeforeAppendRef.current = readRowHeights(scrollContainer);
			}
			return;
		}

		const firstSurvivingRowIndex = previousRowIds.findIndex((id) => currentRowIds.has(id));
		const evictedRowCount = firstSurvivingRowIndex === -1 ? previousRowIds.length : firstSurvivingRowIndex;

		// A genuine append keeps every surviving previously-known row in its original relative
		// order at the front of `rows` — some leading ones may be missing if they were evicted
		// (maxPages), but the ones that remain must still line up. If a next-page fetch is instead
		// cancelled by, say, a sort/filter change landing in the same commit, `isFetchingNextPage`
		// can already read `false` by the time `rows` updates, which would otherwise slip past the
		// check above and be misread as top-page eviction. Verify the surviving rows actually
		// continue in the expected order before compensating; if they don't, bail without consuming
		// the baseline — the give-up effect below runs in this same commit (its own
		// `isFetchingNextPage` dependency just changed too) and clears it, so a later, real append
		// starts clean. Also require at least one surviving row: `Array.every` on the empty array
		// vacuously returns `true`, so a completely disjoint replacement (e.g. a sort/filter change
		// whose unrelated result shares zero rows with the baseline) would otherwise pass this check
		// and be misread as every previous row having been evicted, scrolling up by their full
		// height for no reason. Mirrors `firstExistingRowIndex !== -1` in the prepend effect above.
		const survivingPreviousIds = previousRowIds.filter((id) => currentRowIds.has(id));
		const isGenuineAppend =
			firstSurvivingRowIndex !== -1 &&
			survivingPreviousIds.every((id, index) => {
				const row = rows[index];
				return row !== undefined && rowKey(row) === id;
			});
		// See the prepend effect above: row-order continuity alone still isn't proof this `rows`
		// change came from the armed fetch, since cached data for a swapped query key could
		// coincidentally share a run of ids with the stale baseline. Requiring that
		// `isFetchingNextPage` was actually observed `true` since this baseline armed rules that
		// out.
		if (!isGenuineAppend || !hasObservedNextPageFetchStartRef.current) {
			return;
		}

		// Removing rows from the top of the DOM shifts all surviving content upward without the
		// browser changing `scrollTop` on its own. Left uncompensated, the viewport would keep its
		// numeric `scrollTop` but land on rows further down the (now shifted) list than the ones
		// the user was actually looking at - a forward skip equal to the evicted height. Scrolling
		// up by that same height cancels the shift out, so the same rows stay in view regardless of
		// whether the user is pinned to the live bottom edge or has scrolled away from it.
		if (evictedRowCount > 0) {
			scrollUp(sumRowHeights(previousRowHeights, evictedRowCount));
		}

		rowIdsBeforeAppendRef.current = null;
		rowHeightsBeforeAppendRef.current = null;
		pendingScrollUpRef.current = null;
	}, [rows, rowKey, scrollContainer, isFetchingNextPage]);

	// Mirrors the prepend give-up effect above: if the next-page fetch ends without the append
	// effect ever consuming the baseline (empty/failed result leaves `rows` unchanged), disarm
	// it so a later, unrelated `rows` change can't be misread as top eviction from this fetch.
	// Declared after the append effect above, same ordering guarantee as the prepend pair.
	useLayoutEffect(() => {
		if (isFetchingNextPage) {
			return;
		}
		rowIdsBeforeAppendRef.current = null;
		rowHeightsBeforeAppendRef.current = null;
		pendingScrollUpRef.current = null;
	}, [isFetchingNextPage]);

	return {handleScrollStartReach, handleScrollEndReach};
}

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
		loadingPreviousPageLabel,
		loadingNextPageLabel,
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
	const loadingPreviousTestId = dataTestId !== undefined ? `${dataTestId}-loading-previous` : undefined;
	const loadingNextTestId = dataTestId !== undefined ? `${dataTestId}-loading-next` : undefined;

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

	const hasSelectionColumn = selection !== undefined;

	const dataColumns: DataTableColumn<DisplayRow<TRow>>[] = useMemo(
		() =>
			columns.map((column, columnIndex) => ({
				id: column.sortKey ?? `col-${column.key}`,
				header: column.label,
				enableSorting: column.sortKey !== undefined,
				accessorFn: column.sortKey !== undefined ? () => null : undefined,
				cell: (info) => {
					const displayRow = info.row.original;

					if (displayRow.kind === 'skeleton') {
						return (
							<SkeletonCell
								label={displayRow.label}
								isFirstCell={!hasSelectionColumn && columnIndex === 0}
								edge={displayRow.id}
								testId={displayRow.id === 'previous' ? loadingPreviousTestId : loadingNextTestId}
							/>
						);
					}

					return column.render(displayRow.row);
				},
			})),
		[columns, hasSelectionColumn, loadingPreviousTestId, loadingNextTestId],
	);

	const {
		selectAllLabel,
		selectRowLabel,
		checkIsAllSelected,
		checkIsIndeterminate,
		checkIsRowSelected,
		onSelectAll,
		onSelect,
	} = selection ?? {};
	const selectionColumn: DataTableColumn<DisplayRow<TRow>> | undefined = useMemo(() => {
		if (
			checkIsIndeterminate === undefined ||
			checkIsAllSelected === undefined ||
			onSelectAll === undefined ||
			selectAllLabel === undefined ||
			checkIsRowSelected === undefined ||
			onSelect === undefined ||
			selectRowLabel === undefined
		) {
			return undefined;
		}

		return {
			id: 'select',
			enableSorting: false,
			header: () => (
				<div data-interactive className="flex items-center">
					<Checkbox
						checked={checkIsIndeterminate() ? 'indeterminate' : checkIsAllSelected()}
						onCheckedChange={() => onSelectAll()}
						aria-label={selectAllLabel}
					/>
				</div>
			),
			cell: (info) => {
				const displayRow = info.row.original;

				if (displayRow.kind === 'skeleton') {
					return (
						<SkeletonCell
							label={displayRow.label}
							isFirstCell
							edge={displayRow.id}
							testId={displayRow.id === 'previous' ? loadingPreviousTestId : loadingNextTestId}
						/>
					);
				}

				const id = rowKey(displayRow.row);
				return (
					<div data-interactive className="flex items-center">
						<Checkbox
							checked={checkIsRowSelected(id)}
							onCheckedChange={() => onSelect(id)}
							aria-label={selectRowLabel(id)}
						/>
					</div>
				);
			},
		};
	}, [
		checkIsIndeterminate,
		checkIsAllSelected,
		onSelectAll,
		selectAllLabel,
		checkIsRowSelected,
		onSelect,
		selectRowLabel,
		rowKey,
		loadingPreviousTestId,
		loadingNextTestId,
	]);

	const tableColumns = selectionColumn !== undefined ? [selectionColumn, ...dataColumns] : dataColumns;

	const displayRows: DisplayRow<TRow>[] = useMemo(() => {
		const result: DisplayRow<TRow>[] = [];

		if (isFetchingPreviousPage) {
			result.push({kind: 'skeleton', id: 'previous', label: loadingPreviousPageLabel});
		}

		for (const row of rows) {
			result.push({kind: 'row', row});
		}

		if (isFetchingNextPage) {
			result.push({kind: 'skeleton', id: 'next', label: loadingNextPageLabel});
		}

		return result;
	}, [rows, isFetchingPreviousPage, isFetchingNextPage, loadingPreviousPageLabel, loadingNextPageLabel]);

	const getDisplayRowId = useCallback(
		(displayRow: DisplayRow<TRow>) =>
			displayRow.kind === 'skeleton' ? `__skeleton-${displayRow.id}` : rowKey(displayRow.row),
		[rowKey],
	);

	const [scrollContainer, setScrollContainerState] = useState<HTMLDivElement | null>(null);
	const scrollContainerRef = useRef<HTMLDivElement | null>(null);
	const setScrollContainer = useCallback((node: HTMLDivElement | null) => {
		scrollContainerRef.current = node;
		setScrollContainerState(node);
	}, []);

	const {handleScrollStartReach, handleScrollEndReach} = useEdgeScrollCompensation({
		scrollContainer,
		scrollContainerRef,
		rows,
		rowKey,
		hasPreviousPage,
		hasNextPage,
		isFetching,
		isFetchingPreviousPage,
		isFetchingNextPage,
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

	const isInitialLoad = isFetching && rows.length === 0;

	const dataTable = (
		<DataTable<DisplayRow<TRow>>
			columns={tableColumns}
			data={displayRows}
			getRowId={getDisplayRowId}
			aria-label={ariaLabel}
			size={DS_TABLE_SIZE[size]}
			emptyState={emptyState}
			loading={isInitialLoad}
			sorting={hasSortableColumns ? {manual: true, sortState, onSortingChange: handleSortingChange} : undefined}
		/>
	);

	return (
		<div className={cn('relative', hasScrollHandlers && 'h-full min-h-0 flex-1', className)}>
			{isFetching && !isInitialLoad && (
				<div
					aria-hidden
					className="pointer-events-none absolute inset-0 z-10 bg-neutral-background-subtle opacity-50"
					data-testid={dataTestId !== undefined ? `${dataTestId}-loading-overlay` : undefined}
				/>
			)}
			<div
				ref={hasScrollHandlers ? setScrollContainer : undefined}
				// Scroll anchoring (the browser's default scroll-position-preservation heuristic)
				// fights with our own measured compensation below: both react to the same DOM
				// mutation, so rows evicted from the top get their height subtracted twice,
				// overshooting the scroll position. Disable it so our compensation is authoritative.
				className={cn(hasScrollHandlers && 'h-full overflow-y-auto [overflow-anchor:none]')}
				data-testid={dataTestId}
			>
				{hasScrollHandlers ? (
					<InfiniteScroller
						onVerticalScrollStartReach={handleScrollStartReach}
						onVerticalScrollEndReach={handleScrollEndReach}
						scrollableContainerRef={scrollContainerRef}
					>
						<div>
							<div data-testid="sortable-table-top-sentinel" />
							{dataTable}
							<div data-testid="sortable-table-bottom-sentinel" />
						</div>
					</InfiniteScroller>
				) : (
					dataTable
				)}
			</div>
		</div>
	);
}

export {SortableTable};
