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
import {z} from 'zod';
import {PageLayout} from '@camunda/design-system';
import {assertAdminSectionAvailable, getAdminSectionConfig} from '#/admin/adminSections';
import {MEMBER_KINDS, type MemberKind} from '#/admin/modules/roles/members';
import {AdminRoleDetailPage} from '#/admin/pages/AdminRoleDetailPage';
import {ForbiddenError} from '#/shared/errors';
import {queries} from '#/shared/http/queries';
import {requestErrorSchema} from '#/shared/http/request';
import {ForbiddenPage} from '#/shared/pages/shadcn.components/ForbiddenPage';
import {GenericErrorPage} from '#/shared/pages/shadcn.components/GenericErrorPage';
import {NotFoundPage} from '#/shared/pages/shadcn.components/NotFoundPage';

export const Route = createFileRoute('/_shadcn/_auth/admin/roles/$roleId')({
	validateSearch: z.object({tab: z.enum(MEMBER_KINDS).optional()}),
	beforeLoad: () => {
		assertAdminSectionAvailable('roles');
	},
	loader: async ({context: {queryClient}, params: {roleId}}) => {
		try {
			await Promise.all([queryClient.query(queries.getRole(roleId)), queryClient.query(queries.adminClientConfig())]);
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
		const {tab} = Route.useSearch();
		const {data: role} = useSuspenseQuery(queries.getRole(roleId));
		const {data: clientConfig} = useSuspenseQuery(queries.adminClientConfig());
		const {isOidc, isCamundaGroupsEnabled} = getAdminSectionConfig();

		const handleTabChange = useCallback(
			(nextTab: MemberKind) => {
				navigate({to: '.', search: {tab: nextTab === 'users' ? undefined : nextTab}, replace: true});
			},
			[navigate],
		);

		const handleDeleted = useCallback(() => {
			navigate({to: '/admin/roles', replace: true});
		}, [navigate]);

		return (
			<AdminRoleDetailPage
				role={role}
				isOidc={isOidc}
				isCamundaGroupsEnabled={isCamundaGroupsEnabled}
				defaultRoleIds={clientConfig.defaultRoleIds}
				activeTab={tab ?? 'users'}
				onTabChange={handleTabChange}
				onDeleted={handleDeleted}
			/>
		);
	},
});
