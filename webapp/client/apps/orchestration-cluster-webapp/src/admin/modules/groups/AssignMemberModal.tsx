/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {AssignMembersModal} from '../shared/AssignMembersModal';
import {useAssignCandidates} from '../shared/useAssignCandidates';
import {getMembersQuery, toMemberRow, type MemberKind} from './members';
import {useGroupMemberMutations} from './useGroupMemberMutations';

const CANDIDATE_LIMIT = 20;

type Props = {
	isOpen: boolean;
	groupId: string;
	kind: MemberKind;
	isOidc: boolean;
	onClose: () => void;
};

const AssignMemberModal: React.FC<Props> = ({isOpen, groupId, kind, isOidc, onClose}) => {
	const {assign} = useGroupMemberMutations(groupId);
	const hasUsersInIdentityProvider = kind === 'users' && isOidc;
	const entersIdAsText = kind === 'clients' || hasUsersInIdentityProvider;

	const candidates = useAssignCandidates({
		kind,
		enabled: !entersIdAsText,
		getAssignedMembersQuery: (page) => getMembersQuery(kind, groupId, {page}),
		getMemberId: (member) => toMemberRow(kind, member).id,
		limit: CANDIDATE_LIMIT,
	});

	return (
		<AssignMembersModal
			isOpen={isOpen}
			owner="groups"
			kind={kind}
			entersIdAsText={entersIdAsText}
			candidates={candidates}
			isPending={assign.isPending}
			onAssign={(ids) => assign.mutateAsync({kind, ids}).catch(() => ({failed: ids.map((id) => ({id}))}))}
			onClose={onClose}
		/>
	);
};

export {AssignMemberModal};
