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
import {useCallback, useState} from 'react';
import {afterEach, describe, expect, vi} from 'vitest';
import {z} from 'zod';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockAssignUserToGroupEndpoint,
	mockDeleteGroupEndpoint,
	mockQueryGroupsEndpoint,
	mockQueryClientsByGroupEndpoint,
	mockQueryMappingRulesByGroupEndpoint,
	mockQueryMappingRulesEndpoint,
	mockQueryRolesByGroupEndpoint,
	mockQueryRolesEndpoint,
	mockQueryUsersByGroupEndpoint,
	mockQueryUsersEndpoint,
	mockUnassignUserFromGroupEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	queryMappingRulesRequestBodySchema,
	queryRolesRequestBodySchema,
	queryUsersByGroupRequestBodySchema,
	queryUsersRequestBodySchema,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {createGroup, createPage} from '#/shared-test-modules/api-mocks/groups';
import {createMappingRule} from '#/shared-test-modules/api-mocks/mapping-rules';
import {createUser} from '#/shared-test-modules/api-mocks/users';
import type {GroupDetailSearch} from '#/admin/modules/groups/detailSearchSchema';
import {AdminGroupDetailPage, type AdminGroupDetailPageProps} from './AdminGroupDetailPage';

// Hovering or focusing a tab prefetches its first page, and the pointer position survives between tests.
const mockEmptyRoles = () => mockQueryRolesByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))});
const mockEmptyUsers = () => mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))});

