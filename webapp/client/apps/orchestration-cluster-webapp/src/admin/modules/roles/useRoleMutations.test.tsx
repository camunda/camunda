/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {http, HttpResponse} from 'msw';
import {toast} from '@camunda/design-system';
import {cleanup, renderHook} from 'vitest-browser-react';
import {afterEach, describe, expect, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {useRoleMutations} from './useRoleMutations';

const ROLE = {roleId: 'role-a', name: 'Role A', description: 'first'};

function setUp() {
	const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
	const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries');

	const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
		<QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
	);

	return {invalidateQueries, Wrapper};
}

function searchResponse(items: (typeof ROLE)[]) {
	return HttpResponse.json({items, page: {totalItems: items.length}});
}

describe('useRoleMutations', () => {
	afterEach(async () => {
		vi.restoreAllMocks();
		await cleanup();
	});

	it('should not refresh the role list until search no longer returns a deleted role', async ({worker}) => {
		// given
		const {invalidateQueries, Wrapper} = setUp();
		vi.spyOn(toast, 'success').mockImplementation(() => '');
		let searches = 0;
		worker.use(
			http.delete('*/roles/:roleId', () => new HttpResponse(null, {status: 204})),
			http.post('*/roles/search', () => {
				searches += 1;
				return searchResponse(searches < 2 ? [ROLE] : []);
			}),
		);
		const {result} = await renderHook(() => useRoleMutations(), {wrapper: Wrapper});

		// when
		await result.current.remove.mutateAsync(ROLE.roleId);

		// then
		await vi.waitFor(() => expect(invalidateQueries).toHaveBeenCalledWith({queryKey: ['queryRoles']}), {
			timeout: 10_000,
		});
		expect(searches).toBe(2);
	});

	it('should not refresh the role list until search returns the updated role', async ({worker}) => {
		// given
		const {invalidateQueries, Wrapper} = setUp();
		vi.spyOn(toast, 'success').mockImplementation(() => '');
		const updated = {...ROLE, name: 'Role B'};
		let searches = 0;
		worker.use(
			http.put('*/roles/:roleId', () => HttpResponse.json(updated)),
			http.post('*/roles/search', () => {
				searches += 1;
				return searchResponse([searches < 2 ? ROLE : updated]);
			}),
		);
		const {result} = await renderHook(() => useRoleMutations(), {wrapper: Wrapper});

		// when
		await result.current.update.mutateAsync({
			roleId: ROLE.roleId,
			name: updated.name,
			description: updated.description,
		});

		// then
		await vi.waitFor(() => expect(invalidateQueries).toHaveBeenCalledWith({queryKey: ['queryRoles']}), {
			timeout: 10_000,
		});
		expect(searches).toBe(2);
	});
});
