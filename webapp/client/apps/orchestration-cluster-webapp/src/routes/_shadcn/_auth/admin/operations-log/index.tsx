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
import {assertAdminSectionAvailable} from '#/admin/adminSections';
import {AdminOperationsLogPage} from '#/admin/pages/AdminOperationsLogPage';
import {getAuditLogsRequestBody} from '#/admin/modules/operations-log/auditLogs';
import {operationsLogSearchSchema, type OperationsLogSearch} from '#/admin/modules/operations-log/searchSchema';
import {ForbiddenError} from '#/shared/errors';
import {queries} from '#/shared/http/queries';
import {ForbiddenPage} from '#/shared/pages/shadcn.components/ForbiddenPage';
import {GenericErrorPage} from '#/shared/pages/shadcn.components/GenericErrorPage';

export const Route = createFileRoute('/_shadcn/_auth/admin/operations-log/')({
	validateSearch: operationsLogSearchSchema,
	beforeLoad: () => {
		assertAdminSectionAvailable('operations-log');
	},
	loaderDeps: ({search}) => ({search}),
	loader: async ({context: {queryClient}, deps: {search}}) => {
		await queryClient.query({
			...queries.queryAuditLogs(getAuditLogsRequestBody(search)),
			staleTime: 'static',
		});
	},
	errorComponent: function AdminOperationsLogErrorPage({error, reset}: ErrorComponentProps) {
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
	component: function AdminOperationsLogRoute() {
		const search = Route.useSearch();
		const navigate = useNavigate();
		const {data} = useSuspenseQuery(queries.queryAuditLogs(getAuditLogsRequestBody(search)));

		const handleSearchChange = useCallback(
			(next: Partial<OperationsLogSearch>) => {
				navigate({to: '/admin/operations-log', search: {...search, ...next}});
			},
			[navigate, search],
		);

		return (
			<AdminOperationsLogPage
				auditLogs={data.items}
				totalItems={data.page.totalItems}
				search={search}
				onSearchChange={handleSearchChange}
			/>
		);
	},
});
