/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import type {BatchOperationItem, BatchOperationType} from '@camunda/camunda-api-zod-schemas/8.10';
import {PaginatedSortableTable} from '#/operate/shared/PaginatedSortableTable/PaginatedSortableTable';
import {PanelHeader} from '#/operate/shared/PanelHeader/PanelHeader';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/EmptyMessage';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/ErrorMessage';
import {formatDate} from './utils';
import {ItemKeyCell} from './ItemKeyCell';
import {StateCell} from './StateCell';
import {useBatchOperationItems} from './useBatchOperationItems';
import {TableContainer} from './styled';

type Props = {
	batchOperationKey: string;
	batchOperationType: BatchOperationType | undefined;
};

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
	} = useBatchOperationItems(batchOperationKey);

	const columns = (() => {
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
				<ItemKeyCell
					itemKey={row.processInstanceKey}
					fallbackText={t('operate.batchOperation.itemsTable.noProcessInstance')}
					href={`/operate/processes/${row.processInstanceKey}`}
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
						<ItemKeyCell
							itemKey={row.processInstanceKey}
							fallbackText={t('operate.batchOperation.itemsTable.noProcessInstance')}
							href={row.state === 'COMPLETED' ? undefined : `/operate/processes/${row.processInstanceKey}`}
							label={
								row.state === 'COMPLETED'
									? undefined
									: t('operate.batchOperation.itemsTable.viewProcessInstance', {key: row.processInstanceKey})
							}
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
	})();

	const emptyState =
		status === 'error' ? (
			<ErrorMessage />
		) : (
			<EmptyMessage message={t('operate.batchOperation.itemsTable.emptyMessage')} />
		);

	return (
		<TableContainer>
			<PanelHeader
				title={t('operate.batchOperation.itemsTable.title')}
				count={totalItems}
				hasMoreTotalItems={hasMoreTotalItems}
			/>
			<PaginatedSortableTable<BatchOperationItem>
				size="md"
				columns={columns}
				rows={items}
				rowKey={(row) => row.itemKey}
				isFetching={isFetching && !isFetchingPreviousPage && !isFetchingNextPage}
				emptyState={emptyState}
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
		</TableContainer>
	);
};

export {BatchItemsTable};
