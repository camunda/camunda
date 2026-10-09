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
	CreateRoleRequestBody,
	Role,
	QueryRolesRequestBody,
	UpdateRoleRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {isDuplicateRoleIdError} from './isDuplicateRoleIdError';
import {request} from '#/shared/http/request';
import {getErrorMessage} from '#/shared/http/getErrorMessage';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';
import {queries} from '#/shared/http/queries';
import {isFirstPageWithRoom, patchListCaches, removeFromListCaches} from '#/shared/http/patchListCaches';
import {waitUntilGone, waitUntilReady} from '#/shared/http/waitUntilReady';

function compareRolesBySort(a: Role, b: Role, queryKey: QueryKey): number {
	const body = queryKey[1] as QueryRolesRequestBody | undefined;
	const [sort] = body?.sort ?? [];
	if (sort === undefined) {
		return 0;
	}

	const valueA = a[sort.field];
	const valueB = b[sort.field];
	const direction = sort.order === 'desc' ? -1 : 1;
	return direction * (valueA < valueB ? -1 : valueA > valueB ? 1 : 0);
}

function roleMatchesFilter(role: Role, filter: QueryRolesRequestBody['filter']): boolean {
	const roleIdFilter = filter?.roleId;
	if (roleIdFilter === undefined) {
		return true;
	}
	if (typeof roleIdFilter === 'string') {
		return role.roleId === roleIdFilter;
	}

	const pattern = roleIdFilter.$like;
	return pattern === undefined || role.roleId.includes(pattern.replaceAll('*', ''));
}

function canInsertRole(role: Role, queryKey: QueryKey, currentItemCount: number): boolean {
	const body = queryKey[1] as QueryRolesRequestBody | undefined;
	return roleMatchesFilter(role, body?.filter) && isFirstPageWithRoom(body?.page, currentItemCount);
}

function useRoleMutations() {
	const {t} = useTranslation();
	const queryClient = useQueryClient();

	const invalidateRoles = () => queryClient.invalidateQueries({queryKey: ['queryRoles']});

	const reconcileRoles = (confirmed: boolean) => {
		if (confirmed) {
			void invalidateRoles();
		} else {
			toast.warning(t('admin.roles.changeNotConfirmed'), {
				description: t('admin.roles.changeNotConfirmedDescription'),
			});
		}
	};

	const create = useMutation({
		mutationFn: async (body: CreateRoleRequestBody) => {
			const {response, error} = await request(endpoints.createRole(body));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json() as Promise<Role>;
		},
		onSuccess: (role) => {
			patchListCaches(queryClient, {
				queryKeyPrefix: ['queryRoles'],
				item: role,
				getId: (role: Role) => role.roleId,
				compare: compareRolesBySort,
				canInsert: canInsertRole,
			});
			toast.success(t('admin.roles.roleCreated', {name: role.name}));

			void waitUntilReady(queryClient, queries.getRole(role.roleId)).then((ready) =>
				reconcileRoles(ready !== undefined),
			);
		},
		onError: async (error) => {
			if (isDuplicateRoleIdError(error)) {
				return;
			}
			toast.error(t('admin.roles.createRoleFailed'), {description: await getErrorMessage(error)});
		},
	});

	const update = useMutation({
		mutationFn: async (input: {roleId: string} & UpdateRoleRequestBody) => {
			const {response, error} = await request(endpoints.updateRole(input));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json() as Promise<Role>;
		},
		onSuccess: (role) => {
			patchListCaches(queryClient, {
				queryKeyPrefix: ['queryRoles'],
				item: role,
				getId: (role: Role) => role.roleId,
				compare: compareRolesBySort,
				canInsert: canInsertRole,
			});
			queryClient.setQueryData(queries.getRole(role.roleId).queryKey, role);
			toast.success(t('admin.roles.roleUpdated', {name: role.name}));

			void waitUntilReady(queryClient, queries.getRole(role.roleId), {
				isReady: (data) => data.name === role.name && data.description === role.description,
			}).then((ready) => reconcileRoles(ready !== undefined));
		},
		onError: async (error) => {
			toast.error(t('admin.roles.updateRoleFailed'), {description: await getErrorMessage(error)});
		},
	});

	const remove = useMutation({
		mutationFn: async (roleId: string) => {
			const {error} = await request(endpoints.deleteRole({roleId}));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return roleId;
		},
		onSuccess: (roleId) => {
			removeFromListCaches(queryClient, {
				queryKeyPrefix: ['queryRoles'],
				id: roleId,
				getId: (role: Role) => role.roleId,
			});
			queryClient.removeQueries({queryKey: queries.getRole(roleId).queryKey});
			toast.success(t('admin.roles.roleDeleted', {roleId}));

			void waitUntilGone(queryClient, {
				queryKey: ['roleGone', roleId],
				queryFn: async () => {
					const {response, error} = await request(endpoints.getRole({roleId}));
					if (error !== null) {
						throw mapQueryError(error);
					}
					return response.json();
				},
			}).then(reconcileRoles);
		},
		onError: async (error) => {
			toast.error(t('admin.roles.deleteRoleFailed'), {description: await getErrorMessage(error)});
		},
	});

	return {create, update, remove};
}

export {useRoleMutations};
