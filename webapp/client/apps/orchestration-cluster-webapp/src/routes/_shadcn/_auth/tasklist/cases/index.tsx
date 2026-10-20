/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, stripSearchParams, type ErrorComponentProps} from '@tanstack/react-router';
import {useSuspenseQuery} from '@tanstack/react-query';
import {PageLayout} from '@camunda/design-system';
import {TasklistCasesPage} from '#/tasklist/pages/TasklistCasesPage';
import {getCasesRequestBody} from '#/tasklist/modules/cases/getCasesRequestBody';
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
		await queryClient.query(queries.queryProcessInstances(getCasesRequestBody(search)));
	},
	errorComponent: function CasesErrorPage({error, reset}: ErrorComponentProps) {
		return (
			<PageLayout>
				{error instanceof ForbiddenError ? <ForbiddenPage /> : <GenericErrorPage reset={reset} />}
			</PageLayout>
		);
	},
	component: function TasklistCasesRoute() {
		const search = Route.useSearch();
		const {data} = useSuspenseQuery({
			...queries.queryProcessInstances(getCasesRequestBody(search)),
			refetchInterval: 5000,
		});

		return (
			<TasklistCasesPage
				cases={data.items}
				totalItems={data.page.totalItems}
				hasMoreTotalItems={data.page.hasMoreTotalItems}
				search={search}
			/>
		);
	},
});
