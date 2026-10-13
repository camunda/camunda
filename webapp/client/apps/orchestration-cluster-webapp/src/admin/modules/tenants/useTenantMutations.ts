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
	CreateTenantRequestBody,
	QueryTenantsRequestBody,
	Tenant,
	UpdateTenantRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';
import {queries} from '#/shared/http/queries';
import {isFirstPageWithRoom, patchListCaches, removeFromListCaches} from '#/shared/http/patchListCaches';
import {waitUntilGone, waitUntilReady} from '#/shared/http/waitUntilReady';
import {QUERY_KEY_BY_TAB} from './useTenantMemberMutations';

function compareTenantsBySort(a: Tenant, b: Tenant, queryKey: QueryKey): number {
	const body = queryKey[1] as QueryTenantsRequestBody | undefined;
	const [sort] = body?.sort ?? [];
	if (sort === undefined) {
		return 0;
	}

	const field = sort.field === 'name' ? 'name' : 'tenantId';
	const valueA = a[field];
	const valueB = b[field];
	const direction = sort.order === 'DESC' ? -1 : 1;
	return direction * (valueA < valueB ? -1 : valueA > valueB ? 1 : 0);
}

function canInsertTenant(_tenant: Tenant, queryKey: QueryKey, currentItemCount: number): boolean {
	const body = queryKey[1] as QueryTenantsRequestBody | undefined;
	const [sort] = body?.sort ?? [];
	const isUnfiltered = body?.filter?.tenantId === undefined;
	return isUnfiltered && sort?.field !== 'name' && isFirstPageWithRoom(body?.page, currentItemCount);
}

function useTenantMutations() {
	const {t} = useTranslation();
	const queryClient = useQueryClient();

	const invalidateTenants = () => queryClient.invalidateQueries({queryKey: ['queryTenants']});

	const reconcileTenants = (confirmed: boolean) => {
		if (confirmed) {
			void invalidateTenants();
		} else {
			toast.warning(t('admin.tenants.changeNotConfirmed'), {
				description: t('admin.tenants.changeNotConfirmedDescription'),
			});
		}
	};

	const create = useMutation({
		mutationFn: async (tenant: CreateTenantRequestBody) => {
			const {response, error} = await request(endpoints.createTenant(tenant));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json() as Promise<Tenant>;
		},
		onSuccess: (tenant) => {
			patchListCaches(queryClient, {
				queryKeyPrefix: ['queryTenants'],
				item: tenant,
				getId: (tenant: Tenant) => tenant.tenantId,
				compare: compareTenantsBySort,
				canInsert: canInsertTenant,
			});
			toast.success(t('admin.tenants.createTenantSuccess'));

			void waitUntilReady(queryClient, queries.getTenant(tenant.tenantId)).then((ready) =>
				reconcileTenants(ready !== undefined),
			);
		},
		onError: () => toast.error(t('admin.tenants.createTenantError')),
	});

	const update = useMutation({
		mutationFn: async (tenant: Pick<Tenant, 'tenantId'> & UpdateTenantRequestBody) => {
			const {response, error} = await request(endpoints.updateTenant(tenant));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json() as Promise<Tenant>;
		},
		onSuccess: (tenant) => {
			patchListCaches(queryClient, {
				queryKeyPrefix: ['queryTenants'],
				item: tenant,
				getId: (tenant: Tenant) => tenant.tenantId,
				compare: compareTenantsBySort,
				canInsert: () => false,
			});
			queryClient.setQueryData(queries.getTenant(tenant.tenantId).queryKey, tenant);
			toast.success(t('admin.tenants.updateTenantSuccess'));

			void waitUntilReady(queryClient, queries.getTenant(tenant.tenantId), {
				isReady: (data) => data.name === tenant.name && (data.description ?? '') === (tenant.description ?? ''),
			}).then((ready) => reconcileTenants(ready !== undefined));
		},
		onError: () => toast.error(t('admin.tenants.updateTenantError')),
	});

	const remove = useMutation({
		mutationFn: async (tenant: Pick<Tenant, 'tenantId'>) => {
			const {error} = await request(endpoints.deleteTenant(tenant));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return tenant.tenantId;
		},
		onSuccess: (tenantId) => {
			removeFromListCaches(queryClient, {
				queryKeyPrefix: ['queryTenants'],
				id: tenantId,
				getId: (tenant: Tenant) => tenant.tenantId,
			});
			toast.success(t('admin.tenants.deleteTenantSuccess'));

			void waitUntilGone(queryClient, {
				queryKey: ['tenantGone', tenantId],
				queryFn: async () => {
					const {response, error} = await request(endpoints.getTenant({tenantId}));
					if (error !== null) {
						throw mapQueryError(error);
					}
					return response.json();
				},
			}).then((confirmed) => {
				queryClient.removeQueries({queryKey: queries.getTenant(tenantId).queryKey, type: 'inactive'});
				for (const key of Object.values(QUERY_KEY_BY_TAB)) {
					queryClient.removeQueries({queryKey: [key, tenantId], type: 'inactive'});
				}
				reconcileTenants(confirmed);
			});
		},
		onError: () => toast.error(t('admin.tenants.deleteTenantError')),
	});

	return {create, update, remove};
}

export {useTenantMutations};
