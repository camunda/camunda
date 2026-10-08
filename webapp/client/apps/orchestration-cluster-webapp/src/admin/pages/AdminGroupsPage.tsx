/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useMemo, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Pencil, Trash2} from '@camunda/design-system/icons';
import {
	Button,
	DataTable,
	Label,
	PageHeader,
	PageLayout,
	SearchInput,
	type DataTableColumn,
	type DataTableRowAction,
	type SortingConfig,
} from '@camunda/design-system';
import type {Group} from '@camunda/camunda-api-zod-schemas/8.11';
import {AddGroupModal} from '#/admin/modules/groups/AddGroupModal';
import {EditGroupModal} from '#/admin/modules/groups/EditGroupModal';
import {DeleteGroupModal} from '#/admin/modules/groups/DeleteGroupModal';
import {DEFAULT_PAGE_SIZE, PAGE_SIZES, type GroupsSearch} from '#/admin/modules/groups/searchSchema';

type SortingState = NonNullable<SortingConfig['sortState']>;

const SEARCH_DEBOUNCE = 500;
const GROUPS_GUIDE_URL = 'https://docs.camunda.io/docs/next/components/admin/group/';

type ModalState = {type: 'create'} | {type: 'edit'; group: Group} | {type: 'delete'; group: Group} | null;

type AdminGroupsPageProps = {
	groups: Group[];
	totalItems: number;
	search: GroupsSearch;
	onSearchChange: (next: Partial<GroupsSearch>) => void;
	onOpenGroup: (group: Group) => void;
};

const AdminGroupsPage: React.FC<AdminGroupsPageProps> = ({groups, totalItems, search, onSearchChange, onOpenGroup}) => {
	const {t} = useTranslation();
	const appliedSearchTerm = search.search ?? '';
	const [searchDraft, setSearchDraft] = useState(appliedSearchTerm);
	const [lastAppliedSearchTerm, setLastAppliedSearchTerm] = useState(appliedSearchTerm);
	const [modalState, setModalState] = useState<ModalState>(null);

	if (lastAppliedSearchTerm !== appliedSearchTerm) {
		setLastAppliedSearchTerm(appliedSearchTerm);
		setSearchDraft(appliedSearchTerm);
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

	const closeModal = useCallback(() => setModalState(null), []);

	const columns = useMemo<DataTableColumn<Group>[]>(
		() => [
			{accessorKey: 'groupId', header: t('admin.groups.groupId')},
			{accessorKey: 'name', header: t('admin.groups.groupName')},
		],
		[t],
	);

	const rowActions = useMemo<DataTableRowAction<Group>[]>(
		() => [
			{
				id: 'edit',
				label: t('admin.groups.editGroup'),
				icon: <Pencil aria-hidden />,
				iconOnly: true,
				onClick: (group) => setModalState({type: 'edit', group}),
			},
			{
				id: 'delete',
				label: t('admin.groups.deleteGroup'),
				icon: <Trash2 aria-hidden />,
				iconOnly: true,
				variant: 'destructive',
				onClick: (group) => setModalState({type: 'delete', group}),
			},
		],
		[t],
	);

	const sortState = useMemo<SortingState>(
		() => [{id: search.sortField ?? 'groupId', desc: search.sortOrder === 'desc'}],
		[search.sortField, search.sortOrder],
	);

	const handleSortingChange = useCallback(
		(state: SortingState) => {
			const [sorted] = state;

			onSearchChange({
				sortField: sorted?.id as GroupsSearch['sortField'],
				sortOrder: sorted === undefined ? undefined : sorted.desc ? 'desc' : 'asc',
				page: undefined,
			});
		},
		[onSearchChange],
	);

	const handlePaginationChange = useCallback(
		({pageIndex, pageSize}: {pageIndex: number; pageSize: number}) => {
			const nextPageSize = pageSize === DEFAULT_PAGE_SIZE ? undefined : (pageSize as GroupsSearch['pageSize']);
			const hasPageSizeChanged = pageSize !== (search.pageSize ?? DEFAULT_PAGE_SIZE);

			onSearchChange({
				pageSize: nextPageSize,
				page: hasPageSizeChanged || pageIndex === 0 ? undefined : pageIndex + 1,
			});
		},
		[onSearchChange, search.pageSize],
	);

	const title = t('admin.headerNavItemGroups');

	return (
		<PageLayout id="main-content" tabIndex={-1}>
			<div className="flex flex-col gap-6">
				<div className="flex flex-col gap-1">
					<PageHeader title={title} />
					<p className="text-sm leading-5 text-muted-foreground">
						{t('admin.groups.guideBody')}
						<Button asChild variant="link" className="h-auto p-0 align-baseline font-normal">
							<a href={GROUPS_GUIDE_URL} target="_blank" rel="noopener noreferrer">
								{t('admin.groups.guideLinkLabel')}
							</a>
						</Button>
					</p>
				</div>

				<div className="flex flex-col gap-4">
					<div className="flex items-center gap-4">
						<div className="flex-1">
							<Label htmlFor="groups-search" className="sr-only">
								{t('admin.groups.searchByGroupId')}
							</Label>
							<SearchInput
								id="groups-search"
								className="max-w-sm min-w-48"
								placeholder={t('admin.groups.searchByGroupId')}
								value={searchDraft}
								onChange={(event) => setSearchDraft(event.target.value)}
								onClear={() => setSearchDraft('')}
							/>
						</div>
						<Button type="button" onClick={() => setModalState({type: 'create'})}>
							{t('admin.groups.createGroup')}
						</Button>
					</div>
					<DataTable
						aria-label={title}
						className="**:data-[slot=data-table-actions-trigger]:ms-auto"
						columns={columns}
						data={groups}
						getRowId={(group) => group.groupId}
						onRowClick={onOpenGroup}
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
						emptyState={t('admin.groups.noGroupsFound')}
					/>
				</div>
			</div>

			{modalState?.type === 'create' && <AddGroupModal isOpen onClose={closeModal} />}
			{modalState?.type === 'edit' && <EditGroupModal isOpen group={modalState.group} onClose={closeModal} />}
			{modalState?.type === 'delete' && (
				<DeleteGroupModal isOpen groupId={modalState.group.groupId} onClose={closeModal} />
			)}
		</PageLayout>
	);
};

export {AdminGroupsPage};
export type {AdminGroupsPageProps};
