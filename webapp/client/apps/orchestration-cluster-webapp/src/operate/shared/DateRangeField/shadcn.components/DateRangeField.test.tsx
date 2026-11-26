/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Form} from 'react-final-form';
import {render, type RenderResult} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {describe, expect, vi, afterEach} from 'vitest';
import {userEvent} from 'vitest/browser';
import {format} from 'date-fns';
import {MockDateRangeField} from './mocks';

function getWrapper(initialValues?: {[key: string]: string}) {
	const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
		return (
			<>
				<Form onSubmit={() => {}} initialValues={initialValues}>
					{() => children}
				</Form>
				<div>Outside element</div>
			</>
		);
	};

	return Wrapper;
}

const pad = (value: string | number) => {
	return String(value).padStart(2, '0');
};

// react-day-picker labels calendar day buttons with the full formatted date
// ("Wednesday, October 1st, 2025"), prefixed with "Today, " for the current
// day. Matching with an unanchored regex finds the button regardless of that
// prefix, instead of having to special-case "today" in every test.
function dayButtonName(date: Date) {
	return new RegExp(format(date, 'PPPP'));
}

async function pickDateTimeRange({screen, fromDay, toDay}: {screen: RenderResult; fromDay: number; toDay: number}) {
	await expect.element(screen.getByRole('dialog')).toBeVisible();

	const today = new Date();
	const year = today.getFullYear();
	const month = today.getMonth();

	await userEvent.click(screen.getByRole('button', {name: dayButtonName(new Date(year, month, fromDay))}));
	await userEvent.click(screen.getByRole('button', {name: dayButtonName(new Date(year, month, toDay))}));

	return {
		fromDay: pad(fromDay),
		toDay: pad(toDay),
		month: pad(month + 1),
		year: String(year),
	};
}

async function applyDateRange(screen: RenderResult) {
	const applyButton = screen.getByRole('button', {name: 'Apply'});
	await expect.element(applyButton).not.toBeDisabled();
	await userEvent.click(applyButton);
	await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
}

