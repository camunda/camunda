/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {PanelHeader} from '#/operate/shared/PanelHeader/PanelHeader';
import {PaginatedSortableTable} from '#/operate/shared/PaginatedSortableTable/PaginatedSortableTable';
import {StateIcon} from '#/operate/shared/StateIcon/StateIcon';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/EmptyMessage';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/ErrorMessage';
import {tracking} from '#/shared/tracking';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {useInstancesSelection} from '#/operate/shared/hooks/useInstancesSelection';
import {useDecisionInstancesSearch} from './useDecisionInstancesSearch';
import {isSpecificTenant, type DecisionsSearch} from './decisionsFilter';
import {formatEvaluationDate} from '#/operate/shared/utils/formatEvaluationDate';
import {Toolbar} from './Toolbar';
import {Container, DecisionName, InstanceLink} from './styled';
import type {DecisionInstance} from '@camunda/camunda-api-zod-schemas/8.10';

type Props = {
	search: DecisionsSearch;
};

const InstancesTable: React.FC<Props> = ({search}) => {
	const {t} = useTranslation();
	const {
		decisionInstances,
		totalCount,
		hasMoreTotalItems,
		status,
		isFetching,
		isFetchingPreviousPage,
		hasPreviousPage,
		fetchPreviousPage,
		isFetchingNextPage,
		hasNextPage,
		fetchNextPage,
		filter,
	} = useDecisionInstancesSearch(search);

	const selection = useInstancesSelection(totalCount);

	const searchKey = useMemo(() => JSON.stringify(search), [search]);
	useEffect(() => {
		selection.reset();
		// eslint-disable-next-line react-hooks/exhaustive-deps -- reset selection only when the filter set changes, mirroring legacy's `filtersJSON` effect key
	}, [searchKey]);

	const isTenantColumnVisible =
		getClientConfig().deployment.isMultiTenancyEnabled && !isSpecificTenant(search.tenantId);
	const hasBusinessIds = decisionInstances.some(({businessId}) => Boolean(businessId));

	const columns = [
		{
			key: 'decisionDefinitionName',
			label: t('operate.decisions.instancesTable.name'),
			render: (row: DecisionInstance) => (
				<DecisionName>
					<StateIcon
						state={row.state}
						data-testid={`${row.state}-icon-${row.decisionEvaluationInstanceKey}`}
						size={20}
					/>
					{row.decisionDefinitionName}
				</DecisionName>
			),
		},
		{
			key: 'decisionEvaluationInstanceKey',
			label: t('operate.decisions.instancesTable.decisionInstanceKey'),
			render: (row: DecisionInstance) => (
				<InstanceLink
					href={`/operate/decisions/${row.decisionEvaluationInstanceKey}`}
					onClick={() => {
						tracking.track({eventName: 'operate:navigation', link: 'decision-instances-parent-process-details'});
					}}
					title={t('operate.decisions.instancesTable.viewDecisionInstance', {
						key: row.decisionEvaluationInstanceKey,
					})}
				>
					{row.decisionEvaluationInstanceKey}
				</InstanceLink>
			),
		},
		{
			key: 'decisionDefinitionVersion',
			label: t('operate.decisions.instancesTable.version'),
			render: (row: DecisionInstance) => row.decisionDefinitionVersion,
		},
		...(hasBusinessIds
			? [
					{
						key: 'businessId',
						sortKey: 'businessId',
						label: t('operate.decisions.instancesTable.businessId'),
						render: (row: DecisionInstance) => row.businessId ?? '--',
					},
				]
			: []),
		...(isTenantColumnVisible
			? [
					{
						key: 'tenantId',
						label: t('operate.decisions.instancesTable.tenant'),
						render: (row: DecisionInstance) => row.tenantId,
					},
				]
			: []),
		{
			key: 'evaluationDate',
			sortKey: 'evaluationDate',
			isDefault: true,
			defaultOrder: 'desc' as const,
			label: t('operate.decisions.instancesTable.evaluationDate'),
			render: (row: DecisionInstance) => formatEvaluationDate(row.evaluationDate),
		},
		{
			key: 'processInstanceKey',
			label: t('operate.decisions.instancesTable.processInstanceKey'),
			render: (row: DecisionInstance) =>
				row.processInstanceKey ? (
					<InstanceLink
						href={`/operate/processes/${row.processInstanceKey}`}
						onClick={() => {
							tracking.track({eventName: 'operate:navigation', link: 'decision-instances-parent-process-details'});
						}}
						title={t('operate.decisions.instancesTable.viewProcessInstance', {key: row.processInstanceKey})}
					>
						{row.processInstanceKey}
					</InstanceLink>
				) : (
					t('operate.decisions.instancesTable.none')
				),
		},
	];

	const emptyState =
		status === 'error' ? (
			<ErrorMessage />
		) : (
			<EmptyMessage
				message={t('operate.decisions.instancesTable.emptyMessage')}
				additionalInfo={filter === undefined ? t('operate.decisions.instancesTable.emptyAdditionalInfo') : undefined}
			/>
		);

	return (
		<Container>
			<PanelHeader
				title={t('operate.decisions.instancesTable.title')}
				count={totalCount}
				hasMoreTotalItems={hasMoreTotalItems}
			/>
			<Toolbar
				selectedCount={selection.selectedCount}
				includedIds={selection.includedIds}
				excludedIds={selection.excludedIds}
				filter={filter ?? {}}
				onDeleted={selection.reset}
				onDiscard={selection.reset}
			/>
			<PaginatedSortableTable<DecisionInstance>
				columns={columns}
				rows={decisionInstances}
				rowKey={(row) => row.decisionEvaluationInstanceKey}
				isFetching={isFetching && !isFetchingPreviousPage && !isFetchingNextPage}
				emptyState={emptyState}
				selectionType="checkbox"
				selectAllLabel={t('operate.decisions.instancesTable.selectAll')}
				selectRowLabel={(id) => t('operate.decisions.instancesTable.selectRow', {key: id})}
				checkIsAllSelected={() => selection.isAllSelected}
				checkIsIndeterminate={() => selection.isIndeterminate}
				checkIsRowSelected={selection.isRowSelected}
				onSelectAll={selection.selectAll}
				onSelect={selection.select}
				pagination={{
					hasPreviousPage,
					hasNextPage,
					isFetchingPreviousPage,
					isFetchingNextPage,
					fetchPreviousPage,
					fetchNextPage,
				}}
				data-testid="decision-instances-table"
			/>
		</Container>
	);
};

export {InstancesTable};
