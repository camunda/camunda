/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo} from 'react';
import {Button, DataTableSkeleton, InlineNotification, Pagination} from '@carbon/react';
import {keepPreviousData, useQuery} from '@tanstack/react-query';
import {createLink, useNavigate} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {SortableTable} from '#/operate/shared/SortableTable';
import {BatchItemsCount} from '#/operate/shared/BatchItemsCount';
import {BatchStateIndicator} from '#/operate/shared/BatchStateIndicator';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/EmptyMessage';
import {ForbiddenError} from '#/shared/errors';
import {ForbiddenPage} from '#/shared/pages/ForbiddenPage';
import {GenericErrorPage} from '#/shared/pages/GenericErrorPage';
import {batchOperationsOptions} from './batchOperations.queries';
import {formatOperationType, formatStartDate} from './utils';
import {PageContainer, PanelHeader, Title, TableContainer, VisuallyHiddenH1, OperationLink} from './styled';
import type {BatchOperation} from '@camunda/camunda-api-zod-schemas/8.11';

type Props = {
	page: number;
	pageSize: number;
	sort: string;
};

const BatchOperationLink = createLink(OperationLink);

const BatchOperations: React.FC<Props> = ({page, pageSize, sort}) => {
	const navigate = useNavigate();
	const {t, i18n} = useTranslation();
	const query = useQuery({...batchOperationsOptions({page, pageSize, sort}), placeholderData: keepPreviousData});

	useEffect(() => {
		const updateTitle = () => {
			document.title = i18n.t('operate.batchOperations.pageTitle');
		};
		updateTitle();
		i18n.on('languageChanged', updateTitle);
		return () => {
			i18n.off('languageChanged', updateTitle);
		};
	}, [i18n]);

	const columns = useMemo(
		() => [
			{
				key: 'operationType',
				label: t('operate.batchOperations.operation'),
				sortKey: 'operationType',
				defaultOrder: 'desc' as const,
				render: (row: BatchOperation) => (
					<BatchOperationLink
						to="/operate/batch-operations/$batchOperationKey"
						params={{batchOperationKey: row.batchOperationKey}}
					>
						{formatOperationType(row.batchOperationType)}
					</BatchOperationLink>
				),
			},
			{
				key: 'state',
				label: t('operate.batchOperations.state'),
				sortKey: 'state',
				defaultOrder: 'desc' as const,
				render: (row: BatchOperation) => <BatchStateIndicator state={row.state} />,
			},
			{
				key: 'items',
				label: t('operate.batchOperations.items'),
				render: (row: BatchOperation) => (
					<BatchItemsCount
						totalCount={row.operationsTotalCount}
						completedCount={row.operationsCompletedCount}
						failedCount={row.operationsFailedCount}
					/>
				),
			},
			{
				key: 'actor',
				label: t('operate.batchOperations.actor'),
				sortKey: 'actorId',
				defaultOrder: 'desc' as const,
				render: (row: BatchOperation) => row.actorId ?? '--',
			},
			{
				key: 'startDate',
				label: t('operate.batchOperations.startDate'),
				sortKey: 'startDate',
				defaultOrder: 'desc' as const,
				render: (row: BatchOperation) => formatStartDate(row.startDate),
			},
		],
		[t],
	);

	if (query.error instanceof ForbiddenError) {
		return <ForbiddenPage />;
	}

	if (query.isPending) {
		return <DataTableSkeleton columnCount={5} rowCount={5} showHeader={false} showToolbar={false} />;
	}

	if (query.isError && query.data === undefined) {
		return <GenericErrorPage reset={() => void query.refetch()} />;
	}

	const data = query.data;
	const totalItems = data.page.totalItems;

	return (
		<PageContainer>
			<VisuallyHiddenH1>{t('operate.batchOperations.title')}</VisuallyHiddenH1>
			<PanelHeader>
				<Title>{t('operate.batchOperations.title')}</Title>
			</PanelHeader>
			{query.isError && (
				<div>
					<InlineNotification
						kind="error"
						title={t('operate.batchOperations.refreshError')}
						hideCloseButton
						lowContrast
						role="alert"
					/>
					<Button kind="ghost" onClick={() => void query.refetch()}>
						{t('errorGenericErrorPageButtonLabel')}
					</Button>
				</div>
			)}
			<TableContainer $isEmpty={data.items.length === 0 && !query.isFetching && !query.isError}>
				<SortableTable
					columns={columns}
					rows={data.items}
					rowKey={(row) => row.batchOperationKey}
					isFetching={query.isFetching}
					emptyState={
						query.isError || query.isFetching ? undefined : (
							<EmptyMessage
								message={t('operate.batchOperations.emptyMessage')}
								additionalInfo={t('operate.batchOperations.emptyAdditionalInfo')}
							/>
						)
					}
					hideHeaderWhenEmpty={!query.isFetching}
					data-testid="batch-operations-table"
				/>
			</TableContainer>
			{totalItems > pageSize && (
				<Pagination
					totalItems={totalItems}
					pageSize={pageSize}
					pageSizes={[20, 50, 100]}
					page={page}
					backwardText={t('operate.batchOperations.previousPage')}
					forwardText={t('operate.batchOperations.nextPage')}
					itemRangeText={(min, max, total) => t('operate.batchOperations.itemRange', {min, max, count: total})}
					itemText={(min, max) => t('operate.batchOperations.itemText', {min, max, count: max - min + 1})}
					itemsPerPageText={t('operate.batchOperations.itemsPerPage')}
					pageNumberText={t('operate.batchOperations.pageNumber')}
					pageRangeText={(_, total) => t('operate.batchOperations.pageRange', {count: total})}
					pageSelectLabelText={(total) => t('operate.batchOperations.pageSelectLabel', {count: total})}
					pageText={(current) => t('operate.batchOperations.pageText', {page: current})}
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
