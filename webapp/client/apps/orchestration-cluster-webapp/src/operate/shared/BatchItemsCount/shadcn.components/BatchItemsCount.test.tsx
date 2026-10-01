/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {TooltipProvider} from '@camunda/design-system';
import {describe, expect} from 'vitest';
import {render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {BatchItemsCount} from './BatchItemsCount';

function renderWithTooltipProvider(children: React.ReactElement) {
	return render(<TooltipProvider>{children}</TooltipProvider>);
}

describe('<BatchItemsCount />', () => {
	it('should show a "no items" indicator when there are no items at all', async () => {
		const screen = await renderWithTooltipProvider(
			<BatchItemsCount totalCount={0} completedCount={0} failedCount={0} />,
		);

		await expect.element(screen.getByLabelText('no items')).toBeVisible();
	});

	it('should show a "not started" indicator when items are pending but none have progressed', async () => {
		const screen = await renderWithTooltipProvider(
			<BatchItemsCount totalCount={5} completedCount={0} failedCount={0} />,
		);

		await expect.element(screen.getByLabelText('not started')).toBeVisible();
	});

	it('should show per-status counts once at least one item has progressed', async () => {
		const screen = await renderWithTooltipProvider(
			<BatchItemsCount totalCount={10} completedCount={3} failedCount={1} />,
		);

		await expect.element(screen.getByRole('status', {name: '3 successful'})).toBeVisible();
		await expect.element(screen.getByRole('status', {name: '1 failed'})).toBeVisible();
		await expect.element(screen.getByRole('status', {name: '6 pending'})).toBeVisible();
	});

	it('should show the failure reason in a tooltip on hover', async () => {
		const screen = await renderWithTooltipProvider(
			<BatchItemsCount totalCount={10} completedCount={3} failedCount={1} />,
		);

		await expect.element(screen.getByRole('tooltip')).not.toBeInTheDocument();

		await userEvent.hover(screen.getByRole('status', {name: '1 failed'}));

		await expect.element(screen.getByRole('tooltip')).toMatchTextContent('1 failed');
	});

	it('should format large counts using compact notation', async () => {
		const screen = await renderWithTooltipProvider(
			<BatchItemsCount totalCount={1_500} completedCount={1_200} failedCount={200} />,
		);

		await expect.element(screen.getByText('1.2K')).toBeVisible();
		await expect.element(screen.getByText('200')).toBeVisible();
		await expect.element(screen.getByText('100')).toBeVisible();
	});
});
