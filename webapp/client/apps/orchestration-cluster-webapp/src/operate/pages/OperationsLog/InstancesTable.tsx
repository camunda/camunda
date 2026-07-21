/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useSuspenseQuery} from '@tanstack/react-query';
import type {AuditLog, AuditLogSortField, QueryAuditLogsRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {queries} from '#/shared/http/queries';
import {tracking} from '#/shared/tracking';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {logger} from '#/operate/shared/utils/logger';
import {PanelHeader} from '#/operate/shared/PanelHeader/PanelHeader';
import {PaginatedSortableTable} from '#/operate/shared/PaginatedSortableTable/PaginatedSortableTable';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/EmptyMessage';
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

type Props = {
	search: OperationsLogSearch;
};

const InstancesTable: React.FC<Props> = ({search}) => {
	const {t} = useTranslation();
	const [detailsModal, setDetailsModal] = useState<DetailsModalState>({isOpen: false});

	const selectedTenantId = search.tenantId === 'all' ? undefined : search.tenantId;

	const {data: processDefinitions} = useSuspenseQuery(queries.queryProcessDefinitions({page: {limit: 1000}}));
	const {data: decisionDefinitions} = useSuspenseQuery(queries.queryDecisionDefinitions({page: {limit: 1000}}));

	const processDefinitionNameMap = useMemo(
		() => Object.fromEntries(processDefinitions.items.map((def) => [def.processDefinitionKey, def.name])),
		[processDefinitions],
	);
	const decisionDefinitionNameMap = useMemo(
		() => Object.fromEntries(decisionDefinitions.items.map((def) => [def.decisionDefinitionKey, def.name])),
		[decisionDefinitions],
	);

	const selectedProcessDefinition =
		search.process && search.version !== undefined
			? processDefinitions.items.find(
					(def) =>
						def.processDefinitionId === search.process &&
						def.version === search.version &&
						(selectedTenantId === undefined || def.tenantId === selectedTenantId),
				)
			: undefined;

	const [rawSortField, rawSortOrder] = (search.sort ?? DEFAULT_SORT).split('+');
	const sortField = (rawSortField ?? 'timestamp') as AuditLogSortField;
	const sortOrder = (rawSortOrder ?? 'desc') as 'asc' | 'desc';

	const requestFilter: NonNullable<QueryAuditLogsRequestBody['filter']> = {
		category: {$neq: 'ADMIN'},
		processDefinitionKey: selectedProcessDefinition?.processDefinitionKey,
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
		isFetchingPreviousPage,
		hasPreviousPage,
		fetchPreviousPage,
		isFetchingNextPage,
		hasNextPage,
		fetchNextPage,
	} = useAuditLogs(requestFilter, [{field: sortField, order: sortOrder}]);

	useEffect(() => {
		if (data !== undefined) {
			tracking.track({
				eventName: 'operate:audit-logs-loaded',
				filters: Object.keys(requestFilter),
				sort: sortField,
			});
		}
		// Only re-fire when a new page of results actually arrives, matching legacy's `select`-based
		// tracking; including requestFilter/sortField would double-fire between a filter change and
		// the query settling.
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [data]);

	useEffect(() => {
		if (error) {
			tracking.track({eventName: 'operate:audit-logs-fetch-failed'});
			notificationsStore.displayNotification({
				isDismissable: true,
				kind: 'error',
				title: t('operate.operationsLog.notifications.fetchFailed'),
			});
			logger.error(error);
		}
	}, [error, t]);

	const auditLogs = useMemo(() => data?.pages.flatMap((page) => page.items) ?? [], [data]);
	const totalCount = data?.pages.at(0)?.page.totalItems ?? 0;

	const hasAnyFilter = Object.entries(search).some(([key, value]) => key !== 'sort' && value !== undefined);

	const emptyState = hasAnyFilter ? (
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
					processDefinitionName={
						row.processDefinitionKey ? processDefinitionNameMap[row.processDefinitionKey] : undefined
					}
					decisionDefinitionName={
						row.decisionDefinitionKey ? decisionDefinitionNameMap[row.decisionDefinitionKey] : undefined
					}
				/>
			),
		},
		{
			key: 'parentEntity',
			label: t('operate.operationsLog.table.parentEntity'),
			render: (row: AuditLog) => (
				<CellParentEntity
					item={row}
					processDefinitionName={
						row.processDefinitionKey ? processDefinitionNameMap[row.processDefinitionKey] : undefined
					}
				/>
			),
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
			<PanelHeader title={t('operate.operationsLog.title')} count={totalCount} />
			<PaginatedSortableTable
				columns={columns}
				rows={auditLogs}
				rowKey={(row) => row.auditLogKey}
				emptyState={emptyState}
				pagination={{
					hasPreviousPage,
					hasNextPage,
					isFetchingPreviousPage,
					isFetchingNextPage,
					fetchPreviousPage,
					fetchNextPage,
				}}
				data-testid="operations-log-table"
			/>
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
