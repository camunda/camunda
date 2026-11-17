/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import {QueryClient} from '@tanstack/react-query';
import type {Job} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '#/vitest-modules/test-extend';
import {mockQueryJobsEndpoint} from '#/shared-test-modules/mock-handlers';
import {createListenerJob} from '#/shared-test-modules/api-mocks/jobs';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {listenersQuery} from './listeners.queries';

function jobsPage(items: Job[], page: {totalItems: number; hasMoreTotalItems: boolean}) {
	return HttpResponse.json(createPaginatedResponse({items, page: {...page, startCursor: null, endCursor: null}}));
}

function fetchPages(pages: number) {
	return new QueryClient({defaultOptions: {queries: {retry: false}}}).fetchInfiniteQuery({
		...listenersQuery({processInstanceKey: '1', elementId: null, elementInstanceKey: '1'}),
		pages,
	});
}

const fullPage = Array.from({length: 50}, (_, index) => createListenerJob({jobKey: `${index}`}));

describe('listenersQuery', () => {
	it('should keep paging while the total is only a lower bound, until a short page', async ({worker}) => {
		worker.use(
			mockQueryJobsEndpoint({
				schema: z.object({page: z.object({from: z.literal(50)})}),
				successResponse: jobsPage([createListenerJob({jobKey: '50'})], {totalItems: 50, hasMoreTotalItems: true}),
				failureResponse: jobsPage(fullPage, {totalItems: 50, hasMoreTotalItems: true}),
			}),
		);

		const {pages} = await fetchPages(5);

		expect(pages.map(({items}) => items.length)).toEqual([50, 1]);
	});

	it('should not request another page after the last full page of an exact total', async ({worker}) => {
		worker.use(
			mockQueryJobsEndpoint({successResponse: jobsPage(fullPage, {totalItems: 50, hasMoreTotalItems: false})}),
		);

		const {pages} = await fetchPages(5);

		expect(pages).toHaveLength(1);
	});
});
