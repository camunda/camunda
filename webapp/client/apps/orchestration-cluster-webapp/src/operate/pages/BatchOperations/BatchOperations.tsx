/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Pagination} from '@carbon/react';
import {useQuery, keepPreviousData} from '@tanstack/react-query';
import {Link, useNavigate} from '@tanstack/react-router';
import {queries} from '#/shared/http/queries';
import {ForbiddenError} from '#/shared/errors';
import {SortableTable} from '#/operate/shared/SortableTable';
import {BatchItemsCount} from '#/operate/shared/BatchItemsCount';
import {BatchStateIndicator} from '#/operate/shared/BatchStateIndicator';
import {PageContainer, PanelHeader} from './styled';
import type {BatchOperation} from '@camunda/camunda-api-zod-schemas/8.10';

const MAX_OPERATIONS_PER_REQUEST = 20;
const DEFAULT_SORT = 'endDate+desc';

function formatOperationType(type: string): string {
	return type
		.split('_')
		.map((word) => word.charAt(0) + word.slice(1).toLowerCase())
		.join(' ');
}

type Props = {
	page: number;
	pageSize: number;
	sort: string | undefined;
};

const COLUMNS = [
	{key: 'operationType', label: 'Operation type', sortKey: 'operationType', defaultOrder: 'desc' as const},
	{key: 'state', label: 'State', sortKey: 'state', defaultOrder: 'desc' as const},
	{key: 'items', label: 'Items'},
	{key: 'actor', label: 'Actor', sortKey: 'actorId', defaultOrder: 'desc' as const},
	{key: 'startDate', label: 'Start date', sortKey: 'startDate', defaultOrder: 'desc' as const},
];

const BatchOperations: React.FC<Props> = ({page, pageSize, sort}) => {
	const navigate = useNavigate();
	const [sortField, sortOrder] = (sort ?? DEFAULT_SORT).split('+');

	const body = {
		sort: [{field: sortField as 'endDate', order: (sortOrder ?? 'desc') as 'asc' | 'desc'}],
		page: {from: (page - 1) * pageSize, limit: pageSize},
	};

	// useQuery with keepPreviousData preserves rows during page transitions rather than
	// suspending on each page change. Revisit once the Suspense pattern settles from #55411.
	const {data, isLoading, isFetching, error} = useQuery({
		...queries.queryBatchOperations(body),
		placeholderData: keepPreviousData,
	});

	// Let the /_auth/operate route errorComponent handle ForbiddenError → ForbiddenPage
	if (error instanceof ForbiddenError) {
		throw error;
	}

	const columns = COLUMNS.map((col) => ({
		...col,
		render: (row: BatchOperation) => {
			switch (col.key) {
				case 'operationType':
					return (
						<Link to="/operate/batch-operations/$batchOperationKey" params={{batchOperationKey: row.batchOperationKey}}>
							{formatOperationType(row.batchOperationType)}
						</Link>
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
					return row.actorId ?? '';
				case 'startDate':
					return row.startDate;
				default:
					return null;
			}
		},
	}));

	const totalItems = data?.page.totalItems ?? 0;

	return (
		<PageContainer>
			<PanelHeader>
				<h1>Batch operations</h1>
			</PanelHeader>
			<SortableTable
				columns={columns}
				rows={data?.items ?? []}
				rowKey={(row) => row.batchOperationKey}
				isLoading={isLoading}
				isFetching={isFetching && !isLoading}
				emptyState={<span>No batch operations found</span>}
				data-testid="batch-operations-table"
			/>
			{!isLoading && (
				<Pagination
					totalItems={totalItems}
					pageSize={pageSize}
					pageSizes={[MAX_OPERATIONS_PER_REQUEST]}
					page={page}
					onChange={({page: newPage, pageSize: newPageSize}) => {
						void navigate({
							search: (prev) => ({...prev, page: newPage, pageSize: newPageSize}),
						});
					}}
				/>
			)}
		</PageContainer>
	);
};

export {BatchOperations};
