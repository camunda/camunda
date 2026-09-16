/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {useTranslation} from 'react-i18next';
import {DataTable, Skeleton, type DataTableColumn} from '@camunda/design-system';
import SvgErrorRobot from '#/shared/svg/ErrorRobot';
import {EmptyState} from '#/operate/components/EmptyState/shadcn.components/EmptyState';

type Row = {id: string; content: React.ReactNode};

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
			cell: ({row}) => row.original.content,
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
				<DataTable<Row>
					size="sm"
					columns={columns}
					data={rows}
					aria-label={header}
					getRowId={(row) => row.id}
					expansion={(row) => {
						const content = expandedContents[row.id];
						return content ? React.cloneElement(content, {tabIndex: 0}) : null;
					}}
				/>
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
