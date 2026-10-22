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
import type {Group} from '@camunda/camunda-api-zod-schemas/8.11';
import {assertAdminSectionAvailable} from '#/admin/adminSections';
import {AdminGroupsPage} from '#/admin/pages/AdminGroupsPage';
import {getGroupsRequestBody, groupsSearchSchema, type GroupsSearch} from '#/admin/modules/groups/searchSchema';
import {ForbiddenError} from '#/shared/errors';
import {queries} from '#/shared/http/queries';
import {ForbiddenPage} from '#/shared/pages/shadcn.components/ForbiddenPage';
import {GenericErrorPage} from '#/shared/pages/shadcn.components/GenericErrorPage';

export const Route = createFileRoute('/_shadcn/_auth/admin/groups/')({
	validateSearch: groupsSearchSchema,
	beforeLoad: () => {
		assertAdminSectionAvailable('groups');
	},
	loaderDeps: ({search}) => ({search}),
	loader: async ({context: {queryClient}, deps: {search}}) => {
		await queryClient.query({
			...queries.queryGroups(getGroupsRequestBody(search)),
			staleTime: 'static',
		});
	},
	errorComponent: function AdminGroupsErrorPage({error, reset}: ErrorComponentProps) {
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
	component: function AdminGroupsRoute() {
		const search = Route.useSearch();
		const navigate = useNavigate();
		const {data} = useSuspenseQuery(queries.queryGroups(getGroupsRequestBody(search)));

		const handleSearchChange = useCallback(
			(next: Partial<GroupsSearch>) => {
				navigate({to: '/admin/groups', search: {...search, ...next}});
			},
			[navigate, search],
		);

		const handleOpenGroup = useCallback(
			(group: Group) => {
				navigate({to: '/admin/groups/$groupId', params: {groupId: group.groupId}});
			},
			[navigate],
		);

		return (
			<AdminGroupsPage
				groups={data.items}
				totalItems={data.page.totalItems}
				search={search}
				onSearchChange={handleSearchChange}
				onOpenGroup={handleOpenGroup}
			/>
		);
	},
});
