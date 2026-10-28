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

type MembersChange = {kind: MemberKind; ids: string[]};
type Direction = 'assign' | 'unassign';

const SCAN_PAGE_SIZE = 100;

// One pass over the role's members finds every wanted ID, so confirming several changes costs a single scan.
async function findMembers(kind: MemberKind, roleId: string, ids: string[]): Promise<Set<string>> {
	const found = new Set<string>();
	for (let from = 0; ; from += SCAN_PAGE_SIZE) {
		const data = await getMembersQuery(kind, roleId, {page: {from, limit: SCAN_PAGE_SIZE}}).queryFn();
		for (const item of data.items) {
			const id = toMemberRow(kind, item).id;
			if (ids.includes(id)) {
				found.add(id);
			}
		}
		if (found.size === ids.length || from + SCAN_PAGE_SIZE >= data.page.totalItems) {
			return found;
		}
	}
}

function useRoleMemberMutations(roleId: string) {
	const {t} = useTranslation();
	const queryClient = useQueryClient();

	const mutate =
		(direction: Direction) =>
		async ({kind, ids}: MembersChange) => {
			const buildRequest = direction === 'assign' ? getAssignRequest : getUnassignRequest;
			const outcomes = await Promise.all(
				ids.map(async (id) => {
					const {error} = await request(buildRequest(kind, roleId, id));
					return {id, error: error === null ? null : mapQueryError(error)};
				}),
			);

			const succeeded = outcomes.filter(({error}) => error === null).map(({id}) => id);
			const failed = outcomes.flatMap(({id, error}) => (error === null ? [] : [{id, error}]));
			const [firstFailure] = failed;
			if (succeeded.length === 0 && firstFailure !== undefined) {
				throw firstFailure.error;
			}
			return {kind, ids: succeeded, failed};
		};

	const reconcile = async (direction: Direction, {kind, ids}: {kind: MemberKind; ids: string[]}) => {
		const shouldBeListed = direction === 'assign';
		let latest: Set<string> | undefined;
		await waitUntilReady(
			queryClient,
			{
				queryKey: ['roleMemberCheck', roleId, kind, ...ids],
				queryFn: async () => (latest = await findMembers(kind, roleId, ids)),
			},
			{isReady: (found) => ids.every((id) => found.has(id) === shouldBeListed)},
		);

		const confirmed = latest === undefined ? [] : ids.filter((id) => latest?.has(id) === shouldBeListed);
		if (confirmed.length < ids.length) {
			toast.warning(t('admin.roles.changeNotConfirmed'), {
				description: t('admin.roles.changeNotConfirmedDescription'),
			});
		}
		if (confirmed.length > 0) {
			void queryClient.invalidateQueries({queryKey: getMembersQueryKeyPrefix(roleId, kind)});
		}
	};

	const assign = useMutation({
		mutationFn: mutate('assign'),
		onSuccess: (result) => {
			for (const id of result.ids) {
				toast.success(t(`admin.roles.members.${result.kind}.assigned`, {id}));
			}
			for (const {error} of result.failed) {
				void getErrorMessage(error).then((description) =>
					toast.error(t('admin.roles.members.assignFailed'), {description}),
				);
			}
			void reconcile('assign', result);
		},
		onError: async (error) => {
			toast.error(t('admin.roles.members.assignFailed'), {description: await getErrorMessage(error)});
		},
	});

	const unassign = useMutation({
		mutationFn: ({kind, id}: {kind: MemberKind; id: string}) => mutate('unassign')({kind, ids: [id]}),
		onSuccess: (result) => {
			for (const id of result.ids) {
				removeFromListCaches(queryClient, {
					queryKeyPrefix: getMembersQueryKeyPrefix(roleId, result.kind),
					id,
					getId: (item: MembersResponse['items'][number]) => toMemberRow(result.kind, item).id,
				});
				toast.success(t(`admin.roles.members.${result.kind}.removed`, {id}));
			}
			void reconcile('unassign', result);
		},
		onError: async (error) => {
			toast.error(t('admin.roles.members.removeFailed'), {description: await getErrorMessage(error)});
		},
	});

	return {assign, unassign};
}

export {useRoleMemberMutations};
