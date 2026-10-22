/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {
	createElementInstance,
	createQueryElementInstancesResponse,
} from '#/shared-test-modules/api-mocks/element-instances';
import {getNextSearchOffset, isWindowPastShrunkResults, PAGE_LIMIT} from './useElementSearchResults';

function response(count: number, total: number, hasMoreTotalItems: boolean) {
	const body = createQueryElementInstancesResponse(
		Array.from({length: count}, (_, index) => createElementInstance({elementInstanceKey: String(index + 1)})),
		total,
	);
	body.page.hasMoreTotalItems = hasMoreTotalItems;
	return body;
}

describe('getNextSearchOffset', () => {
	it('should continue while the exact total exceeds the loaded offset', () => {
		// when / then
		expect(getNextSearchOffset(response(PAGE_LIMIT, 120, false), 0)).toBe(PAGE_LIMIT);
		expect(getNextSearchOffset(response(20, 120, false), 100)).toBeNull();
	});

	it('should continue after a full page below the capped total', () => {
		// when / then
		expect(getNextSearchOffset(response(PAGE_LIMIT, 10000, true), 9900)).toBe(9950);
	});

	it('should stop at the capped result window instead of requesting beyond it', () => {
		// when / then
		expect(getNextSearchOffset(response(PAGE_LIMIT, 10000, true), 9950)).toBeNull();
	});

	it.for([0, 10])('should stop on a short page of %i items before the total is reached', (count) => {
		// when / then
		expect(getNextSearchOffset(response(count, 10000, true), 100)).toBeNull();
	});
});

describe('isWindowPastShrunkResults', () => {
	const items = (count: number) =>
		Array.from({length: count}, (_, index) => createElementInstance({elementInstanceKey: String(index + 1)}));

	it('should detect a later window that holds less than a page after the results shrank', () => {
		// when / then
		expect(isWindowPastShrunkResults({items: items(10), totalCount: 60, firstOffset: PAGE_LIMIT})).toBe(true);
		expect(isWindowPastShrunkResults({items: [], totalCount: 30, firstOffset: PAGE_LIMIT})).toBe(true);
	});

	it('should keep a window that starts at the first page', () => {
		// when / then
		expect(isWindowPastShrunkResults({items: items(10), totalCount: 10, firstOffset: 0})).toBe(false);
	});

	it('should keep a later window that still holds a full page', () => {
		// when / then
		expect(isWindowPastShrunkResults({items: items(PAGE_LIMIT), totalCount: 400, firstOffset: PAGE_LIMIT})).toBe(false);
	});

	it('should keep a window when there are no results at all', () => {
		// when / then
		expect(isWindowPastShrunkResults({items: [], totalCount: 0, firstOffset: PAGE_LIMIT})).toBe(false);
	});
});
