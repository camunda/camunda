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
import {LabelWithPopover} from './LabelWithPopover';

describe('<LabelWithPopover />', () => {
	it('should render label', async () => {
		const screen = await render(
			<LabelWithPopover title="hover title" popoverContent={<span>Popover content</span>} align="top-start">
				Label text
			</LabelWithPopover>,
		);

		await expect.element(screen.getByText('Label text')).toBeVisible();
	});

	it('should have the title attribute on the label', async () => {
		const screen = await render(
			<LabelWithPopover title="hover title" popoverContent={<span>Details</span>} align="top-start">
				Label text
			</LabelWithPopover>,
		);

		await expect.element(screen.getByTitle('hover title')).toBeVisible();
	});

	it('should show popover content on mouse enter', async () => {
		const screen = await render(
			<LabelWithPopover title="hover title" popoverContent={<span>Popover details</span>} align="top-start">
				Label text
			</LabelWithPopover>,
		);

		await userEvent.hover(screen.getByTitle('hover title'));

		await expect.element(screen.getByTestId('label-with-popover-content')).toBeVisible();
	});

	it('should hide popover content on mouse leave', async () => {
		const screen = await render(
			<LabelWithPopover title="hover title" popoverContent={<span>Popover details</span>} align="top-start">
				Label text
			</LabelWithPopover>,
		);

		await userEvent.hover(screen.getByTitle('hover title'));
		await expect.element(screen.getByTestId('label-with-popover-content')).toBeVisible();

		// Radix keeps the tooltip open if the pointer moves from the trigger
		// straight into the content region (deliberate "hoverable content"
		// behavior) — unhover the trigger, then move the pointer somewhere
		// definitely outside both, or Radix never registers the pointer as
		// having left.
		await userEvent.unhover(screen.getByTitle('hover title'));
		await userEvent.hover(screen.baseElement);

		await expect.element(screen.getByTestId('label-with-popover-content')).not.toBeInTheDocument();
	});
});
