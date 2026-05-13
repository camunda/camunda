/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {QueryClient} from '@tanstack/react-query';
import {RouterProvider, createMemoryHistory, createRouter} from '@tanstack/react-router';
import {routeTree} from '../routeTree.gen';

function createTestRouter(initialLocation = '/') {
	return createRouter({
		routeTree,
		history: createMemoryHistory({initialEntries: [initialLocation]}),
		context: {
			queryClient: new QueryClient({
				defaultOptions: {queries: {retry: false}},
			}),
		},
	});
}

async function renderWithRouter(initialLocation = '/') {
	const router = createTestRouter(initialLocation);
	const screen = await render(<RouterProvider router={router} />);
	return {...screen, router};
}

export {createTestRouter, renderWithRouter};
