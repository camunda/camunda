/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo} from 'react';
import {createFileRoute, Outlet, type ErrorComponentProps, useNavigate} from '@tanstack/react-router';
import {useSuspenseInfiniteQuery, useSuspenseQuery} from '@tanstack/react-query';
import {TasklistProcessesPage} from '#/tasklist/pages/TasklistProcessesPage';
import {processesSearchSchema} from '#/tasklist/modules/processes/searchSchema';
import {getProcessDefinitionsRequestBody} from '#/tasklist/modules/processes/getProcessDefinitionsRequestBody';
import {StartProcessProvider} from '#/tasklist/modules/processes/StartProcessProvider';
import {queries} from '#/shared/http/queries';
import {ForbiddenError} from '#/shared/errors';
import {ForbiddenPage} from '#/shared/pages/ForbiddenPage';
import {GenericErrorPage} from '#/shared/pages/GenericErrorPage';

export const Route = createFileRoute('/_carbon/_auth/tasklist/processes')({
	validateSearch: processesSearchSchema,
	loaderDeps: ({search}) => ({search}),
	loader: async ({context: {queryClient}, deps: {search}}) => {
		const {tenants} = await queryClient.ensureQueryData(queries.getCurrentUser());
		await queryClient.ensureInfiniteQueryData(
			queries.queryProcessDefinitionsInfinite(getProcessDefinitionsRequestBody(search, tenants)),
		);
	},
	errorComponent: function ProcessesErrorPage({error, reset}: ErrorComponentProps) {
		if (error instanceof ForbiddenError) {
			return (
				<main id="main-content" className="cds--content">
					<ForbiddenPage />
				</main>
			);
		}

		return (
			<main id="main-content" className="cds--content">
				<GenericErrorPage reset={reset} />
			</main>
		);
	},
	component: function TasklistProcessesRoute() {
		const search = Route.useSearch();
		const navigate = useNavigate();
		const {data: currentUser} = useSuspenseQuery(queries.getCurrentUser());
		const processDefinitionsRequestBody = getProcessDefinitionsRequestBody(search, currentUser.tenants);
		const {data, fetchNextPage, hasNextPage, isFetchingNextPage} = useSuspenseInfiniteQuery({
			...queries.queryProcessDefinitionsInfinite(processDefinitionsRequestBody),
			refetchInterval: 5000,
		});
		const processes = useMemo(() => data.pages.flatMap((page) => page.items), [data]);
		const openStartProcessForm = useCallback(
			(processDefinitionKey: string) => {
				navigate({
					to: '/tasklist/processes/$processDefinitionKey/start',
					params: {processDefinitionKey},
					search,
				});
			},
			[navigate, search],
		);

		return (
			<StartProcessProvider>
				<TasklistProcessesPage
					initialFilterValues={search}
					tenants={currentUser.tenants}
					processes={processes}
					hasNextPage={hasNextPage}
					isFetchingNextPage={isFetchingNextPage}
					onLoadMore={fetchNextPage}
					onOpenStartProcessForm={openStartProcessForm}
				>
					<Outlet />
				</TasklistProcessesPage>
			</StartProcessProvider>
		);
	},
});
