/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {TooltipProvider, Toaster, toast} from '@camunda/design-system';
import {cleanup, render} from 'vitest-browser-react';
import {describe, expect, vi, afterEach} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {userEvent} from 'vitest/browser';
import {createTenant} from '#/shared-test-modules/api-mocks/tenants';
import {AdminTenantsPage, type AdminTenantsPageProps} from './AdminTenantsPage';

const DEBOUNCED = {timeout: 3000};

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

async function renderPage(overrides: Partial<AdminTenantsPageProps> = {}) {
	const onSearchChange = vi.fn();
	const onOpenTenant = vi.fn();
	const screen = await render(
		<AdminTenantsPage
			tenants={[createTenant()]}
			totalItems={1}
			search={{}}
			onSearchChange={onSearchChange}
			onOpenTenant={onOpenTenant}
			{...overrides}
		/>,
		{wrapper: getWrapper()},
	);

	return {screen, onSearchChange, onOpenTenant};
}

describe('<AdminTenantsPage />', () => {
	afterEach(async () => {
		await cleanup();
		toast.dismiss();
	});

	it('should list the tenants', async () => {
		const {screen} = await renderPage({
			tenants: [createTenant({tenantId: 'tenant-a', name: 'Tenant A'})],
		});

		await expect.element(screen.getByRole('cell', {name: 'tenant-a'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Tenant A'})).toBeVisible();
	});

	it('should open a tenant when the reader clicks its row', async () => {
		const tenant = createTenant({tenantId: 'tenant-a', name: 'Tenant A'});
		const {screen, onOpenTenant} = await renderPage({tenants: [tenant]});

		await userEvent.click(screen.getByRole('cell', {name: 'tenant-a'}));

		expect(onOpenTenant).toHaveBeenCalledWith(tenant);
	});

	it('should search by tenant ID once the reader stops typing', async () => {
		const {screen, onSearchChange} = await renderPage();

		await userEvent.fill(screen.getByRole('searchbox'), 'tenant-a');

		await vi.waitFor(
			() => expect(onSearchChange).toHaveBeenCalledWith({search: 'tenant-a', page: undefined}),
			DEBOUNCED,
		);
	});

	it('should not overwrite newer input when a stale search term is applied', async () => {
		const {screen} = await renderPage();
		const searchBox = screen.getByRole('searchbox');

		await userEvent.fill(searchBox, 'a');
		await userEvent.fill(searchBox, 'ab');

		await screen.rerender(
			<AdminTenantsPage
				tenants={[createTenant()]}
				totalItems={1}
				search={{search: 'a'}}
				onSearchChange={() => {}}
				onOpenTenant={() => {}}
			/>,
		);

		await expect.element(searchBox).toHaveValue('ab');
	});

	it('should reverse the tenant ID order when the reader sorts the column', async () => {
		const {screen, onSearchChange} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Tenant ID'}));

		expect(onSearchChange).toHaveBeenCalledWith({sortField: 'tenantId', sortOrder: 'DESC', page: undefined});
	});

	it('should return to the first page when the page size changes', async () => {
		const {screen, onSearchChange} = await renderPage({search: {page: 3}, totalItems: 200});

		await userEvent.click(screen.getByRole('combobox'));
		await userEvent.click(screen.getByRole('option', {name: '50'}));

		expect(onSearchChange).toHaveBeenCalledWith({pageSize: 50, page: undefined});
	});

	it('should open the create tenant modal from the page header', async () => {
		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Create tenant'}));

		await expect.element(screen.getByRole('heading', {name: 'Create tenant'})).toBeVisible();
	});

	it('should open the edit modal for a tenant with its fields prefilled', async () => {
		const {screen} = await renderPage({
			tenants: [createTenant({tenantId: 'tenant-a', name: 'Tenant A'})],
		});

		await userEvent.click(screen.getByRole('button', {name: /row actions/i}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Edit'}));

		await expect.element(screen.getByRole('heading', {name: 'Edit tenant'})).toBeVisible();
		await expect.element(screen.getByRole('textbox', {name: 'Name'})).toHaveValue('Tenant A');
	});

	it('should open the delete confirmation for a tenant', async () => {
		const {screen} = await renderPage({tenants: [createTenant({name: 'Tenant A'})]});

		await userEvent.click(screen.getByRole('button', {name: /row actions/i}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Delete'}));

		await expect.element(screen.getByRole('heading', {name: 'Delete tenant'})).toBeVisible();
		await expect.element(screen.getByRole('alertdialog').getByText('Tenant A')).toBeVisible();
	});

	it('should not offer edit or delete for the default tenant', async () => {
		const {screen} = await renderPage({
			tenants: [
				createTenant({tenantId: '<default>', name: 'Default'}),
				createTenant({tenantId: 'tenant-a', name: 'Tenant A'}),
			],
		});

		await expect.element(screen.getByRole('button', {name: /row actions/i})).toHaveLength(1);
	});
});
