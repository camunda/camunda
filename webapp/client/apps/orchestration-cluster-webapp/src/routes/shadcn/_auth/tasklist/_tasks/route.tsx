/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, retainSearchParams, stripSearchParams} from '@tanstack/react-router';
import {TasksLayoutPage} from '#/tasklist/pages/shadcn.components/TasksLayoutPage';
import {
	tasklistIndexSearchDefaults,
	tasklistIndexSearchSchema,
	enforceSortInvariant,
	stripCustomFilterParams,
} from '#/tasklist/modules/available-tasks/searchSchema';

export const Route = createFileRoute('/shadcn/_auth/tasklist/_tasks')({
	validateSearch: tasklistIndexSearchSchema,
	search: {
		middlewares: [
			stripSearchParams(tasklistIndexSearchDefaults),
			retainSearchParams(['filter', 'sortBy']),
			enforceSortInvariant,
			stripCustomFilterParams,
		],
	},
	component: TasksLayoutPage,
});
