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
import type {User} from '@camunda/camunda-api-zod-schemas/8.11';
import {CreateUserModal} from '#/admin/modules/users/CreateUserModal';
import {EditUserModal} from '#/admin/modules/users/EditUserModal';
import {DeleteUserModal} from '#/admin/modules/users/DeleteUserModal';
import {DEFAULT_PAGE_SIZE, PAGE_SIZES, type UsersSearch} from '#/admin/modules/users/searchSchema';

type SortingState = NonNullable<SortingConfig['sortState']>;

const SEARCH_DEBOUNCE = 500;
const USERS_GUIDE_URL = 'https://docs.camunda.io/docs/next/components/admin/user/';

type ModalState = {type: 'create'} | {type: 'edit'; user: User} | {type: 'delete'; user: User} | null;

type AdminUsersPageProps = {
	users: User[];
	totalItems: number;
	search: UsersSearch;
	onSearchChange: (next: Partial<UsersSearch>) => void;
	onOpenUser: (user: User) => void;
};

const AdminUsersPage: React.FC<AdminUsersPageProps> = ({users, totalItems, search, onSearchChange, onOpenUser}) => {
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

	const columns = useMemo<DataTableColumn<User>[]>(
		() => [
			{accessorKey: 'username', header: t('admin.users.username')},
			{accessorKey: 'name', header: t('admin.users.name')},
			{accessorKey: 'email', header: t('admin.users.email')},
		],
		[t],
	);

	const rowActions = useMemo<DataTableRowAction<User>[]>(
		() => [
			{
				id: 'edit',
				label: t('admin.users.editUser'),
				icon: <Pencil aria-hidden />,
				iconOnly: true,
				onClick: (user) => setModalState({type: 'edit', user}),
			},
			{
				id: 'delete',
				label: t('admin.users.deleteUser'),
				icon: <Trash2 aria-hidden />,
				iconOnly: true,
				variant: 'destructive',
				onClick: (user) => setModalState({type: 'delete', user}),
			},
		],
		[t],
	);

	const sortState = useMemo<SortingState>(
		() => [{id: search.sortField ?? 'username', desc: search.sortOrder === 'DESC'}],
		[search.sortField, search.sortOrder],
	);

	const handleSortingChange = useCallback(
		(state: SortingState) => {
			const [sorted] = state;

			onSearchChange({
				sortField: sorted?.id as UsersSearch['sortField'],
				sortOrder: sorted === undefined ? undefined : sorted.desc ? 'DESC' : 'ASC',
				page: undefined,
			});
		},
		[onSearchChange],
	);

	const handlePaginationChange = useCallback(
		({pageIndex, pageSize}: {pageIndex: number; pageSize: number}) => {
			const nextPageSize = pageSize === DEFAULT_PAGE_SIZE ? undefined : (pageSize as UsersSearch['pageSize']);
			const hasPageSizeChanged = pageSize !== (search.pageSize ?? DEFAULT_PAGE_SIZE);

			onSearchChange({
				pageSize: nextPageSize,
				page: hasPageSizeChanged || pageIndex === 0 ? undefined : pageIndex + 1,
			});
		},
		[onSearchChange, search.pageSize],
	);

	const title = t('admin.headerNavItemUsers');

	return (
		<PageLayout id="main-content" tabIndex={-1}>
			<div className="flex flex-col gap-6">
				<div className="flex flex-col gap-1">
					<PageHeader title={title} />
					<p className="text-sm leading-5 text-muted-foreground">
						{t('admin.users.guideBody')}
						<Button asChild variant="link" className="h-auto p-0 align-baseline font-normal">
							<a href={USERS_GUIDE_URL} target="_blank" rel="noopener noreferrer">
								{t('admin.users.guideLinkLabel')}
							</a>
						</Button>
					</p>
				</div>

				<div className="flex flex-col gap-4">
					<div className="flex items-center gap-4">
						<div className="flex-1">
							<Label htmlFor="users-search" className="sr-only">
								{t('admin.users.searchByUsername')}
							</Label>
							<SearchInput
								id="users-search"
								className="max-w-sm min-w-48"
								placeholder={t('admin.users.searchByUsername')}
								value={searchDraft}
								onChange={(event) => setSearchDraft(event.target.value)}
								onClear={() => setSearchDraft('')}
							/>
						</div>
						<Button type="button" onClick={() => setModalState({type: 'create'})}>
							{t('admin.users.createUser')}
						</Button>
					</div>
					<DataTable
						aria-label={title}
						className="**:data-[slot=data-table-actions-trigger]:ms-auto"
						columns={columns}
						data={users}
						getRowId={(user) => user.username}
						onRowClick={onOpenUser}
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
						emptyState={t('admin.users.noUsersFound')}
					/>
				</div>
			</div>

			{modalState?.type === 'create' && <CreateUserModal isOpen onClose={closeModal} />}
			{modalState?.type === 'edit' && <EditUserModal isOpen user={modalState.user} onClose={closeModal} />}
			{modalState?.type === 'delete' && (
				<DeleteUserModal isOpen username={modalState.user.username} onClose={closeModal} />
			)}
		</PageLayout>
	);
};

export {AdminUsersPage};
export type {AdminUsersPageProps};
