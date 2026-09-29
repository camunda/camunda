/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useCallback, useEffect, useEffectEvent, useLayoutEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import SvgErrorRobot from '#/shared/svg/ErrorRobot';
import {EmptyState} from '#/operate/components/EmptyState/shadcn.components/EmptyState';
import type {ExpandableListRow} from './ExpandableList.types';
import {
	DEFAULT_EXPANDABLE_LIST_VARIANT,
	EXPANDABLE_LIST_VARIANTS,
	type ExpandableListVariant,
} from './ExpandableList.variants';

type RowHeightEntry = {isDetailRow: boolean; height: number};

const readRowHeightEntries = (container: HTMLElement): RowHeightEntry[] => {
	const tbodyRowElements = container.querySelectorAll<HTMLElement>(
		'[data-slot="table-body"] [data-slot="table-row"], [data-slot="table-body"] [data-slot="data-table-expansion-row"]',
	);

	return Array.from(tbodyRowElements).map((rowElement) => ({
		isDetailRow:
			rowElement.getAttribute('data-slot') === 'data-table-expansion-row' ||
			rowElement.querySelector('[data-row-kind="detail"]') !== null,
		height: rowElement.getBoundingClientRect().height,
	}));
};

const sumRealRowHeights = (entries: RowHeightEntry[], targetRealRowCount: number): number => {
	let total = 0;
	let realRowsSeen = 0;

	for (const entry of entries) {
		if (!entry.isDetailRow) {
			if (realRowsSeen >= targetRealRowCount) {
				break;
			}

			realRowsSeen += 1;
		}

		total += entry.height;
	}

	return total;
};

type Props = {
	isPending: boolean;
	isError: boolean;
	emptyState?: React.ReactNode;
	listTestId: string;
	dataTestId: string;
	header: string;
	rows: ExpandableListRow[];
	expandedContents: Record<string, React.ReactElement<{tabIndex: number}>>;
	variant?: ExpandableListVariant;
	hasNextPage: boolean;
	hasPreviousPage: boolean;
	isFetchingNextPage: boolean;
	isFetchingPreviousPage: boolean;
	isFetchNextPageError?: boolean;
	isFetchPreviousPageError?: boolean;
	onLoadNextPage: () => void;
	onLoadPreviousPage: () => void;
};

