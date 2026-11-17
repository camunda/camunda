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
import {AdminAuthorizationsPage} from '#/admin/pages/AdminAuthorizationsPage';
import {getAuthorizationsRequestBody} from '#/admin/modules/authorizations/getAuthorizationsRequestBody';
import {
	authorizationsSearchSchema,
	getAvailableResourceTypes,
	resolveResourceType,
	type AuthorizationsSearch,
} from '#/admin/modules/authorizations/searchSchema';
import {ForbiddenError} from '#/shared/errors';
import {queries} from '#/shared/http/queries';
import {ForbiddenPage} from '#/shared/pages/shadcn.components/ForbiddenPage';
import {GenericErrorPage} from '#/shared/pages/shadcn.components/GenericErrorPage';

function resolveSearchContext(search: AuthorizationsSearch) {
	const {isOidc, isTenantsApiEnabled, isCamundaGroupsEnabled} = getAdminSectionConfig();
	const availableResourceTypes = getAvailableResourceTypes(isTenantsApiEnabled);
	const resourceType = resolveResourceType(search.resourceType, availableResourceTypes);

	return {isOidc, isCamundaGroupsEnabled, availableResourceTypes, resourceType};
}

export const Route = createFileRoute('/_shadcn/_auth/admin/authorizations/')({
	validateSearch: authorizationsSearchSchema,
	beforeLoad: () => {
		assertAdminSectionAvailable('authorizations');
	},
	loaderDeps: ({search}) => ({search}),
	loader: ({context: {queryClient}, deps: {search}}) => {
		const {resourceType} = resolveSearchContext(search);

		// Not awaited: the page shell renders at once and the table shows its own loading state and
		// error, so a failure here is left for the table's query to report.
		queryClient.query(queries.queryAuthorizations(getAuthorizationsRequestBody(search, resourceType))).catch(() => {});

		return queryClient.query(queries.adminClientConfig());
	},
	errorComponent: function AdminAuthorizationsErrorPage({error, reset}: ErrorComponentProps) {
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
	component: function AdminAuthorizationsRoute() {
		const search = Route.useSearch();
		const navigate = useNavigate();
		const {isOidc, isCamundaGroupsEnabled, availableResourceTypes, resourceType} = resolveSearchContext(search);
		const {data: clientConfig} = useSuspenseQuery(queries.adminClientConfig());

		const handleSearchChange = useCallback(
			(next: Partial<AuthorizationsSearch>) => {
				navigate({to: '/admin/authorizations', search: {...search, ...next}});
			},
			[navigate, search],
		);

		return (
			<AdminAuthorizationsPage
				search={search}
				resourceType={resourceType}
				availableResourceTypes={availableResourceTypes}
				clientConfig={clientConfig}
				isOidc={isOidc}
				isCamundaGroupsEnabled={isCamundaGroupsEnabled}
				onSearchChange={handleSearchChange}
			/>
		);
	},
});
