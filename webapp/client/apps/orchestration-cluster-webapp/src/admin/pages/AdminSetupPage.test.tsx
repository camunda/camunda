/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {userEvent} from 'vitest/browser';
import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockCreateAdminUserEndpoint} from '#/shared-test-modules/mock-handlers';
import {AdminSetupPage} from './AdminSetupPage';

async function renderPage() {
	return renderWithRouter(AdminSetupPage, {path: '/admin/setup'});
}

describe('<AdminSetupPage />', () => {
	it('should show the setup form', async () => {
		const screen = await renderPage();

		await expect.element(screen.getByRole('heading', {name: 'Create admin user'})).toBeVisible();
		await expect.element(screen.getByLabelText(/^Username/)).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Create user'})).toBeVisible();
	});

	it('should describe the password field with its requirements before any validation error', async () => {
		const screen = await renderPage();

		await expect
			.element(screen.getByLabelText(/^Password/))
			.toHaveAccessibleDescription(/at least 6 characters long and include at least one number/);
	});

	it('should wait for blur before showing a password mismatch', async () => {
		const screen = await renderPage();

		await userEvent.fill(screen.getByLabelText(/^Password/), 'secret1');
		await userEvent.fill(screen.getByLabelText(/^Confirm password/), 'secret2');
		await expect.element(screen.getByText('Passwords do not match')).not.toBeInTheDocument();

		await userEvent.tab();

		await expect.element(screen.getByText('Passwords do not match')).toBeVisible();
	});

	it('should require username, password and password confirmation', async () => {
		const screen = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Create user'}));

		await expect.element(screen.getByText('Username is required')).toBeVisible();
		await expect.element(screen.getByText('Password is required')).toBeVisible();
		await expect.element(screen.getByText('Confirm password is required')).toBeVisible();
	});

	it('should reject a password without a digit', async () => {
		const screen = await renderPage();

		await userEvent.fill(screen.getByLabelText(/^Username/), 'admin');
		await userEvent.fill(screen.getByLabelText(/^Password/), 'secret');
		await userEvent.fill(screen.getByLabelText(/^Confirm password/), 'secret');
		await userEvent.click(screen.getByRole('button', {name: 'Create user'}));

		await expect
			.element(
				screen.getByRole('alert').filter({hasText: 'at least 6 characters long and include at least one number'}),
			)
			.toBeVisible();
	});

	it('should reject an invalid email', async () => {
		const screen = await renderPage();

		await userEvent.fill(screen.getByLabelText('Email'), 'not-an-email');
		await userEvent.click(screen.getByRole('button', {name: 'Create user'}));

		await expect.element(screen.getByText('Please enter a valid email')).toBeVisible();
	});

	it("should show the backend's error detail inline", async ({worker}) => {
		worker.use(
			mockCreateAdminUserEndpoint({
				successResponse: HttpResponse.json(
					{
						type: 'about:blank',
						title: 'FORBIDDEN',
						status: 403,
						detail: 'An admin user already exists',
						instance: '/v2/setup/user',
					},
					{status: 403},
				),
			}),
		);
		const screen = await renderPage();

		await userEvent.fill(screen.getByLabelText(/^Username/), 'admin');
		await userEvent.fill(screen.getByLabelText(/^Password/), 'secret123');
		await userEvent.fill(screen.getByLabelText(/^Confirm password/), 'secret123');
		await userEvent.click(screen.getByRole('button', {name: 'Create user'}));

		await expect.element(screen.getByText('An admin user already exists')).toBeVisible();
	});
});
