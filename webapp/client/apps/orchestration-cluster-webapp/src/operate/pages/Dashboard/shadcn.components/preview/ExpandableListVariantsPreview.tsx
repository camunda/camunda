/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Design-review preview for the Dashboard list-tile row shape. Lives only on the
// `operate-ds-expandable-variants` branch and is closed, not merged.

import {useState} from 'react';
import {Link, getRouteApi} from '@tanstack/react-router';
import {Heading, Text, TooltipProvider} from '@camunda/design-system';
import {InstancesBar} from '#/operate/components/InstancesBar/shadcn.components/InstancesBar';
import {ExpandableList} from '#/operate/pages/Dashboard/shadcn.components/ExpandableList';
import type {ExpandableListRow} from '#/operate/pages/Dashboard/shadcn.components/ExpandableList.types';
import {
	DEFAULT_EXPANDABLE_LIST_VARIANT,
	EXPANDABLE_LIST_VARIANT_IDS,
	type ExpandableListVariant,
} from '#/operate/pages/Dashboard/shadcn.components/ExpandableList.variants';
import {VARIANT_SELECTIONS} from './variantSelection';

const ROUTE_PATH = '/_shadcn/_auth/operate-preview/expandable-list-variants';

const PAGE_SIZE = 15;
const TOTAL_ROWS = 45;

const PROCESS_NAMES = [
	'Order fulfilment',
	'Invoice approval',
	'Customer onboarding',
	'Payment reconciliation',
	'Shipment tracking',
	'Refund handling',
	'Credit check',
	'Contract renewal',
	'Fraud review',
];

/**
 * Rows deliberately span the cases the candidates disagree most about:
 * incident-only, active-only, zero-count, and a name long enough to truncate.
 */
const buildRows = (): ExpandableListRow[] =>
	Array.from({length: TOTAL_ROWS}, (_, index) => {
		const name =
			index === 4
				? 'Cross-border settlement reconciliation with counterparty exception handling'
				: `${PROCESS_NAMES[index % PROCESS_NAMES.length]} ${Math.floor(index / PROCESS_NAMES.length) + 1}`;
		const activeCount = index % 7 === 0 ? 0 : index * 13;
		const incidentsCount = index % 3 === 0 ? index % 11 : 0;

		return {
			id: `process-${index}`,
			name,
			activeCount,
			incidentsCount,
			content: (
				<InstancesBar
					label={{type: 'process', size: 'medium', text: name}}
					activeInstancesCount={activeCount}
					incidentsCount={incidentsCount}
					size="medium"
				/>
			),
		};
	});

const ALL_ROWS = buildRows();

/** Only every third row expands, so the toggle-on-non-expandable-rows gap stays visible. */
const EXPANDED_CONTENTS: Record<string, React.ReactElement<{tabIndex: number}>> = Object.fromEntries(
	ALL_ROWS.filter((_, index) => index % 3 === 0).map((row) => [
		row.id,
		<div key={row.id} className="flex flex-col gap-1 py-2 pl-6">
			<Text as="div" variant="label-md">{`${row.name} — Version 3`}</Text>
			<Text as="div" variant="label-md">{`${row.name} — Version 2`}</Text>
			<Text as="div" variant="label-md">{`${row.name} — Version 1`}</Text>
		</div>,
	]),
);

const VariantPanel: React.FC<{variant: ExpandableListVariant}> = ({variant}) => {
	const [endIndex, setEndIndex] = useState(PAGE_SIZE);
	const [isFetchingNextPage, setIsFetchingNextPage] = useState(false);

	const onLoadNextPage = () => {
		if (isFetchingNextPage || endIndex >= TOTAL_ROWS) {
			return;
		}
		setIsFetchingNextPage(true);
		window.setTimeout(() => {
			setEndIndex((current) => Math.min(current + PAGE_SIZE, TOTAL_ROWS));
			setIsFetchingNextPage(false);
		}, 500);
	};

	return (
		<section className="flex min-w-0 flex-1 basis-96 flex-col gap-2 rounded-md border p-3">
			<Heading as="h2" variant="heading-xs">
				{variant}
				{variant === DEFAULT_EXPANDABLE_LIST_VARIANT ? ' (current default)' : ''}
			</Heading>
			<ExpandableList
				variant={variant}
				isPending={false}
				isError={false}
				listTestId={`variant-${variant}-list`}
				dataTestId={`variant-${variant}-table`}
				header="Process Instances by Name"
				rows={ALL_ROWS.slice(0, endIndex)}
				expandedContents={EXPANDED_CONTENTS}
				hasNextPage={endIndex < TOTAL_ROWS}
				hasPreviousPage={false}
				isFetchingNextPage={isFetchingNextPage}
				isFetchingPreviousPage={false}
				onLoadNextPage={onLoadNextPage}
				onLoadPreviousPage={() => {}}
			/>
		</section>
	);
};

const ExpandableListVariantsPreview: React.FC = () => {
	const {variant} = getRouteApi(ROUTE_PATH).useSearch();
	const shown = variant === 'all' ? EXPANDABLE_LIST_VARIANT_IDS : [variant];

	return (
		<TooltipProvider>
			<main className="flex h-full flex-col gap-4 overflow-y-auto p-4">
				<Heading as="h1" variant="heading-md">
					Dashboard list row shape — design review
				</Heading>
				<Text as="p" variant="body-md">
					The same rows rendered by every candidate row shape. All share one ExpandableList shell,
					so pagination, expansion and empty/error states are identical — only the row rendering
					differs.
				</Text>
				<nav className="flex flex-wrap gap-2">
					{VARIANT_SELECTIONS.map((selection) => (
						<Link
							key={selection}
							to="/operate-preview/expandable-list-variants"
							search={{variant: selection}}
							className="rounded-md border px-3 py-1 text-sm aria-[current=page]:bg-accent"
							activeOptions={{includeSearch: true}}
						>
							{selection}
						</Link>
					))}
				</nav>
				<div className={shown.length > 1 ? 'flex flex-wrap items-start gap-4' : 'max-w-2xl'}>
					{shown.map((id) => (
						<VariantPanel key={id} variant={id} />
					))}
				</div>
			</main>
		</TooltipProvider>
	);
};

export {ExpandableListVariantsPreview};
