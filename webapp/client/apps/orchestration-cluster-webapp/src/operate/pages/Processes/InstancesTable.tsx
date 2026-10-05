/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState, type Dispatch, type ReactNode, type SetStateAction} from 'react';
import {useMachine} from '@xstate/react';
import {useTranslation} from 'react-i18next';
import {useQuery, useQueryClient} from '@tanstack/react-query';
import {DataTableSkeleton, SkeletonText} from '@carbon/react';
import type {
	BatchOperationItem,
	BatchOperationItemState,
	BatchOperationType,
	ProcessDefinition,
	ProcessInstance,
	ProcessInstanceState,
	QueryBatchOperationItemsRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {PanelHeader} from '#/operate/shared/PanelHeader/PanelHeader';
import {PaginatedSortableTable} from '#/operate/shared/PaginatedSortableTable/PaginatedSortableTable';
import {StateIcon} from '#/operate/shared/StateIcon/StateIcon';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/EmptyMessage';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/ErrorMessage';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {isSpecificTenant} from '#/operate/shared/utils/isSpecificTenant';
import {formatTimestamp} from '#/operate/shared/utils/formatTimestamp';
import {useProcessInstancesSearch} from './useProcessInstancesSearch';
import {
	batchOperationItemsForInstancesQueryOptions,
	batchOperationItemsQueryOptions,
} from './batchOperationItems.queries';
import {InstanceOperations} from './InstanceOperations';
import type {ProcessesSearch} from './processesFilter';
import {InstancesTableContainer, ProcessName, InstanceLink, VisuallyHiddenStatus} from './styled';
import {useProcessInstancesSelection, type ProcessInstancesSelection} from './useProcessInstancesSelection';
import {ProcessesToolbar} from './ProcessesToolbar';
import {processBulkOperationMachine} from './processBulkOperationMachine';
import {MoveAction} from './MoveAction';
import {MigrateAction} from './MigrateAction';
import {getMigrationFilter, getMigrationStatisticsFilter, type MigrationScope} from './getMigrationFilter';
import {BatchModificationFooter} from './BatchModificationFooter';
import {useDiagramXml} from './useDiagramXml';
import {getActiveInstancesFilter} from './getActiveInstancesFilter';
import {getElementName} from './getElementName';
import type {ProcessDefinitionSelection} from './DiagramPanel';
import type {ProcessesMode, ProcessesNavigationBlocker} from './ProcessesLayout';
import type {BatchModificationScope} from './useBatchModificationStatistics';

type Props = {
	search: ProcessesSearch;
	mode?: ProcessesMode;
	navigationBlocker?: ProcessesNavigationBlocker;
	processDefinitionSelection?: ProcessDefinitionSelection;
	selectedTargetElementId?: string;
	onEnterMode?: (mode: ProcessesMode) => void;
	onExitMode?: () => void;
	onSelectionScopeChange?: Dispatch<SetStateAction<BatchModificationScope | null>>;
	onMigrationEnter?: (source: ProcessDefinition, scope: MigrationScope) => void;
	isActionMode?: boolean;
	renderActions?: (selection: ProcessInstancesSelection) => ReactNode;
};

function getDisplayState(instance: ProcessInstance): ProcessInstanceState | 'INCIDENT' {
	if (instance.state === 'SUSPENDED') {
		return 'SUSPENDED';
	}
	return instance.hasIncident ? 'INCIDENT' : instance.state;
}

const OPERATION_STATE_ORDER: BatchOperationItemState[] = ['FAILED', 'ACTIVE', 'CANCELED', 'SKIPPED', 'COMPLETED'];
const ACTIVE_ITEMS_REFETCH_INTERVAL_MS = 5000;

function getOperationStatesByInstance(items: BatchOperationItem[]) {
	const statesByInstance = new Map<string, Set<BatchOperationItemState>>();
	for (const item of items) {
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
		const operations = operationsByInstance.get(item.processInstanceKey) ?? [];
		operationsByInstance.set(item.processInstanceKey, [...operations, item.operationType]);
		return operationsByInstance;
	}, new Map<string, BatchOperationType[]>());
}

function selectOperationItems({items}: {items: BatchOperationItem[]}) {
	return {
		states: getOperationStatesByInstance(items),
		failures: items
			.filter((item) => item.state === 'FAILED')
			.reduce((messages, item) => {
				const errors = messages.get(item.processInstanceKey) ?? [];
				errors.push(item.errorMessage);
				messages.set(item.processInstanceKey, errors);
				return messages;
			}, new Map<string, (string | null)[]>()),
	};
}

