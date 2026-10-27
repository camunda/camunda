/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, beforeEach, afterEach, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {formatISODate, formatISODateTime} from './formatDateRelative';

const FAKE_NOW = new Date(2024, 0, 10, 12, 0, 0, 0);

const t = (year: number, month: number, day: number, hour = 12, minute = 0) =>
	new Date(year, month - 1, day, hour, minute, 0, 0).toISOString();

describe('formatDateRelative', () => {
	beforeEach(() => {
		vi.useFakeTimers({toFake: ['Date']});
		vi.setSystemTime(FAKE_NOW);
	});

	afterEach(() => {
		vi.useRealTimers();
	});

	it('should return null for null input to formatISODate', () => {
		expect(formatISODate(null)).toBeNull();
	});

	it('should return null for undefined input to formatISODate', () => {
		expect(formatISODate(undefined)).toBeNull();
	});

	it('should return null for an invalid ISO string in formatISODate', () => {
		expect(formatISODate('not-a-date')).toBeNull();
	});

	it('should format "Now"', () => {
		const result = formatISODate(FAKE_NOW.toISOString());

		expect(result?.relative.resolution).toBe('now');
		expect(result?.relative.text).toBe('Now');
		expect(result?.relative.speech).toBe('Now');
	});

	it('should format "1 minute ago"', () => {
		const result = formatISODate(t(2024, 1, 10, 11, 59));

		expect(result?.relative.resolution).toBe('minutes');
		expect(result?.relative.text).toBe('1 minute ago');
	});

	it('should format "10 minutes ago"', () => {
		const result = formatISODate(t(2024, 1, 10, 11, 50));

		expect(result?.relative.resolution).toBe('minutes');
		expect(result?.relative.text).toBe('10 minutes ago');
	});

	it('should format "In 1 minute"', () => {
		const result = formatISODate(t(2024, 1, 10, 12, 1));

		expect(result?.relative.resolution).toBe('minutes');
		expect(result?.relative.text).toBe('In 1 minute');
	});

	it('should format "In 10 minutes"', () => {
		const result = formatISODate(t(2024, 1, 10, 12, 10));

		expect(result?.relative.resolution).toBe('minutes');
		expect(result?.relative.text).toBe('In 10 minutes');
	});

	it('should format "Today"', () => {
		const result = formatISODate(t(2024, 1, 10, 9, 0));

		expect(result?.relative.resolution).toBe('days');
		expect(result?.relative.text).toBe('Today');
	});

	it('should format "Tomorrow"', () => {
		const result = formatISODate(t(2024, 1, 11, 12, 0));

		expect(result?.relative.resolution).toBe('days');
		expect(result?.relative.text).toBe('Tomorrow');
	});

	it('should format "Yesterday"', () => {
		const result = formatISODate(t(2024, 1, 9, 12, 0));

		expect(result?.relative.resolution).toBe('days');
		expect(result?.relative.text).toBe('Yesterday');
	});

	it('should format a weekday name within the same week', () => {
		const result = formatISODate(t(2024, 1, 8, 12, 0));

		expect(result?.relative.resolution).toBe('week');
		expect(result?.relative.text).toBe('Monday');
		expect(result?.relative.speech).toBe('Monday');
	});

	it('should format "6 Jan" within the same year', () => {
		const result = formatISODate(t(2024, 1, 6, 12, 0));

		expect(result?.relative.resolution).toBe('months');
		expect(result?.relative.text).toBe('6 Jan');
		expect(result?.relative.speech).toBe('6th of January');
	});

	it('should format "31 Dec 2023" for a different year', () => {
		const result = formatISODate(t(2023, 12, 31, 12, 0));

		expect(result?.relative.resolution).toBe('years');
		expect(result?.relative.text).toBe('31 Dec 2023');
		expect(result?.relative.speech).toBe('31st of December, 2023');
	});

	it('should include the absolute date in day-month-year format', () => {
		expect(formatISODate(FAKE_NOW.toISOString())?.absolute.text).toBe('10 Jan 2024');
		expect(formatISODate(t(2024, 1, 10, 11, 59))?.absolute.text).toBe('10 Jan 2024');
		expect(formatISODate(t(2023, 12, 31, 12, 0))?.absolute.text).toBe('31 Dec 2023');
	});

	it('should return null for null input to formatISODateTime', () => {
		expect(formatISODateTime(null)).toBeNull();
	});

	it('should format "Now" without a time suffix', () => {
		const result = formatISODateTime(FAKE_NOW.toISOString());

		expect(result?.relative.resolution).toBe('now');
		expect(result?.relative.text).toBe('Now');
	});

	it('should format "1 minute ago" without a time suffix', () => {
		const result = formatISODateTime(t(2024, 1, 10, 11, 59));

		expect(result?.relative.resolution).toBe('minutes');
		expect(result?.relative.text).toBe('1 minute ago');
	});

	it('should format "Today" with a time suffix', () => {
		const result = formatISODateTime(t(2024, 1, 10, 9, 0));

		expect(result?.relative.resolution).toBe('days');
		expect(result?.relative.text).toMatch(/^Today,/);
		expect(result?.relative.speech).toMatch(/^Today at/);
	});

	it('should format "Tomorrow" with a time suffix', () => {
		const result = formatISODateTime(t(2024, 1, 11, 12, 0));

		expect(result?.relative.resolution).toBe('days');
		expect(result?.relative.text).toMatch(/^Tomorrow,/);
		expect(result?.relative.speech).toMatch(/^Tomorrow at/);
	});

	it('should format "Yesterday" with a time suffix', () => {
		const result = formatISODateTime(t(2024, 1, 9, 12, 0));

		expect(result?.relative.resolution).toBe('days');
		expect(result?.relative.text).toMatch(/^Yesterday,/);
		expect(result?.relative.speech).toMatch(/^Yesterday at/);
	});

	it('should format "6 Jan" with a time suffix', () => {
		const result = formatISODateTime(t(2024, 1, 6, 12, 0));

		expect(result?.relative.resolution).toBe('months');
		expect(result?.relative.text).toMatch(/^6 Jan,/);
		expect(result?.relative.speech).toMatch(/^6th of January at/);
	});

	it('should format "31 Dec 2023" with a time suffix', () => {
		const result = formatISODateTime(t(2023, 12, 31, 12, 0));

		expect(result?.relative.resolution).toBe('years');
		expect(result?.relative.text).toMatch(/^31 Dec 2023,/);
		expect(result?.relative.speech).toMatch(/^31st of December, 2023 at/);
	});

	it('should include the absolute date with a time suffix', () => {
		const result = formatISODateTime(FAKE_NOW.toISOString());

		expect(result?.absolute.text).toMatch(/^10 Jan 2024,/);
	});
});
