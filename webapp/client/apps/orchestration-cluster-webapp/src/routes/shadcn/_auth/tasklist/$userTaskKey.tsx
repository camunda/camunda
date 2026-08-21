/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, redirect} from '@tanstack/react-router';

// Interim: the DS task-detail page (#60217-#60223, "Task Layout Page") hasn't
// shipped yet. Redirects here send the user to the existing Carbon page so
// shadcn-side navigation (start-process's resulting-task redirect/notification)
// has somewhere real to land. This is the only place that target is named —
// swap it to the real DS route once that work lands.
const INTERIM_TASK_DETAIL_ROUTE = '/tasklist/$userTaskKey' as const;

export const Route = createFileRoute('/shadcn/_auth/tasklist/$userTaskKey')({
	beforeLoad: ({params: {userTaskKey}}) => {
		throw redirect({
			to: INTERIM_TASK_DETAIL_ROUTE,
			params: {userTaskKey},
			search: {filter: 'all-open', sortBy: 'creation'},
			replace: true,
		});
	},
});
