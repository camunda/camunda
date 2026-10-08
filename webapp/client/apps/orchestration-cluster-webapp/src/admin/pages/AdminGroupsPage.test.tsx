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
	mockCreateGroupEndpoint,
	mockDeleteGroupEndpoint,
	mockGetGroupEndpoint,
	mockUpdateGroupEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createGroup} from '#/shared-test-modules/api-mocks/groups';
import {AdminGroupsPage, type AdminGroupsPageProps} from './AdminGroupsPage';

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

async function renderPage(overrides: Partial<AdminGroupsPageProps> = {}) {
	const onSearchChange = vi.fn();
	const onOpenGroup = vi.fn();
	const screen = await render(
		<AdminGroupsPage
			groups={[createGroup()]}
			totalItems={1}
			search={{}}
			onSearchChange={onSearchChange}
			onOpenGroup={onOpenGroup}
			{...overrides}
		/>,
		{wrapper: getWrapper()},
	);

	return {screen, onSearchChange, onOpenGroup};
}

describe('<AdminGroupsPage />', () => {
	afterEach(async () => {
		await cleanup();
		toast.dismiss();
	});

	it('should list groups', async () => {
		const {screen} = await renderPage({groups: [createGroup({groupId: 'ops', name: 'Operations'})]});

		await expect.element(screen.getByRole('cell', {name: 'ops'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Operations'})).toBeVisible();
	});

	it('should show an empty state when there are no groups', async () => {
		const {screen} = await renderPage({groups: [], totalItems: 0});

		await expect.element(screen.getByText('No groups found.')).toBeVisible();
	});

	it('should open a group when a row is clicked', async () => {
		const group = createGroup({groupId: 'ops'});
		const {screen, onOpenGroup} = await renderPage({groups: [group]});

		await userEvent.click(screen.getByRole('cell', {name: 'ops'}));

		expect(onOpenGroup).toHaveBeenCalledWith(group);
	});

	it('should search by group ID once the reader stops typing', async () => {
		const {screen, onSearchChange} = await renderPage();

		await userEvent.fill(screen.getByRole('searchbox'), 'ops');

		await vi.waitFor(() => expect(onSearchChange).toHaveBeenCalledWith({search: 'ops', page: undefined}), DEBOUNCED);
	});

	it('should reverse the group ID order when the reader sorts the column', async () => {
		const {screen, onSearchChange} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Group ID'}));

		expect(onSearchChange).toHaveBeenCalledWith({sortField: 'groupId', sortOrder: 'desc', page: undefined});
	});

	it('should sort by name when the reader sorts that column', async () => {
		const {screen, onSearchChange} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Group name'}));

		expect(onSearchChange).toHaveBeenCalledWith({sortField: 'name', sortOrder: 'asc', page: undefined});
	});

	it('should return to the first page when the page size changes', async () => {
		const {screen, onSearchChange} = await renderPage({search: {page: 3}, totalItems: 200});

		await userEvent.click(screen.getByRole('combobox'));
		await userEvent.click(screen.getByRole('option', {name: '50'}));

		expect(onSearchChange).toHaveBeenCalledWith({pageSize: 50, page: undefined});
	});

	it('should create a group', async ({worker}) => {
		const group = createGroup({groupId: 'ops', name: 'Operations'});
		worker.use(
			mockCreateGroupEndpoint({successResponse: HttpResponse.json(group, {status: 201})}),
			mockGetGroupEndpoint({successResponse: HttpResponse.json(group)}),
		);
		const {screen} = await renderPage({groups: []});

		await userEvent.click(screen.getByRole('button', {name: 'Create group'}));
		await userEvent.fill(screen.getByLabelText('Group ID'), 'ops');
		await userEvent.fill(screen.getByLabelText('Group name'), 'Operations');
		await userEvent.click(screen.getByRole('button', {name: 'Create group'}).last());

		await expect.element(screen.getByText('Group Operations created')).toBeVisible();
	});

	it('should show an error on the group ID when it already exists', async ({worker}) => {
		worker.use(
			mockCreateGroupEndpoint({
				successResponse: HttpResponse.json(
					{type: 'about:blank', title: 'ALREADY_EXISTS', status: 409, detail: 'Group already exists'},
					{status: 409},
				),
			}),
		);
		const {screen} = await renderPage({groups: []});

		await userEvent.click(screen.getByRole('button', {name: 'Create group'}));
		await userEvent.fill(screen.getByLabelText('Group ID'), 'ops');
		await userEvent.fill(screen.getByLabelText('Group name'), 'Operations');
		await userEvent.click(screen.getByRole('button', {name: 'Create group'}).last());

		await expect.element(screen.getByText('A group with this ID already exists')).toBeVisible();
	});

	it("should show the backend's error detail when creating a group fails", async ({worker}) => {
		worker.use(
			mockCreateGroupEndpoint({
				successResponse: HttpResponse.json(
					{
						type: 'about:blank',
						title: 'UNAVAILABLE',
						status: 503,
						detail: 'Broker connection error',
						instance: '/v2/groups',
					},
					{status: 503},
				),
			}),
		);
		const {screen} = await renderPage({groups: []});

		await userEvent.click(screen.getByRole('button', {name: 'Create group'}));
		await userEvent.fill(screen.getByLabelText('Group ID'), 'ops');
		await userEvent.fill(screen.getByLabelText('Group name'), 'Operations');
		await userEvent.click(screen.getByRole('button', {name: 'Create group'}).last());

		await expect.element(screen.getByText('Failed to create group')).toBeVisible();
		await expect.element(screen.getByText('Broker connection error')).toBeVisible();
	});

	it('should require a group ID and name', async () => {
		const {screen} = await renderPage({groups: []});

		await userEvent.click(screen.getByRole('button', {name: 'Create group'}));
		await userEvent.click(screen.getByRole('button', {name: 'Create group'}).last());

		await expect.element(screen.getByText('Group ID is required')).toBeVisible();
		await expect.element(screen.getByText('Group name is required')).toBeVisible();
	});

	it('should reject an invalid group ID', async () => {
		const {screen} = await renderPage({groups: []});

		await userEvent.click(screen.getByRole('button', {name: 'Create group'}));
		await userEvent.fill(screen.getByLabelText('Group ID'), 'not valid!');
		await userEvent.click(screen.getByRole('button', {name: 'Create group'}).last());

		await expect.element(screen.getByText('Please enter a valid group ID')).toBeVisible();
	});

	it('should edit a group', async ({worker}) => {
		const group = createGroup({groupId: 'ops', name: 'Operations'});
		const updated = {...group, name: 'Ops team'};
		worker.use(
			mockUpdateGroupEndpoint({successResponse: HttpResponse.json(updated)}),
			mockGetGroupEndpoint({successResponse: HttpResponse.json(updated)}),
		);
		const {screen} = await renderPage({groups: [group]});

		await userEvent.click(screen.getByRole('button', {name: 'Row actions'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Edit group'}));
		await userEvent.fill(screen.getByLabelText('Group name'), 'Ops team');
		await userEvent.click(screen.getByRole('button', {name: 'Update group'}));

		await expect.element(screen.getByText('Group Ops team updated')).toBeVisible();
	});

	it('should delete a group', async ({worker}) => {
		const group = createGroup({groupId: 'ops'});
		worker.use(
			mockDeleteGroupEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetGroupEndpoint({successResponse: HttpResponse.json({}, {status: 404})}),
		);
		const {screen} = await renderPage({groups: [group]});

		await userEvent.click(screen.getByRole('button', {name: 'Row actions'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Delete group'}));
		await userEvent.click(screen.getByRole('button', {name: 'Delete group'}).last());

		await expect.element(screen.getByText('Group ops deleted')).toBeVisible();
	});

	it('should warn when a created group cannot be confirmed on the server', async ({worker}) => {
		// given
		worker.use(
			mockCreateGroupEndpoint({successResponse: HttpResponse.json(createGroup({groupId: 'ops'}), {status: 201})}),
			mockGetGroupEndpoint({successResponse: HttpResponse.json({}, {status: 503})}),
		);
		const {screen} = await renderPage({groups: []});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Create group'}));
		await userEvent.fill(screen.getByLabelText('Group ID'), 'ops');
		await userEvent.fill(screen.getByLabelText('Group name'), 'Operations');
		await userEvent.click(screen.getByRole('button', {name: 'Create group'}).last());

		// then
		await expect.element(screen.getByText("Couldn't confirm the change took effect"), RETRY_BUDGET).toBeVisible();
	}, 15_000);

	it('should disable the delete confirmation while the deletion is pending', async ({worker}) => {
		worker.use(mockDeleteGroupEndpoint({successResponse: new HttpResponse(null, {status: 204}), delay: 'infinite'}));
		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Row actions'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Delete group'}));
		const confirmButton = screen.getByRole('button', {name: 'Delete group'}).last();
		await userEvent.click(confirmButton);

		await expect.element(confirmButton).toBeDisabled();
		await expect.element(screen.getByRole('button', {name: 'Cancel'})).toBeDisabled();
	});
});
