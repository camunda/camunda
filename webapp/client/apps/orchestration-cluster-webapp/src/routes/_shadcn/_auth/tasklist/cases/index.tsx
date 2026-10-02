/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {createFileRoute, stripSearchParams, type ErrorComponentProps} from '@tanstack/react-router';
import {useSuspenseQuery} from '@tanstack/react-query';
import {PageLayout} from '@camunda/design-system';
import {TasklistCasesPage} from '#/tasklist/pages/TasklistCasesPage';
import {getCasesRequestBody} from '#/tasklist/modules/cases/getCasesRequestBody';
import {getWaitStatesRequestBody} from '#/tasklist/modules/cases/getWaitStatesRequestBody';
import {casesSearchDefaults, casesSearchSchema} from '#/tasklist/modules/cases/searchSchema';
import {queries} from '#/shared/http/queries';
import {ForbiddenError} from '#/shared/errors';
import {ForbiddenPage} from '#/shared/pages/shadcn.components/ForbiddenPage';
import {GenericErrorPage} from '#/shared/pages/shadcn.components/GenericErrorPage';

export const Route = createFileRoute('/_shadcn/_auth/tasklist/cases/')({
	validateSearch: casesSearchSchema,
	search: {
		middlewares: [stripSearchParams(casesSearchDefaults)],
	},
	loaderDeps: ({search}) => ({search}),
	loader: async ({context: {queryClient}, deps: {search}}) => {
		const {items} = await queryClient.query(queries.queryProcessInstances(getCasesRequestBody(search)));
		await queryClient.query(
			queries.queryElementInstanceWaitStates(
				getWaitStatesRequestBody(items.map(({processInstanceKey}) => processInstanceKey)),
			),
		);
	},
	errorComponent: function CasesErrorPage({error, reset}: ErrorComponentProps) {
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
	component: function TasklistCasesRoute() {
		const search = Route.useSearch();
		const {data} = useSuspenseQuery({
			...queries.queryProcessInstances(getCasesRequestBody(search)),
			refetchInterval: 5000,
		});
		const processInstanceKeys = useMemo(
			() => data.items.map(({processInstanceKey}) => processInstanceKey),
			[data.items],
		);
		const {data: waitStatesData} = useSuspenseQuery({
			...queries.queryElementInstanceWaitStates(getWaitStatesRequestBody(processInstanceKeys)),
			refetchInterval: 5000,
		});

		return (
			<TasklistCasesPage
				cases={data.items}
				waitStates={waitStatesData.items}
				totalItems={data.page.totalItems}
				hasMoreTotalItems={data.page.hasMoreTotalItems}
				search={search}
			/>
		);
	},
});
