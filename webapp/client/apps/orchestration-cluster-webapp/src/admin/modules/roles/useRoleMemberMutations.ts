/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {toast} from '@camunda/design-system';
import {
	getAssignRequest,
	getMembersQuery,
	getMembersQueryKeyPrefix,
	getUnassignRequest,
	toMemberRow,
	type MemberKind,
	type MembersResponse,
} from './members';
import {request} from '#/shared/http/request';
import {getErrorMessage} from '#/shared/http/getErrorMessage';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {removeFromListCaches} from '#/shared/http/patchListCaches';
import {waitUntilReady} from '#/shared/http/waitUntilReady';

type MemberChange = {kind: MemberKind; id: string};
type Direction = 'assign' | 'unassign';

const SCAN_PAGE_SIZE = 100;

async function hasMember(kind: MemberKind, roleId: string, id: string): Promise<boolean> {
	for (let from = 0; ; from += SCAN_PAGE_SIZE) {
		const data = await getMembersQuery(kind, roleId, {page: {from, limit: SCAN_PAGE_SIZE}}).queryFn();
		if (data.items.some((item) => toMemberRow(kind, item).id === id)) {
			return true;
		}
		if (from + SCAN_PAGE_SIZE >= data.page.totalItems) {
			return false;
		}
	}
}

function useRoleMemberMutations(roleId: string) {
	const {t} = useTranslation();
	const queryClient = useQueryClient();

	const mutate =
		(direction: Direction) =>
		async ({kind, id}: MemberChange) => {
			const buildRequest = direction === 'assign' ? getAssignRequest : getUnassignRequest;
			const {error} = await request(buildRequest(kind, roleId, id));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return {kind, id};
		};

	const reconcile = async (direction: Direction, {kind, id}: MemberChange) => {
		const isAssigned = await waitUntilReady(
			queryClient,
			{queryKey: ['roleMemberCheck', roleId, kind, id], queryFn: () => hasMember(kind, roleId, id)},
			{isReady: (assigned) => assigned === (direction === 'assign')},
		);

		if (isAssigned === undefined) {
			toast.warning(t('admin.roles.changeNotConfirmed'), {
				description: t('admin.roles.changeNotConfirmedDescription'),
			});
			return;
		}
		void queryClient.invalidateQueries({queryKey: getMembersQueryKeyPrefix(roleId, kind)});
	};

	const assign = useMutation({
		mutationFn: mutate('assign'),
		onSuccess: (result) => {
			toast.success(t(`admin.roles.members.${result.kind}.assigned`, {id: result.id}));
			void reconcile('assign', result);
		},
		onError: async (error) => {
			toast.error(t('admin.roles.members.assignFailed'), {description: await getErrorMessage(error)});
		},
	});

	const unassign = useMutation({
		mutationFn: mutate('unassign'),
		onSuccess: (result) => {
			removeFromListCaches(queryClient, {
				queryKeyPrefix: getMembersQueryKeyPrefix(roleId, result.kind),
				id: result.id,
				getId: (item: MembersResponse['items'][number]) => toMemberRow(result.kind, item).id,
			});
			toast.success(t(`admin.roles.members.${result.kind}.removed`, {id: result.id}));
			void reconcile('unassign', result);
		},
		onError: async (error) => {
			toast.error(t('admin.roles.members.removeFailed'), {description: await getErrorMessage(error)});
		},
	});

	return {assign, unassign};
}

export {useRoleMemberMutations};
