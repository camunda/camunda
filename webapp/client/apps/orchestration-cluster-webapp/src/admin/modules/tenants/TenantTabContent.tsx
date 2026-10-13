/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useSuspenseQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import type {DataTableColumn} from '@camunda/design-system';
import type {
	MappingRule,
	QueryGroupsByTenantResponseBody,
	Role,
	TenantClient,
	TenantUser,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {queries} from '#/shared/http/queries';
import {TenantMembersTab} from './TenantMembersTab';
import {getTenantMembersRequestBody} from './getTenantMembersRequestBody';
import type {TenantDetailSearch, TenantTab} from './detailSearchSchema';

type ContentProps = {
	tenantId: string;
	search: TenantDetailSearch;
	isOidc: boolean;
	onSearchChange: (next: Partial<TenantDetailSearch>) => void;
};

type TenantGroup = QueryGroupsByTenantResponseBody['items'][number];

const getUsername = (user: TenantUser) => user.username;
const getGroupId = (group: TenantGroup) => group.groupId;
const getRoleId = (role: Role) => role.roleId;
const getMappingRuleId = (mappingRule: MappingRule) => mappingRule.mappingRuleId;
const getClientId = (client: TenantClient) => client.clientId;

const UsersTab: React.FC<ContentProps> = ({tenantId, search, isOidc, onSearchChange}) => {
	const {t} = useTranslation();
	const {data} = useSuspenseQuery(
		queries.queryUsersByTenant(tenantId, getTenantMembersRequestBody(search, 'username')),
	);
	const columns = useMemo<DataTableColumn<TenantUser>[]>(
		() => [{accessorKey: 'username', header: t('admin.tenants.usernameColumn')}],
		[t],
	);

	return (
		<TenantMembersTab
			tab="users"
			tenantId={tenantId}
			rows={data.items}
			columns={columns}
			sortColumnId="username"
			getMemberId={getUsername}
			totalItems={data.page.totalItems}
			search={search}
			isFreeText={isOidc}
			onSearchChange={onSearchChange}
		/>
	);
};

const GroupsTab: React.FC<ContentProps> = ({tenantId, search, onSearchChange}) => {
	const {t} = useTranslation();
	const {data} = useSuspenseQuery(
		queries.queryGroupsByTenant(tenantId, getTenantMembersRequestBody(search, 'groupId')),
	);
	const columns = useMemo<DataTableColumn<TenantGroup>[]>(
		() => [{accessorKey: 'groupId', header: t('admin.tenants.groupIdColumn')}],
		[t],
	);

	return (
		<TenantMembersTab
			tab="groups"
			tenantId={tenantId}
			rows={data.items}
			columns={columns}
			sortColumnId="groupId"
			getMemberId={getGroupId}
			totalItems={data.page.totalItems}
			search={search}
			onSearchChange={onSearchChange}
		/>
	);
};

const RolesTab: React.FC<ContentProps> = ({tenantId, search, onSearchChange}) => {
	const {t} = useTranslation();
	const {data} = useSuspenseQuery(queries.queryRolesByTenant(tenantId, getTenantMembersRequestBody(search, 'roleId')));
	const columns = useMemo<DataTableColumn<Role>[]>(
		() => [
			{accessorKey: 'roleId', header: t('admin.tenants.roleIdColumn')},
			{accessorKey: 'name', header: t('admin.tenants.roleNameColumn'), enableSorting: false},
		],
		[t],
	);

	return (
		<TenantMembersTab
			tab="roles"
			tenantId={tenantId}
			rows={data.items}
			columns={columns}
			sortColumnId="roleId"
			getMemberId={getRoleId}
			totalItems={data.page.totalItems}
			search={search}
			onSearchChange={onSearchChange}
		/>
	);
};

const MappingRulesTab: React.FC<ContentProps> = ({tenantId, search, onSearchChange}) => {
	const {t} = useTranslation();
	const {data} = useSuspenseQuery(
		queries.queryMappingRulesByTenant(tenantId, getTenantMembersRequestBody(search, 'mappingRuleId')),
	);
	const columns = useMemo<DataTableColumn<MappingRule>[]>(
		() => [
			{accessorKey: 'mappingRuleId', header: t('admin.tenants.mappingRuleIdColumn')},
			{accessorKey: 'name', header: t('admin.tenants.mappingRuleNameColumn'), enableSorting: false},
		],
		[t],
	);

	return (
		<TenantMembersTab
			tab="mapping-rules"
			tenantId={tenantId}
			rows={data.items}
			columns={columns}
			sortColumnId="mappingRuleId"
			getMemberId={getMappingRuleId}
			totalItems={data.page.totalItems}
			search={search}
			onSearchChange={onSearchChange}
		/>
	);
};

const ClientsTab: React.FC<ContentProps> = ({tenantId, search, onSearchChange}) => {
	const {t} = useTranslation();
	const {data} = useSuspenseQuery(
		queries.queryClientsByTenant(tenantId, getTenantMembersRequestBody(search, 'clientId')),
	);
	const columns = useMemo<DataTableColumn<TenantClient>[]>(
		() => [{accessorKey: 'clientId', header: t('admin.tenants.clientIdColumn')}],
		[t],
	);

	return (
		<TenantMembersTab
			tab="clients"
			tenantId={tenantId}
			rows={data.items}
			columns={columns}
			sortColumnId="clientId"
			getMemberId={getClientId}
			totalItems={data.page.totalItems}
			search={search}
			isFreeText
			onSearchChange={onSearchChange}
		/>
	);
};

const TAB_COMPONENTS: Record<TenantTab, React.FC<ContentProps>> = {
	users: UsersTab,
	groups: GroupsTab,
	roles: RolesTab,
	'mapping-rules': MappingRulesTab,
	clients: ClientsTab,
};

const TenantTabContent: React.FC<ContentProps & {tab: TenantTab}> = ({tab, ...props}) => {
	const Content = TAB_COMPONENTS[tab];
	return <Content {...props} />;
};

export {TenantTabContent};
