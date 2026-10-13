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
import type {CreateTenantRequestBody, Tenant, UpdateTenantRequestBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';

function useTenantMutations() {
	const {t} = useTranslation();
	const queryClient = useQueryClient();

	const invalidateTenants = () => queryClient.invalidateQueries({queryKey: ['queryTenants']});

	const create = useMutation({
		mutationFn: async (tenant: CreateTenantRequestBody) => {
			const {error} = await request(endpoints.createTenant(tenant));
			if (error !== null) {
				throw mapQueryError(error);
			}
		},
		onSuccess: () => {
			invalidateTenants();
			toast.success(t('admin.tenants.createTenantSuccess'));
		},
		onError: () => toast.error(t('admin.tenants.createTenantError')),
	});

	const update = useMutation({
		mutationFn: async (tenant: Pick<Tenant, 'tenantId'> & UpdateTenantRequestBody) => {
			const {error} = await request(endpoints.updateTenant(tenant));
			if (error !== null) {
				throw mapQueryError(error);
			}
		},
		onSuccess: (_data, {tenantId}) => {
			invalidateTenants();
			queryClient.invalidateQueries({queryKey: ['getTenant', tenantId]});
			toast.success(t('admin.tenants.updateTenantSuccess'));
		},
		onError: () => toast.error(t('admin.tenants.updateTenantError')),
	});

	const remove = useMutation({
		mutationFn: async (tenant: Pick<Tenant, 'tenantId'>) => {
			const {error} = await request(endpoints.deleteTenant(tenant));
			if (error !== null) {
				throw mapQueryError(error);
			}
		},
		onSuccess: () => {
			invalidateTenants();
			toast.success(t('admin.tenants.deleteTenantSuccess'));
		},
		onError: () => toast.error(t('admin.tenants.deleteTenantError')),
	});

	return {create, update, remove};
}

export {useTenantMutations};
