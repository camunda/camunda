/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {DataTableSkeleton, SkeletonText} from '@carbon/react';
import type {
	BatchOperationItem,
	BatchOperationItemState,
	BatchOperationType,
	ProcessInstance,
	ProcessInstanceState,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {PanelHeader} from '#/operate/shared/PanelHeader/PanelHeader';
import {PaginatedSortableTable} from '#/operate/shared/PaginatedSortableTable/PaginatedSortableTable';
import {StateIcon} from '#/operate/shared/StateIcon/StateIcon';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/EmptyMessage';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/ErrorMessage';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {isSpecificTenant} from '#/operate/shared/utils/isSpecificTenant';
import {formatTimestamp} from '#/operate/shared/utils/formatTimestamp';
import {useProcessInstancesSearch} from './useProcessInstancesSearch';
import {useActiveOperationItemsForInstances, useOperationItemsForInstances} from './batchOperationItems.queries';
import {InstanceOperations} from './InstanceOperations';
import type {ProcessesSearch} from './processesFilter';
import {InstancesTableContainer, ProcessName, InstanceLink, VisuallyHiddenStatus} from './styled';

type Props = {
	search: ProcessesSearch;
};

function getDisplayState(instance: ProcessInstance): ProcessInstanceState | 'INCIDENT' {
	if (instance.state === 'SUSPENDED') {
		return 'SUSPENDED';
	}
	return instance.hasIncident ? 'INCIDENT' : instance.state;
}

const OPERATION_STATE_ORDER: BatchOperationItemState[] = ['FAILED', 'ACTIVE', 'CANCELED', 'SKIPPED', 'COMPLETED'];

function getOperationStatesByInstance(items: BatchOperationItem[] | undefined) {
	const statesByInstance = new Map<string, Set<BatchOperationItemState>>();
	for (const item of items ?? []) {
		const states = statesByInstance.get(item.processInstanceKey) ?? new Set<BatchOperationItemState>();
		states.add(item.state);
		statesByInstance.set(item.processInstanceKey, states);
	}

	return new Map(
		[...statesByInstance].map(([processInstanceKey, states]) => [
			processInstanceKey,
			OPERATION_STATE_ORDER.filter((state) => states.has(state)).join(', '),
		]),
	);
}

const InstancesTable: React.FC<Props> = ({search}) => {
	const {t} = useTranslation();
	const {
		processInstances,
		totalCount,
		hasMoreTotalItems,
		status,
		isFetching,
		isPlaceholderData,
		isFetchingPreviousPage,
		hasPreviousPage,
		fetchPreviousPage,
		isFetchingNextPage,
		hasNextPage,
		fetchNextPage,
	} = useProcessInstancesSearch(search);

	// The operation-state column only exists while the list is filtered by a batch operation —
	// outside that filter there is no operation for a row to report on. Truthiness, as in legacy,
	// so an empty key from a hand-edited URL behaves like no filter at all.
	const batchOperationKey = search.batchOperationKey || undefined;
	const isOperationStateColumnVisible = batchOperationKey !== undefined;
	const processInstanceKeys = processInstances.map((instance) => instance.processInstanceKey);
	const {
		data: operationItems,
		isLoading: isLoadingOperationItems,
		isError: isOperationItemsError,
	} = useOperationItemsForInstances(batchOperationKey, processInstanceKeys);
	const operationItemsByInstance = useMemo(() => getOperationStatesByInstance(operationItems), [operationItems]);
	const {data: activeOperationItems} = useActiveOperationItemsForInstances(processInstanceKeys);
	const activeOperationsByInstance = useMemo(() => {
		const byInstance = new Map<string, BatchOperationType[]>();
		activeOperationItems?.forEach((item) => {
			const existing = byInstance.get(item.processInstanceKey);
			if (existing === undefined) {
				byInstance.set(item.processInstanceKey, [item.operationType]);
			} else {
				existing.push(item.operationType);
			}
		});
		return byInstance;
	}, [activeOperationItems]);
	const isTenantColumnVisible =
		getClientConfig().deployment.isMultiTenancyEnabled && !isSpecificTenant(search.tenantId);
	const hasVersionTags = processInstances.some(({processDefinitionVersionTag}) => Boolean(processDefinitionVersionTag));
	const hasBusinessIds = processInstances.some(({businessId}) => Boolean(businessId));
	// End date is only meaningful once the list can contain finished instances; legacy disables
	// sorting on the column otherwise.
	const listHasFinishedInstances = search.canceled || search.completed;

	const columns = [
		{
			key: 'processName',
			sortKey: 'processDefinitionName',
			label: t('operate.processes.instancesTable.name'),
			render: (row: ProcessInstance) => (
				<ProcessName>
					<StateIcon state={getDisplayState(row)} size={20} />
					{row.processDefinitionName ?? row.processDefinitionId}
				</ProcessName>
			),
		},
		...(isOperationStateColumnVisible
			? [
					{
						key: 'instanceOperationState',
						label: t('operate.processes.instancesTable.operationState'),
						render: (row: ProcessInstance) =>
							isLoadingOperationItems ? (
								<SkeletonText width="6rem" />
							) : isOperationItemsError ? (
								t('operate.shared.errorMessage.message')
							) : (
								(operationItemsByInstance.get(row.processInstanceKey) ?? '--')
							),
					},
				]
			: []),
		{
			key: 'processInstanceKey',
			sortKey: 'processInstanceKey',
			label: t('operate.processes.instancesTable.processInstanceKey'),
			render: (row: ProcessInstance) => (
				<InstanceLink
					href={`/operate/processes/${row.processInstanceKey}`}
					title={t('operate.processes.instancesTable.viewInstance', {key: row.processInstanceKey})}
					aria-label={t('operate.processes.instancesTable.viewInstance', {key: row.processInstanceKey})}
				>
					{row.processInstanceKey}
				</InstanceLink>
			),
		},
		{
			key: 'processVersion',
			sortKey: 'processDefinitionVersion',
			label: t('operate.processes.instancesTable.version'),
			render: (row: ProcessInstance) => row.processDefinitionVersion,
		},
		...(hasVersionTags
			? [
					{
						key: 'versionTag',
						label: t('operate.processes.instancesTable.versionTag'),
						render: (row: ProcessInstance) => row.processDefinitionVersionTag ?? '--',
					},
				]
			: []),
		...(hasBusinessIds
			? [
					{
						key: 'businessId',
						sortKey: 'businessId',
						label: t('operate.processes.instancesTable.businessId'),
						render: (row: ProcessInstance) => row.businessId ?? '--',
					},
				]
			: []),
		...(isTenantColumnVisible
			? [
					{
						key: 'tenant',
						sortKey: 'tenantId',
						label: t('operate.processes.instancesTable.tenant'),
						render: (row: ProcessInstance) => row.tenantId,
					},
				]
			: []),
		{
			key: 'startDate',
			sortKey: 'startDate',
			isDefault: true,
			defaultOrder: 'desc' as const,
			label: t('operate.processes.instancesTable.startDate'),
			render: (row: ProcessInstance) => formatTimestamp(row.startDate),
		},
		{
			key: 'endDate',
			...(listHasFinishedInstances ? {sortKey: 'endDate'} : {}),
			label: t('operate.processes.instancesTable.endDate'),
			render: (row: ProcessInstance) => formatTimestamp(row.endDate),
		},
		{
			key: 'parentInstanceId',
			sortKey: 'parentProcessInstanceKey',
			label: t('operate.processes.instancesTable.parentProcessInstanceKey'),
			render: (row: ProcessInstance) =>
				row.parentProcessInstanceKey ? (
					<InstanceLink
						href={`/operate/processes/${row.parentProcessInstanceKey}`}
						title={t('operate.processes.instancesTable.viewParentInstance', {key: row.parentProcessInstanceKey})}
						aria-label={t('operate.processes.instancesTable.viewParentInstance', {
							key: row.parentProcessInstanceKey,
						})}
					>
						{row.parentProcessInstanceKey}
					</InstanceLink>
				) : (
					t('operate.processes.instancesTable.none')
				),
		},
		{
			key: 'operations',
			label: t('operate.processes.instancesTable.operations.header'),
			render: (row: ProcessInstance) => (
				<InstanceOperations
					processInstance={row}
					activeOperations={activeOperationsByInstance.get(row.processInstanceKey) ?? []}
				/>
			),
		},
	];

	const emptyState =
		status === 'error' ? (
			<ErrorMessage />
		) : (
			<EmptyMessage
				message={t('operate.processes.instancesTable.emptyMessage')}
				additionalInfo={
					search.active || search.incidents || search.completed || search.canceled
						? undefined
						: t('operate.processes.instancesTable.emptyAdditionalInfo')
				}
			/>
		);

	return (
		<InstancesTableContainer>
			<PanelHeader
				title={t('operate.processes.instancesTable.title')}
				count={totalCount}
				hasMoreTotalItems={hasMoreTotalItems}
			/>
			{isLoadingOperationItems && (
				<VisuallyHiddenStatus role="status" aria-live="polite">
					{t('operate.processes.instancesTable.operationStateLoading')}
				</VisuallyHiddenStatus>
			)}
			{status === 'pending' && isFetching ? (
				<DataTableSkeleton columnCount={columns.length} rowCount={5} showHeader={false} showToolbar={false} />
			) : (
				<PaginatedSortableTable<ProcessInstance>
					columns={columns}
					rows={processInstances}
					rowKey={(row) => row.processInstanceKey}
					// `isPlaceholderData` keeps the overlay to filter and sort changes, where the rows on
					// screen are stale. A background poll refetches the same key and must not dim the table.
					isFetching={isFetching && isPlaceholderData && !isFetchingPreviousPage && !isFetchingNextPage}
					emptyState={emptyState}
					pagination={{
						hasPreviousPage,
						hasNextPage,
						isFetchingPreviousPage,
						isFetchingNextPage,
						fetchPreviousPage,
						fetchNextPage,
					}}
					data-testid="process-instances-table"
				/>
			)}
		</InstancesTableContainer>
	);
};

export {InstancesTable};
