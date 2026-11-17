/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient, QueryClientProvider, QueryObserver, type QueryKey} from '@tanstack/react-query';
import {http, HttpResponse} from 'msw';
import {toast} from '@camunda/design-system';
import {cleanup, renderHook} from 'vitest-browser-react';
import {afterEach, describe, expect, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {mockQueryUsersByGroupEndpoint} from '#/shared-test-modules/mock-handlers';
import {useGroupMemberMutations} from './useGroupMemberMutations';

const GROUP_ID = 'group-a';
const LIST_KEY: QueryKey = ['groupMembers', GROUP_ID, 'users', {page: {from: 0, limit: 20}}];

function setUp(initial: {items: {username: string}[]; page: {totalItems: number}}) {
	const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
	queryClient.setQueryData(LIST_KEY, initial);
	// Marks the list as active, as the open tab would, without ever refetching it.
	const observer = new QueryObserver(queryClient, {
		queryKey: LIST_KEY,
		queryFn: () => queryClient.getQueryData(LIST_KEY),
		staleTime: Infinity,
	});
	const unsubscribe = observer.subscribe(() => {});

	const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
		<QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
	);

	return {queryClient, unsubscribe, Wrapper};
}

describe('useGroupMemberMutations', () => {
	afterEach(async () => {
		vi.restoreAllMocks();
		await cleanup();
	});

	it('should not confirm a removal that the search index has not caught up with', async ({worker}) => {
		// given
		const {unsubscribe, Wrapper} = setUp({
			items: [{username: 'alice'}, {username: 'bob'}, {username: 'carol'}],
			page: {totalItems: 3},
		});
		const warning = vi.spyOn(toast, 'warning').mockImplementation(() => '');
		const indexStuckAfterAlice = HttpResponse.json({
			items: [{username: 'bob'}, {username: 'carol'}],
			page: {totalItems: 2},
		});
		worker.use(
			http.delete('*/groups/:groupId/users/:username', () => new HttpResponse(null, {status: 204})),
			mockQueryUsersByGroupEndpoint({successResponse: indexStuckAfterAlice}),
		);
		const {result} = await renderHook(() => useGroupMemberMutations(GROUP_ID), {wrapper: Wrapper});

		// when
		await result.current.unassign.mutateAsync({kind: 'users', id: 'alice'});
		await result.current.unassign.mutateAsync({kind: 'users', id: 'bob'});

		// then
		await vi.waitFor(() => expect(warning).toHaveBeenCalledTimes(1), {timeout: 10_000});
		unsubscribe();
	});

	it('should show a single toast when several members are assigned at once', async ({worker}) => {
		// given
		const {unsubscribe, Wrapper} = setUp({items: [{username: 'carol'}], page: {totalItems: 1}});
		const success = vi.spyOn(toast, 'success').mockImplementation(() => '');
		const warning = vi.spyOn(toast, 'warning').mockImplementation(() => '');
		const error = vi.spyOn(toast, 'error').mockImplementation(() => '');
		worker.use(
			http.put('*/groups/:groupId/users/:username', () => new HttpResponse(null, {status: 204})),
			mockQueryUsersByGroupEndpoint({
				successResponse: HttpResponse.json({items: [{username: 'carol'}], page: {totalItems: 4}}),
			}),
		);
		const {result} = await renderHook(() => useGroupMemberMutations(GROUP_ID), {wrapper: Wrapper});

		// when
		await result.current.assign.mutateAsync({kind: 'users', ids: ['alice', 'bob', 'dave']});

		// then
		expect(success).toHaveBeenCalledExactlyOnceWith('3 members assigned');
		expect(warning).not.toHaveBeenCalled();
		expect(error).not.toHaveBeenCalled();
		unsubscribe();
	});

	it('should replace the success toast with the unconfirmed warning when the search index never catches up', async ({
		worker,
	}) => {
		// given
		const {unsubscribe, Wrapper} = setUp({items: [{username: 'carol'}], page: {totalItems: 1}});
		vi.spyOn(toast, 'success').mockImplementation(() => 'summary-toast');
		const warning = vi.spyOn(toast, 'warning').mockImplementation(() => '');
		const dismiss = vi.spyOn(toast, 'dismiss').mockImplementation(() => undefined);
		worker.use(
			http.put('*/groups/:groupId/users/:username', () => new HttpResponse(null, {status: 204})),
			mockQueryUsersByGroupEndpoint({
				successResponse: HttpResponse.json({items: [{username: 'carol'}], page: {totalItems: 1}}),
			}),
		);
		const {result} = await renderHook(() => useGroupMemberMutations(GROUP_ID), {wrapper: Wrapper});

		// when
		await result.current.assign.mutateAsync({kind: 'users', ids: ['alice', 'bob']});

		// then
		await vi.waitFor(() => expect(warning).toHaveBeenCalledOnce(), {timeout: 10_000});
		expect(dismiss).toHaveBeenCalledExactlyOnceWith('summary-toast');
		unsubscribe();
	});

	it('should report several failed assignments in a single toast', async ({worker}) => {
		// given
		const {queryClient, unsubscribe, Wrapper} = setUp({items: [{username: 'carol'}], page: {totalItems: 1}});
		const success = vi.spyOn(toast, 'success').mockImplementation(() => '');
		const warning = vi.spyOn(toast, 'warning').mockImplementation(() => '');
		const error = vi.spyOn(toast, 'error').mockImplementation(() => '');
		worker.use(
			http.put('*/groups/:groupId/users/:username', ({params}) =>
				params.username === 'alice' ? new HttpResponse(null, {status: 204}) : new HttpResponse(null, {status: 500}),
			),
			mockQueryUsersByGroupEndpoint({
				successResponse: HttpResponse.json({items: [{username: 'carol'}], page: {totalItems: 2}}),
			}),
		);
		const {result} = await renderHook(() => useGroupMemberMutations(GROUP_ID), {wrapper: Wrapper});

		// when
		await result.current.assign.mutateAsync({kind: 'users', ids: ['alice', 'bob', 'dave']});

		// then
		await vi.waitFor(() => expect(warning).toHaveBeenCalledOnce());
		expect(warning).toHaveBeenCalledWith('1 of 3 assigned', expect.anything());
		expect(success).not.toHaveBeenCalled();
		expect(error).not.toHaveBeenCalled();
		expect(queryClient.getQueryData<{items: {username: string}[]}>(LIST_KEY)?.items).toContainEqual({
			username: 'alice',
		});
		unsubscribe();
	});
});
