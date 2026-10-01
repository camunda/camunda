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
import {
	problemDetailResponseSchema,
	type CreateUserRequestBody,
	type QueryUsersRequestBody,
	type UpdateUserRequestBody,
	type User,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {request, requestErrorSchema} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';
import {queries} from '#/shared/http/queries';
import {patchListCaches, removeFromListCaches} from '#/shared/http/patchListCaches';
import {waitUntilGone, waitUntilReady} from '#/shared/http/waitUntilReady';

async function getErrorMessage(error: unknown): Promise<string | undefined> {
	if (error instanceof Error) {
		return error.message;
	}

	const requestError = requestErrorSchema.safeParse(error);
	if (!requestError.success) {
		return undefined;
	}

	if (requestError.data.variant === 'network-error') {
		return requestError.data.networkError.message;
	}

	const {response} = requestError.data;
	const problemDetails = await response
		.json()
		.then((body: unknown) => problemDetailResponseSchema.safeParse(body))
		.catch(() => undefined);

	return problemDetails?.success ? problemDetails.data.detail : response.statusText || undefined;
}

function compareUsersBySort(a: User, b: User, queryKey: QueryKey): number {
	const body = queryKey[1] as QueryUsersRequestBody | undefined;
	const [sort] = body?.sort ?? [];
	if (sort === undefined) {
		return 0;
	}

	const valueA = a[sort.field];
	const valueB = b[sort.field];
	const direction = sort.order === 'desc' ? -1 : 1;
	return direction * (valueA < valueB ? -1 : valueA > valueB ? 1 : 0);
}

function useUserMutations() {
	const {t} = useTranslation();
	const queryClient = useQueryClient();

	const invalidateUsers = () => queryClient.invalidateQueries({queryKey: ['users']});

	const create = useMutation({
		mutationFn: async (body: CreateUserRequestBody) => {
			const {response, error} = await request(endpoints.createUser(body));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json() as Promise<User>;
		},
		onSuccess: (user) => {
			patchListCaches(queryClient, {
				queryKeyPrefix: ['users'],
				item: user,
				getId: (u: User) => u.username,
				compare: compareUsersBySort,
			});
			toast.success(t('admin.users.userCreated', {username: user.username}));

			void waitUntilReady(queryClient, queries.getUser(user.username)).then((ready) => {
				if (ready) {
					invalidateUsers();
				}
			});
		},
		onError: async (error) => {
			toast.error(t('admin.users.createUserFailed'), {description: await getErrorMessage(error)});
		},
	});

	const update = useMutation({
		mutationFn: async (input: {username: string} & UpdateUserRequestBody) => {
			const {response, error} = await request(endpoints.updateUser(input));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json() as Promise<User>;
		},
		onSuccess: (user) => {
			patchListCaches(queryClient, {
				queryKeyPrefix: ['users'],
				item: user,
				getId: (u: User) => u.username,
				compare: compareUsersBySort,
			});
			queryClient.setQueryData(queries.getUser(user.username).queryKey, user);
			toast.success(t('admin.users.userUpdated', {username: user.username}));

			void waitUntilReady(queryClient, queries.getUser(user.username), {
				isReady: (data) => data.name === user.name && data.email === user.email,
			}).then((ready) => {
				if (ready) {
					invalidateUsers();
				}
			});
		},
		onError: async (error) => {
			toast.error(t('admin.users.updateUserFailed'), {description: await getErrorMessage(error)});
		},
	});

	const remove = useMutation({
		mutationFn: async (username: string) => {
			const {error} = await request(endpoints.deleteUser({username}));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return username;
		},
		onSuccess: (username) => {
			removeFromListCaches(queryClient, {queryKeyPrefix: ['users'], id: username, getId: (u: User) => u.username});
			queryClient.removeQueries({queryKey: queries.getUser(username).queryKey});
			toast.success(t('admin.users.userDeleted', {username}));

			void waitUntilGone(queryClient, {
				queryKey: ['userGone', username],
				queryFn: async () => {
					const {response, error} = await request(endpoints.getUser({username}));
					if (error !== null) {
						throw mapQueryError(error);
					}
					return response.json();
				},
			}).then((gone) => {
				if (gone) {
					invalidateUsers();
				}
			});
		},
		onError: async (error) => {
			toast.error(t('admin.users.deleteUserFailed'), {description: await getErrorMessage(error)});
		},
	});

	return {create, update, remove};
}

export {useUserMutations};
