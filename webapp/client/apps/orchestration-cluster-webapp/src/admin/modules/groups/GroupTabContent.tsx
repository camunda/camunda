/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {keepPreviousData, useQuery, useSuspenseQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Skeleton, type DataTableColumn} from '@camunda/design-system';
import {queries} from '#/shared/http/queries';
import {GroupMembersTab} from './GroupMembersTab';
import {getMemberId, getMembersQuery, toMemberRow, type MemberKind, type MemberRow} from './members';
import {getGroupMembersRequestBody, type GroupDetailSearch, type GroupDetailSearchChange} from './detailSearchSchema';

type Props = {
	groupId: string;
	kind: MemberKind;
	isOidc: boolean;
	search: GroupDetailSearch;
	onSearchChange: GroupDetailSearchChange;
};

const COLUMN_FIELDS = {
	users: ['id'],
	roles: ['id', 'name'],
	mappingRules: ['id', 'name', 'claimName', 'claimValue'],
	clients: ['id'],
} as const satisfies Record<MemberKind, readonly (keyof MemberRow)[]>;

const GroupTabContent: React.FC<Props> = ({groupId, kind, isOidc, search, onSearchChange}) => {
	const {t} = useTranslation();
	const {data} = useSuspenseQuery(getMembersQuery(kind, groupId, getGroupMembersRequestBody(search)));
	const memberIds = useMemo(() => data.items.map((item) => getMemberId(kind, item)), [data, kind]);

	const enrichesUsers = kind === 'users' && !isOidc;
	const {data: userDetails} = useQuery({
		...queries.queryUsers({filter: {username: {$in: memberIds}}, page: {limit: memberIds.length}}),
		enabled: enrichesUsers && memberIds.length > 0,
		placeholderData: keepPreviousData,
	});
	const isEnriching = enrichesUsers && memberIds.length > 0 && userDetails === undefined;

	const rows = useMemo(() => {
		const detailsByUsername = new Map((userDetails?.items ?? []).map((user) => [user.username, user]));
		return data.items.map((item) => {
			const row = toMemberRow(kind, item);
			const details = enrichesUsers ? detailsByUsername.get(row.id) : undefined;
			return details ? {...row, name: details.name ?? undefined, email: details.email ?? undefined} : row;
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
			{
				accessorKey: 'name',
				header: t('admin.users.name'),
				cell: ({row}) => (isEnriching ? <Skeleton className="h-4 w-24" /> : row.original.name),
			},
			{
				accessorKey: 'email',
				header: t('admin.users.email'),
				cell: ({row}) => (isEnriching ? <Skeleton className="h-4 w-32" /> : row.original.email),
			},
		];
	}, [kind, enrichesUsers, isEnriching, t]);

	return (
		<GroupMembersTab
			groupId={groupId}
			kind={kind}
			isOidc={isOidc}
			rows={rows}
			columns={columns}
			totalItems={data.page.totalItems}
			search={search}
			onSearchChange={onSearchChange}
		/>
	);
};

export {GroupTabContent};
