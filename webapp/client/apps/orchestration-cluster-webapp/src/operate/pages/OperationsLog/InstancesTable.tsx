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
import {Button, DataTableSkeleton, InlineNotification, Stack} from '@carbon/react';
import {
	auditLogSortFieldEnum,
	type AuditLog,
	type GetProcessDefinitionResponseBody,
	type QueryAuditLogsRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {queries} from '#/shared/http/queries';
import {logger} from '#/operate/shared/utils/logger';
import {PanelHeader} from '#/operate/shared/PanelHeader/PanelHeader';
import {PaginatedSortableTable} from '#/operate/shared/PaginatedSortableTable/PaginatedSortableTable';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/EmptyMessage';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/ErrorMessage';
import {
	OperationsLogDetailsModal,
	type DetailsModalState,
} from '#/operate/shared/OperationsLogDetailsModal/OperationsLogDetailsModal';
import {spaceAndCapitalize} from '#/operate/shared/utils/spaceAndCapitalize';
import {formatTimestamp} from '#/operate/shared/utils/formatTimestamp';
import {CellActor} from './Cell/CellActor';
import {CellComment} from './Cell/CellComment';
import {CellDetails} from './Cell/CellDetails';
import {CellEntityKey} from './Cell/CellEntityKey';
import {CellParentEntity} from './Cell/CellParentEntity';
import {CellResult} from './Cell/CellResult';
import {useAuditLogs} from './operationsLog.queries';
import {TableContainer} from './styled';
import {formatToISO} from './utils';
import type {OperationsLogSearch} from './operationsLog.schema';

const DEFAULT_SORT = 'timestamp+desc';

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

	const [rawSortField, rawSortOrder] = (search.sort ?? DEFAULT_SORT).split('+');
	const parsedSortField = auditLogSortFieldEnum.safeParse(rawSortField);
	const sortField = parsedSortField.success ? parsedSortField.data : 'timestamp';
	const sortOrder = rawSortOrder === 'asc' ? 'asc' : 'desc';

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

	useEffect(() => {
		if (error) {
			logger.error(error);
		}
	}, [error]);

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

	const emptyState =
		status === 'error' ? (
			<Stack gap={4} role="alert">
				<ErrorMessage message={t('operate.operationsLog.notifications.fetchFailed')} additionalInfo="" />
				<Button kind="ghost" size="sm" disabled={isFetching} onClick={() => void refetch()}>
					{t('errorGenericErrorPageButtonLabel')}
				</Button>
			</Stack>
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
		{key: 'result', label: '', render: (row: AuditLog) => <CellResult item={row} />},
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
			defaultOrder: 'desc' as const,
			render: (row: AuditLog) => formatTimestamp(row.timestamp),
		},
		{
			key: 'comment',
			label: '',
			render: (row: AuditLog) => <CellComment item={row} setDetailsModal={setDetailsModal} />,
		},
	];

	return (
		<TableContainer>
			<PanelHeader
				title={t('operate.operationsLog.title')}
				count={status === 'success' && !isPlaceholderData ? totalCount : undefined}
				hasMoreTotalItems={status === 'success' && !isPlaceholderData && hasMoreTotalItems}
			/>
			{isDecisionDefinitionError && status === 'success' && !isPlaceholderData && (
				<Stack gap={2}>
					<InlineNotification
						kind="error"
						title={t('operate.operationsLog.decisionDefinitionsLookupFailed')}
						hideCloseButton
						lowContrast
						role="alert"
					/>
					<Button
						kind="ghost"
						size="sm"
						disabled={isFetchingDecisionDefinitions}
						onClick={() => void refetchDecisionDefinitions()}
					>
						{t('operate.operationsLog.decisionDefinitionsRetry')}
					</Button>
				</Stack>
			)}
			{status === 'pending' || (isPlaceholderData && isFetching && auditLogs.length === 0) ? (
				<DataTableSkeleton columnCount={columns.length} rowCount={5} showHeader={false} showToolbar={false} />
			) : (
				<PaginatedSortableTable
					columns={columns}
					rows={status === 'error' ? [] : auditLogs}
					rowKey={(row) => row.auditLogKey}
					isFetching={status === 'success' && isFetching && isPlaceholderData}
					emptyState={emptyState}
					hideHeaderWhenEmpty
					pagination={{
						hasPreviousPage: status === 'success' && !isPlaceholderData && hasPreviousPage,
						hasNextPage: status === 'success' && !isPlaceholderData && hasNextPage,
						isFetchingPreviousPage,
						isFetchingNextPage,
						fetchPreviousPage,
						fetchNextPage,
					}}
					data-testid="operations-log-table"
				/>
			)}
			{detailsModal.auditLog && (
				<OperationsLogDetailsModal
					isOpen={detailsModal.isOpen}
					onClose={() => setDetailsModal({isOpen: false})}
					auditLog={detailsModal.auditLog}
				/>
			)}
		</TableContainer>
	);
};

export {InstancesTable};
