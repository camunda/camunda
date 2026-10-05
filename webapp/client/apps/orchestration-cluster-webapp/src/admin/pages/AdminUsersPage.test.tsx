/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {HttpResponse} from 'msw';
import {cleanup, render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {TooltipProvider, Toaster, toast} from '@camunda/design-system';
import {afterEach, describe, expect, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {
	mockCreateUserEndpoint,
	mockDeleteUserEndpoint,
	mockGetUserEndpoint,
	mockUpdateUserEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createUser} from '#/shared-test-modules/api-mocks/users';
import type {User} from '@camunda/camunda-api-zod-schemas/8.11';
import {AdminUsersPage, type AdminUsersPageProps} from './AdminUsersPage';

const DEBOUNCED = {timeout: 3000};
const RETRY_BUDGET = {timeout: 10_000};

function getWrapper() {
	const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});

	const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
		<QueryClientProvider client={queryClient}>
			<TooltipProvider>{children}</TooltipProvider>
			<Toaster />
		</QueryClientProvider>
	);

	return Wrapper;
}

async function renderPage(overrides: Partial<AdminUsersPageProps> = {}) {
	const onSearchChange = vi.fn();
	const onOpenUser = vi.fn();
	const screen = await render(
		<AdminUsersPage
			users={[createUser()]}
			totalItems={1}
			search={{}}
			onSearchChange={onSearchChange}
			onOpenUser={onOpenUser}
			{...overrides}
		/>,
		{wrapper: getWrapper()},
	);

	return {screen, onSearchChange, onOpenUser};
}

