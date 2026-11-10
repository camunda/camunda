/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient, QueryClientProvider, QueryObserver, type QueryKey} from '@tanstack/react-query';
import type {QueryMappingRulesResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {HttpResponse} from 'msw';
import type {SetupWorker} from 'msw/browser';
import {cleanup, renderHook} from 'vitest-browser-react';
import {afterEach, describe, expect, vi} from 'vitest';
import {toast} from '@camunda/design-system';
import {it} from '#/vitest-modules/test-extend';
import {
	mockCreateMappingRuleEndpoint,
	mockDeleteMappingRuleEndpoint,
	mockQueryMappingRulesEndpoint,
	mockUpdateMappingRuleEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createMappingRule, createQueryMappingRulesResponse} from '#/shared-test-modules/api-mocks/mapping-rules';
import {useMappingRuleMutations} from './useMappingRuleMutations';

const LIST_QUERY_KEY: QueryKey = ['queryMappingRules', {page: {from: 0, limit: 20}}];

const teardownCallbacks: Array<() => void> = [];

function setUpListCache(
	queryClient: QueryClient,
	response: QueryMappingRulesResponseBody,
	queryKey: QueryKey = LIST_QUERY_KEY,
) {
	queryClient.setQueryData(queryKey, response);
	const observer = new QueryObserver(queryClient, {
		queryKey,
		queryFn: () => {
			const cached = queryClient.getQueryData(queryKey);
			return cached === undefined ? Promise.reject(new Error('The mapping rules list cache is empty')) : cached;
		},
		staleTime: Infinity,
	});
	teardownCallbacks.push(observer.subscribe(() => {}));
}

async function waitForConfirmation(queryClient: QueryClient) {
	await vi.waitFor(() => expect(queryClient.isFetching()).toBe(0));
}

function getCachedList(queryClient: QueryClient, queryKey: QueryKey = LIST_QUERY_KEY) {
	return queryClient.getQueryData<QueryMappingRulesResponseBody>(queryKey);
}

async function createIntoList(worker: SetupWorker, queryKey: QueryKey) {
	const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
	const existing = createMappingRule({mappingRuleId: 'a-rule'});
	const created = createMappingRule({mappingRuleId: 'b-rule'});
	setUpListCache(queryClient, createQueryMappingRulesResponse({items: [existing]}), queryKey);
	worker.use(
		mockCreateMappingRuleEndpoint({successResponse: HttpResponse.json(created)}),
		mockQueryMappingRulesEndpoint({successResponse: HttpResponse.json({items: [created], page: {totalItems: 1}})}),
	);
	const {result} = await renderHook(() => useMappingRuleMutations(), {
		wrapper: ({children}) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>,
	});

	await result.current.create.mutateAsync(created);

	const list = getCachedList(queryClient, queryKey);
	await waitForConfirmation(queryClient);
	return {existing, list};
}

describe('useMappingRuleMutations', () => {
	afterEach(async () => {
		vi.restoreAllMocks();
		await cleanup();
		teardownCallbacks.splice(0).forEach((teardown) => teardown());
		toast.dismiss();
	});

	it('should show a created mapping rule in the cached list before the backend confirms it', async ({worker}) => {
		// given
		const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
		const existing = createMappingRule({mappingRuleId: 'a-rule'});
		const created = createMappingRule({mappingRuleId: 'b-rule'});
		setUpListCache(queryClient, createQueryMappingRulesResponse({items: [existing]}));
		worker.use(
			mockCreateMappingRuleEndpoint({successResponse: HttpResponse.json(created)}),
			mockQueryMappingRulesEndpoint({successResponse: HttpResponse.json({items: [created], page: {totalItems: 1}})}),
		);
		const {result} = await renderHook(() => useMappingRuleMutations(), {
			wrapper: ({children}) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>,
		});

		// when
		await result.current.create.mutateAsync(created);

		// then
		expect(getCachedList(queryClient)?.items.map((item) => item.mappingRuleId)).toEqual(['a-rule', 'b-rule']);
		expect(getCachedList(queryClient)?.page.totalItems).toBe(2);
		await waitForConfirmation(queryClient);
	});

	it('should show the edited values in the cached list before the backend confirms them', async ({worker}) => {
		// given
		const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
		const existing = createMappingRule({mappingRuleId: 'my-rule', name: 'Old name'});
		const updated = {...existing, name: 'New name'};
		setUpListCache(queryClient, createQueryMappingRulesResponse({items: [existing]}));
		worker.use(
			mockUpdateMappingRuleEndpoint({successResponse: HttpResponse.json(updated)}),
			mockQueryMappingRulesEndpoint({successResponse: HttpResponse.json({items: [updated], page: {totalItems: 1}})}),
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
		await waitForConfirmation(queryClient);
	});

	it('should drop a deleted mapping rule from the cached list before the backend confirms it', async ({worker}) => {
		// given
		const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
		const doomed = createMappingRule({mappingRuleId: 'doomed-rule'});
		const survivor = createMappingRule({mappingRuleId: 'survivor-rule'});
		setUpListCache(queryClient, createQueryMappingRulesResponse({items: [doomed, survivor]}));
		worker.use(
			mockDeleteMappingRuleEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockQueryMappingRulesEndpoint({successResponse: HttpResponse.json({items: [], page: {totalItems: 0}})}),
		);
		const {result} = await renderHook(() => useMappingRuleMutations(), {
			wrapper: ({children}) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>,
		});

		// when
		await result.current.remove.mutateAsync({mappingRuleId: 'doomed-rule'});

		// then
		expect(getCachedList(queryClient)?.items.map((item) => item.mappingRuleId)).toEqual(['survivor-rule']);
		expect(getCachedList(queryClient)?.page.totalItems).toBe(1);
		await waitForConfirmation(queryClient);
	});

	it('should not insert a created mapping rule into a list filtered by name', async ({worker}) => {
		// given
		const queryKey = ['queryMappingRules', {filter: {name: 'Other'}, page: {from: 0, limit: 20}}];

		// when
		const {existing, list} = await createIntoList(worker, queryKey);

		// then
		expect(list?.items).toEqual([existing]);
	});

	it('should not insert a created mapping rule into a list sorted by name', async ({worker}) => {
		// given
		const queryKey = ['queryMappingRules', {sort: [{field: 'name'}], page: {from: 0, limit: 20}}];

		// when
		const {existing, list} = await createIntoList(worker, queryKey);

		// then
		expect(list?.items).toEqual([existing]);
	});

	it('should not insert a created mapping rule into a list that is already full', async ({worker}) => {
		// given
		const queryKey = ['queryMappingRules', {page: {from: 0, limit: 1}}];

		// when
		const {existing, list} = await createIntoList(worker, queryKey);

		// then
		expect(list?.items).toEqual([existing]);
	});

	it('should warn once that the change is not confirmed when the backend never reflects it', async ({worker}) => {
		// given
		const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
		const created = createMappingRule({mappingRuleId: 'unconfirmed-rule'});
		const warning = vi.spyOn(toast, 'warning').mockImplementation(() => '');
		setUpListCache(queryClient, createQueryMappingRulesResponse({items: []}));
		worker.use(
			mockCreateMappingRuleEndpoint({successResponse: HttpResponse.json(created)}),
			mockQueryMappingRulesEndpoint({successResponse: HttpResponse.json({items: [], page: {totalItems: 0}})}),
		);
		const {result} = await renderHook(() => useMappingRuleMutations(), {
			wrapper: ({children}) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>,
		});

		// when
		await result.current.create.mutateAsync(created);

		// then
		await vi.waitFor(() => expect(warning).toHaveBeenCalledOnce(), {timeout: 10_000});
		expect(getCachedList(queryClient)?.items).toEqual([created]);
		await waitForConfirmation(queryClient);
	});
});
