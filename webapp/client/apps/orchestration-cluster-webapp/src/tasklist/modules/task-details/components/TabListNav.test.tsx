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
	{key: 'task', title: 'Task', label: 'Show task', selected: true, to: '/tasklist/$userTaskKey'},
	{key: 'process', title: 'Process', label: 'Show process', selected: false, to: '/tasklist/$userTaskKey/process'},
	{key: 'history', title: 'History', label: 'Show history', selected: false, to: '/tasklist/$userTaskKey/history'},
];

describe('<TabListNav />', () => {
	it('should render all provided tabs', async () => {
		const screen = await renderWithRouter(() => <TabListNav label="Task Details Navigation" items={mockTabs} />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/123',
		});

		await expect.element(screen.getByRole('link', {name: 'Show task'})).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'Show process'})).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'Show history'})).toBeVisible();
	});

	it('should mark currently selected tab', async () => {
		const screen = await renderWithRouter(() => <TabListNav label="Task Details Navigation" items={mockTabs} />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/123',
		});

		await expect.element(screen.getByRole('link', {name: 'Show task'})).toHaveAttribute('aria-current', 'page');
		await expect.element(screen.getByRole('link', {name: 'Show process'})).not.toHaveAttribute('aria-current');
	});

	it('should hide tabs', async () => {
		const tabsWithHidden: TabItem[] = [
			{key: 'task', title: 'Task', label: 'Show task', selected: true, to: '/tasklist/$userTaskKey'},
			{
				key: 'hidden',
				title: 'Hidden',
				label: 'Hidden tab',
				selected: false,
				to: '/tasklist/$userTaskKey/process',
				visible: false,
			},
		];

		const screen = await renderWithRouter(() => <TabListNav label="Task Details Navigation" items={tabsWithHidden} />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/123',
		});

		await expect.element(screen.getByRole('link', {name: 'Show task'})).toBeVisible();
		await expect.element(screen.getByLabelText('Hidden tab')).toHaveAttribute('aria-hidden', 'true');
		await expect.element(screen.getByLabelText('Hidden tab')).toHaveAttribute('hidden', '');
	});

	it('should navigate to the tab route on click', async () => {
		const {router, ...screen} = await renderWithRouter(
			() => <TabListNav label="Task Details Navigation" items={mockTabs} />,
			{path: '/tasklist/$userTaskKey', initialEntry: '/tasklist/123'},
		);

		await userEvent.click(screen.getByRole('link', {name: 'Show process'}));

		expect(router.state.location.pathname).toContain('/process');
	});
});
