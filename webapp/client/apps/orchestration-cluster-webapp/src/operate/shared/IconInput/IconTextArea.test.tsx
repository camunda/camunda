/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, it, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {Calendar} from '@carbon/react/icons';
import {IconTextArea} from './IconTextArea';

describe('<IconTextArea />', () => {
	it('should render the text area and icon button', async () => {
		const screen = await render(
			<IconTextArea id="date" labelText="Date" Icon={Calendar} onIconClick={vi.fn()} buttonLabel="Open calendar" />,
		);

		await expect.element(screen.getByLabelText('Date')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Open calendar'})).toBeVisible();
	});

	it('should call onIconClick when the icon button is clicked', async () => {
		const onIconClick = vi.fn();
		const screen = await render(
			<IconTextArea id="date" labelText="Date" Icon={Calendar} onIconClick={onIconClick} buttonLabel="Open calendar" />,
		);

		await userEvent.click(screen.getByRole('button', {name: 'Open calendar'}).element());

		expect(onIconClick).toHaveBeenCalledTimes(1);
	});

	it('should show the invalid state and message', async () => {
		const screen = await render(
			<IconTextArea
				id="date"
				labelText="Date"
				Icon={Calendar}
				onIconClick={vi.fn()}
				buttonLabel="Open calendar"
				invalid
				invalidText="Invalid date"
			/>,
		);

		await expect.element(screen.getByText('Invalid date')).toBeVisible();
	});
});
