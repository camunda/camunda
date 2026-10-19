/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {http, HttpResponse} from 'msw';
import {cleanup} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {Toaster, toast} from '@camunda/design-system';
import {afterEach, describe, expect, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockAssignUserToTenantEndpoint,
	mockDeleteTenantEndpoint,
	mockGetTenantEndpoint,
	mockQueryClientsByTenantEndpoint,
	mockQueryGroupsByTenantEndpoint,
	mockQueryRolesByTenantEndpoint,
	mockQueryUsersByTenantEndpoint,
	mockQueryUsersEndpoint,
	mockUnassignUserFromTenantEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createQueryClientsByTenantResponse,
	createQueryGroupsByTenantResponse,
	createQueryUsersByTenantResponse,
	createTenant,
} from '#/shared-test-modules/api-mocks/tenants';
import type {TenantTab} from '#/admin/modules/tenants/detailSearchSchema';
import {AdminTenantDetailPage, type AdminTenantDetailPageProps} from './AdminTenantDetailPage';

async function renderPage(overrides: Partial<AdminTenantDetailPageProps> = {}) {
	const onSearchChange = vi.fn();
	const onDeleted = vi.fn();
	const tabs: TenantTab[] = ['users', 'groups', 'roles'];
	const screen = await renderWithRouter(
		() => (
			<>
				<AdminTenantDetailPage
					tenant={createTenant({tenantId: 'tenant-a', name: 'Tenant A', description: 'My tenant'})}
					tabs={tabs}
					isOidc={false}
					isCamundaGroupsEnabled={true}
					search={{}}
					onSearchChange={onSearchChange}
					onDeleted={onDeleted}
					{...overrides}
				/>
				<Toaster />
			</>
		),
		{path: '/admin/tenants'},
	);

	return {screen, onSearchChange, onDeleted};
}

