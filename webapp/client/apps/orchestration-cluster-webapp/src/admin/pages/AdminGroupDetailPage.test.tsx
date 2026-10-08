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
import {
	mockAssignUserToGroupEndpoint,
	mockDeleteGroupEndpoint,
	mockGetGroupEndpoint,
	mockQueryClientsByGroupEndpoint,
	mockQueryRolesByGroupEndpoint,
	mockQueryUsersByGroupEndpoint,
	mockQueryUsersEndpoint,
	mockUnassignUserFromGroupEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createGroup, createPage} from '#/shared-test-modules/api-mocks/groups';
import {createUser} from '#/shared-test-modules/api-mocks/users';
import {AdminGroupDetailPage, type AdminGroupDetailPageProps} from './AdminGroupDetailPage';

const RETRY_BUDGET = {timeout: 10_000};

async function renderPage(overrides: Partial<AdminGroupDetailPageProps> = {}) {
	const onTabChange = vi.fn();
	const onDeleted = vi.fn();
	const screen = await renderWithRouter(
		() => (
			<>
				<AdminGroupDetailPage
					group={createGroup({groupId: 'ops', name: 'Operations', description: 'The ops team'})}
					isOidc={false}
					activeTab="users"
					onTabChange={onTabChange}
					onDeleted={onDeleted}
					{...overrides}
				/>
				<Toaster />
			</>
		),
		{path: '/admin/groups'},
	);

	return {screen, onTabChange, onDeleted};
}

