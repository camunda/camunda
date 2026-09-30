/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {createLink} from '@tanstack/react-router';
import {ActionableNotification, Button, DataTableSkeleton, InlineLoading} from '@carbon/react';
import type {BatchOperationItem, BatchOperationType} from '@camunda/camunda-api-zod-schemas/8.11';
import {PaginatedSortableTable} from '#/operate/shared/PaginatedSortableTable/PaginatedSortableTable';
import {PanelHeader} from '#/operate/shared/PanelHeader/PanelHeader';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/EmptyMessage';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/ErrorMessage';
import {formatDate} from './utils';
import {ItemKeyCell} from './ItemKeyCell';
import {StateCell} from './StateCell';
import {useBatchOperationItems} from './useBatchOperationItems';
import {ItemLink, ItemsTableContainer, TableContainer} from './styled';

type Props = {
	batchOperationKey: string;
	batchOperationType: BatchOperationType | null | undefined;
};

const ProcessInstanceLink = createLink<React.FC<React.ComponentProps<'a'>>>(ItemLink);

type ProcessInstanceKeyCellProps = {
	processInstanceKey: string;
	fallbackText: string;
	label: string;
	disableLink?: boolean;
};

const ProcessInstanceKeyCell: React.FC<ProcessInstanceKeyCellProps> = ({
	processInstanceKey,
	fallbackText,
	label,
	disableLink = false,
}) => (
	<ItemKeyCell itemKey={processInstanceKey} fallbackText={fallbackText}>
		{disableLink ? undefined : (
			<ProcessInstanceLink
				to="/operate/processes/$processInstanceId"
				params={{processInstanceId: processInstanceKey}}
				title={label}
				aria-label={label}
			>
				{processInstanceKey}
			</ProcessInstanceLink>
		)}
	</ItemKeyCell>
);

const BatchItemsTable: React.FC<Props> = ({batchOperationKey, batchOperationType}) => {
	const {t} = useTranslation();
	const {
		items,
		totalItems,
		hasMoreTotalItems,
		status,
		isFetching,
		hasPreviousPage,
		fetchPreviousPage,
		isFetchingPreviousPage,
		hasNextPage,
		fetchNextPage,
		isFetchingNextPage,
		isFetchNextPageError,
		isFetchPreviousPageError,
		refetch,
	} = useBatchOperationItems(batchOperationKey);

	const columns = useMemo(() => {
		const state = {
			key: 'state',
			label: t('operate.batchOperation.itemsTable.state'),
			render: (row: BatchOperationItem) => <StateCell item={row} />,
		};
		const processedDate = {
			key: 'processedDate',
			label: t('operate.batchOperation.itemsTable.date'),
			render: (row: BatchOperationItem) => formatDate(row.processedDate),
		};
		const processInstanceKey = {
			key: 'processInstanceKey',
			label: t('operate.batchOperation.itemsTable.processInstanceKey'),
			render: (row: BatchOperationItem) => (
				<ProcessInstanceKeyCell
					processInstanceKey={row.processInstanceKey}
					fallbackText={t('operate.batchOperation.itemsTable.noProcessInstance')}
					label={t('operate.batchOperation.itemsTable.viewProcessInstance', {key: row.processInstanceKey})}
				/>
			),
		};

		if (batchOperationType === 'DELETE_DECISION_INSTANCE') {
			return [
				{
					key: 'decisionInstanceKey',
					label: t('operate.batchOperation.itemsTable.decisionInstanceKey'),
					render: (row: BatchOperationItem) => (
						<ItemKeyCell
							itemKey={row.itemKey}
							fallbackText={t('operate.batchOperation.itemsTable.noDecisionInstance')}
							href={row.state === 'COMPLETED' ? undefined : `/operate/decisions/${row.itemKey}`}
							label={
								row.state === 'COMPLETED'
									? undefined
									: t('operate.batchOperation.itemsTable.viewDecisionInstance', {key: row.itemKey})
							}
						/>
					),
				},
				state,
				processedDate,
			];
		}

		if (batchOperationType === 'DELETE_PROCESS_INSTANCE') {
			return [
				{
					key: 'processInstanceKey',
					label: t('operate.batchOperation.itemsTable.processInstanceKey'),
					render: (row: BatchOperationItem) => (
						<ProcessInstanceKeyCell
							processInstanceKey={row.processInstanceKey}
							fallbackText={t('operate.batchOperation.itemsTable.noProcessInstance')}
							label={t('operate.batchOperation.itemsTable.viewProcessInstance', {key: row.processInstanceKey})}
							disableLink={row.state === 'COMPLETED'}
						/>
					),
				},
				state,
				processedDate,
			];
		}

		if (batchOperationType === 'RESOLVE_INCIDENT') {
			return [
				processInstanceKey,
				{
					key: 'incidentKey',
					label: t('operate.batchOperation.itemsTable.incidentKey'),
					render: (row: BatchOperationItem) => (
						<ItemKeyCell itemKey={row.itemKey} fallbackText={t('operate.batchOperation.itemsTable.noIncident')} />
					),
				},
				state,
				processedDate,
			];
		}

		return [processInstanceKey, state, processedDate];
	}, [batchOperationType, t]);

	const isError = status === 'error';
	const showItemsTable = status !== 'pending' && (status === 'success' || items.length > 0);
	const retry = isFetchNextPageError ? fetchNextPage : isFetchPreviousPageError ? fetchPreviousPage : refetch;
	const onRetryClick = () => {
		if (!isFetching) {
			void retry();
		}
	};
	const retryButton = (
		<Button kind="tertiary" size="sm" disabled={isFetching} onClick={() => void retry()}>
			{t('operate.batchOperation.itemsTable.retry')}
		</Button>
	);
	return (
		<TableContainer>
			<PanelHeader
				title={t('operate.batchOperation.itemsTable.title')}
				count={totalItems}
				hasMoreTotalItems={hasMoreTotalItems}
			/>
			{isError && (
				<div>
					{items.length > 0 ? (
						<ActionableNotification
							kind="error"
							inline
							hideCloseButton
							role="alert"
							title={t('operate.shared.errorMessage.message')}
							subtitle={t('operate.batchOperation.itemsTable.retryHint')}
							actionButtonLabel={t('operate.batchOperation.itemsTable.retry')}
							onActionButtonClick={onRetryClick}
						/>
					) : (
						<div role="alert">
							<ErrorMessage additionalInfo={t('operate.batchOperation.itemsTable.retryHint')} />
							{retryButton}
						</div>
					)}
					{isFetching && <InlineLoading description={t('operate.batchOperation.itemsTable.retrying')} />}
				</div>
			)}
			{status === 'pending' && (
				<DataTableSkeleton columnCount={columns.length} rowCount={5} showHeader={false} showToolbar={false} />
			)}
			{showItemsTable && (
				<ItemsTableContainer>
					<PaginatedSortableTable<BatchOperationItem>
						size="md"
						columns={columns}
						rows={items}
						rowKey={(row) => row.itemKey}
						isFetching={isFetching && !isError && !isFetchingPreviousPage && !isFetchingNextPage}
						emptyState={
							isError ? undefined : <EmptyMessage message={t('operate.batchOperation.itemsTable.emptyMessage')} />
						}
						pagination={{
							hasPreviousPage,
							hasNextPage,
							isFetchingPreviousPage,
							isFetchingNextPage,
							fetchPreviousPage,
							fetchNextPage,
						}}
						data-testid="batch-items-table"
					/>
				</ItemsTableContainer>
			)}
		</TableContainer>
	);
};

export {BatchItemsTable};
