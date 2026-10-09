/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import axe from 'axe-core';
import {Form} from 'react-final-form';
import {render} from 'vitest-browser-react';
import {describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {formatISODate} from '#/operate/shared/DateRangeField/formatDate';
import {DateTimeRangeField} from './DateTimeRangeField';

type Values = {from?: string; to?: string};

const FROM = formatISODate(new Date(2026, 9, 7, 8, 30, 15));
const TO = formatISODate(new Date(2026, 9, 14, 17, 45, 0));

const Harness: React.FC<{initialValues?: Values; onSubmit?: (values: Values) => void}> = ({
	initialValues = {},
	onSubmit = vi.fn(),
}) => (
	<Form<Values> onSubmit={onSubmit} initialValues={initialValues}>
		{({handleSubmit}) => (
			<form onSubmit={handleSubmit}>
				<button type="submit">submit</button>
				<DateTimeRangeField idPrefix="range" fromKey="from" toKey="to" />
			</form>
		)}
	</Form>
);

const submitted = async (onSubmit: ReturnType<typeof vi.fn>, screen: Awaited<ReturnType<typeof render>>) => {
	await userEvent.click(screen.getByRole('button', {name: 'submit'}));
	return onSubmit.mock.calls.at(-1)?.[0] as Values;
};

describe('<DateTimeRangeField /> (design system)', () => {
	describe('rendering', () => {
		it('should render labelled From, To and Quick ranges controls', async () => {
			const screen = await render(<Harness />);

			await expect.element(screen.getByRole('textbox', {name: 'From'})).toBeVisible();
			await expect.element(screen.getByRole('textbox', {name: 'To'})).toBeVisible();
			await expect.element(screen.getByRole('combobox', {name: 'Quick ranges'})).toBeVisible();
			await expect.element(screen.getByRole('textbox', {name: 'From'})).toHaveAttribute('aria-invalid', 'false');
		});

		it('should show existing values formatted in local time', async () => {
			const screen = await render(<Harness initialValues={{from: FROM, to: TO}} />);

			await expect.element(screen.getByRole('textbox', {name: 'From'})).toHaveValue('2026-10-07 08:30:15');
			await expect.element(screen.getByRole('textbox', {name: 'To'})).toHaveValue('2026-10-14 17:45:00');
		});

		it('should not open the picker on initial render', async () => {
			const screen = await render(<Harness />);

			await expect.element(screen.getByRole('grid')).not.toBeInTheDocument();
		});
	});

	describe('typing', () => {
		it('should commit a valid value as an ISO date time', async () => {
			const onSubmit = vi.fn();
			const screen = await render(<Harness onSubmit={onSubmit} />);

			await userEvent.fill(screen.getByRole('textbox', {name: 'From'}), '2026-10-07 08:30:15');

			expect((await submitted(onSubmit, screen)).from).toBe(FROM);
		});

		it('should clear the value when the field is emptied', async () => {
			const onSubmit = vi.fn();
			const screen = await render(<Harness initialValues={{from: FROM, to: TO}} onSubmit={onSubmit} />);

			await userEvent.clear(screen.getByRole('textbox', {name: 'From'}));

			const values = await submitted(onSubmit, screen);
			expect(values.from).toBeUndefined();
			expect(values.to).toBe(TO);
		});

		it.for(['not a date', '2026-13-45 99:00:00', '2026-10-07', '2026-10-07 08:30', '07.10.2026 08:30:15'])(
			'should show an error and keep the previous value for malformed text "%s"',
			async (text) => {
				const onSubmit = vi.fn();
				const screen = await render(<Harness initialValues={{from: FROM, to: TO}} onSubmit={onSubmit} />);
				const from = screen.getByRole('textbox', {name: 'From'});

				await userEvent.fill(from, text);
				await userEvent.tab();

				await expect.element(from).toHaveAttribute('aria-invalid', 'true');
				await expect.element(screen.getByText('Enter a date and time as YYYY-MM-DD HH:mm:ss')).toBeVisible();
				expect((await submitted(onSubmit, screen)).from).toBe(FROM);
			},
		);

		it('should not show the error while the field is still focused', async () => {
			const screen = await render(<Harness />);
			const from = screen.getByRole('textbox', {name: 'From'});

			await userEvent.fill(from, 'not a date');

			await expect.element(from).toHaveAttribute('aria-invalid', 'false');
		});

		it('should clear the error once the text is corrected', async () => {
			const onSubmit = vi.fn();
			const screen = await render(<Harness onSubmit={onSubmit} />);
			const from = screen.getByRole('textbox', {name: 'From'});

			await userEvent.fill(from, 'not a date');
			await userEvent.tab();
			await userEvent.fill(from, '2026-10-07 08:30:15');
			await userEvent.tab();

			await expect.element(from).toHaveAttribute('aria-invalid', 'false');
			expect((await submitted(onSubmit, screen)).from).toBe(FROM);
		});

		it('should require the other end of the range', async () => {
			const screen = await render(<Harness initialValues={{from: FROM}} />);

			await expect.element(screen.getByRole('textbox', {name: 'To'})).toHaveAttribute('aria-invalid', 'true');
			await expect.element(screen.getByRole('textbox', {name: 'From'})).toHaveAttribute('aria-invalid', 'false');

			await userEvent.fill(screen.getByRole('textbox', {name: 'To'}), '2026-10-14 17:45:00');
			await userEvent.tab();

			await expect.element(screen.getByRole('textbox', {name: 'To'})).toHaveAttribute('aria-invalid', 'false');
		});

		it('should not require anything when both ends are empty', async () => {
			const screen = await render(<Harness />);

			await expect.element(screen.getByRole('textbox', {name: 'From'})).toHaveAttribute('aria-invalid', 'false');
			await expect.element(screen.getByRole('textbox', {name: 'To'})).toHaveAttribute('aria-invalid', 'false');
		});
	});

	describe('picker', () => {
		it('should open on focus and on click', async () => {
			const screen = await render(<Harness />);

			await userEvent.click(screen.getByRole('textbox', {name: 'From'}));

			await expect.element(screen.getByRole('grid')).toBeVisible();
		});

		it('should highlight the selected day of an existing value', async () => {
			const screen = await render(<Harness initialValues={{from: FROM, to: TO}} />);

			await userEvent.click(screen.getByRole('textbox', {name: 'From'}));

			await expect
				.element(screen.getByRole('gridcell', {name: /October 7th, 2026/}))
				.toHaveAttribute('aria-selected', 'true');
		});

		it('should pick a day and keep the existing time', async () => {
			const onSubmit = vi.fn();
			const screen = await render(<Harness initialValues={{from: FROM, to: TO}} onSubmit={onSubmit} />);

			await userEvent.click(screen.getByRole('textbox', {name: 'From'}));
			await userEvent.click(screen.getByRole('button', {name: /October 9th, 2026/}));

			await expect.element(screen.getByRole('textbox', {name: 'From'})).toHaveValue('2026-10-09 08:30:15');
			expect((await submitted(onSubmit, screen)).from).toBe(formatISODate(new Date(2026, 9, 9, 8, 30, 15)));
		});

		it('should default the time to the start of the day for From and the end of the day for To', async () => {
			const screen = await render(<Harness />);

			await userEvent.click(screen.getByRole('textbox', {name: 'From'}));
			await userEvent.click(screen.getByRole('gridcell', {name: /15/}).getByRole('button').first());
			await userEvent.click(screen.getByRole('textbox', {name: 'To'}));
			await userEvent.click(screen.getByRole('gridcell', {name: /16/}).getByRole('button').first());

			await expect
				.element(screen.getByRole('textbox', {name: 'From'}))
				.toHaveValue(expect.stringMatching(/ 00:00:00$/));
			await expect.element(screen.getByRole('textbox', {name: 'To'})).toHaveValue(expect.stringMatching(/ 23:59:59$/));
		});

		it('should update only the time of an existing value', async () => {
			const onSubmit = vi.fn();
			const screen = await render(<Harness initialValues={{from: FROM, to: TO}} onSubmit={onSubmit} />);

			await userEvent.click(screen.getByRole('textbox', {name: 'From'}));
			await userEvent.fill(screen.getByLabelText('Time'), '10:11:12');

			await expect.element(screen.getByRole('textbox', {name: 'From'})).toHaveValue('2026-10-07 10:11:12');
			expect((await submitted(onSubmit, screen)).from).toBe(formatISODate(new Date(2026, 9, 7, 10, 11, 12)));
		});

		it('should use today when a time is chosen before any date', async () => {
			const screen = await render(<Harness />);

			await userEvent.click(screen.getByRole('textbox', {name: 'From'}));
			await userEvent.fill(screen.getByLabelText('Time'), '10:11:12');

			await expect
				.element(screen.getByRole('textbox', {name: 'From'}))
				.toHaveValue(expect.stringMatching(/ 10:11:12$/));
		});

		it('should stay closed after picking a date and clicking outside', async () => {
			const screen = await render(<Harness />);

			await userEvent.click(screen.getByRole('textbox', {name: 'From'}));
			await userEvent.click(screen.getByRole('gridcell', {name: /15/}).getByRole('button').first());
			await userEvent.click(screen.getByText('Quick ranges'));

			await expect.element(screen.getByRole('grid')).not.toBeInTheDocument();
		});

		it('should close on Escape and keep focus in the input', async () => {
			const screen = await render(<Harness />);
			const from = screen.getByRole('textbox', {name: 'From'});

			await userEvent.click(from);
			await userEvent.keyboard('{Escape}');

			await expect.element(screen.getByRole('grid')).not.toBeInTheDocument();
			await expect.element(from).toHaveFocus();
		});

		it('should not close when clicking inside the input', async () => {
			const screen = await render(<Harness />);
			const from = screen.getByRole('textbox', {name: 'From'});

			await userEvent.click(from);
			await userEvent.click(from);

			await expect.element(screen.getByRole('grid')).toBeVisible();
		});
	});

	describe('quick ranges', () => {
		it.for([
			{label: 'Last 5 minutes', minutes: 5},
			{label: 'Last 1 hour', minutes: 60},
			{label: 'Last 24 hours', minutes: 1440},
			{label: 'Last 2 days', minutes: 2880},
			{label: 'Last 30 days', minutes: 43200},
		])('should fill the range for "$label"', async ({label, minutes}) => {
			const onSubmit = vi.fn();
			const screen = await render(<Harness onSubmit={onSubmit} />);

			await userEvent.click(screen.getByRole('combobox', {name: 'Quick ranges'}));
			await userEvent.click(screen.getByRole('option', {name: label}));

			const {from, to} = await submitted(onSubmit, screen);
			const rangeMs = new Date(to as string).getTime() - new Date(from as string).getTime();
			expect(Math.abs(rangeMs - minutes * 60_000)).toBeLessThanOrEqual(1000);
			expect(Math.abs(Date.now() - new Date(to as string).getTime())).toBeLessThan(10_000);
		});

		it('should list the quick ranges in singular and plural form', async () => {
			const screen = await render(<Harness />);

			await userEvent.click(screen.getByRole('combobox', {name: 'Quick ranges'}));

			await expect.element(screen.getByRole('option', {name: 'Last 1 hour'})).toBeVisible();
			await expect.element(screen.getByRole('option', {name: 'Last 3 hours'})).toBeVisible();
		});

		it('should reset the selection after a manual edit', async () => {
			const screen = await render(<Harness />);

			await userEvent.click(screen.getByRole('combobox', {name: 'Quick ranges'}));
			await userEvent.click(screen.getByRole('option', {name: 'Last 1 hour'}));
			await expect.element(screen.getByRole('combobox', {name: 'Quick ranges'})).toHaveTextContent('Last 1 hour');

			await userEvent.fill(screen.getByRole('textbox', {name: 'From'}), '2026-10-07 08:30:15');

			await expect
				.element(screen.getByRole('combobox', {name: 'Quick ranges'}))
				.toHaveTextContent('Select a quick range');
		});

		it('should replace malformed text and clear the error', async () => {
			const screen = await render(<Harness />);
			const from = screen.getByRole('textbox', {name: 'From'});

			await userEvent.fill(from, 'not a date');
			await userEvent.tab();
			await userEvent.click(screen.getByRole('combobox', {name: 'Quick ranges'}));
			await userEvent.click(screen.getByRole('option', {name: 'Last 1 hour'}));

			await expect.element(from).toHaveAttribute('aria-invalid', 'false');
			await expect.element(from).not.toHaveValue('not a date');
		});

		it('should resolve a one-sided range error', async () => {
			const screen = await render(<Harness initialValues={{from: FROM}} />);

			await userEvent.click(screen.getByRole('combobox', {name: 'Quick ranges'}));
			await userEvent.click(screen.getByRole('option', {name: 'Last 1 hour'}));

			await expect.element(screen.getByRole('textbox', {name: 'To'})).toHaveAttribute('aria-invalid', 'false');
		});
	});

	describe('accessibility', () => {
		it('should associate the error message with the invalid input', async () => {
			const screen = await render(<Harness initialValues={{from: FROM}} />);
			const to = screen.getByRole('textbox', {name: 'To'});

			await expect.element(to).toHaveAccessibleDescription('Enter a date and time');
			await expect.element(to).toHaveAccessibleErrorMessage('Enter a date and time');
		});

		it('should announce validation errors to assistive technology', async () => {
			const screen = await render(<Harness initialValues={{from: FROM}} />);

			await expect.element(screen.getByRole('alert')).toHaveTextContent('Enter a date and time');
		});

		it('should expose the picker popover to keyboard users via ArrowDown', async () => {
			const screen = await render(<Harness initialValues={{from: FROM, to: TO}} />);

			await userEvent.click(screen.getByRole('textbox', {name: 'From'}));
			await userEvent.keyboard('{ArrowDown}');

			await expect.element(screen.getByRole('button', {name: /October 7th, 2026/})).toHaveFocus();
		});

		it('should let keyboard users reach the time field from the calendar', async () => {
			const screen = await render(<Harness initialValues={{from: FROM, to: TO}} />);

			await userEvent.click(screen.getByRole('textbox', {name: 'From'}));
			await userEvent.keyboard('{ArrowDown}');
			await userEvent.tab();
			await userEvent.tab();
			await userEvent.tab();
			await userEvent.tab();
			await userEvent.tab();

			await expect.element(screen.getByLabelText('Time')).toBeVisible();
		});

		it('should let keyboard users pick a quick range', async () => {
			const screen = await render(<Harness />);

			await userEvent.click(screen.getByRole('textbox', {name: 'To'}));
			await userEvent.keyboard('{Escape}');
			await userEvent.tab();
			await userEvent.keyboard('{Enter}');
			await userEvent.keyboard('{ArrowDown}{Enter}');

			await expect.element(screen.getByRole('textbox', {name: 'From'})).not.toHaveValue('');
		});

		it('should have no axe violations with the picker closed', async () => {
			await render(<Harness initialValues={{from: FROM, to: TO}} />);

			const {violations} = await axe.run(document.body, {rules: {region: {enabled: false}}});

			expect(violations.map(({id, nodes}) => `${id}: ${nodes.map(({html}) => html).join(' | ')}`)).toEqual([]);
		});

		it('should have no axe violations with errors shown', async () => {
			await render(<Harness initialValues={{from: FROM}} />);

			const {violations} = await axe.run(document.body, {rules: {region: {enabled: false}}});

			expect(violations.map(({id, nodes}) => `${id}: ${nodes.map(({html}) => html).join(' | ')}`)).toEqual([]);
		});

		it('should have no axe violations with the picker open', async () => {
			const screen = await render(<Harness initialValues={{from: FROM, to: TO}} />);

			await userEvent.click(screen.getByRole('textbox', {name: 'From'}));
			await expect.element(screen.getByRole('grid')).toBeVisible();
			const {violations} = await axe.run(document.body, {rules: {region: {enabled: false}}});

			expect(violations.map(({id, nodes}) => `${id}: ${nodes.map(({html}) => html).join(' | ')}`)).toEqual([]);
		});
	});
});
