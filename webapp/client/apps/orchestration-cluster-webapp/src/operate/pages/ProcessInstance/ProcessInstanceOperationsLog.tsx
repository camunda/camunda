/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {DataTableSkeleton} from '@carbon/react';
import {
	auditLogSortFieldEnum,
	type AuditLog,
	type QueryAuditLogsRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {createSortSearchParamSchema} from '#/shared/sortSearchParam';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/EmptyMessage';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/ErrorMessage';
import {PaginatedSortableTable} from '#/operate/shared/PaginatedSortableTable/PaginatedSortableTable';
import {
	OperationsLogDetailsModal,
	type DetailsModalState,
} from '#/operate/shared/OperationsLogDetailsModal/OperationsLogDetailsModal';
import {formatTimestamp} from '#/operate/shared/utils/formatTimestamp';
import {spaceAndCapitalize} from '#/operate/shared/utils/spaceAndCapitalize';
import {CellActor} from '#/operate/pages/OperationsLog/Cell/CellActor';
import {CellComment} from '#/operate/pages/OperationsLog/Cell/CellComment';
import {CellEntityKey} from '#/operate/pages/OperationsLog/Cell/CellEntityKey';
import {CellResult} from '#/operate/pages/OperationsLog/Cell/CellResult';
import {useAuditLogs} from '#/operate/pages/OperationsLog/operationsLog.queries';
import {useAuditLogsErrorNotification} from '#/operate/pages/OperationsLog/useAuditLogsErrorNotification';
import {TableContainer} from '#/operate/pages/OperationsLog/styled';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';
import {useProcessInstancePage} from './useProcessInstancePage';
import {ProcessInstanceOperationsLogFilters} from './ProcessInstanceOperationsLogFilters';
import type {ProcessInstanceOperationsLogSearch} from './processInstanceOperationsLog.schema';
import {EmptyMessageContainer} from './processInstanceOperationsLog.styled';

const DEFAULT_SORT = {field: 'timestamp', order: 'DESC'} as const;
const sortSearchParamSchema = createSortSearchParamSchema(auditLogSortFieldEnum);

const ProcessInstanceOperationsLog: React.FC<{search: ProcessInstanceOperationsLogSearch}> = ({search}) => {
	const {t} = useTranslation();
	const {processInstanceId} = useProcessInstancePage();
	const {resolvedElementInstance, isFetchingElement, hasSelection} = useProcessInstanceElementSelection();
	const [detailsModal, setDetailsModal] = useState<DetailsModalState>({isOpen: false});
	const hasMultipleInstances = hasSelection && resolvedElementInstance === null;

	const {field: sortField, order: sortOrder} = useMemo(
		() => sortSearchParamSchema.safeParse(search.sort).data ?? DEFAULT_SORT,
		[search.sort],
	);
	const filter: NonNullable<QueryAuditLogsRequestBody['filter']> = {
		category: {$neq: 'ADMIN'},
		processInstanceKey: processInstanceId,
		elementInstanceKey: resolvedElementInstance?.elementInstanceKey,
		operationType: search.operationType?.length ? {$in: search.operationType} : undefined,
		entityType: search.entityType?.length ? {$in: search.entityType} : undefined,
	};

	const {
		data,
		dataUpdatedAt,
		error,
		isPending,
		isFetchingPreviousPage,
		hasPreviousPage,
		fetchPreviousPage,
		isFetchingNextPage,
		hasNextPage,
		fetchNextPage,
	} = useAuditLogs(filter, [{field: sortField, order: sortOrder}], {
		enabled: !isFetchingElement && !hasMultipleInstances,
	});
	useAuditLogsErrorNotification(error, dataUpdatedAt);

	if (hasMultipleInstances) {
		return (
			<TableContainer>
				<EmptyMessageContainer>
					<EmptyMessage message={t('operate.processInstance.operationsLog.multiInstance')} />
				</EmptyMessageContainer>
			</TableContainer>
		);
	}

	const auditLogs = data?.pages.flatMap((page) => page.items) ?? [];

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
			render: (row: AuditLog) => <CellEntityKey item={row} />,
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
			label: '',
			render: (row: AuditLog) => <CellComment item={row} setDetailsModal={setDetailsModal} />,
		},
	];

	return (
		<TableContainer>
			<ProcessInstanceOperationsLogFilters search={search} />
			{isPending ? (
				<DataTableSkeleton columnCount={columns.length} rowCount={10} showHeader={false} showToolbar={false} />
			) : (
				<PaginatedSortableTable
					columns={columns}
					rows={error === null ? auditLogs : []}
					rowKey={(row) => row.auditLogKey}
					emptyState={
						error === null ? (
							<EmptyMessage message={t('operate.processInstance.operationsLog.empty')} />
						) : (
							<ErrorMessage />
						)
					}
					hideHeaderWhenEmpty
					pagination={{
						hasPreviousPage,
						hasNextPage,
						isFetchingPreviousPage,
						isFetchingNextPage,
						fetchPreviousPage,
						fetchNextPage,
					}}
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

export {ProcessInstanceOperationsLog};
