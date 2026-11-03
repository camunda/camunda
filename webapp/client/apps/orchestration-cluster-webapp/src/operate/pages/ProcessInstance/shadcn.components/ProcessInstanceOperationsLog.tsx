/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {
	auditLogSortFieldEnum,
	type AuditLog,
	type QueryAuditLogsRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {createSortSearchParamSchema} from '#/shared/sortSearchParam';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/shadcn.components/EmptyMessage';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/shadcn.components/ErrorMessage';
import {PaginatedSortableTable} from '#/operate/shared/PaginatedSortableTable/shadcn.components/PaginatedSortableTable';
import {OperationsLogDetailsModal} from '#/operate/shared/OperationsLogDetailsModal/shadcn.components/OperationsLogDetailsModal';
import {formatTimestamp} from '#/operate/shared/utils/formatTimestamp';
import {spaceAndCapitalize} from '#/operate/shared/utils/spaceAndCapitalize';
import {useAuditLogs} from '#/operate/pages/OperationsLog/operationsLog.queries';
import {useAuditLogsErrorNotification} from '#/operate/pages/OperationsLog/useAuditLogsErrorNotification';
import {useProcessInstanceElementSelection} from '../useProcessInstanceElementSelection';
import {useProcessInstancePage} from '../useProcessInstancePage';
import type {ProcessInstanceOperationsLogSearch} from '../processInstanceOperationsLog.schema';
import {ProcessInstanceOperationsLogFilters} from './ProcessInstanceOperationsLogFilters';
import {CellActor, CellComment, CellEntityKey, CellResult, type DetailsModalState} from './OperationsLogCells';

const DEFAULT_SORT = {field: 'timestamp', order: 'DESC'} as const;
const sortSearchParamSchema = createSortSearchParamSchema(auditLogSortFieldEnum);

const Centered: React.FC<{children: React.ReactNode}> = ({children}) => (
	<div className="flex h-full items-center justify-center text-center">{children}</div>
);

const ProcessInstanceOperationsLog: React.FC<{search: ProcessInstanceOperationsLogSearch}> = ({search}) => {
	const {t} = useTranslation();
	const {processInstanceId} = useProcessInstancePage();
	const {
		resolvedElementInstance,
		selectedElementInstanceKey,
		selectedElementInstanceCount,
		isSelectedInstancePlaceholder,
		isFetchingElementError,
		hasSelection,
	} = useProcessInstanceElementSelection();
	const [detailsModal, setDetailsModal] = useState<DetailsModalState>({isOpen: false});
	const isScopeResolved = !hasSelection || resolvedElementInstance !== null;
	const hasMultipleInstances =
		selectedElementInstanceKey === null &&
		selectedElementInstanceCount !== null &&
		selectedElementInstanceCount > 0 &&
		resolvedElementInstance === null;
	const hasNoInstances =
		isSelectedInstancePlaceholder ||
		(hasSelection && selectedElementInstanceKey === null && selectedElementInstanceCount === 0);

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
		isFetching,
		isPlaceholderData,
		isFetchingPreviousPage,
		hasPreviousPage,
		fetchPreviousPage,
		isFetchingNextPage,
		hasNextPage,
		fetchNextPage,
	} = useAuditLogs(filter, [{field: sortField, order: sortOrder}], {
		enabled: isScopeResolved,
	});
	useAuditLogsErrorNotification(error, dataUpdatedAt);

	if (isFetchingElementError) {
		return (
			<Centered>
				<ErrorMessage />
			</Centered>
		);
	}

	if (hasMultipleInstances || hasNoInstances) {
		return (
			<Centered>
				<EmptyMessage
					message={t(
						hasMultipleInstances
							? 'operate.processInstance.operationsLog.multiInstance'
							: 'operate.processInstance.operationsLog.empty',
					)}
				/>
			</Centered>
		);
	}

	const auditLogs = data?.pages.flatMap((page) => page.items) ?? [];
	const isLoadingScope = !isScopeResolved || isPending || (isPlaceholderData && isFetching && auditLogs.length === 0);

	const columns = [
		{
			key: 'result',
			label: t('operate.processInstance.operationsLog.table.result'),
			render: (row: AuditLog) => <CellResult item={row} />,
		},
		{
			key: 'operationType',
			label: t('operate.processInstance.operationsLog.table.operationType'),
			sortKey: 'operationType',
			render: (row: AuditLog) => spaceAndCapitalize(row.operationType),
		},
		{
			key: 'entityType',
			label: t('operate.processInstance.operationsLog.table.entityType'),
			sortKey: 'entityType',
			render: (row: AuditLog) => spaceAndCapitalize(row.entityType),
		},
		{
			key: 'entityKey',
			label: t('operate.processInstance.operationsLog.table.entityKey'),
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
			label: t('operate.operationsLog.table.details'),
			render: (row: AuditLog) => <CellComment item={row} setDetailsModal={setDetailsModal} />,
		},
	];

	return (
		<div className="flex h-full min-h-0 flex-col px-4">
			<ProcessInstanceOperationsLogFilters search={search} />
			<div className="min-h-0 flex-1">
				<PaginatedSortableTable
					ariaLabel={t('operate.processInstance.operationsLog.list')}
					size="sm"
					columns={columns}
					rows={error === null ? auditLogs : []}
					rowKey={(row) => row.auditLogKey}
					emptyState={
						isLoadingScope ? undefined : error === null ? (
							<EmptyMessage message={t('operate.processInstance.operationsLog.empty')} />
						) : (
							<ErrorMessage />
						)
					}
					hideHeaderWhenEmpty
					isFetching={isLoadingScope || (isFetching && isPlaceholderData)}
					loadingPreviousPageLabel={t('operate.shared.sortableTable.loadingPreviousPage')}
					loadingNextPageLabel={t('operate.shared.sortableTable.loadingNextPage')}
					pagination={{
						hasPreviousPage: !isPlaceholderData && hasPreviousPage,
						hasNextPage: !isPlaceholderData && hasNextPage,
						isFetchingPreviousPage,
						isFetchingNextPage,
						fetchPreviousPage,
						fetchNextPage,
					}}
				/>
			</div>
			{detailsModal.auditLog && (
				<OperationsLogDetailsModal
					isOpen={detailsModal.isOpen}
					onClose={() => setDetailsModal({isOpen: false})}
					auditLog={detailsModal.auditLog}
				/>
			)}
		</div>
	);
};

export {ProcessInstanceOperationsLog};