describe('<DateRangeField />', () => {
	afterEach(() => {
		vi.useRealTimers();
	});

	it('should close modal on cancel click', async () => {
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});

		await expect.element(screen.getByLabelText('Start Date Range')).toBeVisible();
		await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();

		await userEvent.click(screen.getByLabelText('Start Date Range'));
		await expect.element(screen.getByRole('dialog')).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Cancel'}));
		await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
	});

	it('should pick from and to dates and times', async () => {
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});

		await userEvent.click(screen.getByLabelText('Start Date Range'));

		const fromTime = '11:22:33';
		const toTime = '08:59:59';
		const {year, month, fromDay, toDay} = await pickDateTimeRange({
			screen,
			fromDay: 10,
			toDay: 20,
		});
		await userEvent.fill(screen.getByLabelText('From time'), fromTime);
		await userEvent.fill(screen.getByLabelText('To time'), toTime);
		await applyDateRange(screen);

		await expect
			.element(screen.getByLabelText('Start Date Range'))
			.toHaveTextContent(`${year}-${month}-${fromDay} ${fromTime} - ${year}-${month}-${toDay} ${toTime}`);
	});

	it.for([
		{segment: 'hour', keys: '{Home}{ArrowUp}', expected: '12:59:00'},
		{segment: 'minute', keys: '{Home}{ArrowRight}{ArrowRight}{ArrowRight}{ArrowUp}', expected: '11:00:00'},
		{segment: 'second', keys: '{End}{ArrowDown}', expected: '11:59:59'},
	])('should step the $segment segment with the arrow keys', async ({keys, expected}) => {
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});
		await userEvent.click(screen.getByLabelText('Start Date Range'));
		const fromTime = screen.getByLabelText('From time');
		await userEvent.fill(fromTime, '11:59:00');
		await expect.element(fromTime).toHaveFocus();

		await userEvent.keyboard(keys);

		await expect.element(fromTime).toHaveValue(expected);
	});

	it('should keep partially typed time when stepping with the arrow keys', async () => {
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});
		await userEvent.click(screen.getByLabelText('Start Date Range'));
		const fromTime = screen.getByLabelText('From time');
		await userEvent.fill(fromTime, '11:5');

		await userEvent.keyboard('{ArrowUp}');

		await expect.element(fromTime).toHaveValue('11:5');
	});

	it('should restore previous date on cancel', async () => {
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});

		await userEvent.click(screen.getByLabelText('Start Date Range'));
		await expect.element(screen.getByLabelText('Start Date Range')).toHaveTextContent('Custom');

		const {year, month, fromDay, toDay} = await pickDateTimeRange({
			screen,
			fromDay: 10,
			toDay: 20,
		});
		await applyDateRange(screen);

		const expectedValue = `${year}-${month}-${fromDay} 00:00:00 - ${year}-${month}-${toDay} 23:59:59`;
		await expect.element(screen.getByLabelText('Start Date Range')).toHaveTextContent(expectedValue);

		await userEvent.click(screen.getByLabelText('Start Date Range'));
		await expect.element(screen.getByLabelText('Start Date Range')).toHaveTextContent('Custom');

		await userEvent.click(screen.getByRole('button', {name: 'Cancel'}));
		await expect.element(screen.getByLabelText('Start Date Range')).toHaveTextContent(expectedValue);
	});

	it('should set default values', async () => {
		const screen = await render(<MockDateRangeField />, {
			wrapper: getWrapper({
				startDateFrom: '2021-02-03T12:34:56',
				startDateTo: '2021-02-06T01:02:03',
			}),
		});

		await expect
			.element(screen.getByLabelText('Start Date Range'))
			.toHaveTextContent('2021-02-03 12:34:56 - 2021-02-06 01:02:03');

		await userEvent.click(screen.getByLabelText('Start Date Range'));

		await expect.element(screen.getByLabelText('From date')).toHaveValue('2021-02-03');
		await expect.element(screen.getByLabelText('From time')).toHaveValue('12:34:56');
		await expect.element(screen.getByLabelText('To date')).toHaveValue('2021-02-06');
		await expect.element(screen.getByLabelText('To time')).toHaveValue('01:02:03');
	});

	it('should apply from and to dates typed directly', async () => {
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});

		await userEvent.click(screen.getByLabelText('Start Date Range'));
		await userEvent.fill(screen.getByLabelText('From time'), '12:30:00');
		await userEvent.fill(screen.getByLabelText('To time'), '17:15:00');
		await userEvent.fill(screen.getByLabelText('From date'), '2022-01-01');
		await userEvent.fill(screen.getByLabelText('To date'), '2022-12-01');
		await applyDateRange(screen);

		await expect
			.element(screen.getByLabelText('Start Date Range'))
			.toHaveTextContent('2022-01-01 12:30:00 - 2022-12-01 17:15:00');
	});

	it('should show validation error on invalid character', async () => {
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});
		const TIME_ERROR = 'Time has to be in the format hh:mm:ss';

		await userEvent.click(screen.getByLabelText('Start Date Range'));

		await pickDateTimeRange({
			screen,
			fromDay: 10,
			toDay: 20,
		});

		await expect.element(screen.getByLabelText('From time')).not.toBeInvalid();
		await expect.element(screen.getByText(TIME_ERROR)).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Apply'})).not.toBeDisabled();

		await userEvent.fill(screen.getByLabelText('From time'), '12:30:xx');

		await expect.element(screen.getByLabelText('From time')).toBeInvalid();
		await expect.element(screen.getByText(TIME_ERROR)).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Apply'})).toBeDisabled();
	});

	it('should show validation error on invalid time format', async () => {
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});
		const TIME_ERROR = 'Time has to be in the format hh:mm:ss';

		await userEvent.click(screen.getByLabelText('Start Date Range'));

		await pickDateTimeRange({
			screen,
			fromDay: 10,
			toDay: 20,
		});

		vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout']});
		await userEvent.fill(screen.getByLabelText('From time'), '1111');

		await vi.advanceTimersByTimeAsync(749);
		await expect.element(screen.getByLabelText('From time')).not.toBeInvalid();
		await expect.element(screen.getByText(TIME_ERROR)).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Apply'})).not.toBeDisabled();

		await vi.advanceTimersByTimeAsync(1);
		vi.useRealTimers();

		await expect.element(screen.getByText(TIME_ERROR)).toBeVisible();
		await expect.element(screen.getByLabelText('From time')).toBeInvalid();
		await expect.element(screen.getByRole('button', {name: 'Apply'})).toBeDisabled();
	});

	it('should show validation error and disable apply on a calendar-invalid typed date', async () => {
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});

		await userEvent.click(screen.getByLabelText('Start Date Range'));
		await userEvent.fill(screen.getByLabelText('From date'), '2022-02-31');
		await userEvent.click(screen.getByLabelText('From date'));
		await userEvent.fill(screen.getByLabelText('To date'), '2022-12-01');
		await userEvent.click(screen.getByLabelText('To date'));

		await expect.element(screen.getByLabelText('From date')).toBeInvalid();
		await expect.element(screen.getByRole('button', {name: 'Apply'})).toBeDisabled();
	});
});
