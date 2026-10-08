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
	type MemberPage,
	type MembersResponse,
} from './members';
import {request} from '#/shared/http/request';
import {getErrorMessage} from '#/shared/http/getErrorMessage';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {removeFromListCaches} from '#/shared/http/patchListCaches';
import {waitUntilReady} from '#/shared/http/waitUntilReady';

type MemberChange = {kind: MemberKind; id: string};
type Direction = 'assign' | 'unassign';

function useGroupMemberMutations(groupId: string) {
	const {t} = useTranslation();
	const queryClient = useQueryClient();

	const getActiveLists = (kind: MemberKind) =>
		queryClient
			.getQueriesData<MembersResponse>({queryKey: getMembersQueryKeyPrefix(groupId, kind), type: 'active'})
			.flatMap(([queryKey, data]) => (data === undefined ? [] : [{queryKey, totalItems: data.page.totalItems}]));

	const mutate =
		(direction: Direction) =>
		async ({kind, id}: MemberChange) => {
			const activeLists = getActiveLists(kind);
			const buildRequest = direction === 'assign' ? getAssignRequest : getUnassignRequest;
			const {error} = await request(buildRequest(kind, groupId, id));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return {kind, id, activeLists};
		};

	const reconcile = async (
		direction: Direction,
		{kind, activeLists}: Awaited<ReturnType<ReturnType<typeof mutate>>>,
	) => {
		const confirmations = await Promise.all(
			activeLists.map(({queryKey, totalItems}) =>
				waitUntilReady(queryClient, getMembersQuery(kind, groupId, queryKey[3] as MemberPage), {
					isReady: (data) =>
						direction === 'assign' ? data.page.totalItems > totalItems : data.page.totalItems < totalItems,
				}),
			),
		);

		if (confirmations.some((confirmed) => confirmed === undefined)) {
			toast.warning(t('admin.groups.changeNotConfirmed'), {
				description: t('admin.groups.changeNotConfirmedDescription'),
			});
		}
	};

	const assign = useMutation({
		mutationFn: mutate('assign'),
		onSuccess: (result) => {
			toast.success(t(`admin.groups.members.${result.kind}.assigned`, {id: result.id}));
			void reconcile('assign', result);
		},
		onError: async (error) => {
			toast.error(t('admin.groups.members.assignFailed'), {description: await getErrorMessage(error)});
		},
	});

	const unassign = useMutation({
		mutationFn: mutate('unassign'),
		onSuccess: (result) => {
			removeFromListCaches(queryClient, {
				queryKeyPrefix: getMembersQueryKeyPrefix(groupId, result.kind),
				id: result.id,
				getId: (item: MembersResponse['items'][number]) => toMemberRow(result.kind, item).id,
			});
			toast.success(t(`admin.groups.members.${result.kind}.removed`, {id: result.id}));
			void reconcile('unassign', result);
		},
		onError: async (error) => {
			toast.error(t('admin.groups.members.removeFailed'), {description: await getErrorMessage(error)});
		},
	});

	return {assign, unassign};
}

export {useGroupMemberMutations};
