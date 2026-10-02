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
import {assertAdminSectionAvailable} from '#/admin/adminSections';
import {AdminUserDetailPage} from '#/admin/pages/AdminUserDetailPage';
import {ForbiddenError} from '#/shared/errors';
import {queries} from '#/shared/http/queries';
import {requestErrorSchema} from '#/shared/http/request';
import {ForbiddenPage} from '#/shared/pages/shadcn.components/ForbiddenPage';
import {GenericErrorPage} from '#/shared/pages/shadcn.components/GenericErrorPage';
import {NotFoundPage} from '#/shared/pages/shadcn.components/NotFoundPage';

export const Route = createFileRoute('/_shadcn/_auth/admin/users/$username')({
	beforeLoad: () => {
		assertAdminSectionAvailable('users');
	},
	loader: async ({context: {queryClient}, params: {username}}) => {
		try {
			await queryClient.query(queries.getUser(username));
		} catch (error) {
			const result = requestErrorSchema.safeParse(error);

			if (result.success && result.data.response?.status === 404) {
				throw notFound({routeId: '/_shadcn/_auth/admin/users/$username'});
			}

			throw error;
		}
	},
	notFoundComponent: NotFoundPage,
	errorComponent: function AdminUserDetailErrorPage({error, reset}: ErrorComponentProps) {
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
	component: function AdminUserDetailRoute() {
		const {username} = Route.useParams();
		const navigate = useNavigate();
		const {data: user} = useSuspenseQuery(queries.getUser(username));

		const handleDeleted = useCallback(() => {
			navigate({to: '/admin/users'});
		}, [navigate]);

		return <AdminUserDetailPage user={user} onDeleted={handleDeleted} />;
	},
});
