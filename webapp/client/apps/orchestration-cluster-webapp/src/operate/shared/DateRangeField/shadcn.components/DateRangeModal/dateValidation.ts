/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {isValid} from 'date-fns';
import {parseDate} from '#/operate/shared/utils/parseDate';

// `parseISO` (used by `parseDate`) only accepts the zero-padded `yyyy-MM-dd` shape, but the
// accepted input pattern (and the preserved Carbon field) allow one-digit month/day values
// such as `2022-2-1`. Normalize to `yyyy-MM-dd` here so both the field validator (`DateInput`)
// and the calendar range selection (`DateRangeModal`) treat one-digit input consistently.
const normalizeDateString = (value: string): string | undefined => {
	const match = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(value);
	if (!match) {
		return undefined;
	}
	const [, year, month, day] = match;
	if (year === undefined || month === undefined || day === undefined) {
		return undefined;
	}
	return `${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}`;
};

// Returns the parsed `Date` only for complete, calendar-valid input ("2022-2-1" normalizes and
// parses; "2022-02-31" normalizes but fails `isValid`; "2022-0" fails to normalize at all).
const parseCompleteDate = (value: string | undefined): Date | undefined => {
	const normalized = value === undefined ? undefined : normalizeDateString(value);
	if (normalized === undefined) {
		return undefined;
	}
	const date = parseDate(normalized);
	return isValid(date) ? date : undefined;
};

export {parseCompleteDate};
