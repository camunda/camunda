/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {createLink} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {Link} from '@camunda/design-system';
import type {DecisionInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {PanelHeader} from '#/operate/shared/PanelHeader/shadcn.components/PanelHeader';
import {PaginatedSortableTable} from '#/operate/shared/PaginatedSortableTable/shadcn.components/PaginatedSortableTable';
import {StateIcon} from '#/operate/shared/StateIcon/shadcn.components/StateIcon';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/shadcn.components/ErrorMessage';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {useInstancesSelection} from '#/operate/shared/hooks/useInstancesSelection';
import {isSpecificTenant} from '#/operate/shared/utils/isSpecificTenant';
import {formatEvaluationDate} from '#/operate/shared/utils/formatEvaluationDate';
import {useDecisionInstancesSearch} from '../useDecisionInstancesSearch';
import type {DecisionsSearch} from '../decisionsFilter';
import {Toolbar} from './Toolbar';

type Props = {
	search: DecisionsSearch;
};

const TABLE_CONTAINER_CLASSES = [
	'[&_[data-slot=table-container]]:rounded-none! [&_[data-slot=table-container]]:border-0! [&_[data-slot=table-container]]:shadow-none!',
	'[&_[data-slot=data-table]>div.rounded-xl]:rounded-none! [&_[data-slot=data-table]>div.rounded-xl]:border-0!',
].join(' ');

const InlineLink: React.FC<React.ComponentProps<'a'>> = (props) => <Link inline {...props} />;
const ProcessInstanceLink = createLink(InlineLink);
const DecisionInstanceLink = createLink(InlineLink);

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

	const isRefreshing = isFetching && !isFetchingPreviousPage && !isFetchingNextPage;
	const selection = useInstancesSelection(totalCount);

	const searchKey = JSON.stringify(search);
	const [appliedSearchKey, setAppliedSearchKey] = useState(searchKey);
	if (appliedSearchKey !== searchKey) {
		setAppliedSearchKey(searchKey);
		selection.reset();
	}

	const isTenantColumnVisible =
		getClientConfig().deployment.isMultiTenancyEnabled && !isSpecificTenant(search.tenantId);
	const hasBusinessIds = decisionInstances.some(({businessId}) => Boolean(businessId));

	const columns = [
		{
			key: 'decisionDefinitionName',
			label: t('operate.decisions.instancesTable.name'),
			render: (row: DecisionInstance) => (
				<div className="flex items-center gap-2">
					<StateIcon
						state={row.state}
						data-testid={`${row.state}-icon-${row.decisionEvaluationInstanceKey}`}
						size={20}
					/>
					{row.decisionDefinitionName}
				</div>
			),
		},
		{
			key: 'decisionEvaluationInstanceKey',
			label: t('operate.decisions.instancesTable.decisionInstanceKey'),
			render: (row: DecisionInstance) => (
				<DecisionInstanceLink
					to="/operate/decisions/$decisionInstanceId"
					params={{decisionInstanceId: row.decisionEvaluationInstanceKey}}
					title={t('operate.decisions.instancesTable.viewDecisionInstance', {
						key: row.decisionEvaluationInstanceKey,
					})}
				>
					{row.decisionEvaluationInstanceKey}
				</DecisionInstanceLink>
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
					<ProcessInstanceLink
						to="/operate/processes/$processInstanceId"
						params={{processInstanceId: row.processInstanceKey}}
						title={t('operate.decisions.instancesTable.viewProcessInstance', {key: row.processInstanceKey})}
						aria-label={t('operate.decisions.instancesTable.viewProcessInstance', {key: row.processInstanceKey})}
					>
						{row.processInstanceKey}
					</ProcessInstanceLink>
				) : (
					t('operate.decisions.instancesTable.none')
				),
		},
	];

	const emptyState =
		status === 'error' ? (
			<ErrorMessage />
		) : (
			<>
				<p>{t('operate.decisions.instancesTable.emptyMessage')}</p>
				{filter === undefined && <p>{t('operate.decisions.instancesTable.emptyAdditionalInfo')}</p>}
			</>
		);

	return (
		<section className="flex h-full flex-col">
			<PanelHeader
				title={t('operate.decisions.instancesTable.title')}
				count={totalCount}
				hasMoreTotalItems={hasMoreTotalItems}
			/>
			{filter !== undefined && (
				<Toolbar
					selectedCount={selection.selectedCount}
					includedIds={selection.includedIds}
					excludedIds={selection.excludedIds}
					filter={filter}
					onDeleted={selection.reset}
					onDiscard={selection.reset}
				/>
			)}
			<PaginatedSortableTable<DecisionInstance>
				columns={columns}
				rows={decisionInstances}
				rowKey={(row) => row.decisionEvaluationInstanceKey}
				ariaLabel={t('operate.decisions.instancesTable.tableLabel')}
				loadingPreviousPageLabel={t('operate.decisions.instancesTable.loadingPreviousPage')}
				loadingNextPageLabel={t('operate.decisions.instancesTable.loadingNextPage')}
				isFetching={isRefreshing}
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
					hasPreviousPage: hasPreviousPage && !isRefreshing,
					hasNextPage: hasNextPage && !isRefreshing,
					isFetchingPreviousPage,
					isFetchingNextPage,
					fetchPreviousPage,
					fetchNextPage,
				}}
				className={TABLE_CONTAINER_CLASSES}
				data-testid="decision-instances-table"
			/>
		</section>
	);
};

export {InstancesTable};
