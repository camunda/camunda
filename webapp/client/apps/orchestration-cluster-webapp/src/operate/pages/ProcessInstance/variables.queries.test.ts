/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {createQueryVariablesByUserTaskResponse, createVariable} from '#/shared-test-modules/api-mocks/variables';
import {variablesQuery} from './variables.queries';

const {getNextPageParam} = variablesQuery({processInstanceKey: '1', scopeKey: '1'});

function variablesPage({
	count = 50,
	start = 0,
	totalItems = 50,
	hasMoreTotalItems = false,
	endCursor = 'last-item-cursor',
}: {
	count?: number;
	start?: number;
	totalItems?: number;
	hasMoreTotalItems?: boolean;
	endCursor?: string | null;
} = {}) {
	return createQueryVariablesByUserTaskResponse({
		items: Array.from({length: count}, (_, index) => createVariable({variableKey: String(start + index)})),
		page: {totalItems, hasMoreTotalItems, endCursor},
	});
}

describe('variablesQuery', () => {
	it.for([
		{description: 'a complete short page', count: 49, totalItems: 49, hasMoreTotalItems: false, expected: undefined},
		{description: 'a complete full page', count: 50, totalItems: 50, hasMoreTotalItems: false, expected: undefined},
		{
			description: 'an incomplete full page',
			count: 50,
			totalItems: 51,
			hasMoreTotalItems: false,
			expected: 'last-item-cursor',
		},
		{description: 'a capped total', count: 50, totalItems: 50, hasMoreTotalItems: true, expected: 'last-item-cursor'},
		{
			description: 'an underfilled page with more results',
			count: 49,
			totalItems: 50,
			hasMoreTotalItems: true,
			expected: 'last-item-cursor',
		},
		{
			description: 'an empty page with a capped total',
			count: 0,
			totalItems: 50,
			hasMoreTotalItems: true,
			expected: undefined,
		},
	])('should honor pagination metadata for $description', ({expected, ...options}) => {
		const page = variablesPage(options);

		expect(getNextPageParam(page, [page], null, [null])).toBe(expected);
	});

	it('should stop when the end cursor is absent even if the total is capped', () => {
		const page = variablesPage({hasMoreTotalItems: true, endCursor: null});

		expect(getNextPageParam(page, [page], null, [null])).toBeUndefined();
	});

	it('should stop after the last full page without requesting an empty page', () => {
		const firstPage = variablesPage({totalItems: 100});
		const lastPage = variablesPage({start: 50, totalItems: 100});

		expect(
			getNextPageParam(lastPage, [firstPage, lastPage], 'first-page-cursor', [null, 'first-page-cursor']),
		).toBeUndefined();
	});

	it('should not count overlapping variables toward the exact total twice', () => {
		const firstPage = variablesPage({totalItems: 100});
		const lastPage = variablesPage({start: 49, totalItems: 100});

		expect(getNextPageParam(lastPage, [firstPage, lastPage], 'first-page-cursor', [null, 'first-page-cursor'])).toBe(
			'last-item-cursor',
		);
	});
});
