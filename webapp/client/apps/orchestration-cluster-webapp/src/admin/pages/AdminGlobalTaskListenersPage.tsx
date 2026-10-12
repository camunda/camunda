/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useMemo, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Pencil, Plus, Trash2} from '@camunda/design-system/icons';
import {
	Button,
	DataTable,
	Label,
	PageHeader,
	PageLayout,
	SearchInput,
	type DataTableColumn,
	type SortingConfig,
} from '@camunda/design-system';
import type {GlobalTaskListener} from '@camunda/camunda-api-zod-schemas/8.11';
import {AddGlobalTaskListenerModal} from '#/admin/modules/global-task-listeners/AddGlobalTaskListenerModal';
import {EditGlobalTaskListenerModal} from '#/admin/modules/global-task-listeners/EditGlobalTaskListenerModal';
import {DeleteGlobalTaskListenerModal} from '#/admin/modules/global-task-listeners/DeleteGlobalTaskListenerModal';
import {getEventTypeLabels} from '#/admin/modules/global-task-listeners/eventTypes';
import {
	DEFAULT_PAGE_SIZE,
	PAGE_SIZES,
	type GlobalTaskListenersSearch,
} from '#/admin/modules/global-task-listeners/searchSchema';

type SortingState = NonNullable<SortingConfig['sortState']>;

type ModalState =
	| {type: 'add'}
	| {type: 'edit'; globalTaskListener: GlobalTaskListener}
	| {type: 'delete'; globalTaskListener: GlobalTaskListener};

const SEARCH_DEBOUNCE = 500;
const EMPTY_CELL = '-';
const GLOBAL_TASK_LISTENERS_GUIDE_URL =
	'https://docs.camunda.io/docs/next/components/concepts/global-user-task-listeners/';

type AdminGlobalTaskListenersPageProps = {
	globalTaskListeners: GlobalTaskListener[];
	totalItems: number;
	search: GlobalTaskListenersSearch;
	onSearchChange: (next: Partial<GlobalTaskListenersSearch>) => void;
};

