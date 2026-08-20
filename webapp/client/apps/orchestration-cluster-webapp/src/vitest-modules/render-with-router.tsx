/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {
	Outlet,
	RouterProvider,
	createMemoryHistory,
	createRootRouteWithContext,
	createRoute,
	createRouter,
	type RegisteredRouter,
} from '@tanstack/react-router';

type ValidRoutes = RegisteredRouter['routeTree']['types']['fileRouteTypes']['to'];

async function renderWithRouter(
	Component: React.ComponentType,
	{
		path,
		initialEntry = path,
	}: {
		path: ValidRoutes;
		initialEntry?: string;
	},
) {
	const queryClient = new QueryClient({
		defaultOptions: {
			queries: {retry: false},
		},
	});

	const rootRoute = createRootRouteWithContext<{queryClient: QueryClient}>()({
		component: () => <Outlet />,
	});

	const testRoute = createRoute({
		getParentRoute: () => rootRoute,
		path,
		component: () => <Component />,
	});

	const router = createRouter({
		routeTree: rootRoute.addChildren([testRoute]),
		history: createMemoryHistory({initialEntries: [initialEntry]}),
		defaultPendingMinMs: 0,
		defaultNotFoundComponent: () => null,
		context: {
			queryClient,
		},
	});

	await router.load();

	const screen = await render(
		<QueryClientProvider client={queryClient}>
			<RouterProvider router={router} />
		</QueryClientProvider>,
	);

	return {...screen, router, queryClient};
}

export {renderWithRouter};
