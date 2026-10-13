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
import {Toaster, TooltipProvider, toast} from '@camunda/design-system';
import {afterEach, describe, expect, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {
	mockAssignUserToTenantEndpoint,
	mockDeleteTenantEndpoint,
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

async function renderPage(overrides: Partial<AdminTenantDetailPageProps> = {}) {
	const onSearchChange = vi.fn();
	const onDeleted = vi.fn();
	const tabs: TenantTab[] = ['users', 'groups', 'roles'];
	const screen = await render(
		<AdminTenantDetailPage
			tenant={createTenant({tenantId: 'tenant-a', name: 'Tenant A', description: 'My tenant'})}
			tabs={tabs}
			isOidc={false}
			search={{}}
			onSearchChange={onSearchChange}
			onDeleted={onDeleted}
			{...overrides}
		/>,
		{wrapper: getWrapper()},
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
		await userEvent.click(screen.getByRole('button', {name: 'Assign', exact: true}));

		await expect.element(screen.getByText('Assignment saved')).toBeVisible();
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
