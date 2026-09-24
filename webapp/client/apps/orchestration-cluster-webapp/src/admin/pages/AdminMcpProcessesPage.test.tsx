/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {TooltipProvider} from '@camunda/design-system';
import {describe, expect, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import type {McpProcessTool} from '#/admin/modules/mcp-processes/mcpProcessTools';
import {AdminMcpProcessesPage, type AdminMcpProcessesPageProps} from './AdminMcpProcessesPage';

const DEBOUNCED = {timeout: 3000};

function createTool(overrides: Partial<McpProcessTool> = {}): McpProcessTool {
	return {
		id: '2251799813685249',
		toolName: 'place-order',
		toolProperties: {
			purpose: 'Places an order',
			results: 'An order confirmation',
			whenToUse: 'When the cart is ready',
			whenNotToUse: 'When payment is pending',
		},
		processDefinitionName: 'Order process',
		processDefinitionVersion: 3,
		tenantId: 'acme',
		...overrides,
	};
}

async function renderPage(overrides: Partial<AdminMcpProcessesPageProps> = {}) {
	const onSearchChange = vi.fn();
	const screen = await render(
		<TooltipProvider>
			<AdminMcpProcessesPage
				tools={[createTool()]}
				totalItems={1}
				search={{}}
				isTenantsApiEnabled={false}
				onSearchChange={onSearchChange}
				{...overrides}
			/>
		</TooltipProvider>,
	);

	return {screen, onSearchChange};
}

describe('<AdminMcpProcessesPage />', () => {
	it('should list the tools a process exposes', async () => {
		// when
		const {screen} = await renderPage();

		// then
		await expect.element(screen.getByRole('cell', {name: 'place-order'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Places an order'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Order process'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: '3'})).toBeVisible();
	});

	it('should hide the tenant column while the tenants API is disabled', async () => {
		// when
		const {screen} = await renderPage({isTenantsApiEnabled: false});

		// then
		await expect.element(screen.getByText('Tenant')).not.toBeInTheDocument();
		await expect.element(screen.getByRole('cell', {name: 'acme'})).not.toBeInTheDocument();
	});

	it('should show the tenant column once the tenants API is enabled', async () => {
		// when
		const {screen} = await renderPage({isTenantsApiEnabled: true});

		// then
		await expect.element(screen.getByText('Tenant')).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'acme'})).toBeVisible();
	});

	it('should stand in for a description and version the process does not declare', async () => {
		// given
		const tool = createTool({
			toolProperties: {purpose: null, results: null, whenToUse: null, whenNotToUse: null},
			processDefinitionVersion: null,
		});

		// when
		const {screen} = await renderPage({tools: [tool]});

		// then
		await expect.element(screen.getByRole('cell', {name: '-'}).first()).toBeVisible();
	});

	it('should reveal the tool documentation when a row is expanded', async () => {
		// given
		const {screen} = await renderPage();

		// when
		await userEvent.click(screen.getByRole('button', {name: /expand/i}).first());

		// then
		await expect.element(screen.getByRole('heading', {name: 'Purpose'})).toBeVisible();
		await expect.element(screen.getByText('When the cart is ready')).toBeVisible();
		await expect.element(screen.getByText('When payment is pending')).toBeVisible();
	});

	it('should tell the reader which parts of the tool documentation are missing', async () => {
		// given
		const tool = createTool({
			toolProperties: {purpose: 'Places an order', results: null, whenToUse: null, whenNotToUse: null},
		});
		const {screen} = await renderPage({tools: [tool]});

		// when
		await userEvent.click(screen.getByRole('button', {name: /expand/i}).first());

		// then
		await expect.element(screen.getByText('No information provided.').first()).toBeVisible();
	});

	it('should search by tool name once the reader stops typing', async () => {
		// given
		const {screen, onSearchChange} = await renderPage();

		// when
		await userEvent.fill(screen.getByRole('searchbox'), 'order');

		// then
		await vi.waitFor(() => expect(onSearchChange).toHaveBeenCalledWith({search: 'order', page: undefined}), DEBOUNCED);
	});

	it('should clear the tool name filter', async () => {
		// given
		const {screen, onSearchChange} = await renderPage({search: {search: 'order'}});

		// when
		await userEvent.click(screen.getByRole('button', {name: /clear/i}));

		// then
		await vi.waitFor(
			() => expect(onSearchChange).toHaveBeenCalledWith({search: undefined, page: undefined}),
			DEBOUNCED,
		);
	});

	it('should not re-search while the applied term already matches the input', async () => {
		// when
		const {onSearchChange} = await renderPage({search: {search: 'order'}});

		// then
		await new Promise((resolve) => setTimeout(resolve, 1000));
		expect(onSearchChange).not.toHaveBeenCalled();
	});

	it('should reverse the tool name order when the reader sorts the column', async () => {
		// given
		const {screen, onSearchChange} = await renderPage();

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Tool name'}));

		// then
		expect(onSearchChange).toHaveBeenCalledWith({sortOrder: 'desc', page: undefined});
	});

	it('should return to the first page when the page size changes', async () => {
		// given
		const {screen, onSearchChange} = await renderPage({search: {page: 3}, totalItems: 200});

		// when
		await userEvent.click(screen.getByRole('combobox'));
		await userEvent.click(screen.getByRole('option', {name: '50'}));

		// then
		expect(onSearchChange).toHaveBeenCalledWith({pageSize: 50, page: undefined});
	});
});
