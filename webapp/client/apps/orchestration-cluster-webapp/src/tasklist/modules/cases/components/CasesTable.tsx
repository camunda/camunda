/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {useNavigate, useRouter} from '@tanstack/react-router';
import {DataTable, type DataTableColumn, type SortingConfig} from '@camunda/design-system';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {DateLabel} from '#/tasklist/modules/available-tasks/components/DateLabel';
import {CaseStatusBadge} from '#/tasklist/modules/cases/components/CaseStatusBadge';
import {PAGE_SIZES, casesSearchDefaults, type CasesSearch} from '#/tasklist/modules/cases/searchSchema';
import {formatISODateTime} from '#/tasklist/modules/dates/formatDateRelative';

type SortingState = NonNullable<SortingConfig['sortState']>;

const RowLink: React.FC<React.ComponentProps<'a'> & {href: string}> = ({href, onClick, ...props}) => {
	const router = useRouter();

	return (
		<a
			{...props}
			href={href}
			onClick={(event) => {
				onClick?.(event);

				if (!event.defaultPrevented && event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey) {
					event.preventDefault();
					router.navigate({href});
				}
			}}
		/>
	);
};

const SORT_FIELD_MAPPINGS = {
	processDefinitionName: 'processDefinitionName',
	businessId: 'businessId',
	startDate: 'startDate',
} as const satisfies Partial<Record<keyof ProcessInstance, CasesSearch['sortField']>>;

function isSortableColumn(columnId: string): columnId is keyof typeof SORT_FIELD_MAPPINGS {
	return Object.hasOwn(SORT_FIELD_MAPPINGS, columnId);
}

function getCaseName({processDefinitionName, processDefinitionId}: ProcessInstance): string {
	return processDefinitionName || processDefinitionId;
}

type Props = {
	cases: ProcessInstance[];
	totalItems: number;
	search: CasesSearch;
};

const CasesTable: React.FC<Props> = ({cases, totalItems, search}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();

	const columns = useMemo<DataTableColumn<ProcessInstance>[]>(
		() => [
			{
				accessorKey: 'processDefinitionName',
				header: t('tasklist.casesColumnCase'),
				cell: ({row}) => (
					<span className="truncate font-medium text-neutral-foreground-strong">{getCaseName(row.original)}</span>
				),
			},
			{
				accessorKey: 'businessId',
				header: t('tasklist.casesColumnBusinessId'),
				cell: ({row}) => (
					<span className="truncate font-mono text-xs text-neutral-foreground-subtle">
						{row.original.businessId ?? row.original.processInstanceKey}
					</span>
				),
			},
			{
				accessorKey: 'startDate',
				header: t('tasklist.casesColumnStarted'),
				cell: ({row}) => {
					const startDate = formatISODateTime(row.original.startDate);

					return startDate === null ? (
						row.original.startDate
					) : (
						<DateLabel
							date={startDate}
							relativeLabel={t('tasklist.casesStartedRelativeLabel')}
							absoluteLabel={t('tasklist.casesStartedAbsoluteLabel')}
						/>
					);
				},
			},
			{
				id: 'status',
				header: t('tasklist.casesColumnStatus'),
				cell: ({row}) => <CaseStatusBadge hasIncident={row.original.hasIncident} />,
				enableSorting: false,
			},
		],
		[t],
	);

	const sortState = useMemo<SortingState>(
		() => [{id: search.sortField, desc: search.sortOrder === 'desc'}],
		[search.sortField, search.sortOrder],
	);

	const handleSortingChange = useCallback(
		(state: SortingState) => {
			const [sortedColumn] = state;
			const columnId = sortedColumn?.id ?? '';
			const nextSort = isSortableColumn(columnId)
				? ({sortField: SORT_FIELD_MAPPINGS[columnId], sortOrder: sortedColumn?.desc ? 'desc' : 'asc'} as const)
				: {sortField: casesSearchDefaults.sortField, sortOrder: casesSearchDefaults.sortOrder};

			navigate({
				to: '.',
				search: (previousSearch) => ({...previousSearch, ...nextSort, page: casesSearchDefaults.page}),
			});
		},
		[navigate],
	);

	const handlePaginationChange = useCallback(
		({pageIndex, pageSize}: {pageIndex: number; pageSize: number}) => {
			const nextPageSize = PAGE_SIZES.find((size) => size === pageSize) ?? casesSearchDefaults.pageSize;

			navigate({
				to: '.',
				search: (previousSearch) => ({
					...previousSearch,
					pageSize: nextPageSize,
					page: nextPageSize === search.pageSize ? pageIndex + 1 : casesSearchDefaults.page,
				}),
			});
		},
		[navigate, search.pageSize],
	);

	const router = useRouter();
	const getRowHref = useCallback(
		({processInstanceKey}: ProcessInstance) =>
			router.buildLocation({to: '/tasklist/cases/$processInstanceKey', params: {processInstanceKey}}).href,
		[router],
	);

	return (
		<DataTable
			aria-label={t('tasklist.casesTitle')}
			columns={columns}
			data={cases}
			getRowId={(row) => row.processInstanceKey}
			rowHref={getRowHref}
			linkComponent={RowLink}
			rowClickLabel={(row) => t('tasklist.casesOpenCase', {name: getCaseName(row)})}
			sorting={{manual: true, sortState, onSortingChange: handleSortingChange}}
			pagination={{
				manual: true,
				pageSizes: [...PAGE_SIZES],
				defaultPageSize: casesSearchDefaults.pageSize,
				pageSize: search.pageSize,
				pageIndex: search.page - 1,
				rowCount: totalItems,
				onPaginationChange: handlePaginationChange,
			}}
			emptyState={
				search.search === undefined
					? t('tasklist.casesNoOpenCases')
					: t('tasklist.casesNoMatchingCases', {search: search.search})
			}
		/>
	);
};

export {CasesTable};
