/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {cleanup} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {Toaster, toast} from '@camunda/design-system';
import {afterEach, describe, expect, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockDeleteUserEndpoint, mockGetUserEndpoint} from '#/shared-test-modules/mock-handlers';
import {createUser} from '#/shared-test-modules/api-mocks/users';
import {AdminUserDetailPage} from './AdminUserDetailPage';

async function renderPage(user: ReturnType<typeof createUser>, onDeleted = vi.fn()) {
	return renderWithRouter(
		() => (
			<>
				<AdminUserDetailPage user={user} onDeleted={onDeleted} />
				<Toaster />
			</>
		),
		{path: '/admin/users/$username', initialEntry: `/admin/users/${user.username}`},
	);
}

describe('<AdminUserDetailPage />', () => {
	afterEach(async () => {
		await cleanup();
		toast.dismiss();
	});

	it('should show the user details', async () => {
		const user = createUser({username: 'john.doe', name: 'John Doe', email: 'john.doe@example.com'});
		const onDeleted = vi.fn();

		const screen = await renderPage(user, onDeleted);

		await expect.element(screen.getByRole('heading', {name: 'john.doe'})).toBeVisible();
		await expect.element(screen.getByText('John Doe')).toBeVisible();
		await expect.element(screen.getByText('john.doe@example.com')).toBeVisible();
	});

	it('should navigate to the users list when the breadcrumb is clicked', async () => {
		// given
		const user = createUser({username: 'john.doe'});
		const screen = await renderPage(user);

		// when
		await userEvent.click(screen.getByRole('link', {name: 'Users'}));

		// then
		await vi.waitFor(() => expect(screen.router.state.location.pathname).toBe('/admin/users'));
	});

	it('should delete the user and notify the caller', async ({worker}) => {
		const user = createUser({username: 'john.doe'});
		const onDeleted = vi.fn();
		worker.use(
			mockDeleteUserEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetUserEndpoint({successResponse: HttpResponse.json({}, {status: 404})}),
		);

		const screen = await renderPage(user, onDeleted);

		await userEvent.click(screen.getByRole('button', {name: 'Delete user'}));
		await userEvent.click(screen.getByRole('button', {name: 'Delete user'}).last());

		await vi.waitFor(() => expect(onDeleted).toHaveBeenCalled());
	});
});
