/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import ReactDOM from 'react-dom/client';
import {RouterProvider, createRouter, parseSearchWith, stringifySearchWith} from '@tanstack/react-router';
import {routeTree} from './routeTree.gen';
import {QueryClientProvider} from '@tanstack/react-query';
import {reactQueryClient} from '#/shared/http/reactQueryClient';
import {initI18next} from '#/shared/i18n/i18next';
import {getBootConfig} from '#/shared/config/getBootConfig';
import {parseSearchValueSafe} from '#/shared/parseSearchValueSafe';
import {loadOsano} from '#/shared/osano';

initI18next();

const router = createRouter({
	routeTree,
	basepath: getBootConfig().baseName,
	defaultPreload: 'intent',
	defaultPreloadStaleTime: 0,
	scrollRestoration: true,
	parseSearch: parseSearchWith(parseSearchValueSafe),
	stringifySearch: stringifySearchWith(JSON.stringify, parseSearchValueSafe),
	context: {
		queryClient: reactQueryClient,
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

	loadOsano().finally(() => {
		root.render(
			<QueryClientProvider client={reactQueryClient}>
				<RouterProvider router={router} />
			</QueryClientProvider>,
		);
	});
}
