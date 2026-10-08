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
	mockCreateRoleEndpoint,
	mockDeleteRoleEndpoint,
	mockGetRoleEndpoint,
	mockUpdateRoleEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createRole} from '#/shared-test-modules/api-mocks/roles';
import {AdminRolesPage, type AdminRolesPageProps} from './AdminRolesPage';

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

async function renderPage(overrides: Partial<AdminRolesPageProps> = {}) {
	const onSearchChange = vi.fn();
	const onOpenRole = vi.fn();
	const screen = await render(
		<AdminRolesPage
			roles={[createRole()]}
			totalItems={1}
			defaultRoleIds={[]}
			search={{}}
			onSearchChange={onSearchChange}
			onOpenRole={onOpenRole}
			{...overrides}
		/>,
		{wrapper: getWrapper()},
	);

	return {screen, onSearchChange, onOpenRole};
}

describe('<AdminRolesPage />', () => {
	afterEach(async () => {
		await cleanup();
		toast.dismiss();
	});

	it('should list roles', async () => {
		const {screen} = await renderPage({roles: [createRole({roleId: 'ops', name: 'Operations'})]});

		await expect.element(screen.getByRole('cell', {name: 'ops'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Operations'})).toBeVisible();
	});

	it('should show an empty state when there are no roles', async () => {
		const {screen} = await renderPage({roles: [], totalItems: 0});

		await expect.element(screen.getByText('No roles found.')).toBeVisible();
	});

	it('should disable editing and deleting default roles', async () => {
		// given
		const {screen} = await renderPage({
			roles: [createRole({roleId: 'admin', name: 'Admin'})],
			defaultRoleIds: ['admin'],
		});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Row actions'}));

		// then
		await expect.element(screen.getByRole('menuitem', {name: 'Edit role'})).toBeDisabled();
		await expect.element(screen.getByRole('menuitem', {name: 'Delete role'})).toBeDisabled();
	});

	it('should open a role when a row is clicked', async () => {
		const role = createRole({roleId: 'ops'});
		const {screen, onOpenRole} = await renderPage({roles: [role]});

		await userEvent.click(screen.getByRole('cell', {name: 'ops'}));

		expect(onOpenRole).toHaveBeenCalledWith(role);
	});

	it('should search by role ID once the reader stops typing', async () => {
		const {screen, onSearchChange} = await renderPage();

		await userEvent.fill(screen.getByRole('searchbox'), 'ops');

		await vi.waitFor(() => expect(onSearchChange).toHaveBeenCalledWith({search: 'ops', page: undefined}), DEBOUNCED);
	});

	it('should reverse the role ID order when the reader sorts the column', async () => {
		const {screen, onSearchChange} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Role ID'}));

		expect(onSearchChange).toHaveBeenCalledWith({sortField: 'roleId', sortOrder: 'desc', page: undefined});
	});

	it('should sort by name when the reader sorts that column', async () => {
		const {screen, onSearchChange} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Role name'}));

		expect(onSearchChange).toHaveBeenCalledWith({sortField: 'name', sortOrder: 'asc', page: undefined});
	});

	it('should return to the first page when the page size changes', async () => {
		const {screen, onSearchChange} = await renderPage({search: {page: 3}, totalItems: 200});

		await userEvent.click(screen.getByRole('combobox'));
		await userEvent.click(screen.getByRole('option', {name: '50'}));

		expect(onSearchChange).toHaveBeenCalledWith({pageSize: 50, page: undefined});
	});

	it('should create a role', async ({worker}) => {
		const role = createRole({roleId: 'ops', name: 'Operations'});
		worker.use(
			mockCreateRoleEndpoint({successResponse: HttpResponse.json(role, {status: 201})}),
			mockGetRoleEndpoint({successResponse: HttpResponse.json(role)}),
		);
		const {screen} = await renderPage({roles: []});

		await userEvent.click(screen.getByRole('button', {name: 'Create role'}));
		await userEvent.fill(screen.getByLabelText('Role ID'), 'ops');
		await userEvent.fill(screen.getByLabelText('Role name'), 'Operations');
		await userEvent.click(screen.getByRole('button', {name: 'Create role'}).last());

		await expect.element(screen.getByText('Role Operations created')).toBeVisible();
	});

	it('should show an error on the role ID when it already exists', async ({worker}) => {
		worker.use(
			mockCreateRoleEndpoint({
				successResponse: HttpResponse.json(
					{type: 'about:blank', title: 'ALREADY_EXISTS', status: 409, detail: 'Role already exists'},
					{status: 409},
				),
			}),
		);
		const {screen} = await renderPage({roles: []});

		await userEvent.click(screen.getByRole('button', {name: 'Create role'}));
		await userEvent.fill(screen.getByLabelText('Role ID'), 'ops');
		await userEvent.fill(screen.getByLabelText('Role name'), 'Operations');
		await userEvent.click(screen.getByRole('button', {name: 'Create role'}).last());

		await expect.element(screen.getByText('A role with this ID already exists')).toBeVisible();
	});

	it("should show the backend's error detail when creating a role fails", async ({worker}) => {
		worker.use(
			mockCreateRoleEndpoint({
				successResponse: HttpResponse.json(
					{
						type: 'about:blank',
						title: 'UNAVAILABLE',
						status: 503,
						detail: 'Broker connection error',
						instance: '/v2/roles',
					},
					{status: 503},
				),
			}),
		);
		const {screen} = await renderPage({roles: []});

		await userEvent.click(screen.getByRole('button', {name: 'Create role'}));
		await userEvent.fill(screen.getByLabelText('Role ID'), 'ops');
		await userEvent.fill(screen.getByLabelText('Role name'), 'Operations');
		await userEvent.click(screen.getByRole('button', {name: 'Create role'}).last());

		await expect.element(screen.getByText('Failed to create role')).toBeVisible();
		await expect.element(screen.getByText('Broker connection error')).toBeVisible();
	});

	it('should require a role ID and name', async () => {
		const {screen} = await renderPage({roles: []});

		await userEvent.click(screen.getByRole('button', {name: 'Create role'}));
		await userEvent.click(screen.getByRole('button', {name: 'Create role'}).last());

		await expect.element(screen.getByText('Role ID is required')).toBeVisible();
		await expect.element(screen.getByText('Role name is required')).toBeVisible();
	});

	it('should reject an invalid role ID', async () => {
		const {screen} = await renderPage({roles: []});

		await userEvent.click(screen.getByRole('button', {name: 'Create role'}));
		await userEvent.fill(screen.getByLabelText('Role ID'), 'not valid!');
		await userEvent.click(screen.getByRole('button', {name: 'Create role'}).last());

		await expect.element(screen.getByText('Please enter a valid role ID')).toBeVisible();
	});

	it('should edit a role', async ({worker}) => {
		const role = createRole({roleId: 'ops', name: 'Operations'});
		const updated = {...role, name: 'Ops team'};
		worker.use(
			mockUpdateRoleEndpoint({successResponse: HttpResponse.json(updated)}),
			mockGetRoleEndpoint({successResponse: HttpResponse.json(updated)}),
		);
		const {screen} = await renderPage({roles: [role]});

		await userEvent.click(screen.getByRole('button', {name: 'Row actions'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Edit role'}));
		await userEvent.fill(screen.getByLabelText('Role name'), 'Ops team');
		await userEvent.click(screen.getByRole('button', {name: 'Update role'}));

		await expect.element(screen.getByText('Role Ops team updated')).toBeVisible();
	});

	it('should delete a role', async ({worker}) => {
		const role = createRole({roleId: 'ops'});
		worker.use(
			mockDeleteRoleEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetRoleEndpoint({successResponse: HttpResponse.json({}, {status: 404})}),
		);
		const {screen} = await renderPage({roles: [role]});

		await userEvent.click(screen.getByRole('button', {name: 'Row actions'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Delete role'}));
		await userEvent.click(screen.getByRole('button', {name: 'Delete role'}).last());

		await expect.element(screen.getByText('Role ops deleted')).toBeVisible();
	});

	it('should warn when a created role cannot be confirmed on the server', async ({worker}) => {
		// given
		worker.use(
			mockCreateRoleEndpoint({successResponse: HttpResponse.json(createRole({roleId: 'ops'}), {status: 201})}),
			mockGetRoleEndpoint({successResponse: HttpResponse.json({}, {status: 503})}),
		);
		const {screen} = await renderPage({roles: []});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Create role'}));
		await userEvent.fill(screen.getByLabelText('Role ID'), 'ops');
		await userEvent.fill(screen.getByLabelText('Role name'), 'Operations');
		await userEvent.click(screen.getByRole('button', {name: 'Create role'}).last());

		// then
		await expect.element(screen.getByText("Couldn't confirm the change took effect"), RETRY_BUDGET).toBeVisible();
	}, 15_000);

	it('should disable the delete confirmation while the deletion is pending', async ({worker}) => {
		worker.use(mockDeleteRoleEndpoint({successResponse: new HttpResponse(null, {status: 204}), delay: 'infinite'}));
		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Row actions'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Delete role'}));
		const confirmButton = screen.getByRole('button', {name: 'Delete role'}).last();
		await userEvent.click(confirmButton);

		await expect.element(confirmButton).toBeDisabled();
		await expect.element(screen.getByRole('button', {name: 'Cancel'})).toBeDisabled();
	});
});
