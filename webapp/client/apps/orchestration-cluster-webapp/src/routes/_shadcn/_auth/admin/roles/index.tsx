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
import type {Role} from '@camunda/camunda-api-zod-schemas/8.11';
import {assertAdminSectionAvailable} from '#/admin/adminSections';
import {AdminRolesPage} from '#/admin/pages/AdminRolesPage';
import {getRolesRequestBody, rolesSearchSchema, type RolesSearch} from '#/admin/modules/roles/searchSchema';
import {ForbiddenError} from '#/shared/errors';
import {queries} from '#/shared/http/queries';
import {ForbiddenPage} from '#/shared/pages/shadcn.components/ForbiddenPage';
import {GenericErrorPage} from '#/shared/pages/shadcn.components/GenericErrorPage';

export const Route = createFileRoute('/_shadcn/_auth/admin/roles/')({
	validateSearch: rolesSearchSchema,
	beforeLoad: () => {
		assertAdminSectionAvailable('roles');
	},
	loaderDeps: ({search}) => ({search}),
	loader: async ({context: {queryClient}, deps: {search}}) => {
		await Promise.all([
			queryClient.query({...queries.queryRoles(getRolesRequestBody(search)), staleTime: 'static'}),
			queryClient.query(queries.adminClientConfig()),
		]);
	},
	errorComponent: function AdminRolesErrorPage({error, reset}: ErrorComponentProps) {
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
	component: function AdminRolesRoute() {
		const search = Route.useSearch();
		const navigate = useNavigate();
		const {data} = useSuspenseQuery(queries.queryRoles(getRolesRequestBody(search)));
		const {data: clientConfig} = useSuspenseQuery(queries.adminClientConfig());

		const handleSearchChange = useCallback(
			(next: Partial<RolesSearch>) => {
				navigate({to: '/admin/roles', search: {...search, ...next}});
			},
			[navigate, search],
		);

		const handleOpenRole = useCallback(
			(role: Role) => {
				navigate({to: '/admin/roles/$roleId', params: {roleId: role.roleId}});
			},
			[navigate],
		);

		return (
			<AdminRolesPage
				roles={data.items}
				totalItems={data.page.totalItems}
				defaultRoleIds={clientConfig.defaultRoleIds}
				search={search}
				onSearchChange={handleSearchChange}
				onOpenRole={handleOpenRole}
			/>
		);
	},
});
