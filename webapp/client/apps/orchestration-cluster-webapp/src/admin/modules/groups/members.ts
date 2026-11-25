/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryKey} from '@tanstack/react-query';
import type {
	QueryMappingRulesByGroupResponseBody,
	QueryRolesByGroupResponseBody,
	QueryUsersByGroupResponseBody,
	QueryClientsByGroupResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {endpoints} from '#/shared/http/endpoints';
import {queries} from '#/shared/http/queries';
import {
	getMemberSort,
	narrowMemberSort,
	type MemberSortField,
	type MemberSortRequest,
	type MemberSortSearch,
} from '#/admin/modules/shared/memberSorting';

const MEMBER_KINDS = ['users', 'roles', 'mappingRules', 'clients'] as const;

type MemberKind = (typeof MEMBER_KINDS)[number];

type MemberRow = {
	id: string;
	name?: string;
	email?: string;
	claimName?: string;
	claimValue?: string;
};

type MembersResponse =
	| QueryUsersByGroupResponseBody
	| QueryClientsByGroupResponseBody
	| QueryRolesByGroupResponseBody
	| QueryMappingRulesByGroupResponseBody;

type MemberPage = {
	page: {from: number; limit: number};
	sort?: MemberSortRequest;
};

const MEMBER_SORT_FIELDS = {
	users: {id: 'username'},
	clients: {id: 'clientId'},
	roles: {id: 'roleId', name: 'name'},
	mappingRules: {id: 'mappingRuleId', name: 'name', claimName: 'claimName', claimValue: 'claimValue'},
} as const satisfies Record<MemberKind, Partial<Record<keyof MemberRow, MemberSortField>>>;

const MEMBER_KIND_CONFIG = {
	users: {
		query: (groupId: string, body: MemberPage) =>
			queries.queryUsersByGroup(groupId, narrowMemberSort(body, Object.values(MEMBER_SORT_FIELDS.users))),
		assign: (groupId: string, id: string) => endpoints.assignUserToGroup({groupId, username: id}),
		unassign: (groupId: string, id: string) => endpoints.unassignUserFromGroup({groupId, username: id}),
		toRow: (item: QueryUsersByGroupResponseBody['items'][number]): MemberRow => ({id: item.username}),
	},
	clients: {
		query: (groupId: string, body: MemberPage) =>
			queries.queryClientsByGroup(groupId, narrowMemberSort(body, Object.values(MEMBER_SORT_FIELDS.clients))),
		assign: (groupId: string, id: string) => endpoints.assignClientToGroup({groupId, clientId: id}),
		unassign: (groupId: string, id: string) => endpoints.unassignClientFromGroup({groupId, clientId: id}),
		toRow: (item: QueryClientsByGroupResponseBody['items'][number]): MemberRow => ({id: item.clientId}),
	},
	roles: {
		query: (groupId: string, body: MemberPage) =>
			queries.queryRolesByGroup(groupId, narrowMemberSort(body, Object.values(MEMBER_SORT_FIELDS.roles))),
		assign: (groupId: string, id: string) => endpoints.assignGroupToRole({roleId: id, groupId}),
		unassign: (groupId: string, id: string) => endpoints.unassignGroupFromRole({roleId: id, groupId}),
		toRow: (item: QueryRolesByGroupResponseBody['items'][number]): MemberRow => ({id: item.roleId, name: item.name}),
	},
	mappingRules: {
		query: (groupId: string, body: MemberPage) =>
			queries.queryMappingRulesByGroup(groupId, narrowMemberSort(body, Object.values(MEMBER_SORT_FIELDS.mappingRules))),
		assign: (groupId: string, id: string) => endpoints.assignMappingToGroup({groupId, mappingRuleId: id}),
		unassign: (groupId: string, id: string) => endpoints.unassignMappingFromGroup({groupId, mappingRuleId: id}),
		toRow: (item: QueryMappingRulesByGroupResponseBody['items'][number]): MemberRow => ({
			id: item.mappingRuleId,
			name: item.name,
			claimName: item.claimName,
			claimValue: item.claimValue,
		}),
	},
} satisfies Record<MemberKind, unknown>;

function getMemberColumnSortField(kind: MemberKind, column: keyof MemberRow): MemberSortField | undefined {
	const sortFields: Partial<Record<keyof MemberRow, MemberSortField>> = MEMBER_SORT_FIELDS[kind];
	return sortFields[column];
}

function getMemberIdSortField(kind: MemberKind): MemberSortField {
	return MEMBER_SORT_FIELDS[kind].id;
}

function getMemberSortFields(kind: MemberKind): MemberSortField[] {
	return Object.values(MEMBER_SORT_FIELDS[kind]);
}

function getMemberSortRequest(kind: MemberKind, search: MemberSortSearch) {
	return getMemberSort(search, getMemberSortFields(kind));
}

function getMembersQuery(kind: MemberKind, groupId: string, body: MemberPage) {
	const query = MEMBER_KIND_CONFIG[kind].query(groupId, body);
	return {
		queryKey: query.queryKey as QueryKey,
		queryFn: query.queryFn as () => Promise<MembersResponse>,
	};
}

function toMemberRow(kind: MemberKind, item: MembersResponse['items'][number]): MemberRow {
	// The union of per-kind mappers cannot express that `item` belongs to `kind`.
	return (MEMBER_KIND_CONFIG[kind].toRow as (item: MembersResponse['items'][number]) => MemberRow)(item);
}

function getMemberId(kind: MemberKind, item: MembersResponse['items'][number]): string {
	return toMemberRow(kind, item).id;
}

function toOptimisticMember(kind: MemberKind, id: string): MembersResponse['items'][number] | undefined {
	if (kind === 'users') {
		return {username: id};
	}
	if (kind === 'clients') {
		return {clientId: id};
	}
	return undefined;
}

function compareMembersById(
	kind: MemberKind,
	a: MembersResponse['items'][number],
	b: MembersResponse['items'][number],
	order: 'ASC' | 'DESC' = 'ASC',
) {
	const idA = getMemberId(kind, a);
	const idB = getMemberId(kind, b);
	const comparison = idA < idB ? -1 : idA > idB ? 1 : 0;
	return order === 'DESC' ? -comparison : comparison;
}

function getAssignRequest(kind: MemberKind, groupId: string, id: string) {
	return MEMBER_KIND_CONFIG[kind].assign(groupId, id);
}

function getUnassignRequest(kind: MemberKind, groupId: string, id: string) {
	return MEMBER_KIND_CONFIG[kind].unassign(groupId, id);
}

function getVisibleMemberKinds({isOidc, canManageMappingRules}: {isOidc: boolean; canManageMappingRules: boolean}) {
	return MEMBER_KINDS.filter((kind) => {
		if (kind === 'mappingRules') {
			return canManageMappingRules;
		}
		return kind !== 'clients' || isOidc;
	});
}

function getActiveMemberKind(visibleKinds: readonly MemberKind[], requested: MemberKind | undefined): MemberKind {
	return requested !== undefined && visibleKinds.includes(requested) ? requested : 'users';
}

function getMembersQueryKeyPrefix(groupId: string, kind: MemberKind) {
	return ['groupMembers', groupId, kind] as const;
}

export {
	MEMBER_KINDS,
	compareMembersById,
	getActiveMemberKind,
	getAssignRequest,
	getMemberId,
	getMemberColumnSortField,
	getMemberIdSortField,
	getMemberSortFields,
	getMemberSortRequest,
	getMembersQuery,
	getMembersQueryKeyPrefix,
	getUnassignRequest,
	getVisibleMemberKinds,
	toMemberRow,
	toOptimisticMember,
};
export type {MemberKind, MemberPage, MemberRow, MembersResponse};
