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
import type {User} from '@camunda/camunda-api-zod-schemas/8.11';
import {assertAdminSectionAvailable} from '#/admin/adminSections';
import {AdminUsersPage} from '#/admin/pages/AdminUsersPage';
import {getUsersRequestBody, usersSearchSchema, type UsersSearch} from '#/admin/modules/users/searchSchema';
import {ForbiddenError} from '#/shared/errors';
import {queries} from '#/shared/http/queries';
import {ForbiddenPage} from '#/shared/pages/shadcn.components/ForbiddenPage';
import {GenericErrorPage} from '#/shared/pages/shadcn.components/GenericErrorPage';

export const Route = createFileRoute('/_shadcn/_auth/admin/users/')({
	validateSearch: usersSearchSchema,
	beforeLoad: () => {
		assertAdminSectionAvailable('users');
	},
	loaderDeps: ({search}) => ({search}),
	loader: async ({context: {queryClient}, deps: {search}}) => {
		await queryClient.query({
			...queries.queryUsers(getUsersRequestBody(search)),
			staleTime: 'static',
		});
	},
	errorComponent: function AdminUsersErrorPage({error, reset}: ErrorComponentProps) {
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
	component: function AdminUsersRoute() {
		const search = Route.useSearch();
		const navigate = useNavigate();
		const {data} = useSuspenseQuery(queries.queryUsers(getUsersRequestBody(search)));

		const handleSearchChange = useCallback(
			(next: Partial<UsersSearch>) => {
				navigate({to: '/admin/users', search: {...search, ...next}});
			},
			[navigate, search],
		);

		const handleOpenUser = useCallback(
			(user: User) => {
				navigate({to: '/admin/users/$username', params: {username: user.username}});
			},
			[navigate],
		);

		return (
			<AdminUsersPage
				users={data.items}
				totalItems={data.page.totalItems}
				search={search}
				onSearchChange={handleSearchChange}
				onOpenUser={handleOpenUser}
			/>
		);
	},
});
