/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {TasklistProcessesPage} from '#/tasklist/pages/TasklistProcessesPage';
import {createFileRoute, type ErrorComponentProps} from '@tanstack/react-router';
import {processesSearchSchema} from '#/tasklist/modules/processes/searchSchema';
import {getProcessDefinitionsRequestBody} from '#/tasklist/modules/processes/getProcessDefinitionsRequestBody';
import {queries} from '#/shared/http/queries';
import {useSuspenseInfiniteQuery, useSuspenseQuery} from '@tanstack/react-query';
import {useEffect, useMemo} from 'react';
import {tracking} from '#/shared/tracking';
import {requestErrorSchema} from '#/shared/http/request';
import {ForbiddenPage} from '#/shared/pages/ForbiddenPage';
import {GenericErrorPage} from '#/shared/pages/GenericErrorPage';

const HTTP_STATUS_FORBIDDEN = 403;

export const Route = createFileRoute('/_auth/tasklist/processes')({
	validateSearch: processesSearchSchema,
	loaderDeps: ({search}) => ({search}),
	loader: async ({context: {queryClient}, deps: {search}}) => {
		const {tenants} = await queryClient.ensureQueryData(queries.getCurrentUser());
		await queryClient.ensureInfiniteQueryData(
			queries.queryProcessDefinitionsInfinite(getProcessDefinitionsRequestBody(search, tenants)),
		);
	},
	errorComponent: function ProcessesErrorPage({error, reset}: ErrorComponentProps) {
		useEffect(() => {
			tracking.track({eventName: 'tasklist:processes-fetch-failed'});
		}, [error]);

		const result = requestErrorSchema.safeParse(error);

		if (
			result.success &&
			result.data.variant === 'failed-response' &&
			result.data.response.status === HTTP_STATUS_FORBIDDEN
		) {
			return (
				<main id="main-content" className={`cds--content`}>
					<ForbiddenPage />
				</main>
			);
		}

		return (
			<main id="main-content" className={`cds--content`}>
				<GenericErrorPage reset={reset} />
			</main>
		);
	},
	component: function TasklistProcessesRoute() {
		const search = Route.useSearch();
		const {data: currentUser} = useSuspenseQuery(queries.getCurrentUser());
		const {data, error, fetchNextPage, hasNextPage, isFetchingNextPage} = useSuspenseInfiniteQuery({
			...queries.queryProcessDefinitionsInfinite(getProcessDefinitionsRequestBody(search, currentUser.tenants)),
			refetchInterval: 5000,
		});
		const processes = useMemo(() => data.pages.flatMap((page) => page.items), [data]);

		if (error !== null) {
			throw error;
		}

		return (
			<TasklistProcessesPage
				initialFilterValues={search}
				processes={processes}
				hasNextPage={hasNextPage}
				isFetchingNextPage={isFetchingNextPage}
				onLoadMore={() => fetchNextPage()}
			/>
		);
	},
});
