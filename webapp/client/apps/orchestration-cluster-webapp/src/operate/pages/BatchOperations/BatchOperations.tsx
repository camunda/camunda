/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Pagination} from '@carbon/react';
import {format, parseISO} from 'date-fns';
import {useSuspenseQuery} from '@tanstack/react-query';
import {useNavigate} from '@tanstack/react-router';
import {tracking} from '#/shared/tracking';
import {SortableTable} from '#/operate/shared/SortableTable';
import {BatchItemsCount} from '#/operate/shared/BatchItemsCount';
import {BatchStateIndicator} from '#/operate/shared/BatchStateIndicator';
import {batchOperationsOptions} from './batchOperations.queries';
import {PageContainer, PanelHeader, Title, TableContainer, VisuallyHiddenH1, OperationLink} from './styled';
import type {BatchOperation} from '@camunda/camunda-api-zod-schemas/8.10';

function formatOperationType(type: string): string {
	return type
		.split('_')
		.map((word) => word.charAt(0) + word.slice(1).toLowerCase())
		.join(' ');
}

function formatStartDate(startDate: string | null | undefined): string {
	return startDate ? format(parseISO(startDate), 'yyyy-MM-dd HH:mm:ss') : '--';
}

type Props = {
	page: number;
	pageSize: number;
	sort: string | undefined;
};

const COLUMNS = [
	{key: 'operationType', label: 'Operation', sortKey: 'operationType', defaultOrder: 'desc' as const},
	{key: 'state', label: 'Batch state', sortKey: 'state', defaultOrder: 'desc' as const},
	{key: 'items', label: 'Items'},
	{key: 'actor', label: 'Actor', sortKey: 'actorId', defaultOrder: 'desc' as const},
	{key: 'startDate', label: 'Start date', sortKey: 'startDate', defaultOrder: 'desc' as const},
];

const BatchOperations: React.FC<Props> = ({page, pageSize, sort}) => {
	const navigate = useNavigate();

	const {data, isFetching} = useSuspenseQuery(batchOperationsOptions({page, pageSize, sort}));

	const columns = COLUMNS.map((col) => ({
		...col,
		render: (row: BatchOperation) => {
			switch (col.key) {
				case 'operationType':
					return (
						<OperationLink
							href={`/operate/batch-operations/${row.batchOperationKey}`}
							onClick={() => {
								tracking.track({
									eventName: 'operate:batch-operation-details-opened',
									batchOperationType: row.batchOperationType,
									batchOperationState: row.state,
								});
							}}
						>
							{formatOperationType(row.batchOperationType)}
						</OperationLink>
					);
				case 'state':
					return <BatchStateIndicator state={row.state} />;
				case 'items':
					return (
						<BatchItemsCount
							totalCount={row.operationsTotalCount}
							completedCount={row.operationsCompletedCount}
							failedCount={row.operationsFailedCount}
						/>
					);
				case 'actor':
					return row.actorId ?? '--';
				case 'startDate':
					return formatStartDate(row.startDate);
				default:
					return null;
			}
		},
	}));

	const totalItems = data.page.totalItems;

	return (
		<PageContainer>
			<VisuallyHiddenH1>Batch Operations</VisuallyHiddenH1>
			<PanelHeader>
				<Title>Batch Operations</Title>
			</PanelHeader>
			<TableContainer>
				<SortableTable
					columns={columns}
					rows={data.items}
					rowKey={(row) => row.batchOperationKey}
					isFetching={isFetching}
					emptyState={<span>No batch operations found</span>}
					onSort={(sortBy, sortOrder) => {
						tracking.track({eventName: 'operate:batch-operations-sorted', sortBy, sortOrder});
					}}
					data-testid="batch-operations-table"
				/>
			</TableContainer>
			{totalItems > pageSize && (
				<Pagination
					totalItems={totalItems}
					pageSize={pageSize}
					pageSizes={[20, 50, 100]}
					page={page}
					onChange={({page: newPage, pageSize: newPageSize}) => {
						void navigate({
							to: '.',
							search: (prev) => ({...prev, page: newPage, pageSize: newPageSize}),
						});
					}}
				/>
			)}
		</PageContainer>
	);
};

export {BatchOperations};
