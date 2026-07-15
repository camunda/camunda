/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, cleanup} from 'vitest-browser-react';
import {describe, it, expect, vi, afterEach} from 'vitest';
import {applyDateRange, pickDateTimeRange, retryAssertion} from './pickDateTimeRange';
import {MockDateRangeField} from './mocks';
import {getWrapper} from './getWrapper';

describe('<DateRangeField />', () => {
	afterEach(async () => {
		vi.useRealTimers();
		await cleanup();
	});

	it('should close modal on cancel click', async () => {
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});

		await expect.element(screen.getByTestId('date-range-modal')).not.toBeInTheDocument();

		await screen.getByLabelText('Start Date Range').click();
		await expect.element(screen.getByTestId('date-range-modal')).toHaveClass(/is-visible/);

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
			fromDay: '10',
			toDay: '20',
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
			fromDay: '10',
			toDay: '20',
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

		// Carbon's DatePicker syncs its child inputs from the calendar's selected dates
		// asynchronously on mount; under heavy parallel-test CPU load that can occasionally
		// take longer than the default poll window, so retry the whole assertion. The test
		// timeout below is extended to give the retries room (default 15s is too tight).
		await retryAssertion(() => expect.element(screen.getByLabelText('From date')).toHaveValue('2021-02-03'));
		await retryAssertion(() => expect.element(screen.getByTestId('fromTime')).toHaveValue('12:34:56'));
		await retryAssertion(() => expect.element(screen.getByLabelText('To date')).toHaveValue('2021-02-06'));
		await retryAssertion(() => expect.element(screen.getByTestId('toTime')).toHaveValue('01:02:03'));
	}, 30_000);

	it('should apply from and to dates', async () => {
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});

		await screen.getByLabelText('Start Date Range').click();
		await screen.getByLabelText('From date').fill('2022-01-01');
		await screen.getByLabelText('To date').fill('2022-12-01');
		// Typing a date asynchronously resets the time fields to their defaults via the
		// calendar's onChange (ported 1:1 from legacy). Under CI/parallel-test CPU pressure
		// that reset can race a same-tick time fill, so retry the fill until it sticks —
		// a real user's fill is never fast enough to hit this window.
		await expect.element(screen.getByLabelText('From date')).toHaveValue('2022-01-01');
		await expect.element(screen.getByLabelText('To date')).toHaveValue('2022-12-01');
		await expect
			.poll(async () => {
				await screen.getByTestId('fromTime').fill('12:30:00');
				return (screen.getByTestId('fromTime').element() as HTMLInputElement).value;
			})
			.toBe('12:30:00');
		await expect
			.poll(async () => {
				await screen.getByTestId('toTime').fill('17:15:00');
				return (screen.getByTestId('toTime').element() as HTMLInputElement).value;
			})
			.toBe('17:15:00');
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
			fromDay: '10',
			toDay: '20',
		});

		await expect.element(screen.getByTestId('fromTime')).not.toBeInvalid();
		await expect.element(screen.getByText(TIME_ERROR)).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Apply'})).not.toBeDisabled();

		await screen.getByTestId('fromTime').fill('12:30:xx');

		await expect.element(screen.getByTestId('fromTime')).toBeInvalid();
		await expect.element(screen.getByText(TIME_ERROR)).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Apply'})).toBeDisabled();
	});

	it('should show validation error on invalid time format', async () => {
		vi.useFakeTimers({shouldAdvanceTime: true});

		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});
		const TIME_ERROR = 'Time has to be in the format hh:mm:ss';

		await screen.getByLabelText('Start Date Range').click();

		await pickDateTimeRange({
			screen,
			fromDay: '10',
			toDay: '20',
		});

		await screen.getByTestId('fromTime').fill('1111');

		await expect.element(screen.getByTestId('fromTime')).not.toBeInvalid();
		await expect.element(screen.getByText(TIME_ERROR)).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Apply'})).not.toBeDisabled();

		vi.runOnlyPendingTimers();

		await expect.element(screen.getByText(TIME_ERROR)).toBeVisible();
		await expect.element(screen.getByTestId('fromTime')).toBeInvalid();
		await expect.element(screen.getByRole('button', {name: 'Apply'})).toBeDisabled();
	});
});
