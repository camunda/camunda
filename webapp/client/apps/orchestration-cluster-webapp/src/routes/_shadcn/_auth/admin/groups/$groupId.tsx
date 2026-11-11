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
	getGroupMembersRequestBody,
	groupDetailSearchSchema,
	type GroupDetailSearch,
} from '#/admin/modules/groups/detailSearchSchema';
import {getActiveMemberKind, getMembersQuery, getVisibleMemberKinds} from '#/admin/modules/groups/members';
import {AdminGroupDetailPage} from '#/admin/pages/AdminGroupDetailPage';
import {ForbiddenError} from '#/shared/errors';
import {queries} from '#/shared/http/queries';
import {requestErrorSchema} from '#/shared/http/request';
import {ForbiddenPage} from '#/shared/pages/shadcn.components/ForbiddenPage';
import {GenericErrorPage} from '#/shared/pages/shadcn.components/GenericErrorPage';
import {NotFoundPage} from '#/shared/pages/shadcn.components/NotFoundPage';

export const Route = createFileRoute('/_shadcn/_auth/admin/groups/$groupId')({
	validateSearch: groupDetailSearchSchema,
	beforeLoad: () => {
		assertAdminSectionAvailable('groups');
	},
	loaderDeps: ({search}) => ({search}),
	loader: async ({context: {queryClient}, params: {groupId}, deps: {search}}) => {
		try {
			await Promise.all([
				queryClient.query(queries.getGroup(groupId)),
				queryClient.query(queries.adminClientConfig()).then((clientConfig) => {
					const sectionConfig = getAdminSectionConfig();
					const visibleKinds = getVisibleMemberKinds({
						isOidc: sectionConfig.isOidc,
						canManageMappingRules: canManageMappingRuleMembers(sectionConfig, clientConfig.isAdditionalIdpConfigured),
					});
					const kind = getActiveMemberKind(visibleKinds, search.tab);
					return queryClient.query(getMembersQuery(kind, groupId, getGroupMembersRequestBody(search)));
				}),
			]);
		} catch (error) {
			const result = requestErrorSchema.safeParse(error);

			if (result.success && result.data.response?.status === 404) {
				throw notFound({routeId: '/_shadcn/_auth/admin/groups/$groupId'});
			}

			throw error;
		}
	},
	notFoundComponent: NotFoundPage,
	errorComponent: function AdminGroupDetailErrorPage({error, reset}: ErrorComponentProps) {
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
	component: function AdminGroupDetailRoute() {
		const {groupId} = Route.useParams();
		const navigate = useNavigate();
		const search = Route.useSearch();
		const {data: group} = useSuspenseQuery(queries.getGroup(groupId));
		const {data: clientConfig} = useSuspenseQuery(queries.adminClientConfig());
		const sectionConfig = getAdminSectionConfig();

		const handleSearchChange = useCallback(
			(next: Partial<GroupDetailSearch>, options?: {replace?: boolean}) => {
				navigate({
					to: '/admin/groups/$groupId',
					params: {groupId},
					search: {...search, ...next},
					replace: options?.replace ?? true,
				});
			},
			[navigate, search, groupId],
		);

		const handleDeleted = useCallback(() => {
			navigate({to: '/admin/groups', replace: true});
		}, [navigate]);

		return (
			<AdminGroupDetailPage
				group={group}
				isOidc={sectionConfig.isOidc}
				canManageMappingRules={canManageMappingRuleMembers(sectionConfig, clientConfig.isAdditionalIdpConfigured)}
				search={search}
				onSearchChange={handleSearchChange}
				onDeleted={handleDeleted}
			/>
		);
	},
});
