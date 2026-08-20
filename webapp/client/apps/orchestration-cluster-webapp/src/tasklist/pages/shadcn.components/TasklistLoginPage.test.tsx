/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {TasklistLoginPage} from './TasklistLoginPage';

describe('<TasklistLoginPage />', () => {
	it('should have the correct copyright notice', async () => {
		vi.useFakeTimers();
		const mockYear = 1984;
		vi.setSystemTime(new Date(mockYear, 0));

		const screen = await renderWithRouter(TasklistLoginPage, {path: '/shadcn/tasklist/login'});

		await expect
			.element(screen.getByText(`© Camunda Services GmbH ${mockYear}. All rights reserved. | 0.0.0`))
			.toBeVisible();
		vi.useRealTimers();
	});

	it('should not allow the form to be submitted with empty fields', async () => {
		const screen = await renderWithRouter(TasklistLoginPage, {path: '/shadcn/tasklist/login'});

		const usernameField = screen.getByLabelText(/username/i);
		const passwordField = screen.getByLabelText(/password/i);

		await userEvent.click(screen.getByRole('button', {name: /login/i}));

		await expect.element(usernameField).toHaveAccessibleDescription(/username is required/i);
		await expect.element(usernameField).toBeInvalid();
		await expect.element(passwordField).toHaveAccessibleDescription(/password is required/i);
		await expect.element(passwordField).toBeInvalid();

		await userEvent.fill(usernameField, 'demo');
		await userEvent.click(screen.getByRole('button', {name: /login/i}));

		await expect.element(passwordField).not.toHaveAccessibleDescription(/username is required/i);
		await expect.element(usernameField).toBeValid();
		await expect.element(passwordField).toHaveAccessibleDescription(/password is required/i);
		await expect.element(passwordField).toBeInvalid();

		await userEvent.clear(usernameField);
		await userEvent.fill(passwordField, 'demo');
		await userEvent.click(screen.getByRole('button', {name: /login/i}));

		await expect.element(passwordField).not.toHaveAccessibleDescription(/password is required/i);
		await expect.element(passwordField).toBeValid();
		await expect.element(usernameField).toHaveAccessibleDescription(/username is required/i);
		await expect.element(usernameField).toBeInvalid();
	});
});
