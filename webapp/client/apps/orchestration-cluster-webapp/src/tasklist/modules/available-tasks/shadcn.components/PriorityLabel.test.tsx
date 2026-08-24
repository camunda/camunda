/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {TooltipProvider} from '@camunda/design-system';
import {render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {describe, expect} from 'vitest';
import {PriorityLabel} from './PriorityLabel';

const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => <TooltipProvider>{children}</TooltipProvider>;

describe('<PriorityLabel />', () => {
	it('should render "Critical" short label for priority > 75', async () => {
		const screen = await render(<PriorityLabel priority={80} />, {wrapper: Wrapper});

		await expect.element(screen.getByText('Critical', {exact: true})).toBeVisible();
	});

	it('should render "High" short label for priority 51-75', async () => {
		const screen = await render(<PriorityLabel priority={60} />, {wrapper: Wrapper});

		await expect.element(screen.getByText('High', {exact: true})).toBeVisible();
	});

	it('should render "Medium" short label for priority 26-50', async () => {
		const screen = await render(<PriorityLabel priority={30} />, {wrapper: Wrapper});

		await expect.element(screen.getByText('Medium', {exact: true})).toBeVisible();
	});

	it('should render "Low" short label for priority 0-25', async () => {
		const screen = await render(<PriorityLabel priority={10} />, {wrapper: Wrapper});

		await expect.element(screen.getByText('Low', {exact: true})).toBeVisible();
	});

	it('should show "Priority: Critical" as the title attribute for critical priority', async () => {
		const screen = await render(<PriorityLabel priority={80} />, {wrapper: Wrapper});

		await expect.element(screen.getByTitle('Priority: Critical')).toBeVisible();
	});

	it('should show the long "Priority: High" label as tooltip content on hover', async () => {
		const screen = await render(<PriorityLabel priority={60} />, {wrapper: Wrapper});

		await userEvent.hover(screen.getByTitle('Priority: High'));

		await expect.element(screen.getByText('Priority: High')).toBeVisible();
	});
});
