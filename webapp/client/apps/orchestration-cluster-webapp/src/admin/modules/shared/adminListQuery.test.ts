/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient, queryOptions} from '@tanstack/react-query';
import {describe, expect, it, vi} from 'vitest';
import {ADMIN_LIST_STALE_TIME_MS, adminListQuery} from './adminListQuery';

describe('adminListQuery', () => {
	it('should keep optimistically patched data instead of refetching within the stale window', async () => {
		// given
		const queryFn = vi.fn().mockResolvedValue(['from-stale-index']);
		const options = queryOptions({queryKey: ['queryAdminList'], queryFn});
		const queryClient = new QueryClient();
		queryClient.setQueryData(options.queryKey, ['optimistic-row']);

		// when
		const data = await queryClient.query(adminListQuery(options));

		// then
		expect(data).toEqual(['optimistic-row']);
		expect(queryFn).not.toHaveBeenCalled();
	});

	it('should refetch once the stale window has passed', async () => {
		// given
		vi.useFakeTimers();
		try {
			const queryFn = vi.fn().mockResolvedValue(['fresh']);
			const options = queryOptions({queryKey: ['queryAdminList'], queryFn});
			const queryClient = new QueryClient();
			queryClient.setQueryData(options.queryKey, ['optimistic-row']);

			// when
			vi.advanceTimersByTime(ADMIN_LIST_STALE_TIME_MS + 1);
			const data = await queryClient.query(adminListQuery(options));

			// then
			expect(data).toEqual(['fresh']);
			expect(queryFn).toHaveBeenCalledOnce();
		} finally {
			vi.useRealTimers();
		}
	});
});
