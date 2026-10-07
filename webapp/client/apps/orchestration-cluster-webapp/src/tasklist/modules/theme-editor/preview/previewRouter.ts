/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient} from '@tanstack/react-query';
import {
	createMemoryHistory,
	createRootRoute,
	createRoute,
	createRouter,
	parseSearchWith,
	stringifySearchWith,
} from '@tanstack/react-router';
import {queries} from '#/shared/http/queries';
import {parseSearchValueSafe} from '#/shared/parseSearchValueSafe';
import {tasklistIndexSearchSchema} from '#/tasklist/modules/available-tasks/searchSchema';
import {processesSearchSchema} from '#/tasklist/modules/processes/searchSchema';
import {taskDetailsHistorySearchSchema} from '#/tasklist/modules/task-details-history/sortUtils';
import {PREVIEW_TASKS, PREVIEW_USER} from './fixtures';
import {PreviewNoTaskSelected, PreviewProcesses, PreviewTasksLayout} from './PreviewListPages';
import {
	PreviewShell,
	PreviewTaskDetail,
	PreviewTaskHistory,
	PreviewTaskProcess,
	PreviewTaskVariables,
} from './PreviewPages';

const PREVIEW_PATHS = {
	task: `/tasklist/${PREVIEW_TASKS[0]!.userTaskKey}`,
	tasks: '/tasklist',
	processes: '/tasklist/processes',
} as const;

/**
 * An in-memory router for the preview. Route ids copy the real ones (for example
 * `/_shadcn/_auth/tasklist/_tasks`), so Tasklist components that read the router with a
 * `from` id keep working, and task selection and tabs change the preview without touching
 * the page URL.
 */
function createPreviewRouter() {
	const rootRoute = createRootRoute({component: PreviewShell});
	const shadcnRoute = createRoute({getParentRoute: () => rootRoute, id: '_shadcn'});
	const authRoute = createRoute({getParentRoute: () => shadcnRoute, id: '_auth'});
	const tasklistRoute = createRoute({getParentRoute: () => authRoute, path: 'tasklist'});
	const tasksRoute = createRoute({
		getParentRoute: () => tasklistRoute,
		id: '_tasks',
		validateSearch: tasklistIndexSearchSchema,
		component: PreviewTasksLayout,
	});
	const tasksIndexRoute = createRoute({
		getParentRoute: () => tasksRoute,
		path: '/',
		component: PreviewNoTaskSelected,
	});
	const taskRoute = createRoute({
		getParentRoute: () => tasksRoute,
		path: '$userTaskKey',
		component: PreviewTaskDetail,
	});
	const taskIndexRoute = createRoute({getParentRoute: () => taskRoute, path: '/', component: PreviewTaskVariables});
	const taskProcessRoute = createRoute({
		getParentRoute: () => taskRoute,
		path: 'process',
		component: PreviewTaskProcess,
	});
	const taskHistoryRoute = createRoute({
		getParentRoute: () => taskRoute,
		path: 'history',
		validateSearch: taskDetailsHistorySearchSchema,
		component: PreviewTaskHistory,
	});
	const processesRoute = createRoute({
		getParentRoute: () => tasklistRoute,
		path: 'processes',
		validateSearch: processesSearchSchema,
		component: PreviewProcesses,
	});

	const routeTree = rootRoute.addChildren([
		shadcnRoute.addChildren([
			authRoute.addChildren([
				tasklistRoute.addChildren([
					tasksRoute.addChildren([
						tasksIndexRoute,
						taskRoute.addChildren([taskIndexRoute, taskProcessRoute, taskHistoryRoute]),
					]),
					processesRoute,
				]),
			]),
		]),
	]);

	// Creating a router registers it globally for hot module replacement. Keep the app router there.
	const appRouter = window.__TSR_ROUTER__;
	const router = createRouter({
		routeTree,
		history: createMemoryHistory({initialEntries: [PREVIEW_PATHS.task]}),
		defaultNotFoundComponent: () => null,
		defaultPendingMinMs: 0,
		parseSearch: parseSearchWith(parseSearchValueSafe),
		stringifySearch: stringifySearchWith(JSON.stringify, parseSearchValueSafe),
	});
	window.__TSR_ROUTER__ = appRouter;

	return router;
}

/** A query client that only serves fixture data: nothing is refetched or retried. */
function createPreviewQueryClient() {
	const queryClient = new QueryClient({
		defaultOptions: {
			queries: {
				staleTime: 'static',
				retry: false,
				refetchOnMount: false,
				refetchOnReconnect: false,
				refetchOnWindowFocus: false,
			},
			mutations: {retry: false},
		},
	});

	queryClient.setQueryData(queries.getCurrentUser().queryKey, PREVIEW_USER);

	return queryClient;
}

type PreviewRouter = ReturnType<typeof createPreviewRouter>;

export {PREVIEW_PATHS, createPreviewQueryClient, createPreviewRouter};
export type {PreviewRouter};
