/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient, QueryClientProvider, QueryObserver, type QueryKey} from '@tanstack/react-query';
import {HttpResponse} from 'msw';
import {cleanup, renderHook} from 'vitest-browser-react';
import {afterEach, describe, expect, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {
	mockAssignUserToTenantEndpoint,
	mockQueryUsersByTenantEndpoint,
	mockUnassignUserFromTenantEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {useTenantMemberMutations} from './useTenantMemberMutations';

type UsersPage = {items: {username: string}[]; page: {totalItems: number}};

const TENANT_ID = 'tenant-a';
const LIST_KEY: QueryKey = [
	'queryUsersByTenant',
	TENANT_ID,
	{sort: [{field: 'username', order: 'ASC'}], page: {from: 0, limit: 20}},
];

function setUp(initial: UsersPage) {
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

describe('useTenantMemberMutations', () => {
	afterEach(async () => {
		await cleanup();
	});

	it('should show an assigned user right away while the search index is still catching up', async ({worker}) => {
		// given
		const {queryClient, unsubscribe, Wrapper} = setUp({items: [{username: 'carol'}], page: {totalItems: 1}});
		worker.use(
			mockAssignUserToTenantEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockQueryUsersByTenantEndpoint({
				successResponse: HttpResponse.json({items: [{username: 'carol'}], page: {totalItems: 1}}),
			}),
		);
		const {result} = await renderHook(() => useTenantMemberMutations('users', TENANT_ID), {wrapper: Wrapper});

		// when
		await result.current.assign.mutateAsync([{username: 'alice'}]);

		// then
		expect(queryClient.getQueryData<UsersPage>(LIST_KEY)).toEqual({
			items: [{username: 'alice'}, {username: 'carol'}],
			page: {totalItems: 2},
		});
		unsubscribe();
	});

	it('should invalidate the members once the search index reflects the assignment', async ({worker}) => {
		// given
		const {queryClient, unsubscribe, Wrapper} = setUp({items: [], page: {totalItems: 0}});
		const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries');
		worker.use(
			mockAssignUserToTenantEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockQueryUsersByTenantEndpoint({
				successResponse: HttpResponse.json({items: [{username: 'alice'}], page: {totalItems: 1}}),
			}),
		);
		const {result} = await renderHook(() => useTenantMemberMutations('users', TENANT_ID), {wrapper: Wrapper});

		// when
		await result.current.assign.mutateAsync([{username: 'alice'}]);

		// then
		await vi.waitFor(() =>
			expect(invalidateQueries).toHaveBeenCalledWith({queryKey: ['queryUsersByTenant', TENANT_ID]}),
		);
		unsubscribe();
	});

	it('should remove an unassigned user right away', async ({worker}) => {
		// given
		const {queryClient, unsubscribe, Wrapper} = setUp({
			items: [{username: 'alice'}, {username: 'carol'}],
			page: {totalItems: 2},
		});
		worker.use(
			mockUnassignUserFromTenantEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockQueryUsersByTenantEndpoint({
				successResponse: HttpResponse.json({items: [{username: 'alice'}], page: {totalItems: 2}}),
			}),
		);
		const {result} = await renderHook(() => useTenantMemberMutations('users', TENANT_ID), {wrapper: Wrapper});

		// when
		await result.current.unassign.mutateAsync('alice');

		// then
		expect(queryClient.getQueryData<UsersPage>(LIST_KEY)).toEqual({
			items: [{username: 'carol'}],
			page: {totalItems: 1},
		});
		unsubscribe();
	});
});
