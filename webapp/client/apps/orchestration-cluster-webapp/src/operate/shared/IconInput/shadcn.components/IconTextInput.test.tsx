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
import {Calendar} from '@camunda/design-system/icons';
import {it} from '#/vitest-modules/test-extend';
import {IconTextInput} from './IconTextInput';

describe('<IconTextInput />', () => {
	it('should render the trigger button labelled by the field label', async () => {
		const screen = await render(
			<IconTextInput
				id="date"
				labelText="Date"
				value=""
				Icon={Calendar}
				onIconClick={vi.fn()}
				buttonLabel="Open calendar"
			/>,
		);

		await expect.element(screen.getByRole('button', {name: 'Date'})).toBeVisible();
	});

	it('should call onIconClick when the trigger button is clicked', async () => {
		const onIconClick = vi.fn();
		const screen = await render(
			<IconTextInput
				id="date"
				labelText="Date"
				value=""
				Icon={Calendar}
				onIconClick={onIconClick}
				buttonLabel="Open calendar"
			/>,
		);

		await userEvent.click(screen.getByRole('button', {name: 'Date'}));

		expect(onIconClick).toHaveBeenCalledTimes(1);
	});

	it('should fall back to buttonLabel as the accessible name when no field label is given', async () => {
		const screen = await render(
			<IconTextInput id="date" value="" Icon={Calendar} onIconClick={vi.fn()} buttonLabel="Open calendar" />,
		);

		await expect.element(screen.getByRole('button', {name: 'Open calendar'})).toBeVisible();
	});

	it('should show the invalid state and message', async () => {
		const screen = await render(
			<IconTextInput
				id="date"
				labelText="Date"
				value=""
				Icon={Calendar}
				onIconClick={vi.fn()}
				buttonLabel="Open calendar"
				invalid
				invalidText="Invalid date"
			/>,
		);

		await expect.element(screen.getByText('Invalid date')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Date'})).toHaveAttribute('aria-invalid', 'true');
	});
});
