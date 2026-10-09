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
	CreateGlobalTaskListenerRequestBody,
	GlobalTaskListener,
	QueryGlobalTaskListenersRequestBody,
	UpdateGlobalTaskListenerRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';
import {queries} from '#/shared/http/queries';
import {patchListCaches, removeFromListCaches} from '#/shared/http/patchListCaches';
import {waitUntilGone, waitUntilReady} from '#/shared/http/waitUntilReady';

const LIST_QUERY_KEY_PREFIX = ['searchGlobalTaskListeners'];

function compareValues(a: string | number | boolean | null, b: string | number | boolean | null): number {
	if (a === b) {
		return 0;
	}
	if (a === null) {
		return -1;
	}
	if (b === null) {
		return 1;
	}
	return a < b ? -1 : 1;
}

function compareGlobalTaskListenersBySort(a: GlobalTaskListener, b: GlobalTaskListener, queryKey: QueryKey): number {
	const body = queryKey[1] as QueryGlobalTaskListenersRequestBody | undefined;
	const [sort] = body?.sort ?? [];
	if (sort === undefined) {
		return 0;
	}

	return (sort.order === 'desc' ? -1 : 1) * compareValues(a[sort.field] ?? null, b[sort.field] ?? null);
}

function globalTaskListenerMatchesFilter(
	globalTaskListener: GlobalTaskListener,
	filter: QueryGlobalTaskListenersRequestBody['filter'],
): boolean {
	const idFilter = filter?.id;
	if (idFilter === undefined) {
		return true;
	}
	if (typeof idFilter === 'string') {
		return globalTaskListener.id === idFilter;
	}

	const pattern = idFilter.$like;
	return pattern === undefined || globalTaskListener.id.includes(pattern.replaceAll('*', ''));
}

// Only insert into a page that is both searched for this listener and not already full - a full page
// may need an item pushed onto the next page, which a purely optimistic update cannot do safely.
function canInsertGlobalTaskListener(
	globalTaskListener: GlobalTaskListener,
	queryKey: QueryKey,
	currentItemCount: number,
): boolean {
	const body = queryKey[1] as QueryGlobalTaskListenersRequestBody | undefined;
	if (!globalTaskListenerMatchesFilter(globalTaskListener, body?.filter)) {
		return false;
	}

	const limit = body?.page?.limit;
	return limit === undefined || currentItemCount < limit;
}

function isSameConfiguration(a: GlobalTaskListener, b: GlobalTaskListener): boolean {
	return (
		a.type === b.type &&
		a.retries === b.retries &&
		a.afterNonGlobal === b.afterNonGlobal &&
		a.priority === b.priority &&
		a.eventTypes.length === b.eventTypes.length &&
		a.eventTypes.every((eventType) => b.eventTypes.includes(eventType))
	);
}

function useGlobalTaskListenerMutations() {
	const {t} = useTranslation();
	const queryClient = useQueryClient();

	const invalidateGlobalTaskListeners = () => queryClient.invalidateQueries({queryKey: LIST_QUERY_KEY_PREFIX});

	const reconcileGlobalTaskListeners = (confirmed: boolean) => {
		if (confirmed) {
			void invalidateGlobalTaskListeners();
		} else {
			toast.warning(t('admin.globalTaskListeners.changeNotConfirmed'), {
				description: t('admin.globalTaskListeners.changeNotConfirmedDescription'),
			});
		}
	};

	const patchCaches = (globalTaskListener: GlobalTaskListener) =>
		patchListCaches(queryClient, {
			queryKeyPrefix: LIST_QUERY_KEY_PREFIX,
			item: globalTaskListener,
			getId: (item: GlobalTaskListener) => item.id,
			compare: compareGlobalTaskListenersBySort,
			canInsert: canInsertGlobalTaskListener,
		});

	const create = useMutation({
		mutationFn: async (body: CreateGlobalTaskListenerRequestBody) => {
			const {response, error} = await request(endpoints.createGlobalTaskListener(body));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json() as Promise<GlobalTaskListener>;
		},
		onSuccess: (globalTaskListener) => {
			patchCaches(globalTaskListener);
			toast.success(t('admin.globalTaskListeners.createGlobalTaskListenerSuccess'));

			void waitUntilReady(queryClient, queries.getGlobalTaskListener(globalTaskListener.id)).then((ready) =>
				reconcileGlobalTaskListeners(ready !== undefined),
			);
		},
		onError: () => toast.error(t('admin.globalTaskListeners.createGlobalTaskListenerError')),
	});

	const update = useMutation({
		mutationFn: async (input: Pick<GlobalTaskListener, 'id'> & UpdateGlobalTaskListenerRequestBody) => {
			const {response, error} = await request(endpoints.updateGlobalTaskListener(input));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json() as Promise<GlobalTaskListener>;
		},
		onSuccess: (globalTaskListener) => {
			patchCaches(globalTaskListener);
			queryClient.setQueryData(queries.getGlobalTaskListener(globalTaskListener.id).queryKey, globalTaskListener);
			toast.success(t('admin.globalTaskListeners.updateGlobalTaskListenerSuccess'));

			void waitUntilReady(queryClient, queries.getGlobalTaskListener(globalTaskListener.id), {
				isReady: (data) => isSameConfiguration(data, globalTaskListener),
			}).then((ready) => reconcileGlobalTaskListeners(ready !== undefined));
		},
		onError: () => toast.error(t('admin.globalTaskListeners.updateGlobalTaskListenerError')),
	});

	const remove = useMutation({
		mutationFn: async ({id}: Pick<GlobalTaskListener, 'id'>) => {
			const {error} = await request(endpoints.deleteGlobalTaskListener({id}));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return id;
		},
		onSuccess: (id) => {
			removeFromListCaches(queryClient, {
				queryKeyPrefix: LIST_QUERY_KEY_PREFIX,
				id,
				getId: (item: GlobalTaskListener) => item.id,
			});
			queryClient.removeQueries({queryKey: queries.getGlobalTaskListener(id).queryKey});
			toast.success(t('admin.globalTaskListeners.deleteGlobalTaskListenerSuccess'));

			void waitUntilGone(queryClient, {
				queryKey: ['globalTaskListenerGone', id],
				queryFn: async () => {
					const {response, error} = await request(endpoints.getGlobalTaskListener({id}));
					if (error !== null) {
						throw mapQueryError(error);
					}
					return response.json();
				},
			}).then(reconcileGlobalTaskListeners);
		},
		onError: () => toast.error(t('admin.globalTaskListeners.deleteGlobalTaskListenerError')),
	});

	return {create, update, remove};
}

export {useGlobalTaskListenerMutations};
