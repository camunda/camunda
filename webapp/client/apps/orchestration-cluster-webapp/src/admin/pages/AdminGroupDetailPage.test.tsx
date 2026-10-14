/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse, http} from 'msw';
import {cleanup} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {Toaster, toast} from '@camunda/design-system';
import {afterEach, describe, expect, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockDeleteGroupEndpoint,
	mockGetGroupEndpoint,
	mockQueryClientsByGroupEndpoint,
	mockQueryMappingRulesByGroupEndpoint,
	mockQueryRolesByGroupEndpoint,
	mockQueryUsersByGroupEndpoint,
	mockQueryUsersEndpoint,
	mockUnassignUserFromGroupEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {endpoints} from '@camunda/camunda-api-zod-schemas/8.11';
import {createGroup, createPage} from '#/shared-test-modules/api-mocks/groups';
import {createMappingRule} from '#/shared-test-modules/api-mocks/mapping-rules';
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

function mockMembersChangingOn(
	change: {getUrl: (params: {groupId: string; username: string}) => string; method: 'PUT' | 'DELETE'},
	{before, after}: {before: {username: string}[]; after: {username: string}[]},
) {
	let changed = false;
	return [
		http[change.method === 'PUT' ? 'put' : 'delete'](
			decodeURIComponent(change.getUrl({groupId: ':groupId', username: ':username'})),
			() => {
				changed = true;
				return new HttpResponse(null, {status: 204});
			},
		),
		http.post(decodeURIComponent(endpoints.queryUsersByGroup.getUrl({groupId: ':groupId'})), () =>
			HttpResponse.json(createPage(changed ? after : before)),
		),
	];
}

function mockLaggingAssign(urls: {assign: string; list: string}, {before, after}: {before: object[]; after: object[]}) {
	let assigned = false;
	let lookupsAfterAssign = 0;
	const handlers = [
		http.put(decodeURIComponent(urls.assign), () => {
			assigned = true;
			return new HttpResponse(null, {status: 204});
		}),
		http.post(decodeURIComponent(urls.list), async ({request}) => {
			const {page} = (await request.json()) as {page: {from: number; limit: number}};
			if (assigned) {
				lookupsAfterAssign += 1;
			}
			const items = lookupsAfterAssign > 1 ? after : before;
			return HttpResponse.json(createPage(items.slice(page.from, page.from + page.limit), {totalItems: items.length}));
		}),
	];
	return {handlers, hasServedNewList: () => lookupsAfterAssign > 1};
}

// Searches like the real backend: applies the id filter's $notIn, then the page limit.
function mockCandidateSearch(url: string, idField: string, items: Record<string, unknown>[]) {
	return http.post(decodeURIComponent(url), async ({request}) => {
		const body = (await request.json()) as {
			filter?: Record<string, {$notIn?: string[]} | undefined>;
			page: {limit: number};
		};
		const excluded = body.filter?.[idField]?.$notIn ?? [];
		const matching = items.filter((item) => !excluded.includes(String(item[idField])));
		return HttpResponse.json(createPage(matching.slice(0, body.page.limit), {totalItems: matching.length}));
	});
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
			...mockMembersChangingOn(endpoints.assignUserToGroup, {before: [], after: [{username: 'jane.doe'}]}),
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createPage([createUser({username: 'jane.doe'})]))}),
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

	it('should ask for a username, not a client ID, when assigning an external user', async ({worker}) => {
		worker.use(mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([]))}));
		const {screen} = await renderPage({isOidc: true});

		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));

		await expect.element(screen.getByPlaceholder('Enter username')).toBeVisible();
	});

	it('should assign an external user by username when the login is delegated', async ({worker}) => {
		// given
		worker.use(
			...mockMembersChangingOn(endpoints.assignUserToGroup, {before: [], after: [{username: 'external.user'}]}),
		);
		const {screen} = await renderPage({isOidc: true});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.fill(screen.getByLabelText('Username'), 'external.user');
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}).last());

		// then
		await expect.element(screen.getByText('User external.user assigned')).toBeVisible();
	});

	it('should list an assigned user in username order before the server shows it', async ({worker}) => {
		// given
		const server = mockLaggingAssign(
			{
				assign: endpoints.assignUserToGroup.getUrl({groupId: ':groupId', username: ':username'}),
				list: endpoints.queryUsersByGroup.getUrl({groupId: ':groupId'}),
			},
			{
				before: [{username: 'alice'}, {username: 'carol'}],
				after: [{username: 'alice'}, {username: 'bob'}, {username: 'carol'}],
			},
		);
		worker.use(...server.handlers, mockQueryUsersEndpoint({successResponse: HttpResponse.json(createPage([]))}));
		const {screen} = await renderPage({isOidc: true});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.fill(screen.getByLabelText('Username'), 'bob');
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}).last());

		// then
		await expect.element(screen.getByRole('cell', {name: 'bob', exact: true})).toBeVisible();
		expect(server.hasServedNewList()).toBe(false);
		const usernames = screen
			.getByRole('cell')
			.elements()
			.map((cell) => cell.textContent);
		expect(usernames.filter((text) => ['alice', 'bob', 'carol'].includes(text ?? ''))).toEqual([
			'alice',
			'bob',
			'carol',
		]);
		await vi.waitFor(() => expect(server.hasServedNewList()).toBe(true), RETRY_BUDGET);
		await expect.element(screen.getByText("Couldn't confirm the change took effect")).not.toBeInTheDocument();
	}, 15_000);

	it('should list an assigned client in client ID order before the server shows it', async ({worker}) => {
		// given
		const server = mockLaggingAssign(
			{
				assign: endpoints.assignClientToGroup.getUrl({groupId: ':groupId', clientId: ':clientId'}),
				list: endpoints.queryClientsByGroup.getUrl({groupId: ':groupId'}),
			},
			{
				before: [{clientId: 'Client-B'}, {clientId: 'client-a'}],
				after: [{clientId: 'Client-B'}, {clientId: 'client-a'}, {clientId: 'client-c'}],
			},
		);
		worker.use(...server.handlers);
		const {screen} = await renderPage({isOidc: true, activeTab: 'clients'});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign client'}));
		await userEvent.fill(screen.getByLabelText('Client ID'), 'client-c');
		await userEvent.click(screen.getByRole('button', {name: 'Assign client'}).last());

		// then
		await expect.element(screen.getByRole('cell', {name: 'client-c', exact: true})).toBeVisible();
		expect(server.hasServedNewList()).toBe(false);
		await vi.waitFor(() => expect(server.hasServedNewList()).toBe(true), RETRY_BUDGET);
	}, 15_000);

	it('should not insert an assigned user into a full page', async ({worker}) => {
		// given
		const existing = Array.from({length: 20}, (_, index) => ({username: `user-${String(index).padStart(2, '0')}`}));
		const server = mockLaggingAssign(
			{
				assign: endpoints.assignUserToGroup.getUrl({groupId: ':groupId', username: ':username'}),
				list: endpoints.queryUsersByGroup.getUrl({groupId: ':groupId'}),
			},
			{before: existing, after: [...existing.slice(0, 1), {username: 'user-00a'}, ...existing.slice(1)]},
		);
		worker.use(...server.handlers, mockQueryUsersEndpoint({successResponse: HttpResponse.json(createPage([]))}));
		const {screen} = await renderPage({isOidc: true});
		await expect.element(screen.getByRole('cell', {name: 'user-19', exact: true})).toBeVisible();

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.fill(screen.getByLabelText('Username'), 'user-00a');
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}).last());

		// then
		await expect.element(screen.getByText('User user-00a assigned')).toBeVisible();
		expect(server.hasServedNewList()).toBe(false);
		await expect.element(screen.getByRole('cell', {name: 'user-00a', exact: true})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('cell', {name: 'user-00a', exact: true}), RETRY_BUDGET).toBeVisible();
	}, 15_000);

	it('should not offer users that are already in the group', async ({worker}) => {
		// given
		worker.use(
			mockQueryUsersByGroupEndpoint({successResponse: HttpResponse.json(createPage([{username: 'jane.doe'}]))}),
			mockCandidateSearch(endpoints.queryUsers.getUrl(), 'username', [
				createUser({username: 'jane.doe'}),
				createUser({username: 'john.roe'}),
			]),
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
			mockCandidateSearch(
				endpoints.queryUsers.getUrl(),
				'username',
				everyone.map((username) => createUser({username})),
			),
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
		const server = mockLaggingAssign(
			{
				assign: endpoints.assignUserToGroup.getUrl({groupId: ':groupId', username: ':username'}),
				list: endpoints.queryUsersByGroup.getUrl({groupId: ':groupId'}),
			},
			{before: [], after: [{username: 'jane.doe'}, {username: 'john.roe'}]},
		);
		worker.use(
			...server.handlers,
			mockCandidateSearch(endpoints.queryUsers.getUrl(), 'username', [
				createUser({username: 'jane.doe'}),
				createUser({username: 'john.roe'}),
			]),
		);
		const {screen} = await renderPage();

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.click(screen.getByRole('combobox', {name: 'Username'}));
		await userEvent.click(screen.getByRole('option', {name: 'jane.doe'}));
		await userEvent.click(screen.getByRole('option', {name: 'john.roe'}));
		await userEvent.keyboard('{Escape}');
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}).last());

		// then
		await expect.element(screen.getByText('User jane.doe assigned')).toBeVisible();
		await expect.element(screen.getByText('User john.roe assigned')).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'jane.doe', exact: true})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'john.roe', exact: true})).toBeVisible();
		await vi.waitFor(() => expect(server.hasServedNewList()).toBe(true), RETRY_BUDGET);
	}, 15_000);

	it('should keep the dialog open with the failed users still selected', async ({worker}) => {
		// given
		let assigned = false;
		worker.use(
			http.put(
				decodeURIComponent(endpoints.assignUserToGroup.getUrl({groupId: ':groupId', username: ':username'})),
				({params}) => {
					if (params.username === 'john.roe') {
						return HttpResponse.json({title: 'Conflict', status: 409, detail: 'already a member'}, {status: 409});
					}
					assigned = true;
					return new HttpResponse(null, {status: 204});
				},
			),
			http.post(decodeURIComponent(endpoints.queryUsersByGroup.getUrl({groupId: ':groupId'})), () =>
				HttpResponse.json(createPage(assigned ? [{username: 'jane.doe'}] : [])),
			),
			mockCandidateSearch(endpoints.queryUsers.getUrl(), 'username', [
				createUser({username: 'jane.doe'}),
				createUser({username: 'john.roe'}),
			]),
		);
		const {screen} = await renderPage();

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.click(screen.getByRole('combobox', {name: 'Username'}));
		await userEvent.click(screen.getByRole('option', {name: 'jane.doe'}));
		await userEvent.click(screen.getByRole('option', {name: 'john.roe'}));
		await userEvent.keyboard('{Escape}');
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}).last());

		// then
		await expect.element(screen.getByText('User jane.doe assigned')).toBeVisible();
		await expect.element(screen.getByText('Failed to assign')).toBeVisible();
		await expect.element(screen.getByRole('dialog').getByText('john.roe')).toBeVisible();
		await expect.element(screen.getByRole('dialog').getByText('jane.doe')).not.toBeInTheDocument();
	});

	it('should not offer roles that are already in the group', async ({worker}) => {
		// given
		worker.use(
			mockQueryRolesByGroupEndpoint({
				successResponse: HttpResponse.json(createPage([{roleId: 'admin-role', name: 'Admin role'}])),
			}),
			mockCandidateSearch(endpoints.queryRoles.getUrl(), 'roleId', [
				{roleId: 'admin-role', name: 'Admin role'},
				{roleId: 'viewer-role', name: 'Viewer role'},
			]),
		);
		const {screen} = await renderPage({activeTab: 'roles'});

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
			mockQueryMappingRulesByGroupEndpoint({
				successResponse: HttpResponse.json(createPage([createMappingRule({mappingRuleId: 'assigned-rule'})])),
			}),
			mockCandidateSearch(endpoints.queryMappingRules.getUrl(), 'mappingRuleId', [
				createMappingRule({mappingRuleId: 'assigned-rule'}),
				createMappingRule({mappingRuleId: 'free-rule'}),
			]),
		);
		const {screen} = await renderPage({isOidc: true, activeTab: 'mappingRules'});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign mapping rule'}));
		await userEvent.click(screen.getByRole('combobox'));

		// then
		await expect.element(screen.getByRole('option', {name: 'free-rule'})).toBeVisible();
		await expect.element(screen.getByRole('option', {name: 'assigned-rule'})).not.toBeInTheDocument();
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
			...mockMembersChangingOn(endpoints.unassignUserFromGroup, {before: [{username: 'john.doe'}], after: []}),
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createPage([createUser({username: 'john.doe'})]))}),
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

	it('should step back a page when removing the last member of the last page', async ({worker}) => {
		// given
		let removed = false;
		const users = Array.from({length: 21}, (_, index) => ({username: `user-${index}`}));
		worker.use(
			http.post(decodeURIComponent(endpoints.queryUsersByGroup.getUrl({groupId: ':groupId'})), async ({request}) => {
				const {page} = (await request.json()) as {page: {from: number; limit: number}};
				const total = removed ? 20 : 21;
				return HttpResponse.json(
					createPage(users.slice(page.from, Math.min(page.from + page.limit, total)), {totalItems: total}),
				);
			}),
			mockQueryUsersEndpoint({successResponse: HttpResponse.json(createPage([]))}),
			http.delete(
				decodeURIComponent(endpoints.unassignUserFromGroup.getUrl({groupId: ':groupId', username: ':username'})),
				() => {
					removed = true;
					return new HttpResponse(null, {status: 204});
				},
			),
		);
		const {screen} = await renderPage();
		await userEvent.click(screen.getByRole('button', {name: 'Next', exact: true}));
		await expect.element(screen.getByRole('cell', {name: 'user-20'})).toBeVisible();

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Remove user user-20', exact: true}));
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
			mockGetGroupEndpoint({successResponse: HttpResponse.json({}, {status: 404})}),
		);
		const {screen, onDeleted} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Delete group'}));
		await userEvent.click(screen.getByRole('button', {name: 'Delete group'}).last());

		await vi.waitFor(() => expect(onDeleted).toHaveBeenCalled());
	});
});
