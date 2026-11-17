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
import {assertAdminSectionAvailable, getAdminSectionConfig} from '#/admin/adminSections';
import {AdminClusterVariablesPage} from '#/admin/pages/AdminClusterVariablesPage';
import {getClusterVariablesRequestBody} from '#/admin/modules/cluster-variables/getClusterVariablesRequestBody';
import {
	clusterVariablesSearchSchema,
	type ClusterVariablesSearch,
} from '#/admin/modules/cluster-variables/searchSchema';
import {ForbiddenError} from '#/shared/errors';
import {queries} from '#/shared/http/queries';
import {ForbiddenPage} from '#/shared/pages/shadcn.components/ForbiddenPage';
import {GenericErrorPage} from '#/shared/pages/shadcn.components/GenericErrorPage';

export const Route = createFileRoute('/_shadcn/_auth/admin/cluster-variables/')({
	validateSearch: clusterVariablesSearchSchema,
	beforeLoad: () => {
		assertAdminSectionAvailable('cluster-variables');
	},
	loaderDeps: ({search}) => ({search}),
	loader: async ({context: {queryClient}, deps: {search}}) => {
		await queryClient.query(queries.queryClusterVariables(getClusterVariablesRequestBody(search)));
	},
	errorComponent: function AdminClusterVariablesErrorPage({error, reset}: ErrorComponentProps) {
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
	component: function AdminClusterVariablesRoute() {
		const search = Route.useSearch();
		const navigate = useNavigate();
		const {data} = useSuspenseQuery(queries.queryClusterVariables(getClusterVariablesRequestBody(search)));
		const {isSaas, isTenantsApiEnabled} = getAdminSectionConfig();

		const handleSearchChange = useCallback(
			(next: Partial<ClusterVariablesSearch>) => {
				navigate({to: '/admin/cluster-variables', search: {...search, ...next}});
			},
			[navigate, search],
		);

		return (
			<AdminClusterVariablesPage
				clusterVariables={data.items}
				totalItems={data.page.totalItems}
				search={search}
				isTenantScopeAvailable={!isSaas && isTenantsApiEnabled}
				onSearchChange={handleSearchChange}
			/>
		);
	},
});
