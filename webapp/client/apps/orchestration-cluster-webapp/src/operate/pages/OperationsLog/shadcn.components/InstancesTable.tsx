/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useQueries, useQuery, type UseQueryResult} from '@tanstack/react-query';
import {Alert, Button} from '@camunda/design-system';
import {
	auditLogSortFieldEnum,
	type AuditLog,
	type GetProcessDefinitionResponseBody,
	type QueryAuditLogsRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {queries} from '#/shared/http/queries';
import {createSortSearchParamSchema} from '#/shared/sortSearchParam';
import {logger} from '#/operate/shared/utils/logger';
import {PanelHeader} from '#/operate/shared/PanelHeader/shadcn.components/PanelHeader';
import {PaginatedSortableTable} from '#/operate/shared/PaginatedSortableTable/shadcn.components/PaginatedSortableTable';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/shadcn.components/EmptyMessage';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/shadcn.components/ErrorMessage';
import {
	OperationsLogDetailsModal,
	type DetailsModalState,
} from '#/operate/shared/OperationsLogDetailsModal/shadcn.components/OperationsLogDetailsModal';
import {spaceAndCapitalize} from '#/operate/shared/utils/spaceAndCapitalize';
import {formatTimestamp} from '#/operate/shared/utils/formatTimestamp';
import {CellActor} from './Cell/CellActor';
import {CellComment} from './Cell/CellComment';
import {CellDetails} from './Cell/CellDetails';
import {CellEntityKey} from './Cell/CellEntityKey';
import {CellParentEntity} from './Cell/CellParentEntity';
import {CellResult} from './Cell/CellResult';
import {useAuditLogs} from '../operationsLog.queries';
import {useAuditLogsErrorNotification} from '../useAuditLogsErrorNotification';
import {formatToISO} from '../utils';
import type {OperationsLogSearch} from '../operationsLog.schema';

const DEFAULT_SORT = {field: 'timestamp', order: 'DESC'} as const;
const sortSearchParamSchema = createSortSearchParamSchema(auditLogSortFieldEnum);

const TABLE_CONTAINER_CLASSES = [
	'[&_[data-slot=table-container]]:rounded-none! [&_[data-slot=table-container]]:border-0! [&_[data-slot=table-container]]:shadow-none!',
	'[&_[data-slot=data-table]>div.rounded-xl]:rounded-none! [&_[data-slot=data-table]>div.rounded-xl]:border-0!',
].join(' ');

const combineDefinitionResults = (results: UseQueryResult<GetProcessDefinitionResponseBody>[]) => ({
	names: Object.fromEntries(
		results.flatMap(({data: definition}) =>
			definition ? [[definition.processDefinitionKey, definition.name ?? definition.processDefinitionId]] : [],
		),
	),
	error: results.find(({error}) => error)?.error,
});

type Props = {
	search: OperationsLogSearch;
	selectedTenantId?: string;
	selectedDefinitionKey?: string;
};

const InstancesTable: React.FC<Props> = ({search, selectedTenantId, selectedDefinitionKey}) => {
	const {t} = useTranslation();
	const [detailsModal, setDetailsModal] = useState<DetailsModalState>({isOpen: false});

	const {
		data: decisionDefinitions,
		isError: isDecisionDefinitionError,
		isFetching: isFetchingDecisionDefinitions,
		refetch: refetchDecisionDefinitions,
	} = useQuery({...queries.queryDecisionDefinitions({page: {limit: 1000}}), retry: false});
	const decisionDefinitionNameMap = useMemo(
		() => Object.fromEntries(decisionDefinitions?.items.map((def) => [def.decisionDefinitionKey, def.name]) ?? []),
		[decisionDefinitions],
	);

	const {field: sortField, order: sortOrder} = useMemo(
		() => sortSearchParamSchema.safeParse(search.sort).data ?? DEFAULT_SORT,
		[search.sort],
	);

	const requestFilter: NonNullable<QueryAuditLogsRequestBody['filter']> = {
		category: {$neq: 'ADMIN'},
		processDefinitionKey: selectedDefinitionKey,
		processDefinitionId: search.process && search.version === undefined ? search.process : undefined,
		processInstanceKey: search.processInstanceKey,
		tenantId: selectedTenantId,
		operationType: search.operationType?.length ? {$in: search.operationType} : undefined,
		entityType: search.entityType?.length ? {$in: search.entityType} : undefined,
		result: search.result,
		timestamp:
			search.timestampAfter || search.timestampBefore
				? {$gt: formatToISO(search.timestampAfter), $lt: formatToISO(search.timestampBefore)}
				: undefined,
		actorId: search.actorId,
	};

	const {
		data,
		dataUpdatedAt,
		error,
		status,
		isFetching,
		isPlaceholderData,
		refetch,
		isFetchingPreviousPage,
		hasPreviousPage,
		fetchPreviousPage,
		isFetchingNextPage,
		hasNextPage,
		fetchNextPage,
	} = useAuditLogs(requestFilter, [{field: sortField, order: sortOrder}]);

	useAuditLogsErrorNotification(error, dataUpdatedAt);

	const auditLogs = useMemo(() => data?.pages.flatMap((page) => page.items) ?? [], [data]);
	const processDefinitionKeys = useMemo(
		() => [...new Set(auditLogs.map((log) => log.processDefinitionKey).filter((key): key is string => Boolean(key)))],
		[auditLogs],
	);
	const {names: processDefinitionNameMap, error: definitionError} = useQueries({
		queries: processDefinitionKeys.map((key) => ({...queries.getProcessDefinition(key), retry: false})),
		combine: combineDefinitionResults,
	});
	useEffect(() => {
		if (definitionError) {
			logger.error(definitionError);
		}
	}, [definitionError]);
	const processName = (row: AuditLog) =>
		row.processDefinitionKey
			? (processDefinitionNameMap[row.processDefinitionKey] ?? row.processDefinitionId ?? row.processDefinitionKey)
			: undefined;
	const totalCount = data?.pages.at(0)?.page.totalItems ?? 0;
	const hasMoreTotalItems = data?.pages.at(0)?.page.hasMoreTotalItems ?? false;

	const hasAnyFilter =
		search.tenantId !== undefined ||
		search.process !== undefined ||
		search.version !== undefined ||
		search.processInstanceKey !== undefined ||
		search.operationType !== undefined ||
		search.entityType !== undefined ||
		search.result !== undefined ||
		search.actorId !== undefined ||
		search.timestampAfter !== undefined ||
		search.timestampBefore !== undefined;

	const isLoading = status === 'pending' || (status === 'success' && isFetching && isPlaceholderData);

	const emptyState =
		status === 'error' ? (
			<div className="flex flex-col items-start gap-4" role="alert">
				<ErrorMessage message={t('operate.operationsLog.notifications.fetchFailed')} additionalInfo="" />
				<Button variant="ghost" size="sm" disabled={isFetching} onClick={() => void refetch()}>
					{t('errorGenericErrorPageButtonLabel')}
				</Button>
			</div>
		) : hasAnyFilter ? (
			<EmptyMessage
				message={t('operate.operationsLog.emptyState.noResultsTitle')}
				additionalInfo={t('operate.operationsLog.emptyState.noResultsDescription')}
			/>
		) : (
			<EmptyMessage
				message={t('operate.operationsLog.emptyState.noItemsTitle')}
				additionalInfo={t('operate.operationsLog.emptyState.noItemsDescription')}
			/>
		);

	const columns = [
		{
			key: 'result',
			label: t('operate.operationsLog.table.result'),
			render: (row: AuditLog) => <CellResult item={row} />,
		},
		{
			key: 'operationType',
			label: t('operate.operationsLog.table.operationType'),
			sortKey: 'operationType',
			render: (row: AuditLog) => spaceAndCapitalize(row.operationType),
		},
		{
			key: 'entityType',
			label: t('operate.operationsLog.table.entityType'),
			sortKey: 'entityType',
			render: (row: AuditLog) => spaceAndCapitalize(row.entityType),
		},
		{
			key: 'entityKey',
			label: t('operate.operationsLog.table.entityKey'),
			sortKey: 'entityKey',
			render: (row: AuditLog) => (
				<CellEntityKey
					item={row}
					processDefinitionName={processName(row)}
					decisionDefinitionName={
						row.decisionDefinitionKey ? decisionDefinitionNameMap[row.decisionDefinitionKey] : undefined
					}
				/>
			),
		},
		{
			key: 'parentEntity',
			label: t('operate.operationsLog.table.parentEntity'),
			render: (row: AuditLog) => <CellParentEntity item={row} processDefinitionName={processName(row)} />,
		},
		{
			key: 'details',
			label: t('operate.operationsLog.table.details'),
			render: (row: AuditLog) => <CellDetails item={row} />,
		},
		{
			key: 'user',
			label: t('operate.operationsLog.table.actor'),
			sortKey: 'actorId',
			render: (row: AuditLog) => <CellActor item={row} />,
		},
		{
			key: 'timestamp',
			label: t('operate.operationsLog.table.date'),
			sortKey: 'timestamp',
			isDefault: true,
			defaultOrder: 'DESC' as const,
			render: (row: AuditLog) => formatTimestamp(row.timestamp),
		},
		{
			key: 'comment',
			label: t('operate.operationsLog.table.actions'),
			render: (row: AuditLog) => <CellComment item={row} setDetailsModal={setDetailsModal} />,
		},
	];

	return (
		<section className="flex h-full flex-col">
			<PanelHeader
				title={t('operate.operationsLog.title')}
				className="bg-transparent"
				count={status === 'success' && !isPlaceholderData ? totalCount : undefined}
				hasMoreTotalItems={status === 'success' && !isPlaceholderData && hasMoreTotalItems}
			/>
			{isDecisionDefinitionError && status === 'success' && !isPlaceholderData && (
				<div className="flex flex-col items-start gap-2 p-2">
					<Alert
						variant="destructive"
						role="alert"
						title={t('operate.operationsLog.decisionDefinitionsLookupFailed')}
						className="w-full"
					/>
					<Button
						variant="ghost"
						size="sm"
						disabled={isFetchingDecisionDefinitions}
						onClick={() => void refetchDecisionDefinitions()}
					>
						{t('operate.operationsLog.decisionDefinitionsRetry')}
					</Button>
				</div>
			)}
			<PaginatedSortableTable<AuditLog>
				columns={columns}
				rows={status === 'error' ? [] : auditLogs}
				rowKey={(row) => row.auditLogKey}
				ariaLabel={t('operate.operationsLog.table.tableLabel')}
				loadingPreviousPageLabel={t('operate.operationsLog.table.loadingPreviousPage')}
				loadingNextPageLabel={t('operate.operationsLog.table.loadingNextPage')}
				isFetching={isLoading}
				loadingRowCount={5}
				emptyState={isLoading ? undefined : emptyState}
				hideHeaderWhenEmpty
				pagination={{
					hasPreviousPage: status === 'success' && !isPlaceholderData && hasPreviousPage,
					hasNextPage: status === 'success' && !isPlaceholderData && hasNextPage,
					isFetchingPreviousPage,
					isFetchingNextPage,
					fetchPreviousPage,
					fetchNextPage,
				}}
				className={TABLE_CONTAINER_CLASSES}
				data-testid="operations-log-table"
			/>
			{detailsModal.auditLog && (
				<OperationsLogDetailsModal
					isOpen={detailsModal.isOpen}
					onClose={() => setDetailsModal({isOpen: false})}
					auditLog={detailsModal.auditLog}
				/>
			)}
		</section>
	);
};

export {InstancesTable};
