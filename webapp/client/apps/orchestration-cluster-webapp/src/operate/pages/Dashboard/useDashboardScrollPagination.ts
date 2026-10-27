/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback} from 'react';

const ROW_HEIGHT = 64;

type Params = {
	pageSize: number;
	hasNextPage: boolean;
	hasPreviousPage: boolean;
	isFetchingNextPage: boolean;
	isFetchingPreviousPage: boolean;
	fetchNextPage: () => Promise<unknown>;
	fetchPreviousPage: () => Promise<{
		data?: {pages: Array<{items: unknown[]}>};
		isFetchPreviousPageError?: boolean;
	}>;
};

function useDashboardScrollPagination({
	pageSize,
	hasNextPage,
	hasPreviousPage,
	isFetchingNextPage,
	isFetchingPreviousPage,
	fetchNextPage,
	fetchPreviousPage,
}: Params) {
	return useCallback(
		async (event: React.UIEvent<HTMLDivElement>) => {
			const target = event.currentTarget;
			const atBottom = Math.floor(target.scrollHeight - target.clientHeight - target.scrollTop) <= 0;
			const atTop = target.scrollTop === 0;

			if (atBottom && hasNextPage && !isFetchingNextPage) {
				await fetchNextPage();
			} else if (atTop && hasPreviousPage && !isFetchingPreviousPage) {
				const result = await fetchPreviousPage();
				if (result.isFetchPreviousPageError) {
					return;
				}
				const prependedRowCount = result.data?.pages[0]?.items.length ?? pageSize;
				target.scrollTop = prependedRowCount * ROW_HEIGHT;
			}
		},
		[
			pageSize,
			hasNextPage,
			hasPreviousPage,
			isFetchingNextPage,
			isFetchingPreviousPage,
			fetchNextPage,
			fetchPreviousPage,
		],
	);
}

export {useDashboardScrollPagination};
