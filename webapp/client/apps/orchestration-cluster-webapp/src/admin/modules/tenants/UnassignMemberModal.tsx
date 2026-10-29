/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {RemoveMemberModal} from '../shared/RemoveMemberModal';
import type {TenantTab} from './detailSearchSchema';
import {getMemberKind} from './getMemberKind';
import {useTenantMemberMutations} from './useTenantMemberMutations';

type Props = {
	tab: TenantTab;
	tenantId: string;
	memberId: string | null;
	onClose: () => void;
};

const UnassignMemberModal: React.FC<Props> = ({tab, tenantId, memberId, onClose}) => {
	const {unassign} = useTenantMemberMutations(tab, tenantId);

	return (
		<RemoveMemberModal
			isOpen={memberId !== null}
			owner="tenants"
			kind={getMemberKind(tab)}
			memberId={memberId ?? ''}
			isPending={unassign.isPending}
			onRemove={() => {
				if (memberId !== null) {
					unassign.mutate(memberId, {onSuccess: onClose});
				}
			}}
			onClose={onClose}
		/>
	);
};

export {UnassignMemberModal};
