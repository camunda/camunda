/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Trash2} from '@camunda/design-system/icons';
import {
	Button,
	DataTable,
	Label,
	SearchInput,
	type DataTableColumn,
	type DataTableRowAction,
} from '@camunda/design-system';
import {useDebouncedUrlFilter} from '#/shared/hooks/useDebouncedUrlFilter';
import {AssignEmptyState} from '#/admin/modules/shared/AssignEmptyState';
import {AssignMemberModal} from './AssignMemberModal';
import {RemoveMemberModal} from './RemoveMemberModal';
import type {MemberKind, MemberRow} from './members';
import type {RoleDetailSearch, RoleDetailSearchChange} from './detailSearchSchema';
import {DEFAULT_PAGE_SIZE, PAGE_SIZES} from './searchSchema';

type Props = {
	roleId: string;
	kind: MemberKind;
	isOidc: boolean;
	isCamundaGroupsEnabled: boolean;
	rows: MemberRow[];
	columns: DataTableColumn<MemberRow>[];
	totalItems: number;
	isLoadError: boolean;
	search: RoleDetailSearch;
	onSearchChange: RoleDetailSearchChange;
};

const RoleMembersTab: React.FC<Props> = ({
	roleId,
	kind,
	isOidc,
	isCamundaGroupsEnabled,
	rows,
	columns,
	totalItems,
	isLoadError,
	search,
	onSearchChange,
}) => {
	const {t} = useTranslation();
	const appliedSearchTerm = search.search ?? '';
	const [searchDraft, setSearchDraft] = useDebouncedUrlFilter(appliedSearchTerm, (nextSearch) =>
		onSearchChange({search: nextSearch, page: undefined}),
	);
	const [isAssignOpen, setIsAssignOpen] = useState(false);
	const [memberToRemove, setMemberToRemove] = useState<MemberRow | null>(null);

	const pageSize = search.pageSize ?? DEFAULT_PAGE_SIZE;
	const lastPage = Math.max(1, Math.ceil(totalItems / pageSize));
	const page = search.page;
	useEffect(() => {
		if (page !== undefined && page > lastPage) {
			onSearchChange({page: lastPage === 1 ? undefined : lastPage}, {replace: true});
		}
	}, [page, lastPage, onSearchChange]);

	const rowActions = useMemo<DataTableRowAction<MemberRow>[]>(
		() => [
			{
				id: 'remove',
				label: t(`admin.roles.members.${kind}.remove`),
				ariaLabel: (row) => `${t(`admin.roles.members.${kind}.remove`)} ${row.id}`,
				icon: <Trash2 aria-hidden />,
				iconOnly: true,
				variant: 'destructive',
				onClick: setMemberToRemove,
			},
		],
		[kind, t],
	);

	const isSearchable = kind === 'users';
	const isEmpty = totalItems === 0 && (!isSearchable || appliedSearchTerm.trim() === '');

	return (
		<div className="flex flex-col gap-4">
			{isEmpty ? (
				<AssignEmptyState
					parent="roles"
					kind={kind}
					description={t('admin.members.emptyState.description')}
					onAssign={() => setIsAssignOpen(true)}
				/>
			) : (
				<>
					<div className="flex flex-col gap-4 sm:flex-row sm:items-center">
						{isSearchable && (
							<div className="sm:flex-1">
								<Label htmlFor="role-users-search" className="sr-only">
									{t('admin.users.searchByUsername')}
								</Label>
								<SearchInput
									id="role-users-search"
									className="w-full sm:max-w-sm sm:min-w-48"
									placeholder={t('admin.users.searchByUsername')}
									value={searchDraft}
									onChange={(event) => setSearchDraft(event.target.value)}
									onClear={() => setSearchDraft('')}
								/>
							</div>
						)}
						<Button type="button" className="sm:ml-auto" onClick={() => setIsAssignOpen(true)}>
							{t(`admin.roles.members.${kind}.assign`)}
						</Button>
					</div>
					{isLoadError ? (
						<p role="alert" className="text-sm text-danger-foreground-subtle">
							{t('admin.roles.members.loadError')}
						</p>
					) : (
						<DataTable
							aria-label={t(`admin.roles.members.${kind}.title`)}
							columns={columns}
							data={rows}
							emptyState={isSearchable ? t('admin.users.noUsersFound') : undefined}
							getRowId={(row) => row.id}
							rowActions={rowActions}
							inlineActionsThreshold={2}
							pagination={{
								manual: true,
								pageSizes: [...PAGE_SIZES],
								defaultPageSize: DEFAULT_PAGE_SIZE,
								pageSize,
								pageIndex: (page ?? 1) - 1,
								rowCount: totalItems,
								onPaginationChange: ({pageIndex, pageSize: nextPageSize}) => {
									onSearchChange({
										pageSize:
											nextPageSize === DEFAULT_PAGE_SIZE ? undefined : (nextPageSize as RoleDetailSearch['pageSize']),
										page: nextPageSize !== pageSize || pageIndex === 0 ? undefined : pageIndex + 1,
									});
								},
							}}
						/>
					)}
				</>
			)}

			{isAssignOpen && (
				<AssignMemberModal
					isOpen
					roleId={roleId}
					kind={kind}
					isOidc={isOidc}
					isCamundaGroupsEnabled={isCamundaGroupsEnabled}
					onClose={() => setIsAssignOpen(false)}
				/>
			)}
			{memberToRemove !== null && (
				<RemoveMemberModal
					isOpen
					roleId={roleId}
					kind={kind}
					memberId={memberToRemove.id}
					onClose={() => setMemberToRemove(null)}
				/>
			)}
		</div>
	);
};

export {RoleMembersTab};
