/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {OPERATE_BASE} from '#/shared/operateBase';
import {cn} from '#/shared/cn';
import {useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {createLink} from '@tanstack/react-router';
import {Alert, Button, Link} from '@camunda/design-system';
import type {BatchOperationItem, BatchOperationType} from '@camunda/camunda-api-zod-schemas/8.11';
import {PaginatedSortableTable} from '#/operate/shared/PaginatedSortableTable/shadcn.components/PaginatedSortableTable';
import {PanelHeader} from '#/operate/shared/PanelHeader/shadcn.components/PanelHeader';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/shadcn.components/EmptyMessage';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/shadcn.components/ErrorMessage';
import {formatDate} from '../utils';
import {useBatchOperationItems} from '../useBatchOperationItems';
import {ItemKeyCell} from './ItemKeyCell';
import {StateCell} from './StateCell';

type Props = {
	batchOperationKey: string;
	batchOperationType: BatchOperationType | null | undefined;
};

const TABLE_CONTAINER_CLASSES = [
	'[&_[data-slot=table-container]]:rounded-none! [&_[data-slot=table-container]]:border-0! [&_[data-slot=table-container]]:shadow-none!',
	'[&_[data-slot=data-table]>div.rounded-xl]:rounded-none! [&_[data-slot=data-table]>div.rounded-xl]:border-0!',
	'[&_tr:has([data-item-outcome=failed])>td:first-child]:[box-shadow:inset_3px_0_0_var(--danger-border-strong)]',
	'[&_tr:has([data-item-outcome=failed])+tr[data-slot=data-table-expansion-row]>td:first-child]:[box-shadow:inset_3px_0_0_var(--danger-border-strong)]',
	'[&_tr:has([data-item-outcome=completed])>td:first-child]:[box-shadow:inset_3px_0_0_var(--success-border-strong)]',
].join(' ');

const TableLink: React.FC<React.ComponentProps<'a'>> = (props) => <Link className="underline" {...props} />;
const ProcessInstanceLink = createLink(TableLink);
const DecisionInstanceLink = createLink(TableLink);

type ProcessInstanceKeyCellProps = {
	/** `null` for items that do not target a process instance (e.g. decision or definition items). */
	processInstanceKey: string | null;
	fallbackText: string;
	label: string;
	disableLink?: boolean;
};

const ProcessInstanceKeyCell: React.FC<ProcessInstanceKeyCellProps> = ({
	processInstanceKey,
	fallbackText,
	label,
	disableLink = false,
}) =>
	processInstanceKey === null ? (
		<>{fallbackText}</>
	) : (
		<ItemKeyCell itemKey={processInstanceKey} fallbackText={fallbackText}>
			{disableLink ? undefined : (
				<ProcessInstanceLink
					to={`${OPERATE_BASE}/processes/$processInstanceId` as const}
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
					render: (row: BatchOperationItem) => {
						const label = t('operate.batchOperation.itemsTable.viewDecisionInstance', {key: row.itemKey});

						return (
							<ItemKeyCell
								itemKey={row.itemKey}
								fallbackText={t('operate.batchOperation.itemsTable.noDecisionInstance')}
							>
								{row.state === 'COMPLETED' ? undefined : (
									<DecisionInstanceLink
										to="/operate-preview/decisions/$decisionInstanceId"
										params={{decisionInstanceId: row.itemKey}}
										title={label}
										aria-label={label}
									>
										{row.itemKey}
									</DecisionInstanceLink>
								)}
							</ItemKeyCell>
						);
					},
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
	const showItemsTable = status !== 'error' || items.length > 0;
	const retry = isFetchNextPageError ? fetchNextPage : isFetchPreviousPageError ? fetchPreviousPage : refetch;
	const retryButton = (
		<Button variant="secondary" size="sm" loading={isFetching} onClick={() => void retry()}>
			{t('operate.batchOperation.itemsTable.retry')}
		</Button>
	);

	return (
		<section aria-label={t('operate.batchOperation.itemsTable.title')} className="flex min-h-0 flex-1 flex-col">
			<PanelHeader
				title={t('operate.batchOperation.itemsTable.title')}
				count={totalItems}
				hasMoreTotalItems={hasMoreTotalItems}
			/>
			{isError && (
				<div>
					{items.length > 0 ? (
						<Alert
							variant="destructive"
							title={t('operate.shared.errorMessage.message')}
							description={t('operate.batchOperation.itemsTable.retryHint')}
							className="rounded-none border-x-0"
						>
							{retryButton}
						</Alert>
					) : (
						<div role="alert" className="flex flex-col items-start gap-4 p-4">
							<ErrorMessage additionalInfo={t('operate.batchOperation.itemsTable.retryHint')} />
							{retryButton}
						</div>
					)}
					<div role="status" className={cn('text-sm', isFetching && 'px-4 pb-4')}>
						{isFetching ? t('operate.batchOperation.itemsTable.retrying') : null}
					</div>
				</div>
			)}
			{showItemsTable && (
				<div className="flex min-h-0 flex-1 flex-col">
					<PaginatedSortableTable<BatchOperationItem>
						size="md"
						columns={columns}
						rows={items}
						rowKey={(row) => row.itemKey}
						ariaLabel={t('operate.batchOperation.ds.itemsTable.tableLabel')}
						loadingPreviousPageLabel={t('operate.batchOperation.ds.itemsTable.loadingPreviousPage')}
						loadingNextPageLabel={t('operate.batchOperation.ds.itemsTable.loadingNextPage')}
						expandRowLabel={t('operate.batchOperation.ds.itemsTable.expandFailure')}
						collapseRowLabel={t('operate.batchOperation.ds.itemsTable.collapseFailure')}
						expansion={(row) =>
							row.errorMessage ? (
								<p className="m-0 text-sm break-words whitespace-pre-wrap [overflow-wrap:anywhere]">
									<strong>{t('operate.batchOperation.itemsTable.failureReason')}</strong> {row.errorMessage}
								</p>
							) : null
						}
						isFetching={
							isFetching &&
							!isError &&
							!isFetchingPreviousPage &&
							!isFetchingNextPage &&
							(status === 'pending' || items.length > 0)
						}
						emptyState={
							isError || status === 'pending' ? undefined : (
								<EmptyMessage message={t('operate.batchOperation.itemsTable.emptyMessage')} />
							)
						}
						pagination={{
							hasPreviousPage,
							hasNextPage,
							isFetchingPreviousPage,
							isFetchingNextPage,
							isFetchPreviousPageError,
							fetchPreviousPage,
							fetchNextPage,
						}}
						className={TABLE_CONTAINER_CLASSES}
						data-testid="batch-items-table"
					/>
				</div>
			)}
		</section>
	);
};

export {BatchItemsTable};
