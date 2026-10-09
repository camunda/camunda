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
	groupId: string;
	kind: MemberKind;
	isOidc: boolean;
};

const COLUMN_FIELDS = {
	users: ['id'],
	clients: ['id'],
	roles: ['id', 'name'],
	mappingRules: ['id', 'name', 'claimName', 'claimValue'],
} as const satisfies Record<MemberKind, readonly (keyof MemberRow)[]>;

const GroupMembersTab: React.FC<Props> = ({groupId, kind, isOidc}) => {
	const {t} = useTranslation();
	const [pageIndex, setPageIndex] = useState(0);
	const [pageSize, setPageSize] = useState<number>(DEFAULT_PAGE_SIZE);
	const [isAssignOpen, setIsAssignOpen] = useState(false);
	const [memberToRemove, setMemberToRemove] = useState<MemberRow | null>(null);

	const {data, isPending, isError, isPlaceholderData} = useQuery({
		...getMembersQuery(kind, groupId, {page: {from: pageIndex * pageSize, limit: pageSize}}),
		placeholderData: keepPreviousData,
	});
	const usernames = useMemo(() => (data?.items ?? []).map((item) => toMemberRow(kind, item).id), [data, kind]);

	// The group's users endpoint only returns usernames, so name and email come from a second lookup.
	const enrichesUsers = kind === 'users' && !isOidc;
	const {data: userDetails} = useQuery({
		...queries.queryUsers({filter: {username: {$in: usernames}}, page: {limit: usernames.length}}),
		enabled: enrichesUsers && usernames.length > 0,
		placeholderData: keepPreviousData,
	});
	const rows = useMemo(() => {
		const detailsByUsername = new Map((userDetails?.items ?? []).map((user) => [user.username, user]));
		return (data?.items ?? []).map((item) => {
			const row = toMemberRow(kind, item);
			const details = enrichesUsers ? detailsByUsername.get(row.id) : undefined;
			return details ? {...row, name: details.name, email: details.email} : row;
		});
	}, [data, kind, userDetails, enrichesUsers]);

	const columns = useMemo<DataTableColumn<MemberRow>[]>(() => {
		const memberColumns = COLUMN_FIELDS[kind].map((field) => ({
			accessorKey: field,
			header: t(`admin.groups.members.${kind}.${field}Column`),
		}));
		if (!enrichesUsers) {
			return memberColumns;
		}
		return [
			...memberColumns,
			{accessorKey: 'name', header: t('admin.users.name')},
			{accessorKey: 'email', header: t('admin.users.email')},
		];
	}, [kind, enrichesUsers, t]);

	const rowActions = useMemo<DataTableRowAction<MemberRow>[]>(
		() => [
			{
				id: 'remove',
				label: t(`admin.groups.members.${kind}.remove`),
				ariaLabel: (row) => `${t(`admin.groups.members.${kind}.remove`)} ${row.id}`,
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
					{t(`admin.groups.members.${kind}.assign`)}
				</Button>
			</div>
			{isError && data === undefined ? (
				<p role="alert" className="text-sm text-danger-foreground-subtle">
					{t('admin.groups.members.loadError')}
				</p>
			) : (
				<DataTable
					loading={isPending}
					aria-label={t(`admin.groups.members.${kind}.title`)}
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
					emptyState={t(`admin.groups.members.${kind}.empty`)}
				/>
			)}

			{isAssignOpen && (
				<AssignMemberModal
					isOpen
					groupId={groupId}
					kind={kind}
					isOidc={isOidc}
					onClose={() => setIsAssignOpen(false)}
				/>
			)}
			{memberToRemove !== null && (
				<RemoveMemberModal
					isOpen
					groupId={groupId}
					kind={kind}
					memberId={memberToRemove.id}
					onClose={() => setMemberToRemove(null)}
				/>
			)}
		</div>
	);
};

export {GroupMembersTab};
