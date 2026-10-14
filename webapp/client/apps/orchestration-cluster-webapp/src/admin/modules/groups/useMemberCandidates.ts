/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import type {MultiSelectOption} from '@camunda/design-system';
import type {MemberKind} from './members';
import {queries} from '#/shared/http/queries';

const CANDIDATE_LIMIT = 20;
const CANDIDATE_SEARCH_DEBOUNCE = 300;

type Candidates = {
	options: MultiSelectOption[];
	isError: boolean;
	setSearch: (search: string) => void;
};

// Members already on the current page are excluded on the server, so a candidate limit
// filled with assigned members cannot hide unassigned ones.
function useMemberCandidates(kind: MemberKind, isOidc: boolean, assignedIds: string[]): Candidates {
	const [search, setSearch] = useState('');
	const [searchTerm, setSearchTerm] = useState('');

	useEffect(() => {
		const timeoutId = setTimeout(() => setSearchTerm(search), CANDIDATE_SEARCH_DEBOUNCE);
		return () => clearTimeout(timeoutId);
	}, [search]);

	const idFilter = {
		...(searchTerm === '' ? {} : {$like: `*${searchTerm}*`}),
		...(assignedIds.length === 0 ? {} : {$notIn: assignedIds}),
	};
	const hasIdFilter = Object.keys(idFilter).length > 0;
	const page = {from: 0, limit: CANDIDATE_LIMIT};

	const users = useQuery({
		...queries.queryUsers({filter: hasIdFilter ? {username: idFilter} : {}, page}),
		enabled: kind === 'users' && !isOidc,
	});
	const roles = useQuery({
		...queries.queryRoles({filter: hasIdFilter ? {roleId: idFilter} : {}, page}),
		enabled: kind === 'roles',
	});
	const mappingRules = useQuery({
		...queries.queryMappingRules({filter: hasIdFilter ? {mappingRuleId: idFilter} : {}, page}),
		enabled: kind === 'mappingRules',
	});

	switch (kind) {
		case 'users':
			return {
				options: (users.data?.items ?? []).map(({username}) => ({value: username, label: username})),
				isError: users.isError,
				setSearch,
			};
		case 'roles':
			return {
				options: (roles.data?.items ?? []).map(({roleId}) => ({value: roleId, label: roleId})),
				isError: roles.isError,
				setSearch,
			};
		case 'mappingRules':
			return {
				options: (mappingRules.data?.items ?? []).map(({mappingRuleId}) => ({
					value: mappingRuleId,
					label: mappingRuleId,
				})),
				isError: mappingRules.isError,
				setSearch,
			};
		case 'clients':
			return {options: [], isError: false, setSearch};
	}
}

export {useMemberCandidates};
