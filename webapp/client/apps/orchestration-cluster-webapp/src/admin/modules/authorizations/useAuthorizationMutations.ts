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
import type {Authorization, QueryAuthorizationsRequestBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints, type CreateAuthorizationRequestBody} from '#/shared/http/endpoints';
import {queries} from '#/shared/http/queries';
import {isFirstPageWithRoom, patchListCaches, removeFromListCaches} from '#/shared/http/patchListCaches';
import {waitUntilGone, waitUntilReady} from '#/shared/http/waitUntilReady';
import {DEFAULT_SORT_FIELD} from './searchSchema';

const LIST_QUERY_KEY_PREFIX = ['queryAuthorizations'];

function getAuthorizationId({authorizationKey}: Pick<Authorization, 'authorizationKey'>): string {
	return authorizationKey;
}

function compareAuthorizations(a: Authorization, b: Authorization, queryKey: QueryKey): number {
	const sort = (queryKey[1] as QueryAuthorizationsRequestBody | undefined)?.sort?.[0];
	const field = sort?.field ?? DEFAULT_SORT_FIELD;
	const direction = sort?.order === 'desc' ? -1 : 1;
	const left = a[field] ?? '';
	const right = b[field] ?? '';

	if (left === right) {
		return a.authorizationKey < b.authorizationKey ? -1 : 1;
	}
	return direction * (left < right ? -1 : 1);
}

function canInsertAuthorization(authorization: Authorization, queryKey: QueryKey, currentItemCount: number): boolean {
	const body = queryKey[1] as QueryAuthorizationsRequestBody | undefined;
	const {resourceType, ownerId} = body?.filter ?? {};

	return (
		(resourceType === undefined || resourceType === authorization.resourceType) &&
		(ownerId === undefined || ownerId === authorization.ownerId) &&
		isFirstPageWithRoom(body?.page, currentItemCount)
	);
}

function useAuthorizationMutations() {
	const {t} = useTranslation();
	const queryClient = useQueryClient();

	const reconcileAuthorizations = (confirmed: boolean) => {
		if (confirmed) {
			void Promise.all([
				queryClient.invalidateQueries({queryKey: LIST_QUERY_KEY_PREFIX}),
				queryClient.invalidateQueries({queryKey: ['getAuthorization']}),
			]);
		} else {
			toast.warning(t('admin.authorizations.changeNotConfirmed'), {
				description: t('admin.authorizations.changeNotConfirmedDescription'),
			});
		}
	};

	const create = useMutation({
		mutationFn: async (authorization: CreateAuthorizationRequestBody) => {
			const {response, error} = await request(endpoints.createAuthorization(authorization));
			if (error !== null) {
				throw mapQueryError(error);
			}
			const {authorizationKey} = (await response.json()) as Pick<Authorization, 'authorizationKey'>;
			return {
				...authorization,
				resourcePropertyName: authorization.resourcePropertyName ?? null,
				authorizationKey,
			} as Authorization;
		},
		onSuccess: (authorization) => {
			patchListCaches(queryClient, {
				queryKeyPrefix: LIST_QUERY_KEY_PREFIX,
				item: authorization,
				getId: getAuthorizationId,
				compare: compareAuthorizations,
				canInsert: canInsertAuthorization,
			});
			toast.success(t('admin.authorizations.createAuthorizationSuccess'));

			void waitUntilReady(queryClient, queries.getAuthorization(authorization)).then((ready) =>
				reconcileAuthorizations(ready !== undefined),
			);
		},
		onError: () => toast.error(t('admin.authorizations.createAuthorizationError')),
	});

	const remove = useMutation({
		mutationFn: async (authorization: Pick<Authorization, 'authorizationKey'>) => {
			const {error} = await request(endpoints.deleteAuthorization(authorization));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return authorization;
		},
		onSuccess: ({authorizationKey}) => {
			removeFromListCaches(queryClient, {
				queryKeyPrefix: LIST_QUERY_KEY_PREFIX,
				id: authorizationKey,
				getId: getAuthorizationId,
			});
			queryClient.removeQueries({queryKey: queries.getAuthorization({authorizationKey}).queryKey});
			toast.success(t('admin.authorizations.deleteAuthorizationSuccess'));

			void waitUntilGone(queryClient, {
				queryKey: ['authorizationGone', authorizationKey],
				queryFn: async () => {
					const {response, error} = await request(endpoints.getAuthorization({authorizationKey}));
					if (error !== null) {
						throw mapQueryError(error);
					}
					return response.json();
				},
			}).then(reconcileAuthorizations);
		},
		onError: () => toast.error(t('admin.authorizations.deleteAuthorizationError')),
	});

	return {create, remove};
}

export {useAuthorizationMutations};
