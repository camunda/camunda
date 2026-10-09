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
	mockAssignClientToRoleEndpoint,
	mockAssignGroupToRoleEndpoint,
	mockAssignMappingToRoleEndpoint,
	mockAssignUserToRoleEndpoint,
	mockDeleteRoleEndpoint,
	mockGetRoleEndpoint,
	mockQueryClientsByRoleEndpoint,
	mockQueryGroupsByRoleEndpoint,
	mockQueryGroupsEndpoint,
	mockQueryMappingRulesByRoleEndpoint,
	mockQueryMappingRulesEndpoint,
	mockQueryUsersByRoleEndpoint,
	mockQueryUsersEndpoint,
	mockUnassignGroupFromRoleEndpoint,
	mockUnassignUserFromRoleEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createMembersPage, createRole} from '#/shared-test-modules/api-mocks/roles';
import {createUser} from '#/shared-test-modules/api-mocks/users';
import {AdminRoleDetailPage, type AdminRoleDetailPageProps} from './AdminRoleDetailPage';

const RETRY_BUDGET = {timeout: 10_000};

async function renderPage(overrides: Partial<AdminRoleDetailPageProps> = {}) {
	const onTabChange = vi.fn();
	const onDeleted = vi.fn();
	const screen = await renderWithRouter(
		() => (
			<>
				<AdminRoleDetailPage
					role={createRole({roleId: 'ops', name: 'Operations', description: 'The ops role'})}
					isOidc={false}
					isCamundaGroupsEnabled={true}
					defaultRoleIds={[]}
					activeTab="users"
					onTabChange={onTabChange}
					onDeleted={onDeleted}
					{...overrides}
				/>
				<Toaster />
			</>
		),
		{path: '/admin/roles'},
	);

	return {screen, onTabChange, onDeleted};
}

