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
import type {MappingRule} from '@camunda/camunda-api-zod-schemas/8.10';
import {createMappingRule} from '#/shared-test-modules/api-mocks/mapping-rules';
import {AdminMappingRulesPage, type AdminMappingRulesPageProps} from './AdminMappingRulesPage';

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

async function renderPage(overrides: Partial<AdminMappingRulesPageProps> = {}) {
	const onSearchChange = vi.fn();
	const screen = await render(
		<AdminMappingRulesPage
			mappingRules={[createMappingRule()]}
			totalItems={1}
			search={{}}
			onSearchChange={onSearchChange}
			{...overrides}
		/>,
		{wrapper: getWrapper()},
	);

	return {screen, onSearchChange};
}

describe('<AdminMappingRulesPage />', () => {
	afterEach(async () => {
		await cleanup();
		toast.dismiss();
	});

	it('should list the mapping rules', async () => {
		const {screen} = await renderPage({
			mappingRules: [
				createMappingRule({
					mappingRuleId: 'my-rule',
					name: 'My rule',
					claimName: 'email',
					claimValue: 'demo@example.com',
				}),
			],
		});

		await expect.element(screen.getByRole('cell', {name: 'my-rule'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'My rule'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'email'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'demo@example.com'})).toBeVisible();
	});

	it('should search by mapping rule ID once the reader stops typing', async () => {
		const {screen, onSearchChange} = await renderPage();

		await userEvent.fill(screen.getByRole('searchbox'), 'my-rule');

		await vi.waitFor(
			() => expect(onSearchChange).toHaveBeenCalledWith({search: 'my-rule', page: undefined}),
			DEBOUNCED,
		);
	});

	it('should clear the mapping rule ID filter', async () => {
		const {screen, onSearchChange} = await renderPage({search: {search: 'my-rule'}});

		await userEvent.click(screen.getByRole('button', {name: /clear/i}));

		await vi.waitFor(
			() => expect(onSearchChange).toHaveBeenCalledWith({search: undefined, page: undefined}),
			DEBOUNCED,
		);
	});

	it('should reverse the mapping rule ID order when the reader sorts the column', async () => {
		const {screen, onSearchChange} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Mapping rule ID'}));

		expect(onSearchChange).toHaveBeenCalledWith({sortOrder: 'desc', page: undefined});
	});

	it('should return to the first page when the page size changes', async () => {
		const {screen, onSearchChange} = await renderPage({search: {page: 3}, totalItems: 200});

		await userEvent.click(screen.getByRole('combobox'));
		await userEvent.click(screen.getByRole('option', {name: '50'}));

		expect(onSearchChange).toHaveBeenCalledWith({pageSize: 50, page: undefined});
	});

	it('should open the add mapping rule modal from the page header', async () => {
		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Add mapping rule'}));

		await expect.element(screen.getByRole('heading', {name: 'Add mapping rule'})).toBeVisible();
	});

	it('should open the edit modal for a mapping rule with its fields prefilled', async () => {
		const mappingRule: MappingRule = createMappingRule({mappingRuleId: 'my-rule', name: 'My rule'});
		const {screen} = await renderPage({mappingRules: [mappingRule]});

		await userEvent.click(screen.getByRole('button', {name: /row actions/i}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Edit'}));

		await expect.element(screen.getByRole('heading', {name: 'Edit mapping rule'})).toBeVisible();
		await expect.element(screen.getByRole('textbox', {name: 'Name'})).toHaveValue('My rule');
	});

	it('should open the delete confirmation for a mapping rule', async () => {
		const mappingRule: MappingRule = createMappingRule({name: 'My rule'});
		const {screen} = await renderPage({mappingRules: [mappingRule]});

		await userEvent.click(screen.getByRole('button', {name: /row actions/i}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Delete'}));

		await expect.element(screen.getByRole('heading', {name: 'Delete mapping rule'})).toBeVisible();
		await expect.element(screen.getByRole('alertdialog').getByText('My rule')).toBeVisible();
	});
});
