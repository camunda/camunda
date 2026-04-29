/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import ReactDOM from 'react-dom/client';
import {RouterProvider, createRouter} from '@tanstack/react-router';
import {routeTree} from './routeTree.gen';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import './index.scss';
import {ThemeProvider} from './modules/theme/ThemeProvider';
import {tracking} from '#/modules/tracking';

const queryClient = new QueryClient();

const router = createRouter({
	routeTree,
	defaultPreload: 'intent',
	defaultPreloadStaleTime: 0,
	scrollRestoration: true,
	context: {
		queryClient,
	},
});

declare module '@tanstack/react-router' {
	interface Register {
		router: typeof router;
	}
}

const rootElement = document.getElementById('app')!;

if (!rootElement.innerHTML) {
	const root = ReactDOM.createRoot(rootElement);

	tracking.loadAnalyticsToWillingUsers().finally(() => {
		root.render(
			<ThemeProvider>
				<QueryClientProvider client={queryClient}>
					<RouterProvider router={router} />
				</QueryClientProvider>
			</ThemeProvider>,
		);
	});
}
