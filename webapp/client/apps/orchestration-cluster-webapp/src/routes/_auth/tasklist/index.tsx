/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {TasklistIndexPage} from '#/tasklist/pages/TasklistIndexPage';
import {tasklistIndexSearchDefaults, tasklistIndexSearchSchema} from '#/tasklist/modules/available-tasks/searchSchema';
import {createFileRoute, stripSearchParams} from '@tanstack/react-router';

export const Route = createFileRoute('/_auth/tasklist/')({
	validateSearch: tasklistIndexSearchSchema,
	search: {
		middlewares: [stripSearchParams(tasklistIndexSearchDefaults)],
	},
	component: TasklistIndexPage,
});
