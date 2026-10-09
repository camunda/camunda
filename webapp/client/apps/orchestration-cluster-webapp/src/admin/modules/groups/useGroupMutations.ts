/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient, type QueryKey} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {toast} from '@camunda/design-system';
import type {
	CreateGroupRequestBody,
	Group,
	QueryGroupsRequestBody,
	UpdateGroupRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {isDuplicateGroupIdError} from './isDuplicateGroupIdError';
import {request} from '#/shared/http/request';
import {getErrorMessage} from '#/shared/http/getErrorMessage';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';
import {queries} from '#/shared/http/queries';
import {isFirstPageWithRoom, patchListCaches, removeFromListCaches} from '#/shared/http/patchListCaches';
import {waitUntilGone, waitUntilReady} from '#/shared/http/waitUntilReady';

function compareGroupsBySort(a: Group, b: Group, queryKey: QueryKey): number {
	const body = queryKey[1] as QueryGroupsRequestBody | undefined;
	const [sort] = body?.sort ?? [];
	if (sort === undefined) {
		return 0;
	}

	const valueA = a[sort.field];
	const valueB = b[sort.field];
	const direction = sort.order === 'desc' ? -1 : 1;
	return direction * (valueA < valueB ? -1 : valueA > valueB ? 1 : 0);
}

function groupMatchesFilter(group: Group, filter: QueryGroupsRequestBody['filter']): boolean {
	const groupIdFilter = filter?.groupId;
	if (groupIdFilter === undefined) {
		return true;
	}
	if (typeof groupIdFilter === 'string') {
		return group.groupId === groupIdFilter;
	}

	const pattern = groupIdFilter.$like;
	return pattern === undefined || group.groupId.includes(pattern.replaceAll('*', ''));
}

function canInsertGroup(group: Group, queryKey: QueryKey, currentItemCount: number): boolean {
	const body = queryKey[1] as QueryGroupsRequestBody | undefined;
	return groupMatchesFilter(group, body?.filter) && isFirstPageWithRoom(body?.page, currentItemCount);
}

function useGroupMutations() {
	const {t} = useTranslation();
	const queryClient = useQueryClient();

	const invalidateGroups = () => queryClient.invalidateQueries({queryKey: ['groups']});

	const reconcileGroups = (confirmed: boolean) => {
		if (confirmed) {
			void invalidateGroups();
		} else {
			toast.warning(t('admin.groups.changeNotConfirmed'), {
				description: t('admin.groups.changeNotConfirmedDescription'),
			});
		}
	};

	const create = useMutation({
		mutationFn: async (body: CreateGroupRequestBody) => {
			const {response, error} = await request(endpoints.createGroup(body));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json() as Promise<Group>;
		},
		onSuccess: (group) => {
			patchListCaches(queryClient, {
				queryKeyPrefix: ['groups'],
				item: group,
				getId: (g: Group) => g.groupId,
				compare: compareGroupsBySort,
				canInsert: canInsertGroup,
			});
			toast.success(t('admin.groups.groupCreated', {name: group.name}));

			void waitUntilReady(queryClient, queries.getGroup(group.groupId)).then((ready) =>
				reconcileGroups(ready !== undefined),
			);
		},
		onError: async (error) => {
			if (isDuplicateGroupIdError(error)) {
				return;
			}
			toast.error(t('admin.groups.createGroupFailed'), {description: await getErrorMessage(error)});
		},
	});

	const update = useMutation({
		mutationFn: async (input: {groupId: string} & UpdateGroupRequestBody) => {
			const {response, error} = await request(endpoints.updateGroup(input));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json() as Promise<Group>;
		},
		onSuccess: (group) => {
			patchListCaches(queryClient, {
				queryKeyPrefix: ['groups'],
				item: group,
				getId: (g: Group) => g.groupId,
				compare: compareGroupsBySort,
				canInsert: canInsertGroup,
			});
			queryClient.setQueryData(queries.getGroup(group.groupId).queryKey, group);
			toast.success(t('admin.groups.groupUpdated', {name: group.name}));

			void waitUntilReady(queryClient, queries.getGroup(group.groupId), {
				isReady: (data) => data.name === group.name && data.description === group.description,
			}).then((ready) => reconcileGroups(ready !== undefined));
		},
		onError: async (error) => {
			toast.error(t('admin.groups.updateGroupFailed'), {description: await getErrorMessage(error)});
		},
	});

	const remove = useMutation({
		mutationFn: async (groupId: string) => {
			const {error} = await request(endpoints.deleteGroup({groupId}));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return groupId;
		},
		onSuccess: (groupId) => {
			removeFromListCaches(queryClient, {queryKeyPrefix: ['groups'], id: groupId, getId: (g: Group) => g.groupId});
			queryClient.removeQueries({queryKey: queries.getGroup(groupId).queryKey});
			toast.success(t('admin.groups.groupDeleted', {groupId}));

			void waitUntilGone(queryClient, {
				queryKey: ['groupGone', groupId],
				queryFn: async () => {
					const {response, error} = await request(endpoints.getGroup({groupId}));
					if (error !== null) {
						throw mapQueryError(error);
					}
					return response.json();
				},
			}).then(reconcileGroups);
		},
		onError: async (error) => {
			toast.error(t('admin.groups.deleteGroupFailed'), {description: await getErrorMessage(error)});
		},
	});

	return {create, update, remove};
}

export {useGroupMutations};
