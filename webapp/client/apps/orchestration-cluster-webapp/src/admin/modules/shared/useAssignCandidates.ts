/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery, type QueryFunction, type QueryKey} from '@tanstack/react-query';
import {useMemberCandidates, type CandidateKind} from './useMemberCandidates';

const ASSIGNED_MEMBERS_LIMIT = 1000;

type Params<TResponse extends {items: unknown[]}> = {
	kind: CandidateKind;
	enabled: boolean;
	getAssignedMembersQuery: (page: {from: number; limit: number}) => {
		queryKey: QueryKey;
		queryFn: QueryFunction<TResponse, QueryKey, never>;
	};
	getMemberId: (member: TResponse['items'][number]) => string;
	limit: number;
};

function useAssignCandidates<TResponse extends {items: unknown[]}>({
	kind,
	enabled,
	getAssignedMembersQuery,
	getMemberId,
	limit,
}: Params<TResponse>) {
	const {data: assignedMembers, isError: isAssignedError} = useQuery({
		...getAssignedMembersQuery({from: 0, limit: ASSIGNED_MEMBERS_LIMIT}),
		enabled,
	});
	const hasAssignedMembers = enabled && assignedMembers !== undefined;
	const candidates = useMemberCandidates({
		kind,
		enabled: hasAssignedMembers,
		assignedIds: (assignedMembers?.items ?? []).map(getMemberId),
		limit,
	});

	return {
		...candidates,
		options: hasAssignedMembers ? candidates.options : [],
		items: hasAssignedMembers ? candidates.items : [],
		isError: candidates.isError || isAssignedError,
	};
}

export {useAssignCandidates};
