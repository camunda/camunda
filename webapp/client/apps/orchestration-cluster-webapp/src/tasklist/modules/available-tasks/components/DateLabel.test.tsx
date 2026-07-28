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
import {DateLabel} from './DateLabel';

const todayDate = {
	date: new Date(2024, 0, 12, 10, 30, 0),
	relative: {resolution: 'days' as const, text: 'Today', speech: 'today at 10:30'},
	absolute: {text: '12 Jan 2024'},
};

const weekDate = {
	date: new Date(2024, 0, 8, 12, 0, 0),
	relative: {resolution: 'week' as const, text: 'Monday', speech: 'Monday'},
	absolute: {text: '8 Jan 2024'},
};

const monthsDate = {
	date: new Date(2024, 0, 6, 12, 0, 0),
	relative: {resolution: 'months' as const, text: '6 Jan', speech: '6th of January'},
	absolute: {text: '6 Jan 2024'},
};

const yearsDate = {
	date: new Date(2023, 11, 31, 12, 0, 0),
	relative: {resolution: 'years' as const, text: '31 Dec 2023', speech: '31st of December, 2023'},
	absolute: {text: '31 Dec 2023'},
};

describe('<DateLabel />', () => {
	it('should render the relative date text', async () => {
		const screen = await render(<DateLabel date={monthsDate} relativeLabel="Created" absoluteLabel="Created on" />);

		await expect.element(screen.getByText('6 Jan', {exact: true})).toBeVisible();
	});

	it.for([
		{name: 'week', fixture: weekDate, expectedTitle: 'Created on Monday'},
		{name: 'months', fixture: monthsDate, expectedTitle: 'Created on 6th of January'},
		{name: 'years', fixture: yearsDate, expectedTitle: 'Created on 31st of December, 2023'},
	])('should use absoluteLabel + speech as the title for "$name" resolution', async ({fixture, expectedTitle}) => {
		const screen = await render(<DateLabel date={fixture} relativeLabel="Created" absoluteLabel="Created on" />);

		await expect.element(screen.getByTitle(expectedTitle)).toBeVisible();
	});

	it('should use relativeLabel + speech as the title for non-week/month/year resolutions', async () => {
		const screen = await render(<DateLabel date={todayDate} relativeLabel="Created" absoluteLabel="Created on" />);

		await expect.element(screen.getByTitle('Created today at 10:30')).toBeVisible();
	});

	it('should show popover with absolute date on hover and hide it on unhover', async () => {
		const screen = await render(<DateLabel date={monthsDate} relativeLabel="Created" absoluteLabel="Created on" />);

		// Radix Tooltip content unmounts entirely while closed (Portal-based, no
		// forceMount) rather than staying in the DOM hidden via CSS, so it's not
		// just "not visible" pre-hover/post-unhover — it isn't in the document at all.
		await expect.element(screen.getByText('Created on')).not.toBeInTheDocument();
		await expect.element(screen.getByText('6 Jan 2024')).not.toBeInTheDocument();

		await userEvent.hover(screen.getByTitle('Created on 6th of January'));

		// .first(): Radix Tooltip nests a visually-hidden accessibility announcer
		// with duplicate text inside the real content once opened — .first()
		// resolves to the real (DOM-order-first) content, not the announcer.
		await expect.element(screen.getByText('Created on').first()).toBeVisible();
		await expect.element(screen.getByText('6 Jan 2024').first()).toBeVisible();

		// Radix keeps a tooltip open if the pointer moves from the trigger
		// straight into the content ("hoverable content") — unhover the trigger,
		// then move the pointer somewhere definitely outside both.
		await userEvent.unhover(screen.getByTitle('Created on 6th of January'));
		await userEvent.hover(screen.baseElement);

		await expect.element(screen.getByText('Created on')).not.toBeInTheDocument();
		await expect.element(screen.getByText('6 Jan 2024')).not.toBeInTheDocument();
	});

	it('should render an icon alongside the relative date text when provided', async () => {
		const screen = await render(
			<DateLabel
				date={monthsDate}
				relativeLabel="Created"
				absoluteLabel="Created on"
				icon={<span data-testid="calendar-icon">icon</span>}
			/>,
		);

		await expect.element(screen.getByTestId('calendar-icon')).toBeVisible();
		await expect.element(screen.getByTitle('Created on 6th of January')).toBeVisible();
	});
});
