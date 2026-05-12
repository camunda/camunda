/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {createMemoryHistory, createRootRoute, createRouter, RouterProvider} from '@tanstack/react-router';
import {Login} from './login';
import {it} from '#/vitest-modules/test-extend';
import {describe, expect, vi} from 'vitest';

function createTestRouter() {
	const rootRoute = createRootRoute({component: Login});
	return createRouter({
		routeTree: rootRoute,
		history: createMemoryHistory({initialEntries: ['/']}),
	});
}

describe('<Login />', () => {
	it('should have the correct copyright notice', async () => {
		vi.useFakeTimers();
		const mockYear = 1984;
		vi.setSystemTime(new Date(mockYear, 0));
		const screen = await render(<RouterProvider router={createTestRouter()} />);

		await expect
			.element(screen.getByText(`© Camunda Services GmbH ${mockYear}. All rights reserved. | 0.0.0`))
			.toBeVisible();
		vi.useRealTimers();
	});

	it('should not allow the form to be submitted with empty fields', async () => {
		const screen = await render(<RouterProvider router={createTestRouter()} />);

		await screen.getByRole('button', {name: /login/i}).click();

		await expect.element(screen.getByLabelText(/username/i)).toHaveAccessibleDescription(/username is required/i);
		await expect.element(screen.getByLabelText(/username/i)).toBeInvalid();
		await expect.element(screen.getByLabelText(/^password$/i)).toHaveAccessibleDescription(/password is required/i);
		await expect.element(screen.getByLabelText(/^password$/i)).toBeInvalid();

		await screen.getByLabelText(/username/i).fill('demo');
		await screen.getByRole('button', {name: /login/i}).click();

		await expect.element(screen.getByLabelText(/username/i)).toBeValid();
		await expect.element(screen.getByLabelText(/^password$/i)).toHaveAccessibleDescription(/password is required/i);
		await expect.element(screen.getByLabelText(/^password$/i)).toBeInvalid();

		await screen.getByLabelText(/username/i).fill('');
		await screen.getByLabelText(/^password$/i).fill('demo');
		await screen.getByRole('button', {name: /login/i}).click();

		await expect.element(screen.getByLabelText(/^password$/i)).toBeValid();
		await expect.element(screen.getByLabelText(/username/i)).toHaveAccessibleDescription(/username is required/i);
		await expect.element(screen.getByLabelText(/username/i)).toBeInvalid();
	});
});
