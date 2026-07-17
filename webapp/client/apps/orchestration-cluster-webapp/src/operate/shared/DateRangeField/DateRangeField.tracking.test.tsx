/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, cleanup} from 'vitest-browser-react';
import {describe, it, expect, vi, afterEach} from 'vitest';
import {tracking} from '#/shared/tracking';
import {applyDateRange, pickDateTimeRange} from './pickDateTimeRange';
import {MockDateRangeField} from './mocks';
import {getWrapper} from './getWrapper';

describe('<DateRangeField /> tracking', () => {
	afterEach(async () => {
		await cleanup();
		vi.restoreAllMocks();
	});

	it('should track date picker, date input, time input', async () => {
		const trackSpy = vi.spyOn(tracking, 'track');
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});
		await screen.getByLabelText('Start Date Range').click();
		expect(trackSpy).toHaveBeenNthCalledWith(1, {
			eventName: 'operate:date-range-popover-opened',
			filterName: 'startDateRange',
		});

		await pickDateTimeRange({
			screen,
			fromDay: '10',
			toDay: '20',
			fromTime: '11:22:33',
			toTime: '08:59:59',
		});

		await screen.getByLabelText('From date').fill('2022-01-01');
		await screen.getByLabelText('To date').fill('2022-12-01');
		await applyDateRange(screen);

		expect(trackSpy).toHaveBeenNthCalledWith(2, {
			eventName: 'operate:date-range-applied',
			filterName: 'startDateRange',
			methods: {
				dateInput: true,
				datePicker: true,
				quickFilter: false,
				timeInput: true,
			},
		});
	});

	it('should track date picker', async () => {
		const trackSpy = vi.spyOn(tracking, 'track');
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});
		await screen.getByLabelText('Start Date Range').click();
		expect(trackSpy).toHaveBeenNthCalledWith(1, {
			eventName: 'operate:date-range-popover-opened',
			filterName: 'startDateRange',
		});

		await pickDateTimeRange({
			screen,
			fromDay: '10',
			toDay: '20',
		});
		await applyDateRange(screen);

		expect(trackSpy).toHaveBeenNthCalledWith(2, {
			eventName: 'operate:date-range-applied',
			filterName: 'startDateRange',
			methods: {
				dateInput: false,
				datePicker: true,
				quickFilter: false,
				timeInput: false,
			},
		});
	});

	it('should track date input, time input', async () => {
		const trackSpy = vi.spyOn(tracking, 'track');
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});
		await screen.getByLabelText('Start Date Range').click();
		expect(trackSpy).toHaveBeenNthCalledWith(1, {
			eventName: 'operate:date-range-popover-opened',
			filterName: 'startDateRange',
		});

		await screen.getByLabelText('From date').fill('2022-01-01');
		await screen.getByLabelText('To date').fill('2022-12-01');
		await screen.getByTestId('fromTime').fill('12:30:00');
		await screen.getByTestId('toTime').fill('17:15:00');
		await applyDateRange(screen);

		expect(trackSpy).toHaveBeenNthCalledWith(2, {
			eventName: 'operate:date-range-applied',
			filterName: 'startDateRange',
			methods: {
				dateInput: true,
				datePicker: false,
				quickFilter: false,
				timeInput: true,
			},
		});
	});

	it('should track date picker, time input', async () => {
		const trackSpy = vi.spyOn(tracking, 'track');
		const screen = await render(<MockDateRangeField />, {wrapper: getWrapper()});
		await screen.getByLabelText('Start Date Range').click();
		expect(trackSpy).toHaveBeenNthCalledWith(1, {
			eventName: 'operate:date-range-popover-opened',
			filterName: 'startDateRange',
		});

		await pickDateTimeRange({
			screen,
			fromDay: '10',
			toDay: '20',
			fromTime: '11:22:33',
			toTime: '08:59:59',
		});
		await applyDateRange(screen);
		expect(trackSpy).toHaveBeenNthCalledWith(2, {
			eventName: 'operate:date-range-applied',
			filterName: 'startDateRange',
			methods: {
				dateInput: false,
				datePicker: true,
				quickFilter: false,
				timeInput: true,
			},
		});
	});
});
