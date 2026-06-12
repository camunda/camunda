/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {describe, expect} from 'vitest';
import {CollapsiblePanel} from './CollapsiblePanel';

describe('<CollapsiblePanel />', () => {
	it('should render collapsed by default with expand and filter buttons', async () => {
		const screen = await render(<CollapsiblePanel />);

		await expect.element(screen.getByRole('button', {name: 'Expand to show filters'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Filter tasks'})).toBeVisible();
		await expect.element(screen.getByRole('heading', {name: 'Filters'})).not.toBeInTheDocument();
	});

	it('should expand when clicking the expand button', async () => {
		const screen = await render(<CollapsiblePanel />);

		await userEvent.click(screen.getByRole('button', {name: 'Expand to show filters'}));

		await expect.element(screen.getByRole('heading', {name: 'Filters'})).toBeVisible();
		await expect.element(screen.getByText('All open tasks')).toBeVisible();
		await expect.element(screen.getByText('Assigned to me')).toBeVisible();
		await expect.element(screen.getByText('Unassigned')).toBeVisible();
		await expect.element(screen.getByText('Completed')).toBeVisible();
		await expect.element(screen.getByText('New filter')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Collapse'})).toBeVisible();
	});

	it('should collapse when clicking the collapse button', async () => {
		const screen = await render(<CollapsiblePanel />);

		await userEvent.click(screen.getByRole('button', {name: 'Expand to show filters'}));
		await expect.element(screen.getByRole('heading', {name: 'Filters'})).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Collapse'}));

		await expect.element(screen.getByRole('button', {name: 'Expand to show filters'})).toBeVisible();
		await expect.element(screen.getByRole('heading', {name: 'Filters'})).not.toBeInTheDocument();
	});

	it('should have an accessible navigation landmark', async () => {
		const screen = await render(<CollapsiblePanel />);

		await expect.element(screen.getByRole('navigation', {name: 'Filter controls'})).toBeVisible();
	});
});
