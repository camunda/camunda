/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import type {MultiSelectOption} from '@camunda/design-system';
import {queries} from '#/shared/http/queries';
import {useDebouncedValue} from '#/shared/hooks/useDebouncedValue';

const CANDIDATE_SEARCH_DEBOUNCE = 300;

type CandidateKind = 'users' | 'groups' | 'roles' | 'mappingRules' | 'clients';

type Candidates = {
	options: MultiSelectOption[];
	items: Record<string, unknown>[];
	isError: boolean;
	setSearch: (search: string) => void;
};

type Params = {
	kind: CandidateKind;
	enabled: boolean;
	assignedIds: string[];
	limit: number;
};

function useMemberCandidates({kind, enabled, assignedIds, limit}: Params): Candidates {
	const [search, setSearch] = useState('');
	const searchTerm = useDebouncedValue(search, CANDIDATE_SEARCH_DEBOUNCE);

	const idFilter = {
		...(searchTerm === '' ? {} : {$like: `*${searchTerm}*`}),
		...(assignedIds.length === 0 ? {} : {$notIn: assignedIds}),
	};
	const hasIdFilter = Object.keys(idFilter).length > 0;
	const page = {from: 0, limit};

	const users = useQuery({
		...queries.queryUsers({filter: hasIdFilter ? {username: idFilter} : {}, page}),
		enabled: enabled && kind === 'users',
	});
	const groups = useQuery({
		...queries.queryGroups({filter: hasIdFilter ? {groupId: idFilter} : {}, page}),
		enabled: enabled && kind === 'groups',
	});
	const roles = useQuery({
		...queries.queryRoles({filter: hasIdFilter ? {roleId: idFilter} : {}, page}),
		enabled: enabled && kind === 'roles',
	});
	const mappingRules = useQuery({
		...queries.queryMappingRules({filter: hasIdFilter ? {mappingRuleId: idFilter} : {}, page}),
		enabled: enabled && kind === 'mappingRules',
	});

	switch (kind) {
		case 'users':
			return {
				items: users.data?.items ?? [],
				options: (users.data?.items ?? []).map(({username}) => ({value: username, label: username})),
				isError: users.isError,
				setSearch,
			};
		case 'groups':
			return {
				items: groups.data?.items ?? [],
				options: (groups.data?.items ?? []).map(({groupId}) => ({value: groupId, label: groupId})),
				isError: groups.isError,
				setSearch,
			};
		case 'roles':
			return {
				items: roles.data?.items ?? [],
				options: (roles.data?.items ?? []).map(({roleId}) => ({value: roleId, label: roleId})),
				isError: roles.isError,
				setSearch,
			};
		case 'mappingRules':
			return {
				items: mappingRules.data?.items ?? [],
				options: (mappingRules.data?.items ?? []).map(({mappingRuleId}) => ({
					value: mappingRuleId,
					label: mappingRuleId,
				})),
				isError: mappingRules.isError,
				setSearch,
			};
		case 'clients':
			return {options: [], items: [], isError: false, setSearch};
	}
}

export {useMemberCandidates};
export type {CandidateKind};
