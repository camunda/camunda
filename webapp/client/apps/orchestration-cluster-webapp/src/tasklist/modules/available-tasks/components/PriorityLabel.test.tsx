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
import {PriorityLabel} from './PriorityLabel';

describe('<PriorityLabel />', () => {
	it('should render "Critical" short label for priority > 75', async () => {
		const screen = await render(<PriorityLabel priority={80} />);

		await expect.element(screen.getByText('Critical', {exact: true})).toBeVisible();
	});

	it('should render "High" short label for priority 51-75', async () => {
		const screen = await render(<PriorityLabel priority={60} />);

		await expect.element(screen.getByText('High', {exact: true})).toBeVisible();
	});

	it('should render "Medium" short label for priority 26-50', async () => {
		const screen = await render(<PriorityLabel priority={30} />);

		await expect.element(screen.getByText('Medium', {exact: true})).toBeVisible();
	});

	it('should render "Low" short label for priority 0-25', async () => {
		const screen = await render(<PriorityLabel priority={10} />);

		await expect.element(screen.getByText('Low', {exact: true})).toBeVisible();
	});

	it('should show "Priority: Critical" as the title attribute for critical priority', async () => {
		const screen = await render(<PriorityLabel priority={80} />);

		await expect.element(screen.getByTitle('Priority: Critical')).toBeVisible();
	});

	it('should show the long "Priority: High" label as popover content on hover', async () => {
		const screen = await render(<PriorityLabel priority={60} />);

		await userEvent.hover(screen.getByTitle('Priority: High'));

		await expect.element(screen.getByText('Priority: High')).toBeVisible();
	});
});
