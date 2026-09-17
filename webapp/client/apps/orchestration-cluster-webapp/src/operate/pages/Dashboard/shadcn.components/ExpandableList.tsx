/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {DataTable, Skeleton, type DataTableColumn} from '@camunda/design-system';
import {ChevronDown, ChevronRight} from '@camunda/design-system/icons';
import SvgErrorRobot from '#/shared/svg/ErrorRobot';
import {EmptyState} from '#/operate/components/EmptyState/shadcn.components/EmptyState';

type Row = {id: string; content: React.ReactNode};

type ExpandableRowProps = {
	row: Row;
	expandedContent: React.ReactElement<{tabIndex: number}> | undefined;
};

// DS DataTable's own `expansion` prop injects a toggle for every row unconditionally
// (`getRowCanExpand` has no public per-row override — confirmed against the installed
// package's data-table.js). Carbon hid the toggle entirely for rows with nothing to
// expand, so the expand/collapse control is composed here instead, inside the single
// content column, rather than through that prop.
const ExpandableRow: React.FC<ExpandableRowProps> = ({row, expandedContent}) => {
	const [isExpanded, setIsExpanded] = useState(false);
	const canExpand = expandedContent !== undefined;

	return (
		<div>
			<div className="flex items-center gap-1">
				{canExpand ? (
					<button
						type="button"
						aria-expanded={isExpanded}
						aria-label={isExpanded ? 'Collapse row' : 'Expand row'}
						onClick={() => setIsExpanded((expanded) => !expanded)}
						className="flex h-6 w-6 shrink-0 items-center justify-center rounded hover:bg-neutral-background-subtle focus-visible:bg-neutral-background-subtle focus-visible:outline-none"
					>
						{isExpanded ? (
							<ChevronDown className="size-4" aria-hidden="true" />
						) : (
							<ChevronRight className="size-4" aria-hidden="true" />
						)}
					</button>
				) : (
					<div className="h-6 w-6 shrink-0" />
				)}
				<div className="min-w-0 flex-1">{row.content}</div>
			</div>
			{canExpand && isExpanded && (
				<div className="bg-neutral-background-medium px-4 py-4">
					{React.cloneElement(expandedContent, {tabIndex: 0})}
				</div>
			)}
		</div>
	);
};

type Props = {
	isPending: boolean;
	isError: boolean;
	emptyState?: React.ReactNode;
	listTestId: string;
	dataTestId: string;
	header: string;
	rows: Row[];
	expandedContents: Record<string, React.ReactElement<{tabIndex: number}>>;
	isFetchingNextPage: boolean;
	isFetchingPreviousPage: boolean;
	onScroll: (event: React.UIEvent<HTMLDivElement>) => void;
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
	isFetchingNextPage,
	isFetchingPreviousPage,
	onScroll,
}) => {
	const {t} = useTranslation();

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

	const columns: DataTableColumn<Row>[] = [
		{
			id: 'content',
			header: () => <span className="sr-only">{header}</span>,
			cell: ({row}) => <ExpandableRow row={row.original} expandedContent={expandedContents[row.original.id]} />,
		},
	];

	return (
		<div className="flex flex-1 flex-col overflow-y-auto" onScroll={onScroll} data-testid={listTestId}>
			{isFetchingPreviousPage && (
				<div className="flex justify-center py-2" data-testid={`${listTestId}-loading-previous`}>
					<Skeleton className="h-4 w-24" />
				</div>
			)}
			<div data-testid={dataTestId}>
				<DataTable<Row> size="sm" columns={columns} data={rows} aria-label={header} getRowId={(row) => row.id} />
			</div>
			{isFetchingNextPage && (
				<div className="flex justify-center py-2" data-testid={`${listTestId}-loading-next`}>
					<Skeleton className="h-4 w-24" />
				</div>
			)}
		</div>
	);
};

export {ExpandableList};
export type {Row};