describe('<AdminRoleDetailPage />', () => {
	afterEach(async () => {
		await cleanup();
		toast.dismiss();
	});

	it('should show the role details', async ({worker}) => {
		worker.use(mockQueryUsersByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([]))}));

		const {screen} = await renderPage();

		await expect.element(screen.getByRole('heading', {name: 'Operations'})).toBeVisible();
		await expect.element(screen.getByText('The ops role')).toBeVisible();
	});

	it('should not offer editing or deleting a default role', async ({worker}) => {
		worker.use(mockQueryUsersByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([]))}));

		const {screen} = await renderPage({defaultRoleIds: ['ops']});

		await expect.element(screen.getByRole('heading', {name: 'Operations'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Edit role'})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Delete role'})).not.toBeInTheDocument();
	});

	it('should only offer users and groups when the login is not delegated', async ({worker}) => {
		worker.use(mockQueryUsersByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([]))}));

		const {screen} = await renderPage({isOidc: false});

		await expect.element(screen.getByRole('tab', {name: 'Users'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Groups'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Mapping rules'})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('tab', {name: 'Clients'})).not.toBeInTheDocument();
	});

	it('should also offer mapping rules and clients when the login is delegated', async ({worker}) => {
		worker.use(mockQueryUsersByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([]))}));

		const {screen} = await renderPage({isOidc: true});

		await expect.element(screen.getByRole('tab', {name: 'Mapping rules'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Clients'})).toBeVisible();
	});

	it('should fall back to the users tab when the active tab is not available', async ({worker}) => {
		worker.use(
			mockQueryUsersByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([{username: 'john.doe'}]))}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json(createMembersPage([createUser({username: 'john.doe'})])),
			}),
		);

		const {screen} = await renderPage({isOidc: false, activeTab: 'clients'});

		await expect.element(screen.getByRole('cell', {name: 'john.doe'})).toBeVisible();
	});

	it('should show the name and email of each user when the login is not delegated', async ({worker}) => {
		worker.use(
			mockQueryUsersByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([{username: 'john.doe'}]))}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json(
					createMembersPage([createUser({username: 'john.doe', name: 'John Doe', email: 'john.doe@example.com'})]),
				),
			}),
		);

		const {screen} = await renderPage({isOidc: false});

		await expect.element(screen.getByRole('cell', {name: 'John Doe'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'john.doe@example.com'})).toBeVisible();
	});

	it('should only show the username of each user when the login is delegated', async ({worker}) => {
		worker.use(
			mockQueryUsersByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([{username: 'john.doe'}]))}),
		);

		const {screen} = await renderPage({isOidc: true});

		await expect.element(screen.getByRole('cell', {name: 'john.doe'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Email', exact: true})).not.toBeInTheDocument();
	});

	it('should report the selected tab', async ({worker}) => {
		worker.use(mockQueryUsersByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([]))}));
		const {screen, onTabChange} = await renderPage();

		await userEvent.click(screen.getByRole('tab', {name: 'Groups'}));

		expect(onTabChange).toHaveBeenCalledWith('groups');
	});

	it('should show the name of each group when Camunda groups are enabled', async ({worker}) => {
		worker.use(
			mockQueryGroupsByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([{groupId: 'eng'}]))}),
			mockQueryGroupsEndpoint({
				successResponse: HttpResponse.json(createMembersPage([{groupId: 'eng', name: 'Engineering'}])),
			}),
		);

		const {screen} = await renderPage({activeTab: 'groups', isCamundaGroupsEnabled: true});

		await expect.element(screen.getByRole('cell', {name: 'eng', exact: true})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Engineering'})).toBeVisible();
	});

	it('should only show the ID of each group when Camunda groups are disabled', async ({worker}) => {
		worker.use(
			mockQueryGroupsByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([{groupId: 'eng'}]))}),
		);

		const {screen} = await renderPage({activeTab: 'groups', isCamundaGroupsEnabled: false});

		await expect.element(screen.getByRole('cell', {name: 'eng', exact: true})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Group name'})).not.toBeInTheDocument();
	});

	it('should list the mapping rules of the role', async ({worker}) => {
		worker.use(
			mockQueryMappingRulesByRoleEndpoint({
				successResponse: HttpResponse.json(
					createMembersPage([{mappingRuleId: 'rule-1', name: 'Rule one', claimName: 'groups', claimValue: 'ops'}]),
				),
			}),
		);

		const {screen} = await renderPage({isOidc: true, activeTab: 'mappingRules'});

		await expect.element(screen.getByRole('cell', {name: 'rule-1'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Rule one'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'groups'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'ops'})).toBeVisible();
	});

	it('should list the clients of the role', async ({worker}) => {
		worker.use(
			mockQueryClientsByRoleEndpoint({
				successResponse: HttpResponse.json(createMembersPage([{clientId: 'my-client'}])),
			}),
		);

		const {screen} = await renderPage({isOidc: true, activeTab: 'clients'});

		await expect.element(screen.getByRole('cell', {name: 'my-client'})).toBeVisible();
	});

	it('should show an empty state when the role has no members', async ({worker}) => {
		worker.use(mockQueryUsersByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([]))}));

		const {screen} = await renderPage();

		await expect.element(screen.getByText('No users assigned to this role.')).toBeVisible();
	});

	it('should assign a user to the role', async ({worker}) => {
		worker.use(
			mockQueryUsersByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([]))}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json(createMembersPage([createUser({username: 'jane.doe'})])),
			}),
			mockAssignUserToRoleEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);
		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.click(screen.getByRole('combobox', {name: 'Username'}));
		await userEvent.click(screen.getByRole('option', {name: 'jane.doe'}));
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}).last());

		await expect.element(screen.getByText('User jane.doe assigned')).toBeVisible();
	});

	it('should assign a group chosen from the list when Camunda groups are enabled', async ({worker}) => {
		// given
		worker.use(
			mockQueryGroupsByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([]))}),
			mockQueryGroupsEndpoint({
				successResponse: HttpResponse.json(createMembersPage([{groupId: 'eng', name: 'Engineering'}])),
			}),
			mockAssignGroupToRoleEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);
		const {screen} = await renderPage({activeTab: 'groups', isCamundaGroupsEnabled: true});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign group'}));
		await userEvent.click(screen.getByRole('combobox', {name: 'Group ID'}));
		await userEvent.click(screen.getByRole('option', {name: 'eng'}));
		await userEvent.click(screen.getByRole('button', {name: 'Assign group'}).last());

		// then
		await expect.element(screen.getByText('Group eng assigned')).toBeVisible();
	});

	it('should assign a group by its ID when Camunda groups are disabled', async ({worker}) => {
		// given
		worker.use(
			mockQueryGroupsByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([]))}),
			mockAssignGroupToRoleEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);
		const {screen} = await renderPage({activeTab: 'groups', isCamundaGroupsEnabled: false});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign group'}));
		await userEvent.fill(screen.getByLabelText('Group ID'), 'external-group');
		await userEvent.click(screen.getByRole('button', {name: 'Assign group'}).last());

		// then
		await expect.element(screen.getByText('Group external-group assigned')).toBeVisible();
	});

	it('should assign a mapping rule chosen from the list', async ({worker}) => {
		// given
		worker.use(
			mockQueryMappingRulesByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([]))}),
			mockQueryMappingRulesEndpoint({
				successResponse: HttpResponse.json(
					createMembersPage([{mappingRuleId: 'rule-1', name: 'Rule one', claimName: 'groups', claimValue: 'ops'}]),
				),
			}),
			mockAssignMappingToRoleEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);
		const {screen} = await renderPage({isOidc: true, activeTab: 'mappingRules'});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign mapping rule'}));
		await userEvent.click(screen.getByRole('combobox', {name: 'Mapping rule ID'}));
		await userEvent.click(screen.getByRole('option', {name: 'rule-1'}));
		await userEvent.click(screen.getByRole('button', {name: 'Assign mapping rule'}).last());

		// then
		await expect.element(screen.getByText('Mapping rule rule-1 assigned')).toBeVisible();
	});

	it('should keep the assign button disabled until a member is chosen', async ({worker}) => {
		worker.use(
			mockQueryUsersByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([]))}),
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createMembersPage([]))}),
		);
		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));

		await expect.element(screen.getByRole('button', {name: 'Assign user'}).last()).toBeDisabled();
	});

	it('should assign a client by its ID', async ({worker}) => {
		// given
		worker.use(
			mockQueryClientsByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([]))}),
			mockAssignClientToRoleEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);
		const {screen} = await renderPage({isOidc: true, activeTab: 'clients'});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign client'}));
		await userEvent.fill(screen.getByLabelText('Client ID'), 'my-client');
		await userEvent.click(screen.getByRole('button', {name: 'Assign client'}).last());

		// then
		await expect.element(screen.getByText('Client my-client assigned')).toBeVisible();
	});

	it('should assign an external user by username when the login is delegated', async ({worker}) => {
		// given
		worker.use(
			mockQueryUsersByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([]))}),
			mockAssignUserToRoleEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);
		const {screen} = await renderPage({isOidc: true});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.fill(screen.getByLabelText('Username'), 'external.user');
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}).last());

		// then
		await expect.element(screen.getByText('User external.user assigned')).toBeVisible();
	});

	it('should report a failed member lookup instead of an empty role', async ({worker}) => {
		// given
		worker.use(
			mockQueryUsersByRoleEndpoint({
				successResponse: HttpResponse.json(
					{type: 'about:blank', title: 'UNAVAILABLE', status: 503, detail: 'Unavailable', instance: '/v2/roles'},
					{status: 503},
				),
			}),
		);

		// when
		const {screen} = await renderPage();

		// then
		await expect.element(screen.getByRole('alert')).toHaveTextContent('Members could not be loaded');
		await expect.element(screen.getByText('No users assigned to this role.')).not.toBeInTheDocument();
	});

	it('should report a failed candidate lookup in the assign dialog', async ({worker}) => {
		// given
		worker.use(
			mockQueryUsersByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([]))}),
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

	it('should remove a user from the role', async ({worker}) => {
		worker.use(
			mockQueryUsersByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([{username: 'john.doe'}]))}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json(createMembersPage([createUser({username: 'john.doe'})])),
			}),
			mockUnassignUserFromRoleEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);
		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Remove user john.doe', exact: true}));
		await userEvent.click(screen.getByRole('button', {name: 'Remove user', exact: true}));

		await expect.element(screen.getByText('User john.doe removed')).toBeVisible();
	});

	it('should remove a group from the role', async ({worker}) => {
		// given
		worker.use(
			mockQueryGroupsByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([{groupId: 'eng'}]))}),
			mockQueryGroupsEndpoint({
				successResponse: HttpResponse.json(createMembersPage([{groupId: 'eng', name: 'Engineering'}])),
			}),
			mockUnassignGroupFromRoleEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);
		const {screen} = await renderPage({activeTab: 'groups'});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Remove group eng', exact: true}));
		await userEvent.click(screen.getByRole('button', {name: 'Remove group', exact: true}));

		// then
		await expect.element(screen.getByText('Group eng removed')).toBeVisible();
	});

	it('should warn when a removed user is still listed on the server', async ({worker}) => {
		// given
		worker.use(
			mockQueryUsersByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([{username: 'john.doe'}]))}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json(createMembersPage([createUser({username: 'john.doe'})])),
			}),
			mockUnassignUserFromRoleEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
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
			mockQueryUsersByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([{username: 'john.doe'}]))}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json(createMembersPage([createUser({username: 'john.doe'})])),
			}),
			mockUnassignUserFromRoleEndpoint({
				successResponse: HttpResponse.json(
					{type: 'about:blank', title: 'UNAVAILABLE', status: 503, detail: 'Not allowed', instance: '/v2/roles'},
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

	it('should delete the role and notify the caller', async ({worker}) => {
		worker.use(
			mockQueryUsersByRoleEndpoint({successResponse: HttpResponse.json(createMembersPage([]))}),
			mockDeleteRoleEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetRoleEndpoint({successResponse: HttpResponse.json({}, {status: 404})}),
		);
		const {screen, onDeleted} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Delete role'}));
		await userEvent.click(screen.getByRole('button', {name: 'Delete role'}).last());

		await vi.waitFor(() => expect(onDeleted).toHaveBeenCalled());
	});
});
