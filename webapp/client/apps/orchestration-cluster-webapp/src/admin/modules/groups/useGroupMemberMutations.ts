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
	compareMembersById,
	getAssignRequest,
	getMembersQuery,
	getMembersQueryKeyPrefix,
	getUnassignRequest,
	toMemberRow,
	toOptimisticMember,
	type MemberKind,
	type MemberPage,
	type MembersResponse,
} from './members';
import {request} from '#/shared/http/request';
import {getErrorMessage} from '#/shared/http/getErrorMessage';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {isFirstPageWithRoom, patchListCaches, removeFromListCaches} from '#/shared/http/patchListCaches';
import {waitUntilReady} from '#/shared/http/waitUntilReady';

type MembersChange = {kind: MemberKind; ids: string[]};
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
		async ({kind, ids}: MembersChange) => {
			const activeLists = getActiveLists(kind);
			const buildRequest = direction === 'assign' ? getAssignRequest : getUnassignRequest;
			const outcomes = await Promise.all(
				ids.map(async (id) => {
					const {error} = await request(buildRequest(kind, groupId, id));
					return {id, error: error === null ? null : mapQueryError(error)};
				}),
			);

			const succeeded = outcomes.filter(({error}) => error === null).map(({id}) => id);
			const failed = outcomes.flatMap(({id, error}) => (error === null ? [] : [{id, error}]));
			const [firstFailure] = failed;
			if (succeeded.length === 0 && firstFailure !== undefined) {
				throw firstFailure.error;
			}
			return {kind, ids: succeeded, failed, activeLists};
		};

	const reconcile = async (
		direction: Direction,
		{kind, ids, activeLists}: Awaited<ReturnType<ReturnType<typeof mutate>>>,
	) => {
		const confirmations = await Promise.all(
			activeLists.map(({queryKey, totalItems}) =>
				waitUntilReady(queryClient, getMembersQuery(kind, groupId, queryKey[3] as MemberPage), {
					isReady: (data) =>
						direction === 'assign'
							? data.page.totalItems >= totalItems + ids.length
							: data.page.totalItems <= totalItems - ids.length,
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
			for (const id of result.ids) {
				const item = toOptimisticMember(result.kind, id);
				if (item !== undefined) {
					patchListCaches(queryClient, {
						queryKeyPrefix: getMembersQueryKeyPrefix(groupId, result.kind),
						item,
						getId: (member: MembersResponse['items'][number]) => toMemberRow(result.kind, member).id,
						compare: (a, b) => compareMembersById(result.kind, a, b),
						canInsert: (_member, queryKey, itemCount) =>
							isFirstPageWithRoom((queryKey[3] as MemberPage).page, itemCount),
					});
				}
				toast.success(t(`admin.groups.members.${result.kind}.assigned`, {id}));
			}
			for (const {error} of result.failed) {
				void getErrorMessage(error).then((description) =>
					toast.error(t('admin.groups.members.assignFailed'), {description}),
				);
			}
			void reconcile('assign', result);
		},
		onError: async (error) => {
			toast.error(t('admin.groups.members.assignFailed'), {description: await getErrorMessage(error)});
		},
	});

	const unassign = useMutation({
		mutationFn: ({kind, id}: {kind: MemberKind; id: string}) => mutate('unassign')({kind, ids: [id]}),
		onSuccess: (result) => {
			for (const id of result.ids) {
				removeFromListCaches(queryClient, {
					queryKeyPrefix: getMembersQueryKeyPrefix(groupId, result.kind),
					id,
					getId: (item: MembersResponse['items'][number]) => toMemberRow(result.kind, item).id,
				});
				toast.success(t(`admin.groups.members.${result.kind}.removed`, {id}));
			}
			void reconcile('unassign', result);
		},
		onError: async (error) => {
			toast.error(t('admin.groups.members.removeFailed'), {description: await getErrorMessage(error)});
		},
	});

	return {assign, unassign};
}

export {useGroupMemberMutations};
