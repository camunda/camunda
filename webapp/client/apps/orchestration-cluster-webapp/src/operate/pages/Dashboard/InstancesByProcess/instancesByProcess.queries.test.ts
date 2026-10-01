/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import type {GetProcessDefinitionInstanceStatisticsResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {instancesByProcessInfiniteQuery, PAGE_SIZE} from './instancesByProcess.queries';

const createPage = ({
	itemCount,
	totalItems,
	hasMoreTotalItems = false,
}: {
	itemCount: number;
	totalItems: number;
	hasMoreTotalItems?: boolean;
}): GetProcessDefinitionInstanceStatisticsResponseBody =>
	({
		items: Array.from({length: itemCount}, (_, index) => ({processDefinitionId: `process-${index}`})),
		page: {totalItems, hasMoreTotalItems},
	}) as unknown as GetProcessDefinitionInstanceStatisticsResponseBody;

describe('instancesByProcessInfiniteQuery', () => {
	it('should return the next offset when there are more items and progress was made', () => {
		// given
		const query = instancesByProcessInfiniteQuery();
		const lastPage = createPage({itemCount: PAGE_SIZE, totalItems: PAGE_SIZE * 3});

		// when
		const nextPageParam = query.getNextPageParam(lastPage, [lastPage], {from: 0, limit: PAGE_SIZE}, [
			{from: 0, limit: PAGE_SIZE},
		]);

		// then
		expect(nextPageParam).toEqual({from: PAGE_SIZE, limit: PAGE_SIZE});
	});

	it('should keep paging at the count cap while more total items exist', () => {
		// given
		const query = instancesByProcessInfiniteQuery();
		const lastPage = createPage({itemCount: PAGE_SIZE, totalItems: PAGE_SIZE, hasMoreTotalItems: true});
		const lastPageParam = {from: 0, limit: PAGE_SIZE};

		// when
		const nextPageParam = query.getNextPageParam(lastPage, [lastPage], lastPageParam, [lastPageParam]);

		// then
		expect(nextPageParam).toEqual({from: PAGE_SIZE, limit: PAGE_SIZE});
	});

	it('should return undefined when the last page has been reached', () => {
		// given
		const query = instancesByProcessInfiniteQuery();
		const lastPageParam = PAGE_SIZE * 2;
		const lastPage = createPage({itemCount: 12, totalItems: lastPageParam + 12});

		// when
		const nextPageParam = query.getNextPageParam(lastPage, [lastPage], {from: lastPageParam, limit: PAGE_SIZE}, [
			{from: lastPageParam, limit: PAGE_SIZE},
		]);

		// then
		expect(nextPageParam).toBeUndefined();
	});

	it('should return undefined when the page is empty even if total items suggest more data', () => {
		// given
		const query = instancesByProcessInfiniteQuery();
		const lastPageParam = PAGE_SIZE;
		const lastPage = createPage({itemCount: 0, totalItems: PAGE_SIZE * 3});

		// when
		const nextPageParam = query.getNextPageParam(lastPage, [lastPage], {from: lastPageParam, limit: PAGE_SIZE}, [
			{from: lastPageParam, limit: PAGE_SIZE},
		]);

		// then
		expect(nextPageParam).toBeUndefined();
	});

	it('should step back a full page when enough rows precede the first loaded offset', () => {
		// given
		const query = instancesByProcessInfiniteQuery();
		const firstPage = createPage({itemCount: PAGE_SIZE, totalItems: PAGE_SIZE * 3});
		const firstPageParam = PAGE_SIZE + 10;

		// when
		const previousPageParam = query.getPreviousPageParam?.(
			firstPage,
			[firstPage],
			{from: firstPageParam, limit: PAGE_SIZE},
			[{from: firstPageParam, limit: PAGE_SIZE}],
		);

		// then
		expect(previousPageParam).toEqual({from: 10, limit: PAGE_SIZE});
	});

	it('should end the previous range at the first loaded offset so it cannot overlap cached rows', () => {
		// given
		const query = instancesByProcessInfiniteQuery();
		const firstPage = createPage({itemCount: PAGE_SIZE, totalItems: PAGE_SIZE * 3});
		const firstPageParam = {from: 10, limit: PAGE_SIZE};

		// when
		const previousPageParam = query.getPreviousPageParam?.(firstPage, [firstPage], firstPageParam, [firstPageParam]);

		// then
		expect(previousPageParam).toEqual({from: 0, limit: 10});
	});

	it('should return undefined for previous page on the first page', () => {
		// given
		const query = instancesByProcessInfiniteQuery();
		const firstPage = createPage({itemCount: PAGE_SIZE, totalItems: PAGE_SIZE * 3});

		// when
		const previousPageParam = query.getPreviousPageParam?.(firstPage, [firstPage], {from: 0, limit: PAGE_SIZE}, [
			{from: 0, limit: PAGE_SIZE},
		]);

		// then
		expect(previousPageParam).toBeUndefined();
	});

	it('recovers the exact previous offset and length for a page shorter than PAGE_SIZE, instead of guessing a fixed step back', () => {
		// given
		const query = instancesByProcessInfiniteQuery();
		const shortPageParam = {from: 900, limit: PAGE_SIZE};
		const shortPage = createPage({itemCount: 17, totalItems: 2000});
		// Fetching forward records the short page's real size so a later backward
		// fetch can recover its exact start offset and length instead of assuming
		// PAGE_SIZE, which would overlap into rows the short page already returned.
		const nextPageParam = query.getNextPageParam(shortPage, [shortPage], shortPageParam, [shortPageParam]);
		const nextPage = createPage({itemCount: PAGE_SIZE, totalItems: 2000});

		// when
		const previousPageParam = query.getPreviousPageParam!(nextPage, [nextPage], nextPageParam!, [nextPageParam!]);

		// then
		expect(previousPageParam).toEqual({from: 900, limit: 17});
	});
});
