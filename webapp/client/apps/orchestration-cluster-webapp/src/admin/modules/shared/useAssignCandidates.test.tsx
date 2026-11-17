/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {HttpResponse} from 'msw';
import {cleanup, renderHook} from 'vitest-browser-react';
import {afterEach, describe, expect, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {mockQueryUsersEndpoint} from '#/shared-test-modules/mock-handlers';
import {useAssignCandidates} from './useAssignCandidates';

type AssignedPage = {items: {username: string}[]};

function setUp() {
	const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
	const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
		<QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
	);

	return {Wrapper};
}

function useCandidatesOf(owner: string, loadAssigned: () => Promise<AssignedPage>) {
	return useAssignCandidates<AssignedPage>({
		kind: 'users',
		enabled: true,
		getAssignedMembersQuery: () => ({queryKey: ['assigned', owner], queryFn: loadAssigned}),
		getMemberId: ({username}) => username,
		limit: 20,
	});
}

describe('useAssignCandidates', () => {
	afterEach(async () => {
		await cleanup();
	});

	it('should not list candidates cached by another owner before its assigned members are loaded', async ({worker}) => {
		// given
		const {Wrapper} = setUp();
		worker.use(
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json({items: [{username: 'alice'}], page: {totalItems: 1}}),
			}),
		);
		const first = await renderHook(() => useCandidatesOf('empty-group', () => Promise.resolve({items: []})), {
			wrapper: Wrapper,
		});
		await vi.waitFor(() => expect(first.result.current.options).toEqual([{value: 'alice', label: 'alice'}]));

		// when
		const second = await renderHook(() => useCandidatesOf('other-group', () => new Promise<AssignedPage>(() => {})), {
			wrapper: Wrapper,
		});

		// then
		expect(second.result.current.options).toEqual([]);
		expect(second.result.current.items).toEqual([]);
	});
});