describe('<AdminTenantDetailPage />', () => {
	afterEach(async () => {
		await cleanup();
		toast.dismiss();
	});

	it('should show the tenant details and the users tab by default', async ({worker}) => {
		worker.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse(['jane']))}),
		);

		const {screen} = await renderPage();

		await expect.element(screen.getByRole('heading', {name: 'Tenant A'})).toBeVisible();
		await expect.element(screen.getByText('My tenant')).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'jane'})).toBeVisible();
	});

	it('should only offer the tabs it is given', async ({worker}) => {
		worker.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse())}),
		);

		const {screen} = await renderPage({tabs: ['users', 'roles']});

		await expect.element(screen.getByRole('tab', {name: 'Users'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Roles'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Groups'})).not.toBeInTheDocument();
	});

	it('should reset paging and sorting when the reader switches tab', async ({worker}) => {
		worker.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse())}),
		);

		const {screen, onSearchChange} = await renderPage({search: {page: 3, sortOrder: 'DESC'}});

		await userEvent.click(screen.getByRole('tab', {name: 'Roles'}));

		expect(onSearchChange).toHaveBeenCalledWith({
			tab: 'roles',
			page: undefined,
			pageSize: undefined,
			sortOrder: undefined,
		});
	});

	it('should show the groups of the tenant on the groups tab', async ({worker}) => {
		worker.use(
			mockQueryGroupsByTenantEndpoint({
				successResponse: HttpResponse.json(createQueryGroupsByTenantResponse(['sales'])),
			}),
		);

		const {screen} = await renderPage({search: {tab: 'groups'}});

		await expect.element(screen.getByRole('cell', {name: 'sales'})).toBeVisible();
	});

	it('should assign a group by typing its ID when Camunda groups are disabled', async ({worker}) => {
		// given
		const assignedGroupIds: string[] = [];
		worker.use(
			mockQueryGroupsByTenantEndpoint({
				successResponse: HttpResponse.json(createQueryGroupsByTenantResponse(['sales'])),
			}),
			http.all('*/tenants/:tenantId/groups/:groupId', ({params}) => {
				assignedGroupIds.push(String(params.groupId));
				return new HttpResponse(null, {status: 204});
			}),
		);
		const {screen} = await renderPage({search: {tab: 'groups'}, isCamundaGroupsEnabled: false});
		await expect.element(screen.getByRole('cell', {name: 'sales'})).toBeVisible();
		worker.use(
			mockQueryGroupsByTenantEndpoint({
				successResponse: HttpResponse.json(createQueryGroupsByTenantResponse(['sales', 'external-group'])),
			}),
		);

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign group'}));
		await userEvent.fill(screen.getByRole('textbox', {name: 'Groups'}), 'external-group');
		await userEvent.click(screen.getByRole('button', {name: 'Assign', exact: true}));

		// then
		await expect.element(screen.getByText('Assignment saved')).toBeVisible();
		expect(assignedGroupIds).toEqual(['external-group']);
	});

	it('should assign a member whose ID matches an inherited object property', async ({worker}) => {
		// given
		const assignedClientIds: string[] = [];
		worker.use(
			mockQueryClientsByTenantEndpoint({successResponse: HttpResponse.json(createQueryClientsByTenantResponse())}),
			http.all('*/tenants/:tenantId/clients/:clientId', ({params}) => {
				assignedClientIds.push(String(params.clientId));
				return new HttpResponse(null, {status: 204});
			}),
		);
		const {screen} = await renderPage({tabs: ['users', 'clients'], isOidc: true, search: {tab: 'clients'}});
		await expect.element(screen.getByRole('button', {name: 'Assign client'})).toBeVisible();
		worker.use(
			mockQueryClientsByTenantEndpoint({
				successResponse: HttpResponse.json(createQueryClientsByTenantResponse(['constructor'])),
			}),
		);

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign client'}));
		await userEvent.fill(screen.getByRole('textbox', {name: 'Client ID'}), 'constructor');
		await userEvent.click(screen.getByRole('button', {name: 'Assign', exact: true}));

		// then
		await expect.element(screen.getByText('Assignment saved')).toBeVisible();
		expect(assignedClientIds).toEqual(['constructor']);
	});

	it('should step back to the last page when the requested page no longer exists', async ({worker}) => {
		// given
		worker.use(
			mockQueryUsersByTenantEndpoint({
				successResponse: HttpResponse.json(createQueryUsersByTenantResponse(['jane'])),
			}),
		);

		// when
		const {onSearchChange} = await renderPage({search: {page: 3}});

		// then
		await vi.waitFor(() => expect(onSearchChange).toHaveBeenCalledWith({page: undefined}, {replace: true}));
	});

	it('should show the clients of the tenant on the clients tab', async ({worker}) => {
		worker.use(
			mockQueryClientsByTenantEndpoint({
				successResponse: HttpResponse.json(createQueryClientsByTenantResponse(['connectors'])),
			}),
		);

		const {screen} = await renderPage({tabs: ['users', 'clients'], isOidc: true, search: {tab: 'clients'}});

		await expect.element(screen.getByRole('cell', {name: 'connectors'})).toBeVisible();
	});

	it('should show an empty state when nothing is assigned', async ({worker}) => {
		worker.use(
			mockQueryRolesByTenantEndpoint({successResponse: HttpResponse.json({items: [], page: {totalItems: 0}})}),
		);

		const {screen} = await renderPage({search: {tab: 'roles'}});

		await expect.element(screen.getByText('No roles assigned to this tenant.')).toBeVisible();
	});

	it('should assign a user picked from the user search', async ({worker}) => {
		worker.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse())}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json({
					items: [{username: 'jane', name: 'Jane', email: 'jane@example.com'}],
					page: {totalItems: 1},
				}),
			}),
			mockAssignUserToTenantEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);

		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.click(screen.getByRole('combobox', {name: 'Users'}));
		await userEvent.click(screen.getByRole('option', {name: 'jane'}));
		await userEvent.click(screen.getByRole('heading', {name: 'Assign user'}));
		await userEvent.click(screen.getByRole('button', {name: 'Assign', exact: true}));

		await expect.element(screen.getByText('Assignment saved')).toBeVisible();
	});

	it('should keep the user options in alphabetical order while users are picked', async ({worker}) => {
		// given
		worker.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse())}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json({
					items: [{username: 'carol'}, {username: 'alice'}, {username: 'bob'}],
					page: {totalItems: 3},
				}),
			}),
		);
		const {screen} = await renderPage();

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.click(screen.getByRole('combobox', {name: 'Users'}));
		await userEvent.click(screen.getByRole('option', {name: 'carol'}));

		// then
		const options = screen.getByRole('option').all();
		expect(options.map((option) => option.element().textContent)).toEqual(['alice', 'bob', 'carol']);
	});

	it('should unassign a user after confirmation', async ({worker}) => {
		worker.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse(['jane']))}),
			mockUnassignUserFromTenantEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);

		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: /row actions/i}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Remove'}));
		await userEvent.click(screen.getByRole('alertdialog').getByRole('button', {name: 'Remove'}));

		await expect.element(screen.getByText('Removed from tenant')).toBeVisible();
	});

	it('should delete the tenant and notify the caller', async ({worker}) => {
		worker.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse())}),
			mockDeleteTenantEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetTenantEndpoint({successResponse: new HttpResponse(null, {status: 404})}),
		);

		const {screen, onDeleted} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Delete'}));
		await userEvent.click(screen.getByRole('alertdialog').getByRole('button', {name: 'Delete'}));

		await vi.waitFor(() => expect(onDeleted).toHaveBeenCalled());
	});

	it('should not offer edit or delete for the default tenant', async ({worker}) => {
		worker.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse())}),
		);

		const {screen} = await renderPage({tenant: createTenant({tenantId: '<default>', name: 'Default'})});

		await expect.element(screen.getByRole('heading', {name: 'Default'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Edit'})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Delete'})).not.toBeInTheDocument();
	});
});
