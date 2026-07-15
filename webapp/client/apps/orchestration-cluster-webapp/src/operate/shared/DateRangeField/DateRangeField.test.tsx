/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, cleanup} from 'vitest-browser-react';
import {describe, it, expect, vi, afterEach} from 'vitest';
import {applyDateRange, pickDateTimeRange} from './pickDateTimeRange';
import {MockDateRangeField} from './mocks';
import {getWrapper} from './getWrapper';

// This suite drives a real Carbon Modal + flatpickr instance in the browser; under heavy
// parallel-test CPU load their internal async sync can take longer than the default poll
// timeout, so every assertion below gets a longer one.
const TIMEOUT = {timeout: 10_000};

describe('<DateRangeField />', () => {
	afterEach(cleanup);

	it('should close modal on cancel click', async () => {
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});

		await expect.element(screen.getByTestId('date-range-modal'), TIMEOUT).not.toBeInTheDocument();

		await screen.getByLabelText('Start Date Range').click();
		await expect.element(screen.getByTestId('date-range-modal'), TIMEOUT).toHaveClass(/is-visible/);

		// getByRole doesn't help here since the date range modal portal renders to document.body
		await screen.getByRole('button', {name: 'Cancel'}).click();
		await expect.element(screen.getByTestId('date-range-modal'), TIMEOUT).not.toBeInTheDocument();
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
			.element(screen.getByLabelText('Start Date Range'), TIMEOUT)
			.toHaveValue(`${year}-${month}-${fromDay} ${fromTime} - ${year}-${month}-${toDay} ${toTime}`);
	});

	it('should restore previous date on cancel', async () => {
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});

		await screen.getByLabelText('Start Date Range').click();
		await expect.element(screen.getByLabelText('Start Date Range'), TIMEOUT).toHaveValue('Custom');

		const {year, month, fromDay, toDay} = await pickDateTimeRange({
			screen,
			fromDay: '10',
			toDay: '20',
		});
		await applyDateRange(screen);

		const expectedValue = `${year}-${month}-${fromDay} 00:00:00 - ${year}-${month}-${toDay} 23:59:59`;
		await expect.element(screen.getByLabelText('Start Date Range'), TIMEOUT).toHaveValue(expectedValue);

		await screen.getByLabelText('Start Date Range').click();
		await expect.element(screen.getByLabelText('Start Date Range'), TIMEOUT).toHaveValue('Custom');

		await screen.getByRole('button', {name: 'Cancel'}).click();
		await expect.element(screen.getByLabelText('Start Date Range'), TIMEOUT).toHaveValue(expectedValue);
	});

	it('should set default values', async () => {
		const screen = await render(<MockDateRangeField />, {
			wrapper: getWrapper({
				startDateFrom: '2021-02-03T12:34:56',
				startDateTo: '2021-02-06T01:02:03',
			}),
		});

		await expect
			.element(screen.getByLabelText('Start Date Range'), TIMEOUT)
			.toHaveValue('2021-02-03 12:34:56 - 2021-02-06 01:02:03');

		await screen.getByLabelText('Start Date Range').click();

		await expect.element(screen.getByLabelText('From date'), TIMEOUT).toHaveValue('2021-02-03');
		await expect.element(screen.getByTestId('fromTime'), TIMEOUT).toHaveValue('12:34:56');
		await expect.element(screen.getByLabelText('To date'), TIMEOUT).toHaveValue('2021-02-06');
		await expect.element(screen.getByTestId('toTime'), TIMEOUT).toHaveValue('01:02:03');
	});

	it('should apply from and to dates', async () => {
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});

		await screen.getByLabelText('Start Date Range').click();
		await screen.getByLabelText('From date').fill('2022-01-01');
		await screen.getByLabelText('To date').fill('2022-12-01');
		// Typing a date asynchronously resets the time fields to their defaults via the
		// calendar's onChange (ported 1:1 from legacy). Under CI/parallel-test CPU pressure
		// that reset can race a same-tick time fill, so retry the fill until it sticks —
		// a real user's fill is never fast enough to hit this window.
		await expect.element(screen.getByLabelText('From date'), TIMEOUT).toHaveValue('2022-01-01');
		await expect.element(screen.getByLabelText('To date'), TIMEOUT).toHaveValue('2022-12-01');
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
			.element(screen.getByLabelText('Start Date Range'), TIMEOUT)
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

		await expect.element(screen.getByTestId('fromTime'), TIMEOUT).not.toBeInvalid();
		await expect.element(screen.getByText(TIME_ERROR), TIMEOUT).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Apply'}), TIMEOUT).not.toBeDisabled();

		await screen.getByTestId('fromTime').fill('12:30:xx');

		await expect.element(screen.getByTestId('fromTime'), TIMEOUT).toBeInvalid();
		await expect.element(screen.getByText(TIME_ERROR), TIMEOUT).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Apply'}), TIMEOUT).toBeDisabled();
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

		await expect.element(screen.getByTestId('fromTime'), TIMEOUT).not.toBeInvalid();
		await expect.element(screen.getByText(TIME_ERROR), TIMEOUT).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Apply'}), TIMEOUT).not.toBeDisabled();

		vi.runOnlyPendingTimers();

		await expect.element(screen.getByText(TIME_ERROR), TIMEOUT).toBeVisible();
		await expect.element(screen.getByTestId('fromTime'), TIMEOUT).toBeInvalid();
		await expect.element(screen.getByRole('button', {name: 'Apply'}), TIMEOUT).toBeDisabled();

		vi.clearAllTimers();
		vi.useRealTimers();
	});
});