async function renderPage({search: initialSearch = {}, ...overrides}: Partial<AdminGroupDetailPageProps> = {}) {
	const onSearchChange = vi.fn();
	const onDeleted = vi.fn();
	const Page = () => {
		const [search, setSearch] = useState<GroupDetailSearch>(initialSearch);
		const handleSearchChange = useCallback((next: Partial<GroupDetailSearch>) => {
			onSearchChange(next);
			setSearch((previous) => ({...previous, ...next}));
		}, []);

		return (
			<>
				<AdminGroupDetailPage
					group={createGroup({groupId: 'ops', name: 'Operations', description: 'The ops team'})}
					isOidc={false}
					canManageMappingRules={false}
					onDeleted={onDeleted}
					{...overrides}
					search={search}
					onSearchChange={handleSearchChange}
				/>
				<Toaster />
			</>
		);
	};
	const screen = await renderWithRouter(() => <Page />, {path: '/admin/groups'});

	return {screen, onSearchChange, onDeleted};
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

		const {screen} = await renderPage({isOidc: true, canManageMappingRules: true});

		await expect.element(screen.getByRole('tab', {name: 'Mapping rules'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Clients'})).toBeVisible();
	});

	it('should offer clients but not mapping rules when mapping rules cannot be managed', async ({worker}) => {
		worker.use(mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}));

		const {screen} = await renderPage({isOidc: true, canManageMappingRules: false, search: {tab: 'mappingRules'}});

		await expect.element(screen.getByRole('tab', {name: 'Clients'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Mapping rules'})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('tab', {name: 'Users', selected: true})).toBeVisible();
	});

	it('should fall back to the users tab when the active tab is not available', async ({worker}) => {
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([{username: 'john.doe'}]))}),
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createPage([createUser({username: 'john.doe'})]))}),
		);

		const {screen} = await renderPage({isOidc: false, search: {tab: 'clients'}});

		await expect.element(screen.getByRole('cell', {name: 'john.doe'})).toBeVisible();
	});

	it('should sort the mapping rules of the group by claim name when the reader sorts that column', async ({worker}) => {
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}),
			mockQueryMappingRulesByGroupEndpoint({
				schema: z.object({sort: z.tuple([z.object({field: z.literal('claimName'), order: z.literal('ASC')})])}),
				failureResponse: HttpResponse.json(
					createPage([{mappingRuleId: 'unsorted', name: 'Unsorted', claimName: 'groups', claimValue: 'engineering'}]),
				),
				successResponse: HttpResponse.json(
					createPage([{mappingRuleId: 'sorted', name: 'Sorted', claimName: 'groups', claimValue: 'engineering'}]),
				),
			}),
		);
		const {screen, onSearchChange} = await renderPage({
			isOidc: true,
			canManageMappingRules: true,
			search: {tab: 'mappingRules'},
		});

		await userEvent.click(screen.getByRole('button', {name: 'Claim name'}));

		expect(onSearchChange).toHaveBeenCalledWith({sortField: 'claimName', sortOrder: 'ASC', page: undefined});
		await expect.element(screen.getByRole('cell', {name: 'Sorted'})).toBeVisible();
	});

	it('should sort the users of the group by username when the reader sorts the column', async ({worker}) => {
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([{username: 'john.doe'}]))}),
		);
		const {screen, onSearchChange} = await renderPage({isOidc: true, canManageMappingRules: true});

		await userEvent.click(screen.getByRole('button', {name: 'Username'}));

		expect(onSearchChange).toHaveBeenCalledWith({sortField: 'username', sortOrder: 'ASC', page: undefined});
	});

	it('should not offer sorting on the name and email filled in for each user', async ({worker}) => {
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([{username: 'john.doe'}]))}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json(createPage([createUser({username: 'john.doe', name: 'John Doe'})])),
			}),
		);

		const {screen} = await renderPage({isOidc: false});

		await expect.element(screen.getByRole('button', {name: 'Username'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Email'})).not.toBeInTheDocument();
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

		const {screen} = await renderPage({isOidc: true, canManageMappingRules: true});

		await expect.element(screen.getByRole('cell', {name: 'john.doe'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Email', exact: true})).not.toBeInTheDocument();
	});

	it('should report the selected tab', async ({worker}) => {
		worker.use(mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}), mockEmptyRoles());
		const {screen, onSearchChange} = await renderPage();

		await userEvent.click(screen.getByRole('tab', {name: 'Roles'}));

		expect(onSearchChange).toHaveBeenCalledWith({tab: 'roles', page: undefined, pageSize: undefined});
	});

	it('should list the roles of the group', async ({worker}) => {
		worker.use(
			mockQueryRolesByGroupEndpoint({
				successResponse: HttpResponse.json(createPage([{roleId: 'admin-role', name: 'Admin role'}])),
			}),
		);

		const {screen} = await renderPage({search: {tab: 'roles'}});

		await expect.element(screen.getByRole('cell', {name: 'admin-role'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Admin role'})).toBeVisible();
	});

	it('should list the clients of the group', async ({worker}) => {
		worker.use(
			mockQueryClientsByGroupEndpoint({successResponse: HttpResponse.json(createPage([{clientId: 'my-client'}]))}),
			mockEmptyRoles(),
		);

		const {screen} = await renderPage({isOidc: true, canManageMappingRules: true, search: {tab: 'clients'}});

		await expect.element(screen.getByRole('cell', {name: 'my-client'})).toBeVisible();
	});

	it('should show an empty state when the group has no members', async ({worker}) => {
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}),
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createPage([]))}),
			mockEmptyRoles(),
		);

		const {screen} = await renderPage();

		await expect.element(screen.getByText('No users assigned to this group yet')).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'Learn more about users'})).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));

		await expect.element(screen.getByRole('dialog')).toBeVisible();
	});

	it('should assign a user to the group', async ({worker}) => {
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}),
			mockAssignUserToGroupEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createPage([createUser({username: 'jane.doe'})]))}),
			mockEmptyRoles(),
		);
		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.click(screen.getByRole('combobox', {name: 'Username'}));
		await userEvent.click(screen.getByRole('option', {name: 'jane.doe'}));
		await userEvent.keyboard('{Escape}');
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([{username: 'jane.doe'}]))}),
		);
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
		const {screen} = await renderPage({isOidc: true, canManageMappingRules: true, search: {tab: 'clients'}});

		await userEvent.click(screen.getByRole('button', {name: 'Assign client'}));
		await userEvent.fill(screen.getByLabelText('Client ID'), 'my-client');

		await expect.element(screen.getByRole('button', {name: 'Assign client'}).last()).toBeEnabled();
	});

	it('should ask for a username, not a client ID, when assigning an external user', async ({worker}) => {
		worker.use(mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}));
		const {screen} = await renderPage({isOidc: true, canManageMappingRules: true});

		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));

		await expect.element(screen.getByPlaceholder('Enter username')).toBeVisible();
	});

	it('should hint that an externally entered username is case-sensitive', async ({worker}) => {
		// given
		worker.use(mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}));
		const {screen} = await renderPage({isOidc: true, canManageMappingRules: true});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));

		// then
		await expect.element(screen.getByText(/Usernames are case-sensitive/)).toBeVisible();
	});

	it('should not show the case-sensitivity hint when users are picked from a list', async ({worker}) => {
		// given
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}),
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createPage([]))}),
		);
		const {screen} = await renderPage();

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));

		// then
		await expect.element(screen.getByText(/Usernames are case-sensitive/)).not.toBeInTheDocument();
	});

	it('should assign an external user by username when the login is delegated', async ({worker}) => {
		// given
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}),
			mockAssignUserToGroupEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);
		const {screen} = await renderPage({isOidc: true, canManageMappingRules: true});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.fill(screen.getByLabelText('Username'), 'external.user');
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([{username: 'external.user'}]))}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}).last());

		// then
		await expect.element(screen.getByText('User external.user assigned')).toBeVisible();
	});

	it('should list an assigned user in username order before the server shows it', async ({worker}) => {
		// given
		worker.use(
			mockQueryUsersByGroupEndpoint({
				successResponse: HttpResponse.json(createPage([{username: 'alice'}, {username: 'carol'}])),
			}),
			mockAssignUserToGroupEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createPage([]))}),
		);
		const {screen} = await renderPage({isOidc: true, canManageMappingRules: true});
		await expect.element(screen.getByRole('cell', {name: 'alice', exact: true})).toBeVisible();

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.fill(screen.getByLabelText('Username'), 'bob');
		worker.use(
			mockQueryUsersByGroupEndpoint({
				successResponse: HttpResponse.json(createPage([{username: 'alice'}, {username: 'carol'}])),
				delay: 'infinite',
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}).last());

		// then
		await expect.element(screen.getByRole('cell', {name: 'bob', exact: true})).toBeVisible();
		const memberCells = screen.getByRole('cell', {name: /^(alice|bob|carol)$/});
		await expect.element(memberCells.nth(0)).toHaveTextContent('alice');
		await expect.element(memberCells.nth(1)).toHaveTextContent('bob');
		await expect.element(memberCells.nth(2)).toHaveTextContent('carol');
		await expect.element(memberCells.nth(3)).not.toBeInTheDocument();
	});

	it('should list an assigned user in descending username order before the server shows it', async ({worker}) => {
		worker.use(
			mockQueryUsersByGroupEndpoint({
				successResponse: HttpResponse.json(createPage([{username: 'carol'}, {username: 'bob'}])),
			}),
			mockAssignUserToGroupEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createPage([]))}),
		);
		const {screen} = await renderPage({
			isOidc: true,
			canManageMappingRules: true,
			search: {sortField: 'username', sortOrder: 'DESC'},
		});
		await expect.element(screen.getByRole('cell', {name: 'carol', exact: true})).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.fill(screen.getByLabelText('Username'), 'dave');
		worker.use(
			mockQueryUsersByGroupEndpoint({
				successResponse: HttpResponse.json(createPage([{username: 'carol'}, {username: 'bob'}])),
				delay: 'infinite',
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}).last());

		const memberCells = screen.getByRole('cell', {name: /^(bob|carol|dave)$/});
		await expect.element(memberCells.nth(0)).toHaveTextContent('dave');
		await expect.element(memberCells.nth(1)).toHaveTextContent('carol');
		await expect.element(memberCells.nth(2)).toHaveTextContent('bob');
	});

	it('should show the members the server confirms after an assignment', async ({worker}) => {
		worker.use(
			mockQueryUsersByGroupEndpoint({
				successResponse: HttpResponse.json(createPage([{username: 'alice'}, {username: 'carol'}])),
			}),
			mockAssignUserToGroupEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createPage([]))}),
		);
		const {screen} = await renderPage({isOidc: true, canManageMappingRules: true});
		await expect.element(screen.getByRole('cell', {name: 'alice', exact: true})).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.fill(screen.getByLabelText('Username'), 'bob');
		worker.use(
			mockQueryUsersByGroupEndpoint({
				successResponse: HttpResponse.json(
					createPage([{username: 'alice'}, {username: 'bob'}, {username: 'carol'}, {username: 'dave'}]),
				),
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}).last());

		await expect.element(screen.getByRole('cell', {name: 'dave', exact: true})).toBeVisible();
	});

	it('should not insert an assigned user into a full page', async ({worker}) => {
		// given
		const existing = Array.from({length: 20}, (_, index) => ({username: `user-${String(index).padStart(2, '0')}`}));
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage(existing))}),
			mockAssignUserToGroupEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createPage([]))}),
		);
		const {screen} = await renderPage({isOidc: true, canManageMappingRules: true});
		await expect.element(screen.getByRole('cell', {name: 'user-19', exact: true})).toBeVisible();

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.fill(screen.getByLabelText('Username'), 'user-00a');
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage(existing)), delay: 'infinite'}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}).last());

		// then
		await expect.element(screen.getByText('User user-00a assigned')).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'user-00a', exact: true})).not.toBeInTheDocument();
	});

	it('should show an assigned user on a full page once the server confirms it', async ({worker}) => {
		const existing = Array.from({length: 20}, (_, index) => ({username: `user-${String(index).padStart(2, '0')}`}));
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage(existing))}),
			mockAssignUserToGroupEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createPage([]))}),
		);
		const {screen} = await renderPage({isOidc: true, canManageMappingRules: true});
		await expect.element(screen.getByRole('cell', {name: 'user-19', exact: true})).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.fill(screen.getByLabelText('Username'), 'user-00a');
		worker.use(
			mockQueryUsersByGroupEndpoint({
				successResponse: HttpResponse.json(
					createPage([existing[0], {username: 'user-00a'}, ...existing.slice(1, 19)], {totalItems: 21}),
				),
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}).last());

		await expect.element(screen.getByRole('cell', {name: 'user-00a', exact: true})).toBeVisible();
	});

	it('should not offer users that are already in the group', async ({worker}) => {
		// given
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([{username: 'jane.doe'}]))}),
			mockQueryUsersEndpoint({
				schema: queryUsersRequestBodySchema.and(
					z.object({filter: z.object({username: z.object({$notIn: z.tuple([z.literal('jane.doe')])})})}),
				),
				successResponse: HttpResponse.json(createPage([createUser({username: 'john.roe'})])),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);
		const {screen} = await renderPage();

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.click(screen.getByRole('combobox', {name: 'Username'}));

		// then
		await expect.element(screen.getByRole('option', {name: 'john.roe'})).toBeVisible();
		await expect.element(screen.getByRole('option', {name: 'jane.doe'})).not.toBeInTheDocument();
	});

	it('should still offer users created after the first page of candidates is fully assigned', async ({worker}) => {
		// given
		const everyone = Array.from({length: 22}, (_, index) => `user-${String(index).padStart(2, '0')}`);
		worker.use(
			mockQueryUsersByGroupEndpoint({
				successResponse: HttpResponse.json(createPage(everyone.slice(0, 20).map((username) => ({username})))),
			}),
			mockQueryUsersEndpoint({
				schema: queryUsersRequestBodySchema.and(
					z.object({
						filter: z.object({
							username: z.object({
								$notIn: z.tuple([
									z.literal('user-00'),
									z.literal('user-01'),
									z.literal('user-02'),
									z.literal('user-03'),
									z.literal('user-04'),
									z.literal('user-05'),
									z.literal('user-06'),
									z.literal('user-07'),
									z.literal('user-08'),
									z.literal('user-09'),
									z.literal('user-10'),
									z.literal('user-11'),
									z.literal('user-12'),
									z.literal('user-13'),
									z.literal('user-14'),
									z.literal('user-15'),
									z.literal('user-16'),
									z.literal('user-17'),
									z.literal('user-18'),
									z.literal('user-19'),
								]),
							}),
						}),
					}),
				),
				successResponse: HttpResponse.json(createPage(everyone.slice(20).map((username) => createUser({username})))),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);
		const {screen} = await renderPage();

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.click(screen.getByRole('combobox', {name: 'Username'}));

		// then
		await expect.element(screen.getByRole('option', {name: 'user-20'})).toBeVisible();
		await expect.element(screen.getByRole('option', {name: 'user-21'})).toBeVisible();
		await expect.element(screen.getByRole('option', {name: 'user-00'})).not.toBeInTheDocument();
	});

	it('should assign several users at once', async ({worker}) => {
		// given
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}),
			mockAssignUserToGroupEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json(
					createPage([createUser({username: 'jane.doe'}), createUser({username: 'john.roe'})]),
				),
			}),
		);
		const {screen} = await renderPage();

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.click(screen.getByRole('combobox', {name: 'Username'}));
		await userEvent.click(screen.getByRole('option', {name: 'jane.doe'}));
		await userEvent.click(screen.getByRole('option', {name: 'john.roe'}));
		await userEvent.keyboard('{Escape}');
		worker.use(
			mockQueryUsersByGroupEndpoint({
				successResponse: HttpResponse.json(createPage([{username: 'jane.doe'}, {username: 'john.roe'}])),
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}).last());

		// then
		await expect.element(screen.getByText('2 members assigned')).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'jane.doe', exact: true})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'john.roe', exact: true})).toBeVisible();
	});

	it('should replace the success toast with a warning when the assignment cannot be confirmed', async ({worker}) => {
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}),
			mockAssignUserToGroupEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json(
					createPage([createUser({username: 'jane.doe'}), createUser({username: 'john.roe'})]),
				),
			}),
		);
		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.click(screen.getByRole('combobox', {name: 'Username'}));
		await userEvent.click(screen.getByRole('option', {name: 'jane.doe'}));
		await userEvent.click(screen.getByRole('option', {name: 'john.roe'}));
		await userEvent.keyboard('{Escape}');
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}).last());
		await userEvent.hover(screen.getByText('2 members assigned'));

		await expect.element(screen.getByText("Couldn't confirm the change took effect")).toBeVisible();
		await expect.element(screen.getByText('2 members assigned')).not.toBeInTheDocument();
		await userEvent.unhover(screen.getByText("Couldn't confirm the change took effect"));
	});

	it('should keep the dialog open with the failed users still selected', async ({worker}) => {
		// given
		worker.use(
			mockAssignUserToGroupEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json(
					createPage([createUser({username: 'jane.doe'}), createUser({username: 'john.roe'})]),
				),
			}),
		);
		const {screen} = await renderPage();

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.click(screen.getByRole('combobox', {name: 'Username'}));
		await userEvent.click(screen.getByRole('option', {name: 'jane.doe'}));
		await userEvent.click(screen.getByRole('option', {name: 'john.roe'}));
		await userEvent.keyboard('{Escape}');
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([{username: 'jane.doe'}]))}),
			mockAssignUserToGroupEndpoint({
				successResponse: HttpResponse.json({title: 'Conflict', status: 409, detail: 'already a member'}, {status: 409}),
				once: true,
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}).last());

		// then
		await expect.element(screen.getByText('1 of 2 assigned')).toBeVisible();
		await expect.element(screen.getByRole('dialog')).toBeVisible();
		await expect.element(screen.getByRole('dialog').getByText(/^(jane\.doe|john\.roe)$/)).toHaveLength(1);
	});

	it('should not offer roles that are already in the group', async ({worker}) => {
		// given
		worker.use(
			mockEmptyUsers(),
			mockQueryRolesByGroupEndpoint({
				successResponse: HttpResponse.json(createPage([{roleId: 'admin-role', name: 'Admin role'}])),
			}),
			mockQueryRolesEndpoint({
				schema: queryRolesRequestBodySchema.and(
					z.object({filter: z.object({roleId: z.object({$notIn: z.tuple([z.literal('admin-role')])})})}),
				),
				successResponse: HttpResponse.json(createPage([{roleId: 'viewer-role', name: 'Viewer role'}])),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);
		const {screen} = await renderPage({search: {tab: 'roles'}});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign role'}));
		await userEvent.click(screen.getByRole('combobox'));

		// then
		await expect.element(screen.getByRole('option', {name: 'viewer-role'})).toBeVisible();
		await expect.element(screen.getByRole('option', {name: 'admin-role'})).not.toBeInTheDocument();
	});

	it('should not offer mapping rules that are already in the group', async ({worker}) => {
		// given
		worker.use(
			mockEmptyUsers(),
			mockQueryMappingRulesByGroupEndpoint({
				successResponse: HttpResponse.json(createPage([createMappingRule({mappingRuleId: 'assigned-rule'})])),
			}),
			mockQueryMappingRulesEndpoint({
				schema: queryMappingRulesRequestBodySchema.and(
					z.object({
						filter: z.object({mappingRuleId: z.object({$notIn: z.tuple([z.literal('assigned-rule')])})}),
					}),
				),
				successResponse: HttpResponse.json(createPage([createMappingRule({mappingRuleId: 'free-rule'})])),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);
		const {screen} = await renderPage({isOidc: true, canManageMappingRules: true, search: {tab: 'mappingRules'}});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign mapping rule'}));
		await userEvent.click(screen.getByRole('combobox'));

		// then
		await expect.element(screen.getByRole('option', {name: 'free-rule'})).toBeVisible();
		await expect.element(screen.getByRole('option', {name: 'assigned-rule'})).not.toBeInTheDocument();
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
			mockUnassignUserFromGroupEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createPage([createUser({username: 'john.doe'})]))}),
		);
		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Remove user john.doe', exact: true}));
		worker.use(mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}));
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
		await expect.element(screen.getByText("Couldn't confirm the change took effect")).toBeVisible();
	});

	it('should step back a page when removing the last member of the last page', async ({worker}) => {
		// given
		const users = Array.from({length: 21}, (_, index) => ({username: `user-${index}`}));
		worker.use(
			mockQueryUsersByGroupEndpoint({
				successResponse: HttpResponse.json(createPage(users.slice(0, 20), {totalItems: 21})),
			}),
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createPage([]))}),
			mockUnassignUserFromGroupEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);
		const {screen} = await renderPage();
		await expect.element(screen.getByRole('cell', {name: 'user-19'})).toBeVisible();
		worker.use(
			mockQueryUsersByGroupEndpoint({
				successResponse: HttpResponse.json(createPage(users.slice(20), {totalItems: 21})),
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Next', exact: true}));
		await expect.element(screen.getByRole('cell', {name: 'user-20'})).toBeVisible();

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Remove user user-20', exact: true}));
		worker.use(
			mockQueryUsersByGroupEndpoint({
				schema: queryUsersByGroupRequestBodySchema.extend({page: z.object({from: z.literal(0)})}),
				successResponse: HttpResponse.json(createPage(users.slice(0, 20), {totalItems: 20})),
				failureResponse: HttpResponse.json(createPage([], {totalItems: 20})),
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Remove user', exact: true}));

		// then
		await expect.element(screen.getByRole('cell', {name: 'user-0', exact: true})).toBeVisible();
	});

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
			mockQueryGroupsEndpoint({successResponse: HttpResponse.json({items: [], page: {totalItems: 0}})}),
		);
		const {screen, onDeleted} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Delete group'}));
		await userEvent.click(screen.getByRole('button', {name: 'Delete group'}).last());

		await expect.poll(() => onDeleted).toHaveBeenCalled();
	});
});