describe('<AdminUsersPage />', () => {
	afterEach(async () => {
		await cleanup();
		toast.dismiss();
	});

	it('should list users', async () => {
		const user: User = createUser({username: 'john.doe', name: 'John Doe', email: 'john.doe@example.com'});

		const {screen} = await renderPage({users: [user]});

		await expect.element(screen.getByRole('cell', {name: 'john.doe'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'John Doe'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'john.doe@example.com'})).toBeVisible();
	});

	it('should open a user when a row is clicked', async () => {
		const user = createUser({username: 'john.doe'});
		const {screen, onOpenUser} = await renderPage({users: [user]});

		await userEvent.click(screen.getByRole('cell', {name: 'john.doe'}));

		expect(onOpenUser).toHaveBeenCalledWith(user);
	});

	it('should search by username once the reader stops typing', async () => {
		const {screen, onSearchChange} = await renderPage();

		await userEvent.fill(screen.getByRole('searchbox'), 'jane');

		await vi.waitFor(() => expect(onSearchChange).toHaveBeenCalledWith({search: 'jane', page: undefined}), DEBOUNCED);
	});

	it('should reverse the username order when the reader sorts the column', async () => {
		const {screen, onSearchChange} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Username'}));

		expect(onSearchChange).toHaveBeenCalledWith({sortField: 'username', sortOrder: 'desc', page: undefined});
	});

	it('should sort by name or email when the reader sorts those columns', async () => {
		const {screen, onSearchChange} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Name'}));
		expect(onSearchChange).toHaveBeenCalledWith({sortField: 'name', sortOrder: 'asc', page: undefined});

		await userEvent.click(screen.getByRole('button', {name: 'Email'}));
		expect(onSearchChange).toHaveBeenCalledWith({sortField: 'email', sortOrder: 'asc', page: undefined});
	});

	it('should return to the first page when the page size changes', async () => {
		const {screen, onSearchChange} = await renderPage({search: {page: 3}, totalItems: 200});

		await userEvent.click(screen.getByRole('combobox'));
		await userEvent.click(screen.getByRole('option', {name: '50'}));

		expect(onSearchChange).toHaveBeenCalledWith({pageSize: 50, page: undefined});
	});

	it('should create a user', async ({worker}) => {
		worker.use(
			mockCreateUserEndpoint({successResponse: HttpResponse.json(createUser({username: 'new.user'}))}),
			mockGetUserEndpoint({successResponse: HttpResponse.json(createUser({username: 'new.user'}))}),
		);

		const {screen} = await renderPage({users: []});

		await userEvent.click(screen.getByRole('button', {name: 'Create user'}));
		await userEvent.fill(screen.getByLabelText('Username'), 'new.user');
		await userEvent.fill(screen.getByLabelText('Password'), 'secret123');
		await userEvent.fill(screen.getByLabelText('Confirm password'), 'secret123');
		await userEvent.click(screen.getByRole('button', {name: 'Create user'}).last());

		await expect.element(screen.getByText('User new.user created')).toBeVisible();
	});

	it("should show the backend's error detail when creating a user fails", async ({worker}) => {
		worker.use(
			mockCreateUserEndpoint({
				successResponse: HttpResponse.json(
					{
						type: 'about:blank',
						title: 'UNAVAILABLE',
						status: 503,
						detail: 'Expected to handle request, but there was a connection error with one of the brokers',
						instance: '/v2/users',
					},
					{status: 503},
				),
			}),
		);

		const {screen} = await renderPage({users: []});

		await userEvent.click(screen.getByRole('button', {name: 'Create user'}));
		await userEvent.fill(screen.getByLabelText('Username'), 'new.user');
		await userEvent.fill(screen.getByLabelText('Password'), 'secret123');
		await userEvent.fill(screen.getByLabelText('Confirm password'), 'secret123');
		await userEvent.click(screen.getByRole('button', {name: 'Create user'}).last());

		await expect.element(screen.getByText('Failed to create user')).toBeVisible();
		await expect
			.element(screen.getByText('Expected to handle request, but there was a connection error with one of the brokers'))
			.toBeVisible();
	});

	it('should show a validation error when the passwords do not match', async () => {
		const {screen} = await renderPage({users: []});

		await userEvent.click(screen.getByRole('button', {name: 'Create user'}));
		await userEvent.fill(screen.getByLabelText('Username'), 'new.user');
		await userEvent.fill(screen.getByLabelText('Password'), 'secret123');
		await userEvent.fill(screen.getByLabelText('Confirm password'), 'different');
		await userEvent.click(screen.getByRole('button', {name: 'Create user'}).last());

		await expect.element(screen.getByText('Passwords do not match')).toBeVisible();
	});

	it('should edit a user', async ({worker}) => {
		const user = createUser({username: 'john.doe', name: 'John Doe'});
		worker.use(
			mockUpdateUserEndpoint({successResponse: HttpResponse.json({...user, name: 'Johnny Doe'})}),
			mockGetUserEndpoint({successResponse: HttpResponse.json({...user, name: 'Johnny Doe'})}),
		);

		const {screen} = await renderPage({users: [user]});

		await userEvent.click(screen.getByRole('button', {name: 'Row actions'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Edit user'}));
		await userEvent.fill(screen.getByLabelText('Name'), 'Johnny Doe');
		await userEvent.click(screen.getByRole('button', {name: 'Update user'}));

		await expect.element(screen.getByText('User john.doe updated')).toBeVisible();
	});

	it('should delete a user', async ({worker}) => {
		const user = createUser({username: 'john.doe'});
		worker.use(
			mockDeleteUserEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetUserEndpoint({successResponse: HttpResponse.json({}, {status: 404})}),
		);

		const {screen} = await renderPage({users: [user]});

		await userEvent.click(screen.getByRole('button', {name: 'Row actions'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Delete user'}));
		await userEvent.click(screen.getByRole('button', {name: 'Delete user'}).last());

		await expect.element(screen.getByText('User john.doe deleted')).toBeVisible();
	});

	it('should warn when a created user cannot be confirmed on the server', async ({worker}) => {
		// given
		worker.use(
			mockCreateUserEndpoint({successResponse: HttpResponse.json(createUser({username: 'new.user'}))}),
			mockGetUserEndpoint({successResponse: HttpResponse.json({}, {status: 503})}),
		);
		const {screen} = await renderPage({users: []});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Create user'}));
		await userEvent.fill(screen.getByLabelText('Username'), 'new.user');
		await userEvent.fill(screen.getByLabelText('Password'), 'secret123');
		await userEvent.fill(screen.getByLabelText('Confirm password'), 'secret123');
		await userEvent.click(screen.getByRole('button', {name: 'Create user'}).last());

		// then
		await expect.element(screen.getByText('User new.user created')).toBeVisible();
		await expect.element(screen.getByText("Couldn't confirm the change took effect"), RETRY_BUDGET).toBeVisible();
		await expect.element(screen.getByText('Refresh the page to see the latest state.')).toBeVisible();
	}, 15_000);

	it('should warn when a deleted user is still visible on the server', async ({worker}) => {
		// given
		const user = createUser({username: 'john.doe'});
		worker.use(
			mockDeleteUserEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetUserEndpoint({successResponse: HttpResponse.json(user)}),
		);
		const {screen} = await renderPage({users: [user]});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Row actions'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Delete user'}));
		await userEvent.click(screen.getByRole('button', {name: 'Delete user'}).last());

		// then
		await expect.element(screen.getByText('User john.doe deleted')).toBeVisible();
		await expect.element(screen.getByText("Couldn't confirm the change took effect"), RETRY_BUDGET).toBeVisible();
	}, 15_000);

	it('should disable the delete confirmation while the deletion is pending', async ({worker}) => {
		const user = createUser({username: 'john.doe'});
		worker.use(mockDeleteUserEndpoint({successResponse: new HttpResponse(null, {status: 204}), delay: 'infinite'}));

		const {screen} = await renderPage({users: [user]});

		await userEvent.click(screen.getByRole('button', {name: 'Row actions'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Delete user'}));
		const confirmButton = screen.getByRole('button', {name: 'Delete user'}).last();
		await userEvent.click(confirmButton);

		await expect.element(confirmButton).toBeDisabled();
		await expect.element(screen.getByRole('button', {name: 'Cancel'})).toBeDisabled();
	});
});
