/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {AssignMembersModal as SharedAssignMembersModal} from '../shared/AssignMembersModal';
import {useAssignCandidates} from '../shared/useAssignCandidates';
import type {TenantTab} from './detailSearchSchema';
import {getMemberKind} from './getMemberKind';
import {
	createMember,
	getMemberId,
	membersQuery,
	useTenantMemberMutations,
	type TenantMember,
} from './useTenantMemberMutations';

const CANDIDATE_LIMIT = 50;

type Props = {
	tab: TenantTab;
	tenantId: string;
	isOpen: boolean;
	isFreeText: boolean;
	onClose: () => void;
};

const AssignMembersModal: React.FC<Props> = ({tab, tenantId, isOpen, isFreeText, onClose}) => {
	const kind = getMemberKind(tab);
	const {assign} = useTenantMemberMutations(tab, tenantId);

	const candidates = useAssignCandidates({
		kind,
		enabled: !isFreeText,
		getAssignedMembersQuery: (page) => membersQuery(tab, tenantId, {page}),
		getMemberId: (member) => getMemberId(tab, member),
		limit: CANDIDATE_LIMIT,
	});

	const listedMembersById = useRef(new Map<string, TenantMember>());
	useEffect(() => {
		for (const item of candidates.items) {
			listedMembersById.current.set(getMemberId(tab, item), item);
		}
	}, [candidates.items, tab]);

	return (
		<SharedAssignMembersModal
			isOpen={isOpen}
			owner="tenants"
			kind={kind}
			entersIdAsText={isFreeText}
			candidates={candidates}
			isPending={assign.isPending}
			onAssign={(ids) =>
				assign
					.mutateAsync(ids.map((id) => listedMembersById.current.get(id) ?? createMember(tab, id)))
					.then(() => ({failed: []}))
					.catch(() => ({failed: ids.map((id) => ({id}))}))
			}
			onClose={onClose}
		/>
	);
};

export {AssignMembersModal};
