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

// Carries the members that were assigned before a batch failed, so they still show up in the list.
class AssignFailedError extends Error {
	assigned: TenantMember[];

	constructor(assigned: TenantMember[], cause: unknown) {
		super('Failed to assign tenant members', {cause});
		this.assigned = assigned;
	}
}

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
	const [freshest] = queryClient
		.getQueryCache()
		.findAll({queryKey: [QUERY_KEY_BY_TAB[tab], tenantId]})
		.filter((query) => query.state.data !== undefined)
		.sort((a, b) => b.state.dataUpdatedAt - a.state.dataUpdatedAt);
	return (freshest?.state.data as {page: {totalItems: number}} | undefined)?.page.totalItems;
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
				// A key of its own keeps a second reconciliation from joining this poll and its readiness check.
				queryKey: [...query.queryKey, crypto.randomUUID()],
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

	const applyAssigned = (assigned: TenantMember[]) => {
		const baseline = getCachedTotalItems(queryClient, tab, tenantId);
		for (const member of assigned) {
			patchListCaches(queryClient, {
				queryKeyPrefix,
				item: member,
				getId: (item: TenantMember) => getMemberId(tab, item),
				compare: (a, b, queryKey) => compareMembersBySort(tab, a, b, queryKey),
				canInsert: (_item, queryKey, currentItemCount) =>
					isFirstPageWithRoom((queryKey[2] as MembersListBody | undefined)?.page, currentItemCount),
			});
		}

		void reconcileMembers((totalItems) => totalItems >= (baseline ?? 0) + assigned.length, baseline);
	};

	const assign = useMutation({
		// Members are assigned one by one. A member that is already assigned (409) counts as done, so
		// retrying a batch that failed halfway does not trip over the members that went through.
		mutationFn: async (members: TenantMember[]) => {
			const assigned: TenantMember[] = [];
			for (const member of members) {
				const {error} = await request(assignEndpoint(tab, tenantId, getMemberId(tab, member)));
				if (error === null) {
					assigned.push(member);
				} else if (error.response?.status !== 409) {
					throw new AssignFailedError(assigned, mapQueryError(error));
				}
			}
			return assigned;
		},
		onSuccess: (assigned) => {
			toast.success(t('admin.tenants.assignSuccess'));
			applyAssigned(assigned);
		},
		onError: (error) => {
			toast.error(t('admin.tenants.assignError'));
			if (error instanceof AssignFailedError && error.assigned.length > 0) {
				applyAssigned(error.assigned);
			} else {
				void invalidateMembers();
			}
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

export {QUERY_KEY_BY_TAB, createMember, getMemberId, useTenantMemberMutations};
export type {TenantMember};
