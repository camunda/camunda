/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockCurrentUserEndpoint} from '#/shared-test-modules/mock-handlers';
import {describe, expect, vi} from 'vitest';

describe('<Login />', () => {
	it('should have the correct copyright notice', async ({worker}) => {
		worker.use(mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}));

		vi.useFakeTimers();
		const mockYear = 1984;
		vi.setSystemTime(new Date(mockYear, 0));

		const screen = await renderWithRouter('/login');

		await expect
			.element(screen.getByText(`© Camunda Services GmbH ${mockYear}. All rights reserved. | 0.0.0`))
			.toBeVisible();
		vi.useRealTimers();
	});

	it('should not allow the form to be submitted with empty fields', async ({worker}) => {
		worker.use(mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}));

		const screen = await renderWithRouter('/login');

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
