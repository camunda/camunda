/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useEffect, useRef} from 'react';
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
	/**
	 * Row rendering to use. Consumers leave this unset so the adopted default
	 * governs everywhere; only the design-review preview sets it explicitly.
	 */
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
	const topSentinelRef = useRef<HTMLDivElement | null>(null);
	const bottomSentinelRef = useRef<HTMLDivElement | null>(null);

	// Read via a ref inside the observer callback, rather than as an effect dependency —
	// `observe()` fires its callback synchronously when the target is already intersecting,
	// so depending on the fetching flags here would recreate+re-observe the sentinels on every
	// fetch start/end and immediately re-trigger the same page load in a loop.
	const latestRef = useRef({isFetchingNextPage, isFetchingPreviousPage, onLoadNextPage, onLoadPreviousPage});

	// Declared before the observer effect so it has already refreshed the ref by the time
	// `observe()` below fires its callback synchronously for an already-intersecting target.
	useEffect(() => {
		latestRef.current = {isFetchingNextPage, isFetchingPreviousPage, onLoadNextPage, onLoadPreviousPage};
	});

	// DS DataTable owns its own scroll region (its `Table` wrapper), so wrapping it in
	// another scrollable container to drive pagination via onScroll produces nested/double
	// scrollbars (confirmed against DS docs). Sentinels below/above the table are observed
	// against the page's own natural scroll (`root: null`) instead.
	useEffect(() => {
		const topEl = topSentinelRef.current;
		const bottomEl = bottomSentinelRef.current;

		if (!topEl && !bottomEl) {
			return;
		}

		const observer = new IntersectionObserver((entries) => {
			for (const entry of entries) {
				if (!entry.isIntersecting) {
					continue;
				}

				if (entry.target === topEl && !latestRef.current.isFetchingPreviousPage) {
					latestRef.current.onLoadPreviousPage();
				} else if (entry.target === bottomEl && !latestRef.current.isFetchingNextPage) {
					latestRef.current.onLoadNextPage();
				}
			}
		});

		if (topEl) {
			observer.observe(topEl);
		}

		if (bottomEl) {
			observer.observe(bottomEl);
		}

		return () => observer.disconnect();
	}, [hasNextPage, hasPreviousPage]);

	if (isPending) {
		return (
			<div className="flex flex-col gap-2 p-4" data-testid={`${listTestId}-skeleton`}>
				{Array.from({length: 20}).map((_, index) => (
					<Skeleton key={index} className="h-8 w-full" />
				))}
			</div>
		);
	}

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

	// DS DataTable's `expansion` prop injects the toggle for every row unconditionally (no
	// per-row suppression — confirmed against the installed package's data-table.js). Unlike
	// Carbon, rows with nothing to expand still show a toggle here; accepted per design review
	// to match the DS DataTable expansion pattern. See docs/migration/operate-dashboard-ds-gaps.md.
	// The pagination skeletons are kept as siblings (not table rows) precisely to avoid picking
	// up that same always-on toggle.
	const renderExpansion = (row: ExpandableListRow) => {
		const content = expandedContents[row.id];
		return content ? React.cloneElement(content, {tabIndex: 0}) : null;
	};

	const Variant = EXPANDABLE_LIST_VARIANTS[variant];

	return (
		<div className="flex flex-1 flex-col" data-testid={listTestId}>
			{hasPreviousPage && <div ref={topSentinelRef} data-testid={`${listTestId}-top-sentinel`} />}
			{isFetchingPreviousPage && (
				<div className="flex justify-center py-2" data-testid={`${listTestId}-loading-previous`}>
					<Skeleton className="h-4 w-24" />
				</div>
			)}
			<div data-testid={dataTestId}>
				<Variant header={header} rows={rows} renderExpansion={renderExpansion} />
			</div>
			{isFetchingNextPage && (
				<div className="flex justify-center py-2" data-testid={`${listTestId}-loading-next`}>
					<Skeleton className="h-4 w-24" />
				</div>
			)}
			{hasNextPage && <div ref={bottomSentinelRef} data-testid={`${listTestId}-bottom-sentinel`} />}
		</div>
	);
};

export {ExpandableList};
export type {ExpandableListRow};
