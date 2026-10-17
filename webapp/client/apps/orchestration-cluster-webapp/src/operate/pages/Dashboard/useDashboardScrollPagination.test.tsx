/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, vi} from 'vitest';
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {useDashboardScrollPagination} from './useDashboardScrollPagination';

const ROW_HEIGHT = 64;

const ScrollList: React.FC<{
	fetchPreviousPage: () => Promise<{
		data?: {pages: Array<{items: unknown[]}>};
		isFetchPreviousPageError?: boolean;
	}>;
}> = ({fetchPreviousPage}) => {
	const onScroll = useDashboardScrollPagination({
		pageSize: 50,
		hasNextPage: false,
		hasPreviousPage: true,
		isFetchingNextPage: false,
		isFetchingPreviousPage: false,
		fetchNextPage: () => Promise.resolve(),
		fetchPreviousPage,
	});

	return (
		<div data-testid="list" style={{height: 100, overflow: 'auto'}} onScroll={onScroll}>
			<div style={{height: 5000}} />
		</div>
	);
};

describe('useDashboardScrollPagination', () => {
	it.for([
		{prepended: 50, expected: 50 * ROW_HEIGHT},
		{prepended: 10, expected: 10 * ROW_HEIGHT},
	])(
		'should restore the scroll offset to the height of the $prepended prepended rows',
		async ({prepended, expected}) => {
			// given
			const fetchPreviousPage = vi.fn().mockResolvedValue({
				data: {pages: [{items: Array.from({length: prepended})}]},
			});
			const screen = await render(<ScrollList fetchPreviousPage={fetchPreviousPage} />);
			const list = screen.getByTestId('list').element() as HTMLDivElement;

			// when
			list.dispatchEvent(new Event('scroll'));

			// then
			await expect.poll(() => list.scrollTop).toBe(expected);
			expect(fetchPreviousPage).toHaveBeenCalledOnce();
		},
	);

	it('should keep the scroll offset when the previous page fetch fails', async () => {
		// given
		const fetchPreviousPage = vi.fn().mockResolvedValue({
			data: {pages: [{items: Array.from({length: 50})}]},
			isFetchPreviousPageError: true,
		});
		const screen = await render(<ScrollList fetchPreviousPage={fetchPreviousPage} />);
		const list = screen.getByTestId('list').element() as HTMLDivElement;

		// when
		list.dispatchEvent(new Event('scroll'));

		// then
		await expect.poll(() => fetchPreviousPage.mock.calls.length).toBe(1);
		expect(list.scrollTop).toBe(0);
	});
});
