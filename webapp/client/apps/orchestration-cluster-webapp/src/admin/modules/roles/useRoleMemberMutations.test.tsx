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
import {mockQueryUsersByRoleEndpoint} from '#/shared-test-modules/mock-handlers';
import {useRoleMemberMutations} from './useRoleMemberMutations';

const ROLE_ID = 'role-a';

function setUp() {
	const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
	const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries');

	const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
		<QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
	);

	return {invalidateQueries, Wrapper};
}

function spyOnToasts() {
	return {
		success: vi.spyOn(toast, 'success').mockImplementation(() => ''),
		warning: vi.spyOn(toast, 'warning').mockImplementation(() => ''),
		error: vi.spyOn(toast, 'error').mockImplementation(() => ''),
	};
}

describe('useRoleMemberMutations', () => {
	afterEach(async () => {
		vi.restoreAllMocks();
		await cleanup();
	});

	it('should show a single toast when several members are assigned at once', async ({worker}) => {
		// given
		const {invalidateQueries, Wrapper} = setUp();
		const toasts = spyOnToasts();
		worker.use(
			http.put('*/roles/:roleId/users/:username', () => new HttpResponse(null, {status: 204})),
			mockQueryUsersByRoleEndpoint({
				successResponse: HttpResponse.json({
					items: [{username: 'alice'}, {username: 'bob'}, {username: 'dave'}],
					page: {totalItems: 3},
				}),
			}),
		);
		const {result} = await renderHook(() => useRoleMemberMutations(ROLE_ID), {wrapper: Wrapper});

		// when
		await result.current.assign.mutateAsync({kind: 'users', ids: ['alice', 'bob', 'dave']});

		// then
		expect(toasts.success).toHaveBeenCalledExactlyOnceWith('3 members assigned');
		expect(toasts.warning).not.toHaveBeenCalled();
		expect(toasts.error).not.toHaveBeenCalled();
		await vi.waitFor(
			() => expect(invalidateQueries).toHaveBeenCalledWith({queryKey: ['roleMembers', ROLE_ID, 'users']}),
			{timeout: 10_000},
		);
	});

	it('should report several failed assignments in a single toast', async ({worker}) => {
		// given
		const {invalidateQueries, Wrapper} = setUp();
		const toasts = spyOnToasts();
		worker.use(
			http.put('*/roles/:roleId/users/:username', ({params}) =>
				params.username === 'alice'
					? new HttpResponse(null, {status: 204})
					: HttpResponse.json(
							{type: 'about:blank', title: 'CONFLICT', status: 409, detail: 'already a member', instance: '/v2/roles'},
							{status: 409},
						),
			),
			mockQueryUsersByRoleEndpoint({
				successResponse: HttpResponse.json({items: [{username: 'alice'}], page: {totalItems: 1}}),
			}),
		);
		const {result} = await renderHook(() => useRoleMemberMutations(ROLE_ID), {wrapper: Wrapper});

		// when
		await result.current.assign.mutateAsync({kind: 'users', ids: ['alice', 'bob']});

		// then
		await vi.waitFor(() => expect(toasts.warning).toHaveBeenCalledOnce());
		expect(toasts.warning).toHaveBeenCalledWith('1 of 2 assigned', {description: 'already a member'});
		expect(toasts.success).not.toHaveBeenCalled();
		expect(toasts.error).not.toHaveBeenCalled();
		await vi.waitFor(
			() => expect(invalidateQueries).toHaveBeenCalledWith({queryKey: ['roleMembers', ROLE_ID, 'users']}),
			{timeout: 10_000},
		);
	});
});
