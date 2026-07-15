/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from 'vitest';
import type {RenderResult} from 'vitest-browser-react';
import {formatISODate} from './formatDate';

// This suite drives a real Carbon Modal + flatpickr instance in the browser; under heavy
// parallel-test CPU load their internal async sync can take longer than the default poll
// timeout, so every assertion below gets a longer one.
const TIMEOUT = {timeout: 10_000};

const pad = (value: string | number) => {
	return String(value).padStart(2, '0');
};

// flatpickr renders calendar days as plain `<span aria-label="...">`, not `<button>`,
// so a role-based locator can't find them; click the DOM node directly instead.
function clickCalendarDay(monthName: string, day: string, year: string) {
	const dayEl = document.querySelector<HTMLElement>(`.flatpickr-day[aria-label*="${monthName} ${day}, ${year}"]`);
	if (dayEl === null) {
		throw new Error(`Could not find calendar day "${monthName} ${day}, ${year}"`);
	}
	dayEl.click();
}

async function pickDateTimeRange({
	screen,
	fromDay,
	toDay,
	fromTime,
	toTime,
}: {
	screen: RenderResult;
	fromDay: string;
	toDay: string;
	fromTime?: string;
	toTime?: string;
}) {
	await expect.element(screen.getByTestId('date-range-modal'), TIMEOUT).toHaveClass(/is-visible/);
	const monthName = document.querySelector('.cur-month')?.textContent;
	const year = document.querySelector<HTMLInputElement>('.cur-year')?.value;
	const month = new Date(`${monthName} 01, ${year}`).getMonth() + 1;

	await screen.getByLabelText('From date').click();
	clickCalendarDay(monthName ?? '', fromDay, year ?? '');
	await screen.getByLabelText('To date').click();
	clickCalendarDay(monthName ?? '', toDay, year ?? '');

	if (fromTime !== undefined) {
		await screen.getByTestId('fromTime').fill(fromTime);
	}

	if (toTime !== undefined) {
		await screen.getByTestId('toTime').fill(toTime);
	}

	const fromTimeInput = screen.getByTestId('fromTime').element() as HTMLInputElement;
	const toTimeInput = screen.getByTestId('toTime').element() as HTMLInputElement;

	return {
		fromDay: pad(fromDay),
		toDay: pad(toDay),
		month: pad(month),
		fromDate: formatISODate(new Date(`${year}-${pad(month)}-${pad(fromDay)} ${fromTimeInput.value}`)),
		toDate: formatISODate(new Date(`${year}-${pad(month)}-${pad(toDay)} ${toTimeInput.value}`)),
		year,
	};
}

async function applyDateRange(screen: RenderResult) {
	const applyButton = screen.getByRole('button', {name: 'Apply'});
	await expect.element(applyButton, TIMEOUT).not.toBeDisabled();
	await applyButton.click();
	await expect.element(screen.getByTestId('date-range-modal'), TIMEOUT).not.toBeInTheDocument();
}

export {pickDateTimeRange, applyDateRange};
