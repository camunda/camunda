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
import {createClusterVariable} from '#/shared-test-modules/api-mocks/cluster-variables';
import {AdminClusterVariablesPage, type AdminClusterVariablesPageProps} from './AdminClusterVariablesPage';

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

async function renderPage(overrides: Partial<AdminClusterVariablesPageProps> = {}) {
	const onSearchChange = vi.fn();
	const screen = await render(
		<AdminClusterVariablesPage
			clusterVariables={[createClusterVariable()]}
			totalItems={1}
			search={{}}
			isTenantScopeAvailable
			onSearchChange={onSearchChange}
			{...overrides}
		/>,
		{wrapper: getWrapper()},
	);

	return {screen, onSearchChange};
}

describe('<AdminClusterVariablesPage />', () => {
	afterEach(async () => {
		await cleanup();
		toast.dismiss();
	});

	it('should list the cluster variables with their scope', async () => {
		const {screen} = await renderPage({
			clusterVariables: [
				createClusterVariable({name: 'global-variable', value: '"hello"'}),
				createClusterVariable({name: 'tenant-variable', scope: 'TENANT', tenantId: 'tenant-a', value: '42'}),
			],
			totalItems: 2,
		});

		await expect.element(screen.getByRole('cell', {name: 'global-variable'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: '"hello"'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Global'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'tenant-variable'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Tenant: tenant-a'})).toBeVisible();
	});

	it('should show an empty state when there are no cluster variables', async () => {
		const {screen} = await renderPage({clusterVariables: [], totalItems: 0});

		await expect.element(screen.getByText('No cluster variables found.')).toBeVisible();
	});

	it('should search by name once the reader stops typing', async () => {
		const {screen, onSearchChange} = await renderPage();

		await userEvent.fill(screen.getByRole('searchbox'), 'my-var');

		await vi.waitFor(() => expect(onSearchChange).toHaveBeenCalledWith({search: 'my-var', page: undefined}), DEBOUNCED);
	});

	it('should not overwrite newer input when a stale search term is applied', async () => {
		const {screen} = await renderPage();
		const searchBox = screen.getByRole('searchbox');

		await userEvent.fill(searchBox, 'a');
		await userEvent.fill(searchBox, 'ab');

		// Simulates the route applying the debounced "a" navigation while the reader kept typing.
		await screen.rerender(
			<AdminClusterVariablesPage
				clusterVariables={[createClusterVariable()]}
				totalItems={1}
				search={{search: 'a'}}
				isTenantScopeAvailable
				onSearchChange={() => {}}
			/>,
		);

		await expect.element(searchBox).toHaveValue('ab');
	});

	it('should clear the name filter', async () => {
		const {screen, onSearchChange} = await renderPage({search: {search: 'my-var'}});

		await userEvent.click(screen.getByRole('button', {name: /clear/i}));

		await vi.waitFor(
			() => expect(onSearchChange).toHaveBeenCalledWith({search: undefined, page: undefined}),
			DEBOUNCED,
		);
	});

	it('should reverse the name order when the reader sorts the column', async () => {
		const {screen, onSearchChange} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Name'}));

		expect(onSearchChange).toHaveBeenCalledWith({sortOrder: 'desc', page: undefined});
	});

	it('should return to the first page when the page size changes', async () => {
		const {screen, onSearchChange} = await renderPage({search: {page: 3}, totalItems: 200});

		await userEvent.click(screen.getByRole('combobox'));
		await userEvent.click(screen.getByRole('option', {name: '50'}));

		expect(onSearchChange).toHaveBeenCalledWith({pageSize: 50, page: undefined});
	});

	it('should open the add modal from the page header', async () => {
		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Create cluster variable'}));

		await expect.element(screen.getByRole('heading', {name: 'Create cluster variable'})).toBeVisible();
	});

	it('should open the delete confirmation for a cluster variable', async () => {
		const {screen} = await renderPage({clusterVariables: [createClusterVariable({name: 'my-variable'})]});

		await userEvent.click(screen.getByRole('button', {name: /row actions/i}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Delete'}));

		await expect.element(screen.getByRole('heading', {name: 'Delete cluster variable'})).toBeVisible();
		await expect.element(screen.getByRole('alertdialog').getByText('my-variable')).toBeVisible();
	});

	it('should offer view and edit actions for a cluster variable', async () => {
		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: /row actions/i}));

		await expect.element(screen.getByRole('menuitem', {name: 'View'})).toBeVisible();
		await expect.element(screen.getByRole('menuitem', {name: 'Edit'})).toBeVisible();
	});
});
