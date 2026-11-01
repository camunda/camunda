/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient, QueryClientProvider, QueryObserver} from '@tanstack/react-query';
import type {QueryMappingRulesResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {HttpResponse} from 'msw';
import {cleanup, renderHook} from 'vitest-browser-react';
import {afterEach, describe, expect, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {
	mockCreateMappingRuleEndpoint,
	mockDeleteMappingRuleEndpoint,
	mockGetMappingRuleEndpoint,
	mockUpdateMappingRuleEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createMappingRule, createQueryMappingRulesResponse} from '#/shared-test-modules/api-mocks/mapping-rules';
import {useMappingRuleMutations} from './useMappingRuleMutations';

const LIST_QUERY_KEY = ['queryMappingRules', {page: {from: 0, limit: 20}}];

function setUpListCache(queryClient: QueryClient, response: QueryMappingRulesResponseBody) {
	queryClient.setQueryData(LIST_QUERY_KEY, response);
	const observer = new QueryObserver(queryClient, {
		queryKey: LIST_QUERY_KEY,
		queryFn: () => Promise.reject(new Error('The cached mapping rules list should not be refetched in this test')),
		staleTime: Infinity,
	});
	return observer.subscribe(() => {});
}

function getCachedList(queryClient: QueryClient) {
	return queryClient.getQueryData<QueryMappingRulesResponseBody>(LIST_QUERY_KEY);
}

describe('useMappingRuleMutations', () => {
	afterEach(async () => {
		await cleanup();
	});

	it('should show a created mapping rule in the cached list before the backend confirms it', async ({worker}) => {
		// given
		const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
		const existing = createMappingRule({mappingRuleId: 'a-rule'});
		const created = createMappingRule({mappingRuleId: 'b-rule'});
		const unsubscribe = setUpListCache(queryClient, createQueryMappingRulesResponse({items: [existing]}));
		worker.use(
			mockCreateMappingRuleEndpoint({successResponse: HttpResponse.json(created)}),
			mockGetMappingRuleEndpoint({successResponse: new HttpResponse(null, {status: 404})}),
		);
		const {result} = await renderHook(() => useMappingRuleMutations(), {
			wrapper: ({children}) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>,
		});

		// when
		await result.current.create.mutateAsync(created);

		// then
		expect(getCachedList(queryClient)?.items.map((item) => item.mappingRuleId)).toEqual(['a-rule', 'b-rule']);
		expect(getCachedList(queryClient)?.page.totalItems).toBe(2);
		unsubscribe();
	});

	it('should show the edited values in the cached list before the backend confirms them', async ({worker}) => {
		// given
		const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
		const existing = createMappingRule({mappingRuleId: 'my-rule', name: 'Old name'});
		const updated = {...existing, name: 'New name'};
		const unsubscribe = setUpListCache(queryClient, createQueryMappingRulesResponse({items: [existing]}));
		worker.use(
			mockUpdateMappingRuleEndpoint({successResponse: HttpResponse.json(updated)}),
			mockGetMappingRuleEndpoint({successResponse: HttpResponse.json(existing)}),
		);
		const {result} = await renderHook(() => useMappingRuleMutations(), {
			wrapper: ({children}) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>,
		});

		// when
		await result.current.update.mutateAsync({
			mappingRuleId: 'my-rule',
			name: 'New name',
			claimName: 'email',
			claimValue: 'x',
		});

		// then
		expect(getCachedList(queryClient)?.items.map((item) => item.name)).toEqual(['New name']);
		expect(getCachedList(queryClient)?.page.totalItems).toBe(1);
		unsubscribe();
	});

	it('should drop a deleted mapping rule from the cached list before the backend confirms it', async ({worker}) => {
		// given
		const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
		const doomed = createMappingRule({mappingRuleId: 'doomed-rule'});
		const survivor = createMappingRule({mappingRuleId: 'survivor-rule'});
		const unsubscribe = setUpListCache(queryClient, createQueryMappingRulesResponse({items: [doomed, survivor]}));
		worker.use(
			mockDeleteMappingRuleEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetMappingRuleEndpoint({successResponse: HttpResponse.json(doomed)}),
		);
		const {result} = await renderHook(() => useMappingRuleMutations(), {
			wrapper: ({children}) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>,
		});

		// when
		await result.current.remove.mutateAsync({mappingRuleId: 'doomed-rule'});

		// then
		await vi.waitFor(() => expect(result.current.remove.isSuccess).toBe(true));
		expect(getCachedList(queryClient)?.items.map((item) => item.mappingRuleId)).toEqual(['survivor-rule']);
		expect(getCachedList(queryClient)?.page.totalItems).toBe(1);
		unsubscribe();
	});
});