describe('<AdminGroupDetailPage />', () => {
	afterEach(async () => {
		await cleanup();
		toast.dismiss();
	});

	it('should show the group details', async ({worker}) => {
		worker.use(mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}));

		const {screen} = await renderPage();

		await expect.element(screen.getByRole('heading', {name: 'Operations'})).toBeVisible();
		await expect.element(screen.getByText('The ops team')).toBeVisible();
	});

	it('should only offer users and roles when the login is not delegated', async ({worker}) => {
		worker.use(mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}));

		const {screen} = await renderPage({isOidc: false});

		await expect.element(screen.getByRole('tab', {name: 'Users'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Roles'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Mapping rules'})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('tab', {name: 'Clients'})).not.toBeInTheDocument();
	});

	it('should also offer mapping rules and clients when the login is delegated', async ({worker}) => {
		worker.use(mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}));

		const {screen} = await renderPage({isOidc: true});

		await expect.element(screen.getByRole('tab', {name: 'Mapping rules'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Clients'})).toBeVisible();
	});

	it('should fall back to the users tab when the active tab is not available', async ({worker}) => {
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([{username: 'john.doe'}]))}),
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createPage([createUser({username: 'john.doe'})]))}),
		);

		const {screen} = await renderPage({isOidc: false, activeTab: 'clients'});

		await expect.element(screen.getByRole('cell', {name: 'john.doe'})).toBeVisible();
	});

	it('should show the name and email of each user when the login is not delegated', async ({worker}) => {
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([{username: 'john.doe'}]))}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json(
					createPage([createUser({username: 'john.doe', name: 'John Doe', email: 'john.doe@example.com'})]),
				),
			}),
		);

		const {screen} = await renderPage({isOidc: false});

		await expect.element(screen.getByRole('cell', {name: 'Email', exact: true})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'John Doe'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'john.doe@example.com'})).toBeVisible();
	});

	it('should only show the username of each user when the login is delegated', async ({worker}) => {
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([{username: 'john.doe'}]))}),
		);

		const {screen} = await renderPage({isOidc: true});

		await expect.element(screen.getByRole('cell', {name: 'john.doe'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Email', exact: true})).not.toBeInTheDocument();
	});

	it('should report the selected tab', async ({worker}) => {
		worker.use(mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}));
		const {screen, onTabChange} = await renderPage();

		await userEvent.click(screen.getByRole('tab', {name: 'Roles'}));

		expect(onTabChange).toHaveBeenCalledWith('roles');
	});

	it('should list the roles of the group', async ({worker}) => {
		worker.use(
			mockQueryRolesByGroupEndpoint({
				successResponse: HttpResponse.json(createPage([{roleId: 'admin-role', name: 'Admin role'}])),
			}),
		);

		const {screen} = await renderPage({activeTab: 'roles'});

		await expect.element(screen.getByRole('cell', {name: 'admin-role'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Admin role'})).toBeVisible();
	});

	it('should list the clients of the group', async ({worker}) => {
		worker.use(
			mockQueryClientsByGroupEndpoint({successResponse: HttpResponse.json(createPage([{clientId: 'my-client'}]))}),
		);

		const {screen} = await renderPage({isOidc: true, activeTab: 'clients'});

		await expect.element(screen.getByRole('cell', {name: 'my-client'})).toBeVisible();
	});

	it('should show an empty state when the group has no members', async ({worker}) => {
		worker.use(mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}));

		const {screen} = await renderPage();

		await expect.element(screen.getByText('No users assigned to this group.')).toBeVisible();
	});

	it('should assign a user to the group', async ({worker}) => {
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}),
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createPage([createUser({username: 'jane.doe'})]))}),
			mockAssignUserToGroupEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);
		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.click(screen.getByRole('combobox', {name: 'Username'}));
		await userEvent.click(screen.getByRole('option', {name: 'jane.doe'}));
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}).last());

		await expect.element(screen.getByText('User jane.doe assigned')).toBeVisible();
	});

	it('should keep the assign button disabled until a member is chosen', async ({worker}) => {
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}),
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createPage([]))}),
		);
		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));

		await expect.element(screen.getByRole('button', {name: 'Assign user'}).last()).toBeDisabled();
	});

	it('should assign a client by its ID', async ({worker}) => {
		worker.use(mockQueryClientsByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}));
		const {screen} = await renderPage({isOidc: true, activeTab: 'clients'});

		await userEvent.click(screen.getByRole('button', {name: 'Assign client'}));
		await userEvent.fill(screen.getByLabelText('Client ID'), 'my-client');

		await expect.element(screen.getByRole('button', {name: 'Assign client'}).last()).toBeEnabled();
	});

	it('should assign an external user by username when the login is delegated', async ({worker}) => {
		// given
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}),
			mockAssignUserToGroupEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);
		const {screen} = await renderPage({isOidc: true});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.fill(screen.getByLabelText('Username'), 'external.user');
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}).last());

		// then
		await expect.element(screen.getByText('User external.user assigned')).toBeVisible();
	});

	it('should report a failed member lookup instead of an empty group', async ({worker}) => {
		// given
		worker.use(
			mockQueryUsersByGroupEndpoint({
				successResponse: HttpResponse.json(
					{type: 'about:blank', title: 'UNAVAILABLE', status: 503, detail: 'Unavailable', instance: '/v2/groups'},
					{status: 503},
				),
			}),
		);

		// when
		const {screen} = await renderPage();

		// then
		await expect.element(screen.getByRole('alert')).toHaveTextContent('Members could not be loaded');
		await expect.element(screen.getByText('No users assigned to this group.')).not.toBeInTheDocument();
	});

	it('should report a failed candidate lookup in the assign dialog', async ({worker}) => {
		// given
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json(
					{type: 'about:blank', title: 'UNAVAILABLE', status: 503, detail: 'Unavailable', instance: '/v2/users'},
					{status: 503},
				),
			}),
		);
		const {screen} = await renderPage();

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));

		// then
		await expect.element(screen.getByRole('alert')).toHaveTextContent('Options could not be loaded');
	});

	it('should remove a user from the group', async ({worker}) => {
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([{username: 'john.doe'}]))}),
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createPage([createUser({username: 'john.doe'})]))}),
			mockUnassignUserFromGroupEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);
		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Remove user john.doe', exact: true}));
		await userEvent.click(screen.getByRole('button', {name: 'Remove user', exact: true}));

		await expect.element(screen.getByText('User john.doe removed')).toBeVisible();
	});

	it('should warn when a removed user is still listed on the server', async ({worker}) => {
		// given
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([{username: 'john.doe'}]))}),
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createPage([createUser({username: 'john.doe'})]))}),
			mockUnassignUserFromGroupEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);
		const {screen} = await renderPage();

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Remove user john.doe', exact: true}));
		await userEvent.click(screen.getByRole('button', {name: 'Remove user', exact: true}));

		// then
		await expect.element(screen.getByText("Couldn't confirm the change took effect"), RETRY_BUDGET).toBeVisible();
	}, 15_000);

	it('should show the backend error when removing a member fails', async ({worker}) => {
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([{username: 'john.doe'}]))}),
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createPage([createUser({username: 'john.doe'})]))}),
			mockUnassignUserFromGroupEndpoint({
				successResponse: HttpResponse.json(
					{type: 'about:blank', title: 'UNAVAILABLE', status: 503, detail: 'Not allowed', instance: '/v2/groups'},
					{status: 503},
				),
			}),
		);
		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Remove user john.doe', exact: true}));
		await userEvent.click(screen.getByRole('button', {name: 'Remove user', exact: true}));

		await expect.element(screen.getByText('Failed to remove')).toBeVisible();
		await expect.element(screen.getByText('Not allowed')).toBeVisible();
	});

	it('should delete the group and notify the caller', async ({worker}) => {
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}),
			mockDeleteGroupEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetGroupEndpoint({successResponse: HttpResponse.json({}, {status: 404})}),
		);
		const {screen, onDeleted} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Delete group'}));
		await userEvent.click(screen.getByRole('button', {name: 'Delete group'}).last());

		await vi.waitFor(() => expect(onDeleted).toHaveBeenCalled());
	});
});
