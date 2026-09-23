/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo} from 'react';
import {createFileRoute, useNavigate, type ErrorComponentProps} from '@tanstack/react-router';
import {useSuspenseQuery} from '@tanstack/react-query';
import {PageLayout} from '@camunda/design-system';
import {assertAdminSectionAvailable, getAdminSectionConfig} from '#/admin/adminSections';
import {AdminMcpProcessesPage} from '#/admin/pages/AdminMcpProcessesPage';
import {getMcpProcessToolsRequestBody, mapResponseToTools} from '#/admin/modules/mcp-processes/mcpProcessTools';
import {mcpProcessesSearchSchema, type McpProcessesSearch} from '#/admin/modules/mcp-processes/searchSchema';
import {ForbiddenError} from '#/shared/errors';
import {queries} from '#/shared/http/queries';
import {ForbiddenPage} from '#/shared/pages/shadcn.components/ForbiddenPage';
import {GenericErrorPage} from '#/shared/pages/shadcn.components/GenericErrorPage';

export const Route = createFileRoute('/_shadcn/_auth/admin/mcp-processes/')({
	validateSearch: mcpProcessesSearchSchema,
	beforeLoad: () => {
		assertAdminSectionAvailable('mcp-processes');
	},
	loaderDeps: ({search}) => ({search}),
	loader: async ({context: {queryClient}, deps: {search}}) => {
		await queryClient.ensureQueryData(queries.queryMessageSubscriptions(getMcpProcessToolsRequestBody(search)));
	},
	errorComponent: function AdminMcpProcessesErrorPage({error, reset}: ErrorComponentProps) {
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
	component: function AdminMcpProcessesRoute() {
		const search = Route.useSearch();
		const navigate = useNavigate();
		const {data} = useSuspenseQuery(queries.queryMessageSubscriptions(getMcpProcessToolsRequestBody(search)));
		const tools = useMemo(() => mapResponseToTools(data), [data]);

		const handleSearchChange = useCallback(
			(next: Partial<McpProcessesSearch>) => {
				navigate({to: '/admin/mcp-processes', search: {...search, ...next}});
			},
			[navigate, search],
		);

		return (
			<AdminMcpProcessesPage
				tools={tools}
				totalItems={data.page.totalItems}
				search={search}
				isTenantsApiEnabled={getAdminSectionConfig().isTenantsApiEnabled}
				onSearchChange={handleSearchChange}
			/>
		);
	},
});
