/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {RemoveMemberModal as SharedRemoveMemberModal} from '../shared/RemoveMemberModal';
import type {MemberKind} from './members';
import {useRoleMemberMutations} from './useRoleMemberMutations';

type Props = {
	isOpen: boolean;
	roleId: string;
	kind: MemberKind;
	memberId: string;
	onClose: () => void;
};

const RemoveMemberModal: React.FC<Props> = ({isOpen, roleId, kind, memberId, onClose}) => {
	const {unassign} = useRoleMemberMutations(roleId);

	return (
		<SharedRemoveMemberModal
			isOpen={isOpen}
			owner="roles"
			kind={kind}
			memberId={memberId}
			isPending={unassign.isPending}
			onRemove={() => unassign.mutate({kind, id: memberId}, {onSuccess: onClose})}
			onClose={onClose}
		/>
	);
};

export {RemoveMemberModal};
