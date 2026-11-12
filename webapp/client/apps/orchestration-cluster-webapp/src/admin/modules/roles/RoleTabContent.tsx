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
import type {DataTableColumn} from '@camunda/design-system';
import {queries} from '#/shared/http/queries';
import {RoleMembersTab} from './RoleMembersTab';
import {getMembersQuery, toMemberRow, type MemberKind, type MemberRow} from './members';
import {getRoleMembersRequestBody, type RoleDetailSearch, type RoleDetailSearchChange} from './detailSearchSchema';

type Props = {
	roleId: string;
	kind: MemberKind;
	isOidc: boolean;
	isCamundaGroupsEnabled: boolean;
	search: RoleDetailSearch;
	onSearchChange: RoleDetailSearchChange;
};

const COLUMN_FIELDS = {
	users: ['id'],
	groups: ['id'],
	mappingRules: ['id', 'name', 'claimName', 'claimValue'],
	clients: ['id'],
} as const satisfies Record<MemberKind, readonly (keyof MemberRow)[]>;

const RoleTabContent: React.FC<Props> = ({roleId, kind, isOidc, isCamundaGroupsEnabled, search, onSearchChange}) => {
	const {t} = useTranslation();
	const {data} = useSuspenseQuery(getMembersQuery(kind, roleId, getRoleMembersRequestBody(search)));
	const ids = useMemo(() => data.items.map((item) => toMemberRow(kind, item).id), [data, kind]);

	const enrichesUsers = kind === 'users' && !isOidc;
	const {data: userDetails, isError: isUserDetailsError} = useQuery({
		...queries.queryUsers({filter: {username: {$in: ids}}, page: {limit: ids.length}}),
		enabled: enrichesUsers && ids.length > 0,
		placeholderData: keepPreviousData,
	});
	const enrichesGroups = kind === 'groups' && isCamundaGroupsEnabled;
	const {data: groupDetails, isError: isGroupDetailsError} = useQuery({
		...queries.queryGroups({filter: {groupId: {$in: ids}}, page: {limit: ids.length}}),
		enabled: enrichesGroups && ids.length > 0,
		placeholderData: keepPreviousData,
	});

	const rows = useMemo(() => {
		const detailsByUsername = new Map((userDetails?.items ?? []).map((user) => [user.username, user]));
		const detailsByGroupId = new Map((groupDetails?.items ?? []).map((group) => [group.groupId, group]));
		return data.items.map((item) => {
			const row = toMemberRow(kind, item);
			if (enrichesUsers) {
				const user = detailsByUsername.get(row.id);
				return user ? {...row, name: user.name ?? undefined, email: user.email ?? undefined} : row;
			}
			if (enrichesGroups) {
				const group = detailsByGroupId.get(row.id);
				return group ? {...row, name: group.name ?? undefined} : row;
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

	return (
		<RoleMembersTab
			roleId={roleId}
			kind={kind}
			isOidc={isOidc}
			isCamundaGroupsEnabled={isCamundaGroupsEnabled}
			rows={rows}
			columns={columns}
			totalItems={data.page.totalItems}
			isLoadError={isUserDetailsError || isGroupDetailsError}
			search={search}
			onSearchChange={onSearchChange}
		/>
	);
};

export {RoleTabContent};
