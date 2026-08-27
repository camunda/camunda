/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {TabListNav, type TabItem} from './TabListNav';

const mockTabs: TabItem[] = [
	{key: 'task', title: 'Task', label: 'Show task', selected: true, to: '/shadcn/tasklist/$userTaskKey'},
	{
		key: 'process',
		title: 'Process',
		label: 'Show process',
		selected: false,
		to: '/shadcn/tasklist/$userTaskKey/process',
	},
	{
		key: 'history',
		title: 'History',
		label: 'Show history',
		selected: false,
		to: '/shadcn/tasklist/$userTaskKey/history',
	},
];

describe('<TabListNav />', () => {
	it('should render all provided tabs', async () => {
		const screen = await renderWithRouter(
			() => <TabListNav label="Task Details Navigation" items={mockTabs} userTaskKey="123" />,
			{path: '/shadcn/tasklist/$userTaskKey', initialEntry: '/shadcn/tasklist/123'},
		);

		await expect.element(screen.getByRole('tab', {name: 'Show task'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Show process'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Show history'})).toBeVisible();
	});

	it('should mark currently selected tab', async () => {
		const screen = await renderWithRouter(
			() => <TabListNav label="Task Details Navigation" items={mockTabs} userTaskKey="123" />,
			{path: '/shadcn/tasklist/$userTaskKey', initialEntry: '/shadcn/tasklist/123'},
		);

		await expect.element(screen.getByRole('tab', {name: 'Show task'})).toHaveAttribute('aria-selected', 'true');
		await expect.element(screen.getByRole('tab', {name: 'Show process'})).toHaveAttribute('aria-selected', 'false');
	});

	it('should navigate to the tab route on click', async () => {
		const {router, ...screen} = await renderWithRouter(
			() => <TabListNav label="Task Details Navigation" items={mockTabs} userTaskKey="123" />,
			{path: '/shadcn/tasklist/$userTaskKey', initialEntry: '/shadcn/tasklist/123'},
		);

		await userEvent.click(screen.getByRole('tab', {name: 'Show process'}));

		expect(router.state.location.pathname).toContain('/process');
	});
});
