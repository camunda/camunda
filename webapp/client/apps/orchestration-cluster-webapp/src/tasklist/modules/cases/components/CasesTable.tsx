/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {Link, useRouter} from '@tanstack/react-router';
import {Button, DataTable, EmptyState, type DataTableColumn, type SortingConfig} from '@camunda/design-system';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {DateLabel} from '#/tasklist/modules/available-tasks/components/DateLabel';
import {CaseStatusBadge} from '#/tasklist/modules/cases/components/CaseStatusBadge';
import {SORT_FIELDS, casesSearchDefaults, type CasesSearch} from '#/tasklist/modules/cases/searchSchema';
import {formatISODateTime} from '#/tasklist/modules/dates/formatDateRelative';
import {RouterRowLink} from '#/shared/router/RouterRowLink';

const PAGE_SIZE = 25;

type SortingState = NonNullable<SortingConfig['sortState']>;

function getCaseTitle(processInstance: ProcessInstance) {
	return processInstance.processDefinitionName ?? processInstance.processDefinitionId;
}

function getRowId(processInstance: ProcessInstance) {
	return processInstance.processInstanceKey;
}

const CasesEmptyState: React.FC<{search: CasesSearch}> = ({search}) => {
	const {t} = useTranslation();

	if (search.page > casesSearchDefaults.page) {
		return (
			<EmptyState
				heading={t('tasklist.casesPageOutOfRangeHeading')}
				headingLevel={2}
				description={t('tasklist.casesPageOutOfRangeDescription')}
				action={
					<Button asChild variant="link">
						<Link to="/tasklist/cases" search={{...search, page: casesSearchDefaults.page}}>
							{t('tasklist.casesGoToFirstPage')}
						</Link>
					</Button>
				}
				size="sm"
			/>
		);
	}

	if (search.search) {
		return (
			<EmptyState
				heading={t('tasklist.casesNoMatchingCasesHeading')}
				headingLevel={2}
				description={t('tasklist.casesNoMatchingCasesDescription', {search: search.search})}
				size="sm"
			/>
		);
	}

	return (
		<EmptyState
			heading={t('tasklist.casesNoOpenCasesHeading')}
			headingLevel={2}
			description={t('tasklist.casesNoOpenCasesDescription')}
			size="sm"
		/>
	);
};

type Props = {
	cases: ProcessInstance[];
	totalItems: number;
	search: CasesSearch;
	onSearchChange: (next: Partial<CasesSearch>) => void;
};

const CasesTable: React.FC<Props> = ({cases, totalItems, search, onSearchChange}) => {
	const {t} = useTranslation();
	const router = useRouter();

	const columns = useMemo<DataTableColumn<ProcessInstance>[]>(
		() => [
			{
				accessorKey: 'processDefinitionName',
				header: t('tasklist.casesColumnCase'),
				cell: ({row}) => <span className="font-medium">{getCaseTitle(row.original)}</span>,
			},
			{
				accessorKey: 'businessId',
				header: t('tasklist.casesColumnBusinessId'),
				cell: ({row}) => <span className="font-mono">{row.original.businessId ?? '–'}</span>,
			},
			{
				accessorKey: 'startDate',
				header: t('tasklist.casesColumnStarted'),
				sortDescFirst: true,
				cell: ({row}) => {
					const startDate = formatISODateTime(row.original.startDate);

					if (startDate === null) {
						return row.original.startDate;
					}

					return (
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
				cell: ({row}) => <CaseStatusBadge state={row.original.state} hasIncident={row.original.hasIncident} />,
				enableSorting: false,
			},
		],
		[t],
	);

	const handleSortingChange = useCallback(
		([sortedColumn]: SortingState) => {
			if (sortedColumn === undefined) {
				onSearchChange({sortField: undefined, sortOrder: undefined, page: 1});
				return;
			}

			onSearchChange({
				sortField: SORT_FIELDS.find((field) => field === sortedColumn.id),
				sortOrder: sortedColumn.desc ? 'desc' : 'asc',
				page: 1,
			});
		},
		[onSearchChange],
	);

	const handlePaginationChange = useCallback(
		({pageIndex}: {pageIndex: number}) => {
			onSearchChange({page: pageIndex + 1});
		},
		[onSearchChange],
	);

	const sorting = useMemo(
		() => ({
			manual: true,
			sortState: [{id: search.sortField, desc: search.sortOrder === 'desc'}],
			onSortingChange: handleSortingChange,
		}),
		[search.sortField, search.sortOrder, handleSortingChange],
	);

	const pagination = useMemo(
		() => ({
			manual: true,
			pageSize: PAGE_SIZE,
			pageIndex: search.page - 1,
			rowCount: totalItems,
			onPaginationChange: handlePaginationChange,
		}),
		[search.page, totalItems, handlePaginationChange],
	);

	const getRowHref = useCallback(
		(processInstance: ProcessInstance) =>
			router.buildLocation({
				to: '/tasklist/cases/$processInstanceKey',
				params: {processInstanceKey: processInstance.processInstanceKey},
			}).pathname,
		[router],
	);

	const getRowLabel = useCallback(
		(processInstance: ProcessInstance) => {
			const title = getCaseTitle(processInstance);

			if (processInstance.businessId === null) {
				return title;
			}

			return t('tasklist.casesCaseLinkLabel', {title, businessId: processInstance.businessId});
		},
		[t],
	);

	return (
		<DataTable
			aria-label={t('tasklist.casesTitle')}
			columns={columns}
			data={cases}
			getRowId={getRowId}
			rowHref={getRowHref}
			linkComponent={RouterRowLink}
			rowClickLabel={getRowLabel}
			sorting={sorting}
			pagination={pagination}
			emptyState={<CasesEmptyState search={search} />}
		/>
	);
};

export {CasesTable};
