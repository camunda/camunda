/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, expect, vi, beforeEach} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {createMemoryHistory, createRootRoute, createRouter, RouterProvider} from '@tanstack/react-router';
import {authenticationStore} from '#/modules/auth/stores/authentication';
import {SessionWatcher} from './SessionWatcher';

function makeRouter(initialPath = '/') {
	const rootRoute = createRootRoute({component: SessionWatcher});
	return createRouter({
		routeTree: rootRoute,
		history: createMemoryHistory({initialEntries: [initialPath]}),
	});
}

describe('<SessionWatcher />', () => {
	beforeEach(() => {
		authenticationStore.reset();
	});

	it('navigates to login when session expires', async () => {
		authenticationStore.activateSession();
		const router = makeRouter('/tasks/123');
		const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(undefined as never);

		await render(<RouterProvider router={router} />);
		authenticationStore.disableSession();

		await vi.waitFor(() => {
			expect(navigateSpy).toHaveBeenCalledWith(
				expect.objectContaining({
					to: '/login',
					replace: true,
				}),
			);
		});
	});
});
