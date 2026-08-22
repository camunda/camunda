/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {createFileRoute} from '@tanstack/react-router';
import {useSuspenseInfiniteQuery, useSuspenseQuery} from '@tanstack/react-query';
import {TasklistProcessesPage} from '#/tasklist/pages/shadcn.components/TasklistProcessesPage';
import {processesSearchSchema} from '#/tasklist/modules/processes/searchSchema';
import {getProcessDefinitionsRequestBody} from '#/tasklist/modules/processes/getProcessDefinitionsRequestBody';
import {queries} from '#/shared/http/queries';

export const Route = createFileRoute('/shadcn/_auth/tasklist/processes')({
	validateSearch: processesSearchSchema,
	loaderDeps: ({search}) => ({search}),
	loader: async ({context: {queryClient}, deps: {search}}) => {
		const {tenants} = await queryClient.ensureQueryData(queries.getCurrentUser());
		await queryClient.ensureInfiniteQueryData(
			queries.queryProcessDefinitionsInfinite(getProcessDefinitionsRequestBody(search, tenants)),
		);
	},
	component: function TasklistProcessesRoute() {
		const search = Route.useSearch();
		const {data: currentUser} = useSuspenseQuery(queries.getCurrentUser());
		const processDefinitionsRequestBody = getProcessDefinitionsRequestBody(search, currentUser.tenants);
		const {data, fetchNextPage, hasNextPage, isFetchingNextPage} = useSuspenseInfiniteQuery({
			...queries.queryProcessDefinitionsInfinite(processDefinitionsRequestBody),
			refetchInterval: 5000,
		});
		const processes = useMemo(() => data.pages.flatMap((page) => page.items), [data]);

		return (
			<TasklistProcessesPage
				initialFilterValues={search}
				tenants={currentUser.tenants}
				processes={processes}
				hasNextPage={hasNextPage}
				isFetchingNextPage={isFetchingNextPage}
				onLoadMore={fetchNextPage}
			/>
		);
	},
});
