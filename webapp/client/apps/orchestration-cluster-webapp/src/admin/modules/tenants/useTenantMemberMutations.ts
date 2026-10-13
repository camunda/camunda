/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient, type QueryClient, type QueryFunction, type QueryKey} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {toast} from '@camunda/design-system';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';
import {queries} from '#/shared/http/queries';
import {isFirstPageWithRoom, patchListCaches, removeFromListCaches} from '#/shared/http/patchListCaches';
import {waitUntilReady} from '#/shared/http/waitUntilReady';
import type {TenantTab} from './detailSearchSchema';

const QUERY_KEY_BY_TAB: Record<TenantTab, string> = {
	users: 'queryUsersByTenant',
	groups: 'queryGroupsByTenant',
	roles: 'queryRolesByTenant',
	'mapping-rules': 'queryMappingRulesByTenant',
	clients: 'queryClientsByTenant',
};

const MEMBER_ID_KEY: Record<TenantTab, string> = {
	users: 'username',
	groups: 'groupId',
	roles: 'roleId',
	'mapping-rules': 'mappingRuleId',
	clients: 'clientId',
};

type TenantMember = Record<string, unknown>;

type MembersListBody = {sort?: {field: string; order?: string}[]; page?: {from?: number; limit?: number}};

function getMemberId(tab: TenantTab, member: TenantMember): string {
	return String(member[MEMBER_ID_KEY[tab]]);
}

function createMember(tab: TenantTab, id: string): TenantMember {
	return {[MEMBER_ID_KEY[tab]]: id};
}

function assignEndpoint(tab: TenantTab, tenantId: string, id: string) {
	switch (tab) {
		case 'users':
			return endpoints.assignUserToTenant({tenantId, username: id});
		case 'groups':
			return endpoints.assignGroupToTenant({tenantId, groupId: id});
		case 'roles':
			return endpoints.assignRoleToTenant({tenantId, roleId: id});
		case 'mapping-rules':
			return endpoints.assignMappingRuleToTenant({tenantId, mappingRuleId: id});
		case 'clients':
			return endpoints.assignClientToTenant({tenantId, clientId: id});
	}
}

function unassignEndpoint(tab: TenantTab, tenantId: string, id: string) {
	switch (tab) {
		case 'users':
			return endpoints.unassignUserFromTenant({tenantId, username: id});
		case 'groups':
			return endpoints.unassignGroupFromTenant({tenantId, groupId: id});
		case 'roles':
			return endpoints.unassignRoleFromTenant({tenantId, roleId: id});
		case 'mapping-rules':
			return endpoints.unassignMappingRuleFromTenant({tenantId, mappingRuleId: id});
		case 'clients':
			return endpoints.unassignClientFromTenant({tenantId, clientId: id});
	}
}

function compareMembersBySort(tab: TenantTab, a: TenantMember, b: TenantMember, queryKey: QueryKey): number {
	const [sort] = (queryKey[2] as MembersListBody | undefined)?.sort ?? [];
	const direction = sort?.order === 'DESC' ? -1 : 1;
	const idA = getMemberId(tab, a);
	const idB = getMemberId(tab, b);
	return direction * (idA < idB ? -1 : idA > idB ? 1 : 0);
}

// The tenant member searches cannot filter by ID, so the total count of members is the only way to
// tell whether the search index has caught up with a change.
function getCachedTotalItems(queryClient: QueryClient, tab: TenantTab, tenantId: string): number | undefined {
	const [match] = queryClient.getQueriesData<{page: {totalItems: number}}>({
		queryKey: [QUERY_KEY_BY_TAB[tab], tenantId],
	});
	return match?.[1]?.page.totalItems;
}

function countQuery(tab: TenantTab, tenantId: string) {
	const body = {page: {limit: 1}};
	switch (tab) {
		case 'users':
			return queries.queryUsersByTenant(tenantId, body);
		case 'groups':
			return queries.queryGroupsByTenant(tenantId, body);
		case 'roles':
			return queries.queryRolesByTenant(tenantId, body);
		case 'mapping-rules':
			return queries.queryMappingRulesByTenant(tenantId, body);
		case 'clients':
			return queries.queryClientsByTenant(tenantId, body);
	}
}

function useTenantMemberMutations(tab: TenantTab, tenantId: string) {
	const {t} = useTranslation();
	const queryClient = useQueryClient();

	const queryKeyPrefix: QueryKey = [QUERY_KEY_BY_TAB[tab], tenantId];

	const invalidateMembers = () => queryClient.invalidateQueries({queryKey: queryKeyPrefix});

	const reconcileMembers = async (isReady: (totalItems: number) => boolean, baseline: number | undefined) => {
		if (baseline === undefined) {
			void invalidateMembers();
			return;
		}

		const query = countQuery(tab, tenantId);
		const ready = await waitUntilReady(
			queryClient,
			{
				queryKey: query.queryKey,
				queryFn: query.queryFn as unknown as QueryFunction<{page: {totalItems: number}}, QueryKey, never>,
			},
			{isReady: ({page}) => isReady(page.totalItems)},
		);

		if (ready !== undefined) {
			void invalidateMembers();
		} else {
			toast.warning(t('admin.tenants.changeNotConfirmed'), {
				description: t('admin.tenants.changeNotConfirmedDescription'),
			});
		}
	};

	const assign = useMutation({
		mutationFn: async (members: TenantMember[]) => {
			for (const member of members) {
				const {error} = await request(assignEndpoint(tab, tenantId, getMemberId(tab, member)));
				if (error !== null) {
					throw mapQueryError(error);
				}
			}
			return members;
		},
		onSuccess: (members) => {
			const baseline = getCachedTotalItems(queryClient, tab, tenantId);
			for (const member of members) {
				patchListCaches(queryClient, {
					queryKeyPrefix,
					item: member,
					getId: (item: TenantMember) => getMemberId(tab, item),
					compare: (a, b, queryKey) => compareMembersBySort(tab, a, b, queryKey),
					canInsert: (_item, queryKey, currentItemCount) =>
						isFirstPageWithRoom((queryKey[2] as MembersListBody | undefined)?.page, currentItemCount),
				});
			}
			toast.success(t('admin.tenants.assignSuccess'));

			void reconcileMembers((totalItems) => totalItems >= (baseline ?? 0) + members.length, baseline);
		},
		onError: () => {
			toast.error(t('admin.tenants.assignError'));
			void invalidateMembers();
		},
	});

	const unassign = useMutation({
		mutationFn: async (id: string) => {
			const {error} = await request(unassignEndpoint(tab, tenantId, id));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return id;
		},
		onSuccess: (id) => {
			const baseline = getCachedTotalItems(queryClient, tab, tenantId);
			removeFromListCaches(queryClient, {
				queryKeyPrefix,
				id,
				getId: (item: TenantMember) => getMemberId(tab, item),
			});
			toast.success(t('admin.tenants.unassignSuccess'));

			void reconcileMembers((totalItems) => totalItems <= (baseline ?? 0) - 1, baseline);
		},
		onError: () => {
			toast.error(t('admin.tenants.unassignError'));
			void invalidateMembers();
		},
	});

	return {assign, unassign};
}

export {createMember, getMemberId, useTenantMemberMutations};
export type {TenantMember};
