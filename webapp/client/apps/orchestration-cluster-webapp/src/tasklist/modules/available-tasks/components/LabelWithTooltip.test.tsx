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
import {LabelWithTooltip} from './LabelWithTooltip';

const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => <TooltipProvider>{children}</TooltipProvider>;

describe('<LabelWithTooltip />', () => {
	it('should render label', async () => {
		const screen = await render(
			<LabelWithTooltip title="hover title" content={<span>Tooltip content</span>} align="top-start">
				Label text
			</LabelWithTooltip>,
			{wrapper: Wrapper},
		);

		await expect.element(screen.getByText('Label text')).toBeVisible();
	});

	it('should have the title attribute on the label', async () => {
		const screen = await render(
			<LabelWithTooltip title="hover title" content={<span>Details</span>} align="top-start">
				Label text
			</LabelWithTooltip>,
			{wrapper: Wrapper},
		);

		await expect.element(screen.getByTitle('hover title')).toBeVisible();
	});

	it('should show tooltip content on mouse enter', async () => {
		const screen = await render(
			<LabelWithTooltip title="hover title" content={<span>Tooltip details</span>} align="top-start">
				Label text
			</LabelWithTooltip>,
			{wrapper: Wrapper},
		);

		await userEvent.hover(screen.getByTitle('hover title'));

		await expect.element(screen.getByRole('tooltip', {name: 'Tooltip details'})).toBeVisible();
	});

	it('should hide tooltip content on mouse leave', async () => {
		const screen = await render(
			<LabelWithTooltip title="hover title" content={<span>Tooltip details</span>} align="top-start">
				Label text
			</LabelWithTooltip>,
			{wrapper: Wrapper},
		);

		await userEvent.hover(screen.getByTitle('hover title'));
		await expect.element(screen.getByRole('tooltip', {name: 'Tooltip details'})).toBeVisible();

		await userEvent.unhover(screen.getByTitle('hover title'));
		await userEvent.hover(document.body);

		await expect.element(screen.getByTitle('hover title')).toHaveAttribute('data-state', 'closed');
	});
});