const InstancesTable: React.FC<Props> = ({
	search,
	mode = 'list',
	navigationBlocker,
	processDefinitionSelection,
	selectedTargetElementId,
	onEnterMode,
	onExitMode,
	onSelectionScopeChange,
	onMigrationEnter,
	isActionMode,
	renderActions,
}) => {
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
	} = useProcessInstancesSearch(search, mode !== 'batch-modification');
	const selection = useProcessInstancesSelection(search, processInstances, totalCount, hasMoreTotalItems);
	const queryClient = useQueryClient();
	const [operation, send] = useMachine(processBulkOperationMachine, {input: {queryClient}});
	const [modification, sendModification] = useMachine(processBulkOperationMachine, {input: {queryClient}});
	const isSubmitting = operation.matches('submitting') || modification.matches('submitting');
	const {acceptedKey, acceptedIdentity} = operation.context;
	const {reset, filterIdentity} = selection;
	const selectionFilter = selection.getRequest('cancel').filter;
	const scopeIdentity = JSON.stringify({filter: selectionFilter, selectedCount: selection.selectedCount});
	useEffect(() => {
		onSelectionScopeChange?.((previous) =>
			previous !== null && JSON.stringify(previous) === scopeIdentity
				? previous
				: {filter: selectionFilter, selectedCount: selection.selectedCount},
		);
	}, [selectionFilter, selection.selectedCount, scopeIdentity, onSelectionScopeChange]);
	useEffect(() => {
		if (acceptedKey !== null && acceptedIdentity === filterIdentity) {
			reset();
		}
	}, [acceptedKey, acceptedIdentity, filterIdentity, reset]);
	const definitionKey =
		processDefinitionSelection?.kind === 'single-version'
			? processDefinitionSelection.definition.processDefinitionKey
			: undefined;
	const {data: diagramData} = useDiagramXml(definitionKey);
	const [modeDefinition, setModeDefinition] = useState<ProcessDefinition | null>(null);
	const isDefinitionChanged = modeDefinition === null || modeDefinition.processDefinitionKey !== definitionKey;
	const exitMode = () => {
		setModeDefinition(null);
		onExitMode?.();
	};
	const migrationFilter =
		processDefinitionSelection?.kind === 'single-version'
			? getMigrationFilter({
					search,
					includeIds: selection.mode === 'INCLUDE' ? selection.includedIds : [],
					excludeIds: selection.excludedIds,
					processDefinitionKey: processDefinitionSelection.definition.processDefinitionKey,
				})
			: null;
	const canSelect = status === 'success' && !isPlaceholderData && !isSubmitting;

	// The operation-state column only exists while the list is filtered by a batch operation —
	// outside that filter there is no operation for a row to report on. Truthiness, as in legacy,
	// so an empty key from a hand-edited URL behaves like no filter at all.
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
		data: operationItems,
		isLoading: isLoadingOperationItems,
		isError: isOperationItemsError,
	} = useQuery({
		...batchOperationItemsForInstancesQueryOptions(operationItemsRequestBody),
		enabled: isOperationStateColumnVisible && processInstanceKeys.length > 0,
		select: selectOperationItems,
		refetchInterval: (query) =>
			query.state.data?.items.some(({state}) => state === 'ACTIVE') ? ACTIVE_ITEMS_REFETCH_INTERVAL_MS : false,
	});
	const activeOperationItemsRequestBody = {
		filter: {
			processInstanceKey: {$in: processInstanceKeys},
			state: {$eq: 'ACTIVE'},
		},
		page: {limit: processInstanceKeys.length},
	} satisfies QueryBatchOperationItemsRequestBody;
	const {data: activeOperationsByInstance} = useQuery({
		...batchOperationItemsQueryOptions(activeOperationItemsRequestBody),
		enabled: processInstanceKeys.length > 0,
		select: ({items}) => getActiveOperationsByInstance(items),
		refetchInterval: (query) => ((query.state.data?.items.length ?? 0) > 0 ? ACTIVE_ITEMS_REFETCH_INTERVAL_MS : false),
	});
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
								(operationItems?.states.get(row.processInstanceKey) ?? '--')
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

	const emptyState =
		status === 'error' ? (
			<ErrorMessage />
		) : (
			<EmptyMessage
				message={t('operate.processes.instancesTable.emptyMessage')}
				additionalInfo={
					search.active || search.incidents || search.completed || search.canceled || search.suspended
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
			{status === 'success' && (
				<ProcessesToolbar
					key={filterIdentity}
					selection={selection}
					isSubmitting={isSubmitting}
					isActionMode={isActionMode || mode !== 'list'}
					additionalActions={
						<>
							{processDefinitionSelection && onEnterMode && (
								<MoveAction
									mode={mode}
									isSubmitting={isSubmitting}
									selection={selection}
									processDefinitionSelection={processDefinitionSelection}
									sourceElementId={search.elementId}
									onEnter={() => {
										setModeDefinition(
											processDefinitionSelection.kind === 'single-version'
												? processDefinitionSelection.definition
												: null,
										);
										onEnterMode('batch-modification');
									}}
								/>
							)}
							{processDefinitionSelection && onMigrationEnter && (
								<MigrateAction
									mode={mode}
									isSubmitting={isSubmitting}
									hasActiveScope={migrationFilter !== null}
									selection={selection}
									processDefinitionSelection={processDefinitionSelection}
									onEnter={() => {
										if (processDefinitionSelection.kind !== 'single-version' || migrationFilter === null) {
											return;
										}
										const migrationSelection = {
											search,
											includeIds: selection.mode === 'INCLUDE' ? selection.includedIds : [],
											excludeIds: selection.excludedIds,
										};
										onMigrationEnter(processDefinitionSelection.definition, {
											filter: migrationFilter,
											statisticsFilter: getMigrationStatisticsFilter(migrationSelection),
											selectedCount: selection.selectedCount,
											isCountTruncated: selection.isCountTruncated,
										});
									}}
								/>
							)}
							{renderActions?.(selection)}
						</>
					}
					onSubmit={(action) =>
						send({type: 'submit', action, body: selection.getRequest(action), filterIdentity: selection.filterIdentity})
					}
				/>
			)}
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
					expansionScope={batchOperationKey}
					rowOperationError={
						isOperationStateColumnVisible
							? (row) => {
									if (isLoadingOperationItems || isOperationItemsError) {
										return null;
									}
									const errors = operationItems?.failures.get(row.processInstanceKey);
									const message = errors
										?.map((error) => error ?? t('operate.processes.instancesTable.operationFailed'))
										.join('\n');
									return !message
										? null
										: {
												message,
												expandLabel: t('operate.processes.instancesTable.expandFailure', {
													key: row.processInstanceKey,
												}),
											};
								}
							: undefined
					}
					failureDetailsLabel={t('operate.processes.instancesTable.failureDetails')}
					selectionType="checkbox"
					selectAllLabel={t('operate.processes.toolbar.selectAll')}
					selectRowLabel={(key) => t('operate.processes.toolbar.selectRow', {key})}
					checkIsAllSelected={() => selection.isAllSelected}
					checkIsIndeterminate={() => !selection.isAllSelected && selection.selectedCount > 0}
					checkIsRowSelected={selection.isSelected}
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
			{mode === 'batch-modification' && processDefinitionSelection && onExitMode && navigationBlocker && (
				<BatchModificationFooter
					blocker={navigationBlocker}
					scope={{filter: selectionFilter, selectedCount: selection.selectedCount}}
					processDefinitionSelection={processDefinitionSelection}
					isDefinitionChanged={isDefinitionChanged}
					sourceElementId={search.elementId}
					targetElementId={selectedTargetElementId}
					sourceLabel={getElementName({businessObjects: diagramData?.businessObjects, elementId: search.elementId})}
					targetLabel={getElementName({
						businessObjects: diagramData?.businessObjects,
						elementId: selectedTargetElementId,
					})}
					onExit={exitMode}
					onSubmit={(moveInstruction) => {
						const activeFilter = getActiveInstancesFilter(selection.getRequest('cancel').filter);
						if (
							activeFilter === null ||
							selection.selectedCount < 1 ||
							modeDefinition === null ||
							isDefinitionChanged
						) {
							return;
						}
						exitMode();
						sendModification({
							type: 'submit',
							action: 'modify',
							body: {
								filter: {
									...activeFilter,
									tenantId: {$eq: modeDefinition.tenantId},
								},
								moveInstructions: [moveInstruction],
							},
							filterIdentity,
						});
					}}
				/>
			)}
		</InstancesTableContainer>
	);
};

export {InstancesTable};
