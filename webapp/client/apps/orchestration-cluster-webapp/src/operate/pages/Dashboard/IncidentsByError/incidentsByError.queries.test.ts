/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import type {GetIncidentProcessInstanceStatisticsByErrorResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {incidentsByErrorInfiniteQuery, PAGE_SIZE} from './incidentsByError.queries';

const createPage = ({
	itemCount,
	totalItems,
	hasMoreTotalItems = false,
}: {
	itemCount: number;
	totalItems: number;
	hasMoreTotalItems?: boolean;
}): GetIncidentProcessInstanceStatisticsByErrorResponseBody =>
	({
		items: Array.from({length: itemCount}, (_, index) => ({errorHashCode: index})),
		page: {totalItems, hasMoreTotalItems},
	}) as unknown as GetIncidentProcessInstanceStatisticsByErrorResponseBody;

describe('incidentsByErrorInfiniteQuery', () => {
	it('returns the next offset when there are more items and progress was made', () => {
		// given
		const query = incidentsByErrorInfiniteQuery();
		const lastPage = createPage({itemCount: PAGE_SIZE, totalItems: PAGE_SIZE * 3});

		// when
		const nextPageParam = query.getNextPageParam(lastPage, [lastPage], {from: 0, limit: PAGE_SIZE}, [
			{from: 0, limit: PAGE_SIZE},
		]);

		// then
		expect(nextPageParam).toEqual({from: PAGE_SIZE, limit: PAGE_SIZE});
	});

	it('returns undefined when the last page has been reached', () => {
		// given
		const query = incidentsByErrorInfiniteQuery();
		const lastPageParam = PAGE_SIZE * 2;
		const lastPage = createPage({itemCount: 12, totalItems: lastPageParam + 12});

		// when
		const nextPageParam = query.getNextPageParam(lastPage, [lastPage], {from: lastPageParam, limit: PAGE_SIZE}, [
			{from: lastPageParam, limit: PAGE_SIZE},
		]);

		// then
		expect(nextPageParam).toBeUndefined();
	});

	it('keeps paging at the count cap while more total items exist', () => {
		// given
		const query = incidentsByErrorInfiniteQuery();
		const lastPage = createPage({itemCount: PAGE_SIZE, totalItems: PAGE_SIZE, hasMoreTotalItems: true});
		const lastPageParam = {from: 0, limit: PAGE_SIZE};

		// when
		const nextPageParam = query.getNextPageParam(lastPage, [lastPage], lastPageParam, [lastPageParam]);

		// then
		expect(nextPageParam).toEqual({from: PAGE_SIZE, limit: PAGE_SIZE});
	});

	it('returns undefined when the page is empty even if total items suggest more data, instead of repeating the same offset forever', () => {
		// given
		const query = incidentsByErrorInfiniteQuery();
		const lastPageParam = PAGE_SIZE * 4;
		const lastPage = createPage({itemCount: 0, totalItems: PAGE_SIZE * 10});

		// when
		const nextPageParam = query.getNextPageParam(lastPage, [lastPage], {from: lastPageParam, limit: PAGE_SIZE}, [
			{from: lastPageParam, limit: PAGE_SIZE},
		]);

		// then
		expect(nextPageParam).toBeUndefined();
	});

	it('returns undefined for previous page on the first page', () => {
		// given
		const query = incidentsByErrorInfiniteQuery();
		const firstPage = createPage({itemCount: PAGE_SIZE, totalItems: PAGE_SIZE * 3});

		// when
		const previousPageParam = query.getPreviousPageParam!(firstPage, [firstPage], {from: 0, limit: PAGE_SIZE}, [
			{from: 0, limit: PAGE_SIZE},
		]);

		// then
		expect(previousPageParam).toBeUndefined();
	});

	it('recovers the exact previous offset and length for a page shorter than PAGE_SIZE, instead of guessing a fixed step back', () => {
		// given
		const query = incidentsByErrorInfiniteQuery();
		const shortPageParam = {from: 1300, limit: PAGE_SIZE};
		const shortPage = createPage({itemCount: 23, totalItems: 3000});
		// Fetching forward records the short page's real size so a later backward
		// fetch can recover its exact start offset and length instead of assuming
		// PAGE_SIZE, which would overlap into rows the short page already returned.
		const nextPageParam = query.getNextPageParam(shortPage, [shortPage], shortPageParam, [shortPageParam]);
		const nextPage = createPage({itemCount: PAGE_SIZE, totalItems: 3000});

		// when
		const previousPageParam = query.getPreviousPageParam!(nextPage, [nextPage], nextPageParam!, [nextPageParam!]);

		// then
		expect(previousPageParam).toEqual({from: 1300, limit: 23});
	});
});
