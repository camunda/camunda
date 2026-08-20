/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, beforeEach, afterEach, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {getSecondaryDate} from './getSecondaryDate';

const FAKE_NOW = new Date(2024, 0, 10, 12, 0, 0, 0);

function makeDate(date: Date) {
	return {
		date,
		relative: {resolution: 'months' as const, text: '10 Jan', speech: '10th of January'},
		absolute: {text: '10 Jan 2024'},
	};
}

const pastDate = makeDate(new Date(2024, 0, 9, 12, 0, 0, 0));
const futureDate = makeDate(new Date(2024, 0, 11, 12, 0, 0, 0));
const futureDateLater = makeDate(new Date(2024, 0, 12, 12, 0, 0, 0));

describe('getSecondaryDate', () => {
	beforeEach(() => {
		vi.useFakeTimers({toFake: ['Date']});
		vi.setSystemTime(FAKE_NOW);
	});

	afterEach(() => {
		vi.useRealTimers();
	});

	it('should return the completion date on "creation" sort when the task is completed', () => {
		const result = getSecondaryDate({
			sortBy: 'creation',
			completionDate: pastDate,
			dueDate: null,
			followUpDate: null,
		});

		expect(result).toEqual({completionDate: pastDate});
	});

	it('should return the follow-up date on "creation" sort when it is before the due date and task is not overdue', () => {
		const result = getSecondaryDate({
			sortBy: 'creation',
			completionDate: null,
			dueDate: futureDateLater,
			followUpDate: futureDate,
		});

		expect(result).toEqual({followUpDate: futureDate});
	});

	it('should return the overdue date on "creation" sort when the due date is in the past', () => {
		const result = getSecondaryDate({
			sortBy: 'creation',
			completionDate: null,
			dueDate: pastDate,
			followUpDate: futureDate,
		});

		expect(result).toEqual({overDueDate: pastDate});
	});

	it('should return the due date on "creation" sort when the follow-up is not before the due date', () => {
		const result = getSecondaryDate({
			sortBy: 'creation',
			completionDate: null,
			dueDate: futureDate,
			followUpDate: futureDateLater,
		});

		expect(result).toEqual({dueDate: futureDate});
	});

	it('should return the overdue date on "creation" sort when the due date is overdue and there is no follow-up', () => {
		const result = getSecondaryDate({
			sortBy: 'creation',
			completionDate: null,
			dueDate: pastDate,
			followUpDate: null,
		});

		expect(result).toEqual({overDueDate: pastDate});
	});

	it('should return the due date on "creation" sort when the due date is in the future and no follow-up', () => {
		const result = getSecondaryDate({
			sortBy: 'creation',
			completionDate: null,
			dueDate: futureDate,
			followUpDate: null,
		});

		expect(result).toEqual({dueDate: futureDate});
	});

	it('should return an empty object on "creation" sort when no dates are available', () => {
		const result = getSecondaryDate({
			sortBy: 'creation',
			completionDate: null,
			dueDate: null,
			followUpDate: null,
		});

		expect(result).toEqual({});
	});

	it('should return the completion date on "completion" sort when present', () => {
		const result = getSecondaryDate({
			sortBy: 'completion',
			completionDate: pastDate,
			dueDate: futureDate,
			followUpDate: null,
		});

		expect(result).toEqual({completionDate: pastDate});
	});

	it('should return the due date on "completion" sort when there is no completion date', () => {
		const result = getSecondaryDate({
			sortBy: 'completion',
			completionDate: null,
			dueDate: futureDate,
			followUpDate: null,
		});

		expect(result).toEqual({dueDate: futureDate});
	});

	it('should return the follow-up date on "follow-up" sort when present', () => {
		const result = getSecondaryDate({
			sortBy: 'follow-up',
			completionDate: null,
			dueDate: futureDate,
			followUpDate: pastDate,
		});

		expect(result).toEqual({followUpDate: pastDate});
	});

	it('should return the due date on "follow-up" sort when there is no follow-up date', () => {
		const result = getSecondaryDate({
			sortBy: 'follow-up',
			completionDate: null,
			dueDate: futureDate,
			followUpDate: null,
		});

		expect(result).toEqual({dueDate: futureDate});
	});

	it('should return the due date on "due" sort when the due date is in the future', () => {
		const result = getSecondaryDate({
			sortBy: 'due',
			completionDate: null,
			dueDate: futureDate,
			followUpDate: null,
		});

		expect(result).toEqual({dueDate: futureDate});
	});

	it('should return the overdue date on "due" sort when the due date is in the past', () => {
		const result = getSecondaryDate({
			sortBy: 'due',
			completionDate: null,
			dueDate: pastDate,
			followUpDate: null,
		});

		expect(result).toEqual({overDueDate: pastDate});
	});

	it('should return the due date on "priority" sort when the due date is in the future', () => {
		const result = getSecondaryDate({
			sortBy: 'priority',
			completionDate: null,
			dueDate: futureDate,
			followUpDate: null,
		});

		expect(result).toEqual({dueDate: futureDate});
	});

	it('should return the overdue date on "priority" sort when the due date is in the past', () => {
		const result = getSecondaryDate({
			sortBy: 'priority',
			completionDate: null,
			dueDate: pastDate,
			followUpDate: null,
		});

		expect(result).toEqual({overDueDate: pastDate});
	});

	it('should return an empty object on "priority" sort when no dates are present', () => {
		const result = getSecondaryDate({
			sortBy: 'priority',
			completionDate: null,
			dueDate: null,
			followUpDate: null,
		});

		expect(result).toEqual({});
	});
});
