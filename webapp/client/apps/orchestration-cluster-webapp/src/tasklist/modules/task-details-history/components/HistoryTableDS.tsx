/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// DS-only implementation of the task-history table (see HistoryTable.tsx for the
// flag dispatch). The Carbon original is built on `DataTable`'s render-props
// children API, which the carbon-compat adapter cannot back at all: its generic
// arity differs and it never calls the render function. So this is a full
// rewrite onto the DS declarative API rather than an import swap — the FLAG-tier
// case recorded in docs/migration/human-follow-up.md.
import {useCallback, useMemo} from 'react';
import {Button, DataTable, EmptyState, type DataTableColumn} from '@camunda/design-system';
import {InformationIcon} from '#/shared/design-system-compat';
import {Link, useNavigate} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.10';
import {formatHistoryDate} from '../formatHistoryDate';
import {getOperationTypeTranslationKey} from '../getOperationTypeTranslationKey';
import {
	getNextSortSearchValue,
	getSortParams,
	type TaskDetailsHistorySearch,
	type TaskDetailsHistorySortField,
} from '../sortUtils';
import styles from './HistoryTable.module.scss';

type HistoryRow = {
	id: string;
	operation: string;
	details: React.ReactNode;
	actor: string;
	date: string;
};

// The sort fields the API accepts are named differently from the column ids, so
// keep one explicit map rather than deriving it. Columns absent from this map
// are not sortable (`details` is presentational, `actions` holds the info link).
const COLUMN_ID_TO_SORT_FIELD = {
	operation: 'operationType',
	actor: 'actorId',
	date: 'timestamp',
} as const satisfies Record<string, TaskDetailsHistorySortField>;

type SortableColumnId = keyof typeof COLUMN_ID_TO_SORT_FIELD;

function getColumnIdForSortField(field: TaskDetailsHistorySortField): SortableColumnId {
	const entry = Object.entries(COLUMN_ID_TO_SORT_FIELD).find(([, sortField]) => sortField === field);

	// Every field in the union is present in the map, so this is exhaustive.
	return (entry?.[0] ?? 'date') as SortableColumnId;
}

function isSortableColumnId(id: string): id is SortableColumnId {
	return id in COLUMN_ID_TO_SORT_FIELD;
}

type Props = {
	userTaskKey: string;
	auditLogs: AuditLog[];
	search: TaskDetailsHistorySearch;
};

const HistoryTableDS: React.FC<Props> = ({userTaskKey, auditLogs, search}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const sort = getSortParams(search);

	const data = useMemo<HistoryRow[]>(
		() =>
			auditLogs.map((log) => ({
				id: log.auditLogKey,
				operation: t(getOperationTypeTranslationKey(log.operationType)),
				details:
					log.operationType === 'ASSIGN' ? (
						<>
							<div className={`${styles.detailsLabel} ${styles.detailsLabelDS}`}>
								{t('tasklist.taskDetailsHistoryPropertyAssignee')}
							</div>
							{log.relatedEntityKey}
						</>
					) : (
						'-'
					),
				actor: log.actorId,
				date: formatHistoryDate(log.timestamp),
			})),
		[auditLogs, t],
	);

	const columns = useMemo<DataTableColumn<HistoryRow>[]>(
		() => [
			{
				id: 'operation',
				accessorKey: 'operation',
				header: t('tasklist.taskDetailsHistoryOperationHeader'),
			},
			{
				id: 'details',
				accessorKey: 'details',
				header: t('tasklist.taskDetailsHistoryDetailsHeader'),
				enableSorting: false,
				// `details` is a ReactNode (the ASSIGN case renders a label above the
				// assignee), so render it directly instead of letting TanStack coerce
				// the accessor value to text.
				cell: ({row}) => row.original.details,
			},
			{
				id: 'actor',
				accessorKey: 'actor',
				header: t('tasklist.taskDetailsHistoryActorHeader'),
			},
			{
				id: 'date',
				accessorKey: 'date',
				header: t('tasklist.taskDetailsHistoryDateHeader'),
			},
			{
				id: 'actions',
				header: '',
				enableSorting: false,
				// asChild so the row action keeps its <a> semantics — it navigates to the
				// audit-log detail route, so it must stay a link rather than a button.
				cell: ({row}) => (
					<Button asChild variant="ghost" size="icon-sm">
						<Link
							to="/tasklist/$userTaskKey/history/$auditLogKey"
							params={{userTaskKey, auditLogKey: row.original.id}}
							search={search}
							aria-label={t('tasklist.taskDetailsHistoryDetailsLabel')}
							title={t('tasklist.taskDetailsHistoryDetailsLabel')}
						>
							<InformationIcon />
						</Link>
					</Button>
				),
			},
		],
		[t, userTaskKey, search],
	);

	// Sorting is server-driven through the URL: the route reads `search.sort` and
	// refetches. So the table runs in manual mode — it renders the sort affordance
	// and reports intent, but never reorders `data` itself.
	const sortState = useMemo(
		() => [{id: getColumnIdForSortField(sort.sortBy), desc: sort.sortOrder === 'desc'}],
		[sort.sortBy, sort.sortOrder],
	);

	const handleSortingChange = useCallback(
		(nextState: {id: string; desc: boolean}[]) => {
			const next = nextState[0];

			if (next === undefined || !isSortableColumnId(next.id)) {
				return;
			}

			const targetOrder = next.desc ? 'desc' : 'asc';

			void navigate({
				to: '.',
				search: (previous) => ({
					...previous,
					// getNextSortSearchValue flips whatever order it is handed, so pass
					// the opposite of the order we want to end up on.
					sort: getNextSortSearchValue(
						COLUMN_ID_TO_SORT_FIELD[next.id as SortableColumnId],
						targetOrder === 'asc' ? 'desc' : 'asc',
					),
				}),
			});
		},
		[navigate],
	);

	return (
		<DataTable<HistoryRow>
			columns={columns}
			data={data}
			getRowId={(row) => row.id}
			size="md"
			sorting={{
				manual: true,
				sortState,
				onSortingChange: handleSortingChange,
			}}
			className={styles.tableContainer}
			// DS convention (see VariableEditorDS.tsx): the empty state lives
			// inside the table body as a single full-width row, not as a
			// separate tile replacing the table — per explicit request. Same
			// pattern, this table's own built-in `emptyState` slot instead of a
			// hand-built TableRow/TableCell.
			emptyState={<EmptyState size="sm" heading={t('tasklist.taskDetailsHistoryEmptyMessage')} />}
		/>
	);
};

export {HistoryTableDS};
