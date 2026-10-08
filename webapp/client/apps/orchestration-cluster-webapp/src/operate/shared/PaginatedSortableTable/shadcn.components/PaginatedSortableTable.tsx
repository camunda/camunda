/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {SortableTable} from '#/operate/shared/SortableTable/shadcn.components/SortableTable';

type PaginationProps = {
	hasPreviousPage: boolean;
	hasNextPage: boolean;
	isFetchingPreviousPage: boolean;
	isFetchingNextPage: boolean;
	fetchPreviousPage: () => Promise<unknown>;
	fetchNextPage: () => Promise<unknown>;
};

type SortableTableProps<TRow> = Parameters<typeof SortableTable<TRow>>[0];

type DistributiveOmit<T, K extends PropertyKey> = T extends unknown ? Omit<T, K> : never;

type PaginatedSortableTableProps<TRow> = DistributiveOmit<
	SortableTableProps<TRow>,
	| 'onVerticalScrollStartReach'
	| 'onVerticalScrollEndReach'
	| 'isFetchingPreviousPage'
	| 'isFetchingNextPage'
	| 'hasPreviousPage'
	| 'hasNextPage'
> & {
	pagination: PaginationProps;
};

function PaginatedSortableTable<TRow>({pagination, ...tableProps}: PaginatedSortableTableProps<TRow>) {
	const {hasPreviousPage, hasNextPage, isFetchingPreviousPage, isFetchingNextPage, fetchPreviousPage, fetchNextPage} =
		pagination;

	const handleScrollStartReach = () => {
		// Infinite-query page fetches share one request pipeline: starting fetchPreviousPage()
		// while fetchNextPage() is still in flight (or vice versa, below) would cancel and
		// discard the other direction's request. Block either while the opposite is loading,
		// matching the Dashboard pagination guard in ExpandableList.tsx.
		if (!hasPreviousPage || isFetchingPreviousPage || isFetchingNextPage) {
			return;
		}

		fetchPreviousPage();
	};

	const handleScrollEndReach = () => {
		if (!hasNextPage || isFetchingNextPage || isFetchingPreviousPage) {
			return;
		}

		fetchNextPage();
	};

	return (
		<SortableTable<TRow>
			{...tableProps}
			hasPreviousPage={hasPreviousPage}
			hasNextPage={hasNextPage}
			isFetchingPreviousPage={isFetchingPreviousPage}
			isFetchingNextPage={isFetchingNextPage}
			onVerticalScrollStartReach={handleScrollStartReach}
			onVerticalScrollEndReach={handleScrollEndReach}
		/>
	);
}

export {PaginatedSortableTable};
