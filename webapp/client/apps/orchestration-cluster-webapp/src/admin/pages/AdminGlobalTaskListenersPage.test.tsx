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
import type {GlobalTaskListener} from '@camunda/camunda-api-zod-schemas/8.11';
import {createGlobalTaskListener} from '#/shared-test-modules/api-mocks/global-task-listeners';
import {AdminGlobalTaskListenersPage, type AdminGlobalTaskListenersPageProps} from './AdminGlobalTaskListenersPage';

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

async function renderPage(overrides: Partial<AdminGlobalTaskListenersPageProps> = {}) {
	const onSearchChange = vi.fn();
	const screen = await render(
		<AdminGlobalTaskListenersPage
			globalTaskListeners={[createGlobalTaskListener()]}
			totalItems={1}
			search={{}}
			onSearchChange={onSearchChange}
			{...overrides}
		/>,
		{wrapper: getWrapper()},
	);

	return {screen, onSearchChange};
}

describe('<AdminGlobalTaskListenersPage />', () => {
	afterEach(async () => {
		await cleanup();
		toast.dismiss();
	});

	it('should list the global task listeners', async () => {
		const {screen} = await renderPage({
			globalTaskListeners: [
				createGlobalTaskListener({
					id: 'my-listener',
					type: 'my-type',
					eventTypes: ['creating', 'completing'],
					retries: 5,
					afterNonGlobal: true,
					priority: 10,
				}),
			],
		});

		await expect.element(screen.getByRole('cell', {name: 'my-listener'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'my-type'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Creating, Completing'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: '5'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'After model-level listeners'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: '10'})).toBeVisible();
	});

	it('should show "All events" when every event type is selected', async () => {
		const {screen} = await renderPage({
			globalTaskListeners: [createGlobalTaskListener({eventTypes: ['all']})],
		});

		await expect.element(screen.getByRole('cell', {name: 'All events'})).toBeVisible();
	});

	it('should search by listener ID once the reader stops typing', async () => {
		const {screen, onSearchChange} = await renderPage();

		await userEvent.fill(screen.getByRole('searchbox'), 'my-listener');

		await vi.waitFor(
			() => expect(onSearchChange).toHaveBeenCalledWith({search: 'my-listener', page: undefined}),
			DEBOUNCED,
		);
	});

	it('should clear the listener ID filter', async () => {
		const {screen, onSearchChange} = await renderPage({search: {search: 'my-listener'}});

		await userEvent.click(screen.getByRole('button', {name: /clear/i}));

		await vi.waitFor(
			() => expect(onSearchChange).toHaveBeenCalledWith({search: undefined, page: undefined}),
			DEBOUNCED,
		);
	});

	it('should sort by listener ID, type, execution order, or priority when the reader sorts those columns', async () => {
		const {screen, onSearchChange} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Listener ID'}));
		expect(onSearchChange).toHaveBeenCalledWith({sortField: 'id', sortOrder: 'desc', page: undefined});

		await userEvent.click(screen.getByRole('button', {name: 'Listener type'}));
		expect(onSearchChange).toHaveBeenCalledWith({sortField: 'type', sortOrder: 'asc', page: undefined});

		await userEvent.click(screen.getByRole('button', {name: 'Execution order'}));
		expect(onSearchChange).toHaveBeenCalledWith({sortField: 'afterNonGlobal', sortOrder: 'desc', page: undefined});

		await userEvent.click(screen.getByRole('button', {name: 'Priority'}));
		expect(onSearchChange).toHaveBeenCalledWith({sortField: 'priority', sortOrder: 'desc', page: undefined});
	});

	it('should return to the first page when the page size changes', async () => {
		const {screen, onSearchChange} = await renderPage({search: {page: 3}, totalItems: 200});

		await userEvent.click(screen.getByRole('combobox'));
		await userEvent.click(screen.getByRole('option', {name: '50'}));

		expect(onSearchChange).toHaveBeenCalledWith({pageSize: 50, page: undefined});
	});

	it('should open the add global task listener modal from the page header', async () => {
		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Add global task listener'}));

		await expect.element(screen.getByRole('heading', {name: 'Add global task listener'})).toBeVisible();
	});

	it('should open the edit modal for a global task listener with its fields prefilled', async () => {
		const globalTaskListener: GlobalTaskListener = createGlobalTaskListener({id: 'my-listener', type: 'my-type'});
		const {screen} = await renderPage({globalTaskListeners: [globalTaskListener]});

		await userEvent.click(screen.getByRole('button', {name: /row actions/i}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Edit'}));

		await expect.element(screen.getByRole('heading', {name: 'Edit global task listener'})).toBeVisible();
		await expect.element(screen.getByRole('textbox', {name: 'Listener type'})).toHaveValue('my-type');
	});

	it('should open the delete confirmation for a global task listener', async () => {
		const globalTaskListener: GlobalTaskListener = createGlobalTaskListener({id: 'my-listener'});
		const {screen} = await renderPage({globalTaskListeners: [globalTaskListener]});

		await userEvent.click(screen.getByRole('button', {name: /row actions/i}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Delete'}));

		await expect.element(screen.getByRole('heading', {name: 'Delete global task listener'})).toBeVisible();
		await expect.element(screen.getByRole('alertdialog').getByText('my-listener')).toBeVisible();
	});
});
