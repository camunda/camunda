/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback} from 'react';
import {createFileRoute, notFound, useNavigate, type ErrorComponentProps} from '@tanstack/react-router';
import {useSuspenseQuery} from '@tanstack/react-query';
import {PageLayout} from '@camunda/design-system';
import {assertAdminSectionAvailable, getAdminSectionConfig} from '#/admin/adminSections';
import {AdminTenantDetailPage} from '#/admin/pages/AdminTenantDetailPage';
import {tenantDetailSearchSchema, type TenantDetailSearch} from '#/admin/modules/tenants/detailSearchSchema';
import {getAvailableTenantTabs} from '#/admin/modules/tenants/getAvailableTenantTabs';
import {getTenantMembersRequestBody} from '#/admin/modules/tenants/getTenantMembersRequestBody';
import {ForbiddenError} from '#/shared/errors';
import {queries} from '#/shared/http/queries';
import {requestErrorSchema} from '#/shared/http/request';
import {ForbiddenPage} from '#/shared/pages/shadcn.components/ForbiddenPage';
import {GenericErrorPage} from '#/shared/pages/shadcn.components/GenericErrorPage';
import {NotFoundPage} from '#/shared/pages/shadcn.components/NotFoundPage';

export const Route = createFileRoute('/_shadcn/_auth/admin/tenants/$tenantId')({
	validateSearch: tenantDetailSearchSchema,
	beforeLoad: () => {
		assertAdminSectionAvailable('tenants');
	},
	loaderDeps: ({search}) => ({search}),
	loader: async ({context: {queryClient}, params: {tenantId}, deps: {search}}) => {
		const tabs = getAvailableTenantTabs(getAdminSectionConfig());
		const tab = search.tab !== undefined && tabs.includes(search.tab) ? search.tab : (tabs[0] ?? 'users');

		try {
			await Promise.all([
				queryClient.query(queries.getTenant(tenantId)),
				tab === 'users' &&
					queryClient.query(queries.queryUsersByTenant(tenantId, getTenantMembersRequestBody(search, 'username'))),
				tab === 'groups' &&
					queryClient.query(queries.queryGroupsByTenant(tenantId, getTenantMembersRequestBody(search, 'groupId'))),
				tab === 'roles' &&
					queryClient.query(queries.queryRolesByTenant(tenantId, getTenantMembersRequestBody(search, 'roleId'))),
				tab === 'mapping-rules' &&
					queryClient.query(
						queries.queryMappingRulesByTenant(tenantId, getTenantMembersRequestBody(search, 'mappingRuleId')),
					),
				tab === 'clients' &&
					queryClient.query(queries.queryClientsByTenant(tenantId, getTenantMembersRequestBody(search, 'clientId'))),
			]);
		} catch (error) {
			const result = requestErrorSchema.safeParse(error);

			if (result.success && result.data.response?.status === 404) {
				throw notFound({routeId: '/_shadcn/_auth/admin/tenants/$tenantId'});
			}

			throw error;
		}
	},
	notFoundComponent: NotFoundPage,
	errorComponent: function AdminTenantDetailErrorPage({error, reset}: ErrorComponentProps) {
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
	component: function AdminTenantDetailRoute() {
		const {tenantId} = Route.useParams();
		const search = Route.useSearch();
		const navigate = useNavigate();
		const {data: tenant} = useSuspenseQuery(queries.getTenant(tenantId));
		const config = getAdminSectionConfig();

		const handleSearchChange = useCallback(
			(next: Partial<TenantDetailSearch>) => {
				navigate({to: '/admin/tenants/$tenantId', params: {tenantId}, search: {...search, ...next}});
			},
			[navigate, search, tenantId],
		);

		const handleDeleted = useCallback(() => {
			navigate({to: '/admin/tenants', replace: true});
		}, [navigate]);

		return (
			<AdminTenantDetailPage
				tenant={tenant}
				tabs={getAvailableTenantTabs(config)}
				isOidc={config.isOidc}
				search={search}
				onSearchChange={handleSearchChange}
				onDeleted={handleDeleted}
			/>
		);
	},
});
