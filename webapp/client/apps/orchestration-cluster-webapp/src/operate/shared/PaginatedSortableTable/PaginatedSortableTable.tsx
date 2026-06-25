/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {SortableTable} from '#/operate/shared/SortableTable';

type PaginationProps = {
	hasPreviousPage: boolean;
	hasNextPage: boolean;
	isFetchingPreviousPage: boolean;
	isFetchingNextPage: boolean;
	fetchPreviousPage: () => Promise<unknown>;
	fetchNextPage: () => Promise<unknown>;
};

type SortableTableProps<TRow> = Parameters<typeof SortableTable<TRow>>[0];

const ROW_HEIGHTS: Record<NonNullable<SortableTableProps<unknown>['size']>, number> = {
	xs: 24,
	sm: 32,
	md: 40,
	lg: 48,
	xl: 64,
};

type PaginatedSortableTableProps<TRow> = Omit<
	SortableTableProps<TRow>,
	'onVerticalScrollStartReach' | 'onVerticalScrollEndReach'
> & {
	pagination: PaginationProps;
};

function getScrollStepSize(size: SortableTableProps<unknown>['size']) {
	return 5 * ROW_HEIGHTS[size ?? 'sm'];
}

function PaginatedSortableTable<TRow>({pagination, ...tableProps}: PaginatedSortableTableProps<TRow>) {
	const {hasPreviousPage, hasNextPage, isFetchingPreviousPage, isFetchingNextPage, fetchPreviousPage, fetchNextPage} =
		pagination;

	const handleScrollStartReach: SortableTableProps<TRow>['onVerticalScrollStartReach'] = async (scrollDown) => {
		if (!hasPreviousPage || isFetchingPreviousPage) {
			return;
		}

		await fetchPreviousPage();
		scrollDown(getScrollStepSize(tableProps.size));
	};

	const handleScrollEndReach: SortableTableProps<TRow>['onVerticalScrollEndReach'] = () => {
		if (!hasNextPage || isFetchingNextPage) {
			return;
		}

		fetchNextPage();
	};

	return (
		<SortableTable<TRow>
			{...tableProps}
			onVerticalScrollStartReach={handleScrollStartReach}
			onVerticalScrollEndReach={handleScrollEndReach}
		/>
	);
}

export {PaginatedSortableTable};
