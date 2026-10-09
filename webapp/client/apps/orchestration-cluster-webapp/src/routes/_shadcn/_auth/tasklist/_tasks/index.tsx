/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute} from '@tanstack/react-router';
import {useSuspenseInfiniteQuery} from '@tanstack/react-query';
import {NoTaskSelectedPage} from '#/tasklist/pages/NoTaskSelectedPage';
import {queries} from '#/shared/http/queries';

export const Route = createFileRoute('/_shadcn/_auth/tasklist/_tasks/')({
	component: function NoTaskSelectedRoute() {
		const tasksRequestBody = Route.useRouteContext({select: ({tasksRequestBody}) => tasksRequestBody});
		const {data} = useSuspenseInfiniteQuery(queries.queryUserTasks(tasksRequestBody));
		const hasNoTasks = data.pages[0]?.items.length === 0;

		return <NoTaskSelectedPage hasNoTasks={hasNoTasks} />;
	},
});
