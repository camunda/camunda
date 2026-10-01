/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, type RenderResult} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {describe, expect, vi, afterEach} from 'vitest';
import {userEvent} from 'vitest/browser';
import {format} from 'date-fns';
import {MockDateRangeField} from './mocks';
import {getWrapper} from '../getWrapper';

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

async function pickDateTimeRange({
	screen,
	fromDay,
	toDay,
	fromTime,
	toTime,
}: {
	screen: RenderResult;
	fromDay: number;
	toDay: number;
	fromTime?: string;
	toTime?: string;
}) {
	await expect.element(screen.getByTestId('date-range-modal')).toBeVisible();

	const today = new Date();
	const year = today.getFullYear();
	const month = today.getMonth();

	await userEvent.click(screen.getByRole('button', {name: dayButtonName(new Date(year, month, fromDay))}));
	await userEvent.click(screen.getByRole('button', {name: dayButtonName(new Date(year, month, toDay))}));

	if (fromTime !== undefined) {
		await userEvent.fill(screen.getByTestId('fromTime'), fromTime);
	}

	if (toTime !== undefined) {
		await userEvent.fill(screen.getByTestId('toTime'), toTime);
	}

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
	await applyButton.click();
	await expect.element(screen.getByTestId('date-range-modal')).not.toBeInTheDocument();
}

describe('<DateRangeField />', () => {
	afterEach(() => {
		vi.useRealTimers();
	});

	it('should close modal on cancel click', async () => {
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});

		await expect.element(screen.getByTestId('date-range-modal')).not.toBeInTheDocument();

		await screen.getByLabelText('Start Date Range').click();
		await expect.element(screen.getByTestId('date-range-modal')).toBeVisible();

		await screen.getByRole('button', {name: 'Cancel'}).click();
		await expect.element(screen.getByTestId('date-range-modal')).not.toBeInTheDocument();
	});

	it('should pick from and to dates and times', async () => {
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});

		await screen.getByLabelText('Start Date Range').click();

		const fromTime = '11:22:33';
		const toTime = '08:59:59';
		const {year, month, fromDay, toDay} = await pickDateTimeRange({
			screen,
			fromDay: 10,
			toDay: 20,
			fromTime,
			toTime,
		});
		await applyDateRange(screen);

		await expect
			.element(screen.getByLabelText('Start Date Range'))
			.toHaveValue(`${year}-${month}-${fromDay} ${fromTime} - ${year}-${month}-${toDay} ${toTime}`);
	});

	it('should restore previous date on cancel', async () => {
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});

		await screen.getByLabelText('Start Date Range').click();
		await expect.element(screen.getByLabelText('Start Date Range')).toHaveValue('Custom');

		const {year, month, fromDay, toDay} = await pickDateTimeRange({
			screen,
			fromDay: 10,
			toDay: 20,
		});
		await applyDateRange(screen);

		const expectedValue = `${year}-${month}-${fromDay} 00:00:00 - ${year}-${month}-${toDay} 23:59:59`;
		await expect.element(screen.getByLabelText('Start Date Range')).toHaveValue(expectedValue);

		await screen.getByLabelText('Start Date Range').click();
		await expect.element(screen.getByLabelText('Start Date Range')).toHaveValue('Custom');

		await screen.getByRole('button', {name: 'Cancel'}).click();
		await expect.element(screen.getByLabelText('Start Date Range')).toHaveValue(expectedValue);
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
			.toHaveValue('2021-02-03 12:34:56 - 2021-02-06 01:02:03');

		await screen.getByLabelText('Start Date Range').click();

		await expect.element(screen.getByLabelText('From date')).toHaveValue('2021-02-03');
		await expect.element(screen.getByTestId('fromTime')).toHaveValue('12:34:56');
		await expect.element(screen.getByLabelText('To date')).toHaveValue('2021-02-06');
		await expect.element(screen.getByTestId('toTime')).toHaveValue('01:02:03');
	});

	it('should apply from and to dates typed directly', async () => {
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});

		await screen.getByLabelText('Start Date Range').click();
		await userEvent.fill(screen.getByTestId('fromTime'), '12:30:00');
		await userEvent.fill(screen.getByTestId('toTime'), '17:15:00');
		await userEvent.fill(screen.getByLabelText('From date'), '2022-01-01');
		await userEvent.fill(screen.getByLabelText('To date'), '2022-12-01');
		await applyDateRange(screen);

		await expect
			.element(screen.getByLabelText('Start Date Range'))
			.toHaveValue('2022-01-01 12:30:00 - 2022-12-01 17:15:00');
	});

	it('should show validation error on invalid character', async () => {
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});
		const TIME_ERROR = 'Time has to be in the format hh:mm:ss';

		await screen.getByLabelText('Start Date Range').click();

		await pickDateTimeRange({
			screen,
			fromDay: 10,
			toDay: 20,
		});

		await expect.element(screen.getByTestId('fromTime')).not.toBeInvalid();
		await expect.element(screen.getByText(TIME_ERROR)).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Apply'})).not.toBeDisabled();

		await userEvent.fill(screen.getByTestId('fromTime'), '12:30:xx');

		await expect.element(screen.getByTestId('fromTime')).toBeInvalid();
		await expect.element(screen.getByText(TIME_ERROR)).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Apply'})).toBeDisabled();
	});

	it('should show validation error on invalid time format', async () => {
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});
		const TIME_ERROR = 'Time has to be in the format hh:mm:ss';

		await screen.getByLabelText('Start Date Range').click();

		await pickDateTimeRange({
			screen,
			fromDay: 10,
			toDay: 20,
		});

		vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout']});
		await userEvent.fill(screen.getByTestId('fromTime'), '1111');

		await vi.advanceTimersByTimeAsync(749);
		await expect.element(screen.getByTestId('fromTime')).not.toBeInvalid();
		await expect.element(screen.getByText(TIME_ERROR)).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Apply'})).not.toBeDisabled();

		await vi.advanceTimersByTimeAsync(1);
		vi.useRealTimers();

		await expect.element(screen.getByText(TIME_ERROR)).toBeVisible();
		await expect.element(screen.getByTestId('fromTime')).toBeInvalid();
		await expect.element(screen.getByRole('button', {name: 'Apply'})).toBeDisabled();
	});

	it('should show validation error and disable apply on a calendar-invalid typed date', async () => {
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});

		await screen.getByLabelText('Start Date Range').click();
		await userEvent.fill(screen.getByLabelText('From date'), '2022-02-31');
		await screen.getByLabelText('From date').click();
		await userEvent.fill(screen.getByLabelText('To date'), '2022-12-01');
		await screen.getByLabelText('To date').click();

		await expect.element(screen.getByLabelText('From date')).toBeInvalid();
		await expect.element(screen.getByRole('button', {name: 'Apply'})).toBeDisabled();
	});
});
