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

// This suite drives a real Carbon Modal + flatpickr instance in the browser; under heavy
// parallel-test CPU load their internal async sync can occasionally take longer than the
// default poll window. Retries the whole assertion rather than passing a longer `timeout`
// to `expect.element`, since eslint-plugin-vitest's `valid-expect` rule doesn't recognize
// that overload and flags it as "too many arguments".
async function retryAssertion(assertion: () => Promise<unknown>, attempts = 10, delayMs = 500) {
	for (let attempt = 1; attempt <= attempts; attempt++) {
		try {
			await assertion();
			return;
		} catch (error) {
			if (attempt === attempts) {
				throw error;
			}
			await new Promise((resolve) => setTimeout(resolve, delayMs));
		}
	}
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
	await expect.element(screen.getByTestId('date-range-modal')).toHaveClass(/is-visible/);
	const monthName = document.querySelector('.cur-month')?.textContent;
	const year = document.querySelector<HTMLInputElement>('.cur-year')?.value;
	if (!monthName || !year) {
		throw new Error('Could not read month/year from the flatpickr calendar header');
	}
	const month = new Date(`${monthName} 01, ${year}`).getMonth() + 1;

	await screen.getByLabelText('From date').click();
	clickCalendarDay(monthName, fromDay, year);
	await screen.getByLabelText('To date').click();
	clickCalendarDay(monthName, toDay, year);

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
	await expect.element(applyButton).not.toBeDisabled();
	await applyButton.click();
	await expect.element(screen.getByTestId('date-range-modal')).not.toBeInTheDocument();
}

export {pickDateTimeRange, applyDateRange, retryAssertion};
