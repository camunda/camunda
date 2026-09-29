/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useCallback, useEffect, useEffectEvent, useLayoutEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Skeleton} from '@camunda/design-system';
import SvgErrorRobot from '#/shared/svg/ErrorRobot';
import {EmptyState} from '#/operate/components/EmptyState/shadcn.components/EmptyState';
import type {ExpandableListRow} from './ExpandableList.types';
import {
	DEFAULT_EXPANDABLE_LIST_VARIANT,
	EXPANDABLE_LIST_VARIANTS,
	type ExpandableListVariant,
} from './ExpandableList.variants';

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

	const handleTopSentinelIntersect = useEffectEvent(() => {
		if (isFetchingPreviousPage || isFetchingNextPage) {
			return false;
		}

		previousRowIdsBeforePrependRef.current = new Set(rows.map((row) => row.id));
		onLoadPreviousPage();
		return true;
	});

	const handleBottomSentinelIntersect = useEffectEvent(() => {
		if (isFetchingNextPage || isFetchingPreviousPage) {
			return false;
		}

		onLoadNextPage();
		return true;
	});

	useEffect(() => {
		if (!topSentinel && !bottomSentinel) {
			return;
		}

		const observer = new IntersectionObserver(
			(entries) => {
				let hasTriggeredFetch = false;

				for (const entry of entries) {
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
		// Scoped to the table body (excludes the header row, which shares the
		// same `data-slot`). Includes both real rows and any expansion/detail
		// rows rendered alongside them: the DS `expansion` prop marks those
		// with `data-table-expansion-row`, and the `nativeExpansion` variant
		// marks its own detail rows with `data-row-kind="detail"` since it
		// renders them through the same row primitive as real data rows.
		// Walking in DOM order and stopping once the anchor row itself is
		// reached (rather than a fixed slice) means a prepended row that is
		// still expanded from before it was evicted contributes its detail
		// row's height too, keeping the anchor row's own position accurate.
		const tbodyRowElements = container.querySelectorAll<HTMLElement>(
			'[data-slot="table-body"] [data-slot="table-row"], [data-slot="table-body"] [data-slot="data-table-expansion-row"]',
		);
		let prependedHeight = 0;
		let realRowsSeen = 0;

		for (const rowElement of tbodyRowElements) {
			const isDetailRow =
				rowElement.getAttribute('data-slot') === 'data-table-expansion-row' ||
				rowElement.querySelector('[data-row-kind="detail"]') !== null;

			if (!isDetailRow) {
				if (realRowsSeen >= prependedRowCount) {
					break;
				}

				realRowsSeen += 1;
			}

			prependedHeight += rowElement.getBoundingClientRect().height;
		}

		container.scrollTop += prependedHeight;
		previousRowIdsBeforePrependRef.current = null;
	}, [rows, isFetchingPreviousPage, scrollContainer]);

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
		<div className="flex flex-1 flex-col overflow-y-auto" ref={setScrollContainer} data-testid={listTestId}>
			{hasPreviousPage && <div ref={setTopSentinel} data-testid={`${listTestId}-top-sentinel`} />}
			{isFetchingPreviousPage && (
				<div
					className="flex justify-center py-2"
					data-testid={`${listTestId}-loading-previous`}
					role="status"
					aria-live="polite"
				>
					<span className="sr-only">{t('operate.dashboard.loadingPreviousRows', {header})}</span>
					<Skeleton className="h-4 w-24" aria-hidden />
				</div>
			)}
			<div data-testid={dataTestId}>
				<Variant header={header} rows={rows} renderExpansion={renderExpansion} isPending={isPending} />
			</div>
			{isFetchingNextPage && (
				<div
					className="flex justify-center py-2"
					data-testid={`${listTestId}-loading-next`}
					role="status"
					aria-live="polite"
				>
					<span className="sr-only">{t('operate.dashboard.loadingMoreRows', {header})}</span>
					<Skeleton className="h-4 w-24" aria-hidden />
				</div>
			)}
			{hasNextPage && <div ref={setBottomSentinel} data-testid={`${listTestId}-bottom-sentinel`} />}
		</div>
	);
};

export {ExpandableList};
// TODO(#63423, #63424): delete once InstancesByProcess/IncidentsByError land in PR8
export type {ExpandableListRow};