const ExpandableList: React.FC<Props> = ({
	isPending,
	isError,
	emptyState,
	listTestId,
	dataTestId,
	header,
	rows,
	expandedContents,
	hasNextPage,
	hasPreviousPage,
	isFetchingNextPage,
	isFetchingPreviousPage,
	isFetchNextPageError = false,
	isFetchPreviousPageError = false,
	onLoadNextPage,
	onLoadPreviousPage,
	variant = DEFAULT_EXPANDABLE_LIST_VARIANT,
}) => {
	const {t} = useTranslation();

	const [scrollContainer, setScrollContainerState] = useState<HTMLDivElement | null>(null);
	const [topSentinel, setTopSentinel] = useState<HTMLDivElement | null>(null);
	const [bottomSentinel, setBottomSentinel] = useState<HTMLDivElement | null>(null);

	const scrollContainerElementRef = useRef<HTMLDivElement | null>(null);
	const setScrollContainer = useCallback((node: HTMLDivElement | null) => {
		scrollContainerElementRef.current = node;
		setScrollContainerState(node);
	}, []);

	const previousRowIdsBeforePrependRef = useRef<Set<string> | null>(null);
	const previousSkeletonCompensationRef = useRef(0);
	const loadingPreviousTestId = `${listTestId}-loading-previous`;

	const rowIdsBeforeAppendRef = useRef<string[] | null>(null);
	const rowHeightEntriesBeforeAppendRef = useRef<RowHeightEntry[] | null>(null);
	const topSentinelIntersectingRef = useRef(false);
	const bottomSentinelIntersectingRef = useRef(false);

	const handleTopSentinelIntersect = useEffectEvent(() => {
		if (!hasPreviousPage || isFetchingPreviousPage || isFetchingNextPage) {
			return false;
		}

		previousRowIdsBeforePrependRef.current = new Set(rows.map((row) => row.id));
		onLoadPreviousPage();
		return true;
	});

	const handleBottomSentinelIntersect = useEffectEvent(() => {
		if (!hasNextPage || isFetchingNextPage || isFetchingPreviousPage) {
			return false;
		}

		const container = scrollContainerElementRef.current;

		if (container !== null) {
			rowHeightEntriesBeforeAppendRef.current = readRowHeightEntries(container);
		}

		rowIdsBeforeAppendRef.current = rows.map((row) => row.id);
		onLoadNextPage();
		return true;
	});

	const tryLoadIntersectingSentinel = useEffectEvent(() => {
		if (isFetchingNextPage || isFetchingPreviousPage) {
			return;
		}

		if (topSentinelIntersectingRef.current && !isFetchPreviousPageError && handleTopSentinelIntersect()) {
			return;
		}

		if (bottomSentinelIntersectingRef.current && !isFetchNextPageError) {
			handleBottomSentinelIntersect();
		}
	});

	useEffect(() => {
		if (topSentinel === null) {
			topSentinelIntersectingRef.current = false;
		}

		if (bottomSentinel === null) {
			bottomSentinelIntersectingRef.current = false;
		}

		tryLoadIntersectingSentinel();
	}, [
		topSentinel,
		bottomSentinel,
		hasPreviousPage,
		hasNextPage,
		isFetchingPreviousPage,
		isFetchingNextPage,
		isFetchPreviousPageError,
		isFetchNextPageError,
	]);

	useEffect(() => {
		if (!topSentinel && !bottomSentinel) {
			return;
		}

		const observer = new IntersectionObserver(
			(entries) => {
				let hasTriggeredFetch = false;

				for (const entry of entries) {
					if (entry.target === topSentinel) {
						topSentinelIntersectingRef.current = entry.isIntersecting;
					} else if (entry.target === bottomSentinel) {
						bottomSentinelIntersectingRef.current = entry.isIntersecting;
					}

					if (!entry.isIntersecting || hasTriggeredFetch) {
						continue;
					}

					if (entry.target === topSentinel) {
						hasTriggeredFetch = handleTopSentinelIntersect();
					} else if (entry.target === bottomSentinel) {
						hasTriggeredFetch = handleBottomSentinelIntersect();
					}
				}
			},
			{root: scrollContainer},
		);

		if (topSentinel) {
			observer.observe(topSentinel);
		}

		if (bottomSentinel) {
			observer.observe(bottomSentinel);
		}

		return () => observer.disconnect();
	}, [scrollContainer, topSentinel, bottomSentinel]);

	useLayoutEffect(() => {
		const container = scrollContainerElementRef.current;

		if (container === null || !isFetchingPreviousPage || previousSkeletonCompensationRef.current !== 0) {
			return;
		}

		const skeletonRow = container.querySelector(`[data-testid="${loadingPreviousTestId}"]`);
		const skeletonHeight = skeletonRow?.closest('[data-slot="table-row"]')?.getBoundingClientRect().height ?? 0;

		if (skeletonHeight === 0) {
			return;
		}

		container.scrollTop += skeletonHeight;
		previousSkeletonCompensationRef.current = skeletonHeight;
	}, [isFetchingPreviousPage, loadingPreviousTestId]);

	useLayoutEffect(() => {
		const container = scrollContainerElementRef.current;
		const previousRowIds = previousRowIdsBeforePrependRef.current;

		if (container === null || previousRowIds === null || isFetchingPreviousPage) {
			return;
		}

		// The backing infinite queries cap their page window (react-query's
		// `maxPages`), so a previous-page fetch can evict a page from the
		// bottom while prepending one at the top: total scrollHeight then
		// stays flat even though content shifted. Measure only the DOM height
		// that was actually inserted above the first still-existing ("anchor")
		// row instead of the total height delta, so the viewport stays
		// anchored on the previously-visible rows regardless of what was
		// evicted at the other end.
		const firstExistingRowIndex = rows.findIndex((row) => previousRowIds.has(row.id));
		const prependedRowCount = firstExistingRowIndex === -1 ? rows.length : firstExistingRowIndex;
		const prependedHeight = sumRealRowHeights(readRowHeightEntries(container), prependedRowCount);

		container.scrollTop += prependedHeight - previousSkeletonCompensationRef.current;
		previousSkeletonCompensationRef.current = 0;
		previousRowIdsBeforePrependRef.current = null;
	}, [rows, isFetchingPreviousPage, scrollContainer]);

	useLayoutEffect(() => {
		const container = scrollContainerElementRef.current;
		const previousRowIds = rowIdsBeforeAppendRef.current;
		const previousRowHeightEntries = rowHeightEntriesBeforeAppendRef.current;

		if (container === null || previousRowIds === null || previousRowHeightEntries === null || isFetchingNextPage) {
			return;
		}

		const newRowIds = new Set(rows.map((row) => row.id));
		const firstSurvivingRowIndex = previousRowIds.findIndex((id) => newRowIds.has(id));
		const evictedRowCount = firstSurvivingRowIndex === -1 ? previousRowIds.length : firstSurvivingRowIndex;

		if (evictedRowCount > 0) {
			container.scrollTop -= sumRealRowHeights(previousRowHeightEntries, evictedRowCount);
		}

		rowIdsBeforeAppendRef.current = null;
		rowHeightEntriesBeforeAppendRef.current = null;
	}, [rows, isFetchingNextPage, scrollContainer]);

	if (!isPending) {
		if (isError) {
			return (
				<EmptyState
					icon={<SvgErrorRobot aria-hidden />}
					heading={t('operate.dashboard.fetchErrorHeading')}
					description={t('operate.dashboard.fetchErrorDescription')}
				/>
			);
		}

		if (emptyState !== undefined) {
			return <>{emptyState}</>;
		}
	}

	const renderExpansion = (row: ExpandableListRow) => {
		const content = expandedContents[row.id];
		return content ? React.cloneElement(content, {tabIndex: 0}) : null;
	};

	const Variant = EXPANDABLE_LIST_VARIANTS[variant];

	return (
		<div
			className="flex flex-1 flex-col overflow-x-hidden overflow-y-auto rounded-xl border bg-neutral-background-subtle shadow-sm [overflow-anchor:none]"
			ref={setScrollContainer}
			data-testid={listTestId}
		>
			{hasPreviousPage && <div ref={setTopSentinel} data-testid={`${listTestId}-top-sentinel`} />}
			<div data-testid={dataTestId}>
				<Variant
					header={header}
					rows={rows}
					renderExpansion={renderExpansion}
					isPending={isPending}
					isFetchingNextPage={isFetchingNextPage}
					isFetchingPreviousPage={isFetchingPreviousPage}
					loadingNextPage={{
						testId: `${listTestId}-loading-next`,
						label: t('operate.dashboard.loadingMoreRows', {header}),
					}}
					loadingPreviousPage={{
						testId: loadingPreviousTestId,
						label: t('operate.dashboard.loadingPreviousRows', {header}),
					}}
				/>
			</div>
			{hasNextPage && <div ref={setBottomSentinel} data-testid={`${listTestId}-bottom-sentinel`} />}
		</div>
	);
};

export {ExpandableList};
// TODO(#63423, #63424): delete once InstancesByProcess/IncidentsByError land in PR8
export type {ExpandableListRow};
