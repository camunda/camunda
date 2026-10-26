/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {createLink, useNavigate} from '@tanstack/react-router';
import {useQuery, useQueryClient} from '@tanstack/react-query';
import {useMachine} from '@xstate/react';
import {useTranslation} from 'react-i18next';
import {Link, Skeleton} from '@camunda/design-system';
import type {
	BatchOperationItem,
	BatchOperationItemState,
	BatchOperationType,
	ProcessInstance,
	ProcessInstanceState,
	QueryBatchOperationItemsRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {PanelHeader} from '#/operate/shared/PanelHeader/shadcn.components/PanelHeader';
import {PaginatedSortableTable} from '#/operate/shared/PaginatedSortableTable/shadcn.components/PaginatedSortableTable';
import {StateIcon} from '#/operate/shared/StateIcon/shadcn.components/StateIcon';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/shadcn.components/ErrorMessage';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {isSpecificTenant} from '#/operate/shared/utils/isSpecificTenant';
import {formatTimestamp} from '#/operate/shared/utils/formatTimestamp';
import {useProcessInstancesSearch} from '../useProcessInstancesSearch';
import {useProcessInstancesSelection} from '../useProcessInstancesSelection';
import {batchOperationItemsForInstancesQueryOptions} from '../batchOperationItems.queries';
import type {ProcessesSearch} from '../processesFilter';
import {processBulkOperationMachine} from '../processBulkOperationMachine';
import {InstanceOperations} from './InstanceOperations';
import {ProcessBulkActionDialog} from './ProcessBulkActionDialog';
import {useProcessBulkActions} from './useProcessBulkActions';

type Props = {
	search: ProcessesSearch;
};

const TABLE_CONTAINER_CLASSES = [
	'[&_[data-slot=table-container]]:rounded-none! [&_[data-slot=table-container]]:border-0! [&_[data-slot=table-container]]:shadow-none!',
	'[&_[data-slot=data-table]>div.rounded-xl]:rounded-none! [&_[data-slot=data-table]>div.rounded-xl]:border-0!',
	// Row hover and the ghost icon button hover share a background, so the button needs a stronger one.
	'[&_[data-slot=icon-button]:hover:not(:disabled)]:bg-[var(--neutral-background-strong)]!',
].join(' ');

const OPERATION_STATE_ORDER: BatchOperationItemState[] = ['FAILED', 'ACTIVE', 'CANCELED', 'SKIPPED', 'COMPLETED'];
const ACTIVE_ITEMS_REFETCH_INTERVAL_MS = 5000;

const TableLink: React.FC<React.ComponentProps<'a'>> = (props) => <Link {...props} />;
const InstanceLink = createLink(TableLink);

const STATE_LABEL_KEYS = {
	ACTIVE: 'operate.processes.filters.active',
	INCIDENT: 'operate.processes.filters.incidents',
	SUSPENDED: 'operate.processes.filters.suspended',
	COMPLETED: 'operate.processes.filters.completed',
	TERMINATED: 'operate.processes.filters.canceled',
} as const;

function getDisplayState(instance: ProcessInstance): ProcessInstanceState | 'INCIDENT' {
	if (instance.state === 'SUSPENDED') {
		return 'SUSPENDED';
	}
	return instance.hasIncident ? 'INCIDENT' : instance.state;
}

function getOperationStatesByInstance(items: BatchOperationItem[]) {
	const statesByInstance = new Map<string, Set<BatchOperationItemState>>();
	for (const item of items) {
		if (item.processInstanceKey === null) {
			continue;
		}
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

function getActiveOperationsByInstance(items: BatchOperationItem[]) {
	return items.reduce((operationsByInstance, item) => {
		if (item.processInstanceKey === null) {
			return operationsByInstance;
		}
		const operations = operationsByInstance.get(item.processInstanceKey) ?? [];
		operationsByInstance.set(item.processInstanceKey, [...operations, item.operationType]);
		return operationsByInstance;
	}, new Map<string, BatchOperationType[]>());
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
	// The overlay covers the first load and filter or sort changes, where the rows on screen are
	// missing or stale. A background poll refetches the same key and must not dim the table.
	const isRefreshing =
		isFetching && (status === 'pending' || isPlaceholderData) && !isFetchingPreviousPage && !isFetchingNextPage;
	const selection = useProcessInstancesSelection(search, processInstances, totalCount, hasMoreTotalItems);
	const queryClient = useQueryClient();
	const navigate = useNavigate();
	const [operation, send] = useMachine(processBulkOperationMachine, {input: {queryClient, navigate}});
	const isSubmitting = operation.matches('submitting');
	const {acceptedKey, acceptedIdentity} = operation.context;
	const {reset, filterIdentity} = selection;
	useEffect(() => {
		if (acceptedKey !== null && acceptedIdentity === filterIdentity) {
			reset();
		}
	}, [acceptedKey, acceptedIdentity, filterIdentity, reset]);
	const bulkActions = useProcessBulkActions({
		selection,
		isSubmitting,
		onSubmit: (action) =>
			send({type: 'submit', action, body: selection.getRequest(action), filterIdentity: selection.filterIdentity}),
	});
	const canSelect = status === 'success' && !isPlaceholderData && !isSubmitting;

	// Truthiness, as on Carbon, so an empty key from a hand-edited URL behaves like no filter at all.
	const batchOperationKey = search.batchOperationKey || undefined;
	const isOperationStateColumnVisible = batchOperationKey !== undefined;
	const processInstanceKeys = processInstances.map((instance) => instance.processInstanceKey);
	const operationItemsRequestBody = {
		filter: {
			batchOperationKey: batchOperationKey === undefined ? undefined : {$eq: batchOperationKey},
			processInstanceKey: {$in: processInstanceKeys},
		},
		page: {limit: processInstanceKeys.length},
	} satisfies QueryBatchOperationItemsRequestBody;
	const {
		data: operationStates,
		isLoading: isLoadingOperationItems,
		isError: isOperationItemsError,
	} = useQuery({
		...batchOperationItemsForInstancesQueryOptions(operationItemsRequestBody),
		enabled: isOperationStateColumnVisible && processInstanceKeys.length > 0,
		select: ({items}) => getOperationStatesByInstance(items),
		refetchInterval: (query) =>
			query.state.data?.items.some(({state}) => state === 'ACTIVE') ? ACTIVE_ITEMS_REFETCH_INTERVAL_MS : false,
	});
	const activeOperationItemsRequestBody = {
		filter: {
			processInstanceKey: {$in: processInstanceKeys},
			state: {$eq: 'ACTIVE'},
		},
	} satisfies QueryBatchOperationItemsRequestBody;
	const {data: activeOperationsByInstance} = useQuery({
		...batchOperationItemsForInstancesQueryOptions(activeOperationItemsRequestBody),
		enabled: processInstanceKeys.length > 0,
		select: ({items}) => getActiveOperationsByInstance(items),
		refetchInterval: ACTIVE_ITEMS_REFETCH_INTERVAL_MS,
	});

	const isTenantColumnVisible =
		getClientConfig().deployment.isMultiTenancyEnabled && !isSpecificTenant(search.tenantId);
	const hasVersionTags = processInstances.some(({processDefinitionVersionTag}) => Boolean(processDefinitionVersionTag));
	const hasBusinessIds = processInstances.some(({businessId}) => Boolean(businessId));
	// End date is only meaningful once the list can contain finished instances.
	const listHasFinishedInstances = search.canceled || search.completed;

	const columns = [
		{
			key: 'processName',
			sortKey: 'processDefinitionName',
			label: t('operate.processes.instancesTable.name'),
			render: (row: ProcessInstance) => (
				<div className="flex items-center gap-2">
					<StateIcon state={getDisplayState(row)} size={20} />
					<span className="sr-only">{t(STATE_LABEL_KEYS[getDisplayState(row)])}</span>
					<span>{row.processDefinitionName ?? row.processDefinitionId}</span>
				</div>
			),
		},
		...(isOperationStateColumnVisible
			? [
					{
						key: 'instanceOperationState',
						label: t('operate.processes.instancesTable.operationState'),
						render: (row: ProcessInstance) =>
							isLoadingOperationItems ? (
								<Skeleton className="h-4 w-24" />
							) : isOperationItemsError ? (
								t('operate.shared.errorMessage.message')
							) : (
								(operationStates?.get(row.processInstanceKey) ?? '--')
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
					to="/operate/processes/$processInstanceId"
					params={{processInstanceId: row.processInstanceKey}}
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
			defaultOrder: 'DESC' as const,
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
						to="/operate/processes/$processInstanceId"
						params={{processInstanceId: row.parentProcessInstanceKey}}
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
					activeOperations={activeOperationsByInstance?.get(row.processInstanceKey) ?? []}
				/>
			),
		},
	];

	const hasStateFilter = search.active || search.incidents || search.completed || search.canceled || search.suspended;
	const emptyState =
		status === 'error' ? (
			<ErrorMessage />
		) : (
			<>
				<p>{t('operate.processes.instancesTable.emptyMessage')}</p>
				{!hasStateFilter && <p>{t('operate.processes.instancesTable.emptyAdditionalInfo')}</p>}
			</>
		);

	return (
		<section className="flex h-full flex-col" aria-label={t('operate.processes.instancesTable.panelLabel')}>
			{isLoadingOperationItems && (
				<span className="sr-only" role="status" aria-live="polite">
					{t('operate.processes.instancesTable.operationStateLoading')}
				</span>
			)}
			<PanelHeader
				title={t('operate.processes.instancesTable.title')}
				count={totalCount}
				hasMoreTotalItems={hasMoreTotalItems}
			/>
			<PaginatedSortableTable<ProcessInstance>
				columns={columns}
				rows={processInstances}
				rowKey={(row) => row.processInstanceKey}
				ariaLabel={t('operate.processes.instancesTable.tableLabel')}
				loadingPreviousPageLabel={t('operate.processes.instancesTable.loadingPreviousPage')}
				loadingNextPageLabel={t('operate.processes.instancesTable.loadingNextPage')}
				isFetching={isRefreshing}
				emptyState={emptyState}
				selectionType="checkbox"
				selectAllLabel={t('operate.processes.toolbar.selectAll')}
				selectRowLabel={(key) => t('operate.processes.toolbar.selectRow', {key})}
				checkIsAllSelected={() => selection.isAllSelected}
				checkIsIndeterminate={() => !selection.isAllSelected && selection.selectedCount > 0}
				checkIsRowSelected={selection.isSelected}
				selectedCount={selection.selectedCount}
				onSelectAll={() => {
					if (canSelect && totalCount > 0) {
						selection.selectAll();
					}
				}}
				onSelect={(key) => {
					if (canSelect) {
						selection.toggle(key);
					}
				}}
				onReset={selection.reset}
				batchActions={bulkActions.batchActions}
				selectionCountLabel={bulkActions.selectionCountLabel}
				batchActionsLabel={t('operate.processes.toolbar.bulkActionsLabel')}
				batchActionsCancelLabel={t('operate.processes.toolbar.discard')}
				pagination={{
					hasPreviousPage: hasPreviousPage && !isRefreshing,
					hasNextPage: hasNextPage && !isRefreshing,
					isFetchingPreviousPage,
					isFetchingNextPage,
					fetchPreviousPage,
					fetchNextPage,
				}}
				className={TABLE_CONTAINER_CLASSES}
				data-testid="process-instances-table"
			/>
			<ProcessBulkActionDialog {...bulkActions.dialogProps} />
		</section>
	);
};

export {InstancesTable};
