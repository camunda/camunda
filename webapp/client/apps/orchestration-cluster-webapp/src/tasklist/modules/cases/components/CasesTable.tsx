/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {useNavigate} from '@tanstack/react-router';
import {DataTable, type DataTableColumn, type SortingConfig} from '@camunda/design-system';
import type {ElementInstanceInspection, ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {DateLabel} from '#/tasklist/modules/available-tasks/components/DateLabel';
import {CaseCell} from '#/tasklist/modules/cases/components/CaseCell';
import {CaseStatusBadge} from '#/tasklist/modules/cases/components/CaseStatusBadge';
import {WaitStateList} from '#/tasklist/modules/cases/components/WaitStateList';
import {groupWaitStatesByCase} from '#/tasklist/modules/cases/groupWaitStatesByCase';
import {mapProcessInstanceToCase, type Case} from '#/tasklist/modules/cases/mapProcessInstanceToCase';
import {PAGE_SIZES, casesSearchDefaults, type CasesSearch} from '#/tasklist/modules/cases/searchSchema';
import {formatISODateTime} from '#/tasklist/modules/dates/formatDateRelative';

type SortingState = NonNullable<SortingConfig['sortState']>;

const EMPTY_WAIT_STATES: ElementInstanceInspection[] = [];

const SORT_FIELD_MAPPINGS = {
	caseId: 'businessId',
	startDate: 'startDate',
} as const satisfies Partial<Record<keyof Case, CasesSearch['sortField']>>;

const COLUMN_MAPPINGS = {
	businessId: 'caseId',
	startDate: 'startDate',
} as const satisfies Record<CasesSearch['sortField'], keyof typeof SORT_FIELD_MAPPINGS>;

function isSortableColumn(columnId: string): columnId is keyof typeof SORT_FIELD_MAPPINGS {
	return Object.hasOwn(SORT_FIELD_MAPPINGS, columnId);
}

type Props = {
	cases: ProcessInstance[];
	waitStates: ElementInstanceInspection[];
	totalItems: number;
	search: CasesSearch;
};

const CasesTable: React.FC<Props> = ({cases, waitStates, totalItems, search}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const rows = useMemo(() => cases.map(mapProcessInstanceToCase), [cases]);
	const waitStatesByCase = useMemo(() => groupWaitStatesByCase(waitStates), [waitStates]);

	const columns = useMemo<DataTableColumn<Case>[]>(
		() => [
			{
				accessorKey: 'caseId',
				header: t('tasklist.casesColumnCase'),
				cell: ({row}) => <CaseCell title={row.original.title} caseId={row.original.caseId} />,
			},
			{
				id: 'waitStates',
				header: t('tasklist.casesColumnWaitingFor'),
				cell: ({row}) => (
					<WaitStateList waitStates={waitStatesByCase.get(row.original.processInstanceKey) ?? EMPTY_WAIT_STATES} />
				),
				enableSorting: false,
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
				cell: ({row}) => <CaseStatusBadge status={row.original.status} />,
				enableSorting: false,
			},
		],
		[t, waitStatesByCase],
	);

	const sortState = useMemo<SortingState>(
		() => [{id: COLUMN_MAPPINGS[search.sortField], desc: search.sortOrder === 'desc'}],
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
				search: (previousSearch) => ({
					...previousSearch,
					...nextSort,
					page: casesSearchDefaults.page,
				}),
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

	const handleRowClick = useCallback(
		({processInstanceKey}: Case) => {
			navigate({to: '/tasklist/cases/$processInstanceKey', params: {processInstanceKey}});
		},
		[navigate],
	);

	return (
		<DataTable
			// The design system has no pagination placement option, so the pagination bar is reordered visually.
			className="[&>[data-slot=data-table-pagination]]:order-first"
			aria-label={t('tasklist.casesTitle')}
			columns={columns}
			data={rows}
			getRowId={(row) => row.processInstanceKey}
			onRowClick={handleRowClick}
			rowClickLabel={({caseId}) => t('tasklist.casesOpenCase', {caseId})}
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
