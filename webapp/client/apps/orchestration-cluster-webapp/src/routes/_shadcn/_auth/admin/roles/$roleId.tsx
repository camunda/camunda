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
import {assertAdminSectionAvailable, canManageMappingRuleMembers, getAdminSectionConfig} from '#/admin/adminSections';
import {
	getRoleMembersRequestBody,
	roleDetailSearchSchema,
	type RoleDetailSearch,
} from '#/admin/modules/roles/detailSearchSchema';
import {getActiveMemberKind, getMembersQuery, getVisibleMemberKinds} from '#/admin/modules/roles/members';
import {AdminRoleDetailPage} from '#/admin/pages/AdminRoleDetailPage';
import {ForbiddenError} from '#/shared/errors';
import {queries} from '#/shared/http/queries';
import {requestErrorSchema} from '#/shared/http/request';
import {ForbiddenPage} from '#/shared/pages/shadcn.components/ForbiddenPage';
import {GenericErrorPage} from '#/shared/pages/shadcn.components/GenericErrorPage';
import {NotFoundPage} from '#/shared/pages/shadcn.components/NotFoundPage';

export const Route = createFileRoute('/_shadcn/_auth/admin/roles/$roleId')({
	validateSearch: roleDetailSearchSchema,
	beforeLoad: () => {
		assertAdminSectionAvailable('roles');
	},
	loaderDeps: ({search}) => ({search}),
	loader: async ({context: {queryClient}, params: {roleId}, deps: {search}}) => {
		try {
			await Promise.all([
				queryClient.query(queries.getRole(roleId)),
				queryClient.query(queries.adminClientConfig()).then((clientConfig) => {
					const sectionConfig = getAdminSectionConfig();
					const visibleKinds = getVisibleMemberKinds({
						isOidc: sectionConfig.isOidc,
						canManageMappingRules: canManageMappingRuleMembers(sectionConfig, clientConfig.isAdditionalIdpConfigured),
					});
					const kind = getActiveMemberKind(visibleKinds, search.tab);
					return queryClient.query(getMembersQuery(kind, roleId, getRoleMembersRequestBody(search)));
				}),
			]);
		} catch (error) {
			const result = requestErrorSchema.safeParse(error);

			if (result.success && result.data.response?.status === 404) {
				throw notFound({routeId: '/_shadcn/_auth/admin/roles/$roleId'});
			}

			throw error;
		}
	},
	notFoundComponent: NotFoundPage,
	errorComponent: function AdminRoleDetailErrorPage({error, reset}: ErrorComponentProps) {
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
	component: function AdminRoleDetailRoute() {
		const {roleId} = Route.useParams();
		const navigate = useNavigate();
		const search = Route.useSearch();
		const {data: role} = useSuspenseQuery(queries.getRole(roleId));
		const {data: clientConfig} = useSuspenseQuery(queries.adminClientConfig());
		const sectionConfig = getAdminSectionConfig();
		const {isOidc, isCamundaGroupsEnabled} = sectionConfig;

		const handleSearchChange = useCallback(
			(next: Partial<RoleDetailSearch>, options?: {replace?: boolean}) => {
				navigate({
					to: '/admin/roles/$roleId',
					params: {roleId},
					search: {...search, ...next},
					replace: options?.replace ?? true,
				});
			},
			[navigate, search, roleId],
		);

		const handleDeleted = useCallback(() => {
			navigate({to: '/admin/roles', replace: true});
		}, [navigate]);

		return (
			<AdminRoleDetailPage
				role={role}
				isOidc={isOidc}
				canManageMappingRules={canManageMappingRuleMembers(sectionConfig, clientConfig.isAdditionalIdpConfigured)}
				isCamundaGroupsEnabled={isCamundaGroupsEnabled}
				defaultRoleIds={clientConfig.defaultRoleIds}
				search={search}
				onSearchChange={handleSearchChange}
				onDeleted={handleDeleted}
			/>
		);
	},
});
