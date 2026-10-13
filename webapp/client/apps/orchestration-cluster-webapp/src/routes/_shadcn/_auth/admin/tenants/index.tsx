/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback} from 'react';
import {createFileRoute, useNavigate, type ErrorComponentProps} from '@tanstack/react-router';
import {useSuspenseQuery} from '@tanstack/react-query';
import {PageLayout} from '@camunda/design-system';
import type {Tenant} from '@camunda/camunda-api-zod-schemas/8.11';
import {assertAdminSectionAvailable} from '#/admin/adminSections';
import {AdminTenantsPage} from '#/admin/pages/AdminTenantsPage';
import {getTenantsRequestBody} from '#/admin/modules/tenants/getTenantsRequestBody';
import {tenantsSearchSchema, type TenantsSearch} from '#/admin/modules/tenants/searchSchema';
import {ForbiddenError} from '#/shared/errors';
import {queries} from '#/shared/http/queries';
import {ForbiddenPage} from '#/shared/pages/shadcn.components/ForbiddenPage';
import {GenericErrorPage} from '#/shared/pages/shadcn.components/GenericErrorPage';

export const Route = createFileRoute('/_shadcn/_auth/admin/tenants/')({
	validateSearch: tenantsSearchSchema,
	beforeLoad: () => {
		assertAdminSectionAvailable('tenants');
	},
	loaderDeps: ({search}) => ({search}),
	loader: async ({context: {queryClient}, deps: {search}}) => {
		await queryClient.query(queries.queryTenants(getTenantsRequestBody(search)));
	},
	errorComponent: function AdminTenantsErrorPage({error, reset}: ErrorComponentProps) {
		if (error instanceof ForbiddenError) {
			return (
				<PageLayout>
					<ForbiddenPage />
				</PageLayout>
			);
		}
		return (
			<PageLayout>
				<GenericErrorPage reset={reset} />
			</PageLayout>
		);
	},
	component: function AdminTenantsRoute() {
		const search = Route.useSearch();
		const navigate = useNavigate();
		const {data} = useSuspenseQuery(queries.queryTenants(getTenantsRequestBody(search)));

		const handleSearchChange = useCallback(
			(next: Partial<TenantsSearch>) => {
				navigate({to: '/admin/tenants', search: {...search, ...next}});
			},
			[navigate, search],
		);

		const handleOpenTenant = useCallback(
			({tenantId}: Tenant) => {
				navigate({to: '/admin/tenants/$tenantId', params: {tenantId}});
			},
			[navigate],
		);

		return (
			<AdminTenantsPage
				tenants={data.items}
				totalItems={data.page.totalItems}
				search={search}
				onSearchChange={handleSearchChange}
				onOpenTenant={handleOpenTenant}
			/>
		);
	},
});
