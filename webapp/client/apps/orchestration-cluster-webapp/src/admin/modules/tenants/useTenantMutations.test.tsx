/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient, QueryClientProvider, QueryObserver} from '@tanstack/react-query';
import {HttpResponse} from 'msw';
import {cleanup, renderHook} from 'vitest-browser-react';
import {afterEach, describe, expect, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {mockDeleteTenantEndpoint, mockGetTenantEndpoint} from '#/shared-test-modules/mock-handlers';
import {createTenant} from '#/shared-test-modules/api-mocks/tenants';
import {useTenantMutations} from './useTenantMutations';

const TENANT_ID = 'tenant-a';

describe('useTenantMutations', () => {
	afterEach(async () => {
		await cleanup();
	});

	it('should drop the cached tenant and its members once the tenant is deleted', async ({worker}) => {
		// given
		const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
		queryClient.setQueryData(['getTenant', TENANT_ID], createTenant({tenantId: TENANT_ID}));
		queryClient.setQueryData(['queryUsersByTenant', TENANT_ID, {page: {limit: 20}}], {
			items: [{username: 'alice'}],
			page: {totalItems: 1},
		});
		queryClient.setQueryData(['queryUsersByTenant', 'tenant-b', {page: {limit: 20}}], {
			items: [{username: 'bob'}],
			page: {totalItems: 1},
		});
		worker.use(
			mockDeleteTenantEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetTenantEndpoint({successResponse: new HttpResponse(null, {status: 404})}),
		);
		const {result} = await renderHook(() => useTenantMutations(), {
			wrapper: ({children}) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>,
		});

		// when
		await result.current.remove.mutateAsync({tenantId: TENANT_ID});

		// then
		await vi.waitFor(() => expect(queryClient.getQueryData(['getTenant', TENANT_ID])).toBeUndefined());
		expect(queryClient.getQueryData(['queryUsersByTenant', TENANT_ID, {page: {limit: 20}}])).toBeUndefined();
		expect(queryClient.getQueryData(['queryUsersByTenant', 'tenant-b', {page: {limit: 20}}])).toBeDefined();
	});

	it('should keep the tenant cached while the detail page still observes it', async ({worker}) => {
		// given
		const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
		queryClient.setQueryData(['getTenant', TENANT_ID], createTenant({tenantId: TENANT_ID}));
		const observer = new QueryObserver(queryClient, {
			queryKey: ['getTenant', TENANT_ID],
			queryFn: () => createTenant({tenantId: TENANT_ID}),
			staleTime: Infinity,
		});
		const unsubscribe = observer.subscribe(() => {});
		worker.use(
			mockDeleteTenantEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetTenantEndpoint({successResponse: new HttpResponse(null, {status: 404})}),
		);
		const {result} = await renderHook(() => useTenantMutations(), {
			wrapper: ({children}) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>,
		});

		// when
		await result.current.remove.mutateAsync({tenantId: TENANT_ID});
		await vi.waitFor(() => expect(result.current.remove.isSuccess).toBe(true));

		// then
		expect(queryClient.getQueryData(['getTenant', TENANT_ID])).toBeDefined();
		unsubscribe();
	});
});