const AdminGlobalTaskListenersPage: React.FC<AdminGlobalTaskListenersPageProps> = ({
	globalTaskListeners,
	totalItems,
	search,
	onSearchChange,
}) => {
	const {t} = useTranslation();
	const [modal, setModal] = useState<ModalState | null>(null);
	const appliedSearchTerm = search.search ?? '';
	const [searchDraft, setSearchDraft] = useState(appliedSearchTerm);
	const [lastAppliedSearchTerm, setLastAppliedSearchTerm] = useState(appliedSearchTerm);

	// Back/forward navigation changes the applied term without touching the draft, which
	// would otherwise leave the input showing a term the table is no longer filtered by. Only
	// sync the draft in that case (draft still matches what was last applied) — otherwise a
	// slow round-trip for an earlier debounce would clobber input typed in the meantime.
	if (lastAppliedSearchTerm !== appliedSearchTerm) {
		if (searchDraft === lastAppliedSearchTerm) {
			setSearchDraft(appliedSearchTerm);
		}
		setLastAppliedSearchTerm(appliedSearchTerm);
	}

	useEffect(() => {
		if (searchDraft === appliedSearchTerm) {
			return;
		}
		const timeoutId = setTimeout(() => {
			onSearchChange({search: searchDraft === '' ? undefined : searchDraft, page: undefined});
		}, SEARCH_DEBOUNCE);
		return () => clearTimeout(timeoutId);
	}, [appliedSearchTerm, onSearchChange, searchDraft]);

	const columns = useMemo<DataTableColumn<GlobalTaskListener>[]>(
		() => [
			{accessorKey: 'id', header: t('admin.globalTaskListeners.globalTaskListenerIdColumn')},
			{accessorKey: 'type', header: t('admin.globalTaskListeners.listenerTypeColumn')},
			{
				id: 'eventTypes',
				header: t('admin.globalTaskListeners.eventTypeColumn'),
				enableSorting: false,
				cell: ({row}) => getEventTypeLabels(row.original.eventTypes, t),
			},
			{
				accessorKey: 'retries',
				header: t('admin.globalTaskListeners.retriesColumn'),
				enableSorting: false,
				cell: ({row}) => row.original.retries ?? EMPTY_CELL,
			},
			{
				accessorKey: 'afterNonGlobal',
				header: t('admin.globalTaskListeners.executionOrderColumn'),
				cell: ({row}) =>
					row.original.afterNonGlobal
						? t('admin.globalTaskListeners.executionOrderAfter')
						: t('admin.globalTaskListeners.executionOrderBefore'),
			},
			{
				accessorKey: 'priority',
				header: t('admin.globalTaskListeners.priorityColumn'),
				cell: ({row}) => row.original.priority ?? EMPTY_CELL,
			},
		],
		[t],
	);

	const sortState = useMemo<SortingState>(
		() => [{id: search.sortField ?? 'id', desc: search.sortOrder === 'DESC'}],
		[search.sortField, search.sortOrder],
	);

	const handleSortingChange = useCallback(
		(state: SortingState) => {
			const [sorted] = state;
			onSearchChange({
				sortField: sorted?.id as GlobalTaskListenersSearch['sortField'],
				sortOrder: sorted === undefined ? undefined : sorted.desc ? 'DESC' : 'ASC',
				page: undefined,
			});
		},
		[onSearchChange],
	);

	const handlePaginationChange = useCallback(
		({pageIndex, pageSize}: {pageIndex: number; pageSize: number}) => {
			const nextPageSize =
				pageSize === DEFAULT_PAGE_SIZE ? undefined : (pageSize as GlobalTaskListenersSearch['pageSize']);
			const hasPageSizeChanged = pageSize !== (search.pageSize ?? DEFAULT_PAGE_SIZE);
			onSearchChange({
				pageSize: nextPageSize,
				page: hasPageSizeChanged || pageIndex === 0 ? undefined : pageIndex + 1,
			});
		},
		[onSearchChange, search.pageSize],
	);

	const rowActions = useMemo(
		() => [
			{
				id: 'edit',
				label: t('admin.globalTaskListeners.editGlobalTaskListener'),
				icon: <Pencil aria-hidden />,
				onClick: (globalTaskListener: GlobalTaskListener) => setModal({type: 'edit', globalTaskListener}),
			},
			{
				id: 'delete',
				label: t('admin.globalTaskListeners.deleteGlobalTaskListener'),
				icon: <Trash2 aria-hidden />,
				variant: 'destructive' as const,
				onClick: (globalTaskListener: GlobalTaskListener) => setModal({type: 'delete', globalTaskListener}),
			},
		],
		[t],
	);

	const title = t('admin.headerNavItemGlobalTaskListeners');

	return (
		<PageLayout id="main-content" tabIndex={-1}>
			<div className="flex flex-col gap-6">
				<div className="flex flex-col gap-1">
					<PageHeader title={title} />
					<p className="text-sm leading-5 text-muted-foreground">
						{t('admin.globalTaskListeners.guideBody')}
						<Button asChild variant="link" className="h-auto p-0 align-baseline font-normal">
							<a href={GLOBAL_TASK_LISTENERS_GUIDE_URL} target="_blank" rel="noopener noreferrer">
								{t('admin.globalTaskListeners.guideLinkLabel')}
							</a>
						</Button>
					</p>
				</div>
				<div className="flex flex-col gap-4">
					<div className="flex items-end justify-between gap-4">
						<div className="flex flex-1 flex-col gap-2">
							<Label htmlFor="global-task-listeners-search" className="sr-only">
								{t('admin.globalTaskListeners.searchByListenerId')}
							</Label>
							<SearchInput
								id="global-task-listeners-search"
								className="max-w-sm min-w-48"
								placeholder={t('admin.globalTaskListeners.searchByListenerId')}
								value={searchDraft}
								onChange={(event) => setSearchDraft(event.target.value)}
								onClear={() => setSearchDraft('')}
							/>
						</div>
						<Button onClick={() => setModal({type: 'add'})}>
							<Plus aria-hidden />
							{t('admin.globalTaskListeners.addGlobalTaskListener')}
						</Button>
					</div>
					<DataTable
						aria-label={title}
						columns={columns}
						data={globalTaskListeners}
						getRowId={(globalTaskListener) => globalTaskListener.id}
						rowActions={rowActions}
						sorting={{manual: true, sortState, onSortingChange: handleSortingChange}}
						pagination={{
							manual: true,
							pageSizes: [...PAGE_SIZES],
							defaultPageSize: DEFAULT_PAGE_SIZE,
							pageSize: search.pageSize ?? DEFAULT_PAGE_SIZE,
							pageIndex: (search.page ?? 1) - 1,
							rowCount: totalItems,
							onPaginationChange: handlePaginationChange,
						}}
						emptyState={t('admin.globalTaskListeners.noGlobalTaskListeners')}
					/>
				</div>
			</div>
			<AddGlobalTaskListenerModal isOpen={modal?.type === 'add'} onClose={() => setModal(null)} />
			<EditGlobalTaskListenerModal
				globalTaskListener={modal?.type === 'edit' ? modal.globalTaskListener : null}
				onClose={() => setModal(null)}
			/>
			<DeleteGlobalTaskListenerModal
				globalTaskListener={modal?.type === 'delete' ? modal.globalTaskListener : null}
				onClose={() => setModal(null)}
			/>
		</PageLayout>
	);
};

export {AdminGlobalTaskListenersPage};
export type {AdminGlobalTaskListenersPageProps};
