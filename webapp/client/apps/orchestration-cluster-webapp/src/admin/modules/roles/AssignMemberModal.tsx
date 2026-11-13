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
import {useRoleMemberMutations} from './useRoleMemberMutations';

const CANDIDATE_LIMIT = 20;

type Props = {
	isOpen: boolean;
	roleId: string;
	kind: MemberKind;
	isOidc: boolean;
	isCamundaGroupsEnabled: boolean;
	onClose: () => void;
};

const AssignMemberModal: React.FC<Props> = ({isOpen, roleId, kind, isOidc, isCamundaGroupsEnabled, onClose}) => {
	const {assign} = useRoleMemberMutations(roleId);
	const hasUsersInIdentityProvider = kind === 'users' && isOidc;
	const hasGroupsOutsideCamunda = kind === 'groups' && !isCamundaGroupsEnabled;
	const entersIdAsText = kind === 'clients' || hasUsersInIdentityProvider || hasGroupsOutsideCamunda;

	const candidates = useAssignCandidates({
		kind,
		enabled: !entersIdAsText,
		getAssignedMembersQuery: (page) => getMembersQuery(kind, roleId, {page}),
		getMemberId: (member) => toMemberRow(kind, member).id,
		limit: CANDIDATE_LIMIT,
	});

	return (
		<AssignMembersModal
			isOpen={isOpen}
			owner="roles"
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
