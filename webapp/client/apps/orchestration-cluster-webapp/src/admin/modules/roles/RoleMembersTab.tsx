/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo, useState} from 'react';
import {keepPreviousData, useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Trash2} from '@camunda/design-system/icons';
import {Button, DataTable, type DataTableColumn, type DataTableRowAction} from '@camunda/design-system';
import {queries} from '#/shared/http/queries';
import {AssignMemberModal} from './AssignMemberModal';
import {RemoveMemberModal} from './RemoveMemberModal';
import {getMembersQuery, toMemberRow, type MemberKind, type MemberRow} from './members';
import {DEFAULT_PAGE_SIZE, PAGE_SIZES} from './searchSchema';

type Props = {
	roleId: string;
	kind: MemberKind;
	isOidc: boolean;
	isCamundaGroupsEnabled: boolean;
};

const COLUMN_FIELDS = {
	users: ['id'],
	groups: ['id'],
	mappingRules: ['id', 'name', 'claimName', 'claimValue'],
	clients: ['id'],
} as const satisfies Record<MemberKind, readonly (keyof MemberRow)[]>;

const RoleMembersTab: React.FC<Props> = ({roleId, kind, isOidc, isCamundaGroupsEnabled}) => {
	const {t} = useTranslation();
	const [pageIndex, setPageIndex] = useState(0);
	const [pageSize, setPageSize] = useState<number>(DEFAULT_PAGE_SIZE);
	const [isAssignOpen, setIsAssignOpen] = useState(false);
	const [memberToRemove, setMemberToRemove] = useState<MemberRow | null>(null);

	const {data, isPending, isError, isPlaceholderData} = useQuery({
		...getMembersQuery(kind, roleId, {page: {from: pageIndex * pageSize, limit: pageSize}}),
		placeholderData: keepPreviousData,
	});
	const ids = useMemo(() => (data?.items ?? []).map((item) => toMemberRow(kind, item).id), [data, kind]);

	// The role's users endpoint only returns usernames, so name and email come from a second lookup.
	const enrichesUsers = kind === 'users' && !isOidc;
	const {data: userDetails} = useQuery({
		...queries.queryUsers({filter: {username: {$in: ids}}, page: {limit: ids.length}}),
		enabled: enrichesUsers && ids.length > 0,
		placeholderData: keepPreviousData,
	});
	// The role's groups endpoint only returns group IDs, so the name comes from a second lookup.
	const enrichesGroups = kind === 'groups' && isCamundaGroupsEnabled;
	const {data: groupDetails} = useQuery({
		...queries.queryGroups({filter: {groupId: {$in: ids}}, page: {limit: ids.length}}),
		enabled: enrichesGroups && ids.length > 0,
		placeholderData: keepPreviousData,
	});
	const rows = useMemo(() => {
		const detailsByUsername = new Map((userDetails?.items ?? []).map((user) => [user.username, user]));
		const detailsByGroupId = new Map((groupDetails?.items ?? []).map((group) => [group.groupId, group]));
		return (data?.items ?? []).map((item) => {
			const row = toMemberRow(kind, item);
			if (enrichesUsers) {
				const user = detailsByUsername.get(row.id);
				return user ? {...row, name: user.name, email: user.email} : row;
			}
			if (enrichesGroups) {
				const group = detailsByGroupId.get(row.id);
				return group ? {...row, name: group.name} : row;
			}
			return row;
		});
	}, [data, kind, userDetails, groupDetails, enrichesUsers, enrichesGroups]);

	const columns = useMemo<DataTableColumn<MemberRow>[]>(() => {
		const memberColumns = COLUMN_FIELDS[kind].map((field) => ({
			accessorKey: field,
			header: t(`admin.roles.members.${kind}.${field}Column`),
		}));
		if (enrichesUsers) {
			return [
				...memberColumns,
				{accessorKey: 'name', header: t('admin.users.name')},
				{accessorKey: 'email', header: t('admin.users.email')},
			];
		}
		if (enrichesGroups) {
			return [...memberColumns, {accessorKey: 'name', header: t('admin.roles.members.groups.nameColumn')}];
		}
		return memberColumns;
	}, [kind, enrichesUsers, enrichesGroups, t]);

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

	const handlePaginationChange = useCallback(
		({pageIndex: nextPageIndex, pageSize: nextPageSize}: {pageIndex: number; pageSize: number}) => {
			setPageSize(nextPageSize);
			setPageIndex(nextPageSize === pageSize ? nextPageIndex : 0);
		},
		[pageSize],
	);

	return (
		<div className="flex flex-col gap-4">
			<div className="flex justify-end">
				<Button type="button" disabled={data === undefined || isPlaceholderData} onClick={() => setIsAssignOpen(true)}>
					{t(`admin.roles.members.${kind}.assign`)}
				</Button>
			</div>
			{isError && data === undefined ? (
				<p role="alert" className="text-sm text-danger-foreground-subtle">
					{t('admin.roles.members.loadError')}
				</p>
			) : (
				<DataTable
					loading={isPending}
					aria-label={t(`admin.roles.members.${kind}.title`)}
					columns={columns}
					data={rows}
					getRowId={(row) => row.id}
					rowActions={rowActions}
					inlineActionsThreshold={2}
					pagination={{
						manual: true,
						pageSizes: [...PAGE_SIZES],
						defaultPageSize: DEFAULT_PAGE_SIZE,
						pageSize,
						pageIndex,
						rowCount: data?.page.totalItems ?? 0,
						onPaginationChange: handlePaginationChange,
					}}
					emptyState={t(`admin.roles.members.${kind}.empty`)}
				/>
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
