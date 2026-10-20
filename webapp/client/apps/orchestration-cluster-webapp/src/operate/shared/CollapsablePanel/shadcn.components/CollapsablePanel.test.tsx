/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {CollapsablePanel} from './CollapsablePanel';

describe('<CollapsablePanel />', () => {
	it('should render the collapsed rail with an expand button and call onToggle when clicked', async () => {
		const onToggle = vi.fn();
		const screen = await render(
			<CollapsablePanel label="Filters" panelPosition="LEFT" isCollapsed onToggle={onToggle} maxWidth={300}>
				<div>Content</div>
			</CollapsablePanel>,
		);

		await expect.element(screen.getByTestId('collapsed-panel')).toBeInTheDocument();
		await expect.element(screen.getByText('Content')).not.toBeInTheDocument();

		await userEvent.click(screen.getByRole('button', {name: 'Expand Filters'}));

		expect(onToggle).toHaveBeenCalledTimes(1);
	});

	it('should render the expanded panel with children, header, and footer, and call onToggle when collapsed', async () => {
		const onToggle = vi.fn();
		const screen = await render(
			<CollapsablePanel
				label="Filters"
				panelPosition="LEFT"
				isCollapsed={false}
				onToggle={onToggle}
				maxWidth={300}
				footer={<div>Footer</div>}
			>
				<div>Content</div>
			</CollapsablePanel>,
		);

		const panel = screen.getByTestId('expanded-panel');
		await expect.element(panel).toBeInTheDocument();
		await expect.element(screen.getByText('Filters')).toBeInTheDocument();
		await expect.element(screen.getByText('Content')).toBeInTheDocument();
		await expect.element(screen.getByText('Footer')).toBeInTheDocument();

		await userEvent.click(screen.getByRole('button', {name: 'Collapse Filters'}));

		expect(onToggle).toHaveBeenCalledTimes(1);
	});

	it('should use the collapse/expand accessible labels for a LEFT panel', async () => {
		const screen = await render(
			<CollapsablePanel label="Filters" panelPosition="LEFT" isCollapsed={false} onToggle={vi.fn()} maxWidth={300}>
				<div>Content</div>
			</CollapsablePanel>,
		);

		await expect.element(screen.getByRole('button', {name: 'Collapse Filters'})).toBeInTheDocument();
	});

	it('should use the collapse/expand accessible labels for a RIGHT panel', async () => {
		const screen = await render(
			<CollapsablePanel label="Filters" panelPosition="RIGHT" isCollapsed={false} onToggle={vi.fn()} maxWidth={300}>
				<div>Content</div>
			</CollapsablePanel>,
		);

		await expect.element(screen.getByRole('button', {name: 'Collapse Filters'})).toBeInTheDocument();
	});
});
