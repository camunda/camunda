/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryClient, QueryKey} from '@tanstack/react-query';

type ListQueryData<TItem> = {
	items: TItem[];
	page: {totalItems: number} & Record<string, unknown>;
};

type PatchListCachesOptions<TItem> = {
	queryKeyPrefix: QueryKey;
	item: TItem;
	getId: (item: TItem) => string;
	compare?: (a: TItem, b: TItem, queryKey: QueryKey) => number;
	/**
	 * Gates inserting a brand-new item into a matched query's cached page. Without this, an item
	 * is inserted into every active page regardless of whether it matches that query's filter or
	 * whether the page already has room for it, e.g. prepending a row that doesn't match the
	 * current search term, or growing a full page beyond its configured size.
	 */
	canInsert?: (item: TItem, queryKey: QueryKey, currentItemCount: number) => boolean;
};

function patchListCaches<TItem>(
	queryClient: QueryClient,
	{queryKeyPrefix, item, getId, compare, canInsert}: PatchListCachesOptions<TItem>,
): void {
	const id = getId(item);
	const matches = queryClient.getQueriesData<ListQueryData<TItem>>({queryKey: queryKeyPrefix, type: 'active'});

	for (const [queryKey, data] of matches) {
		if (data === undefined) {
			continue;
		}

		const withoutExisting = data.items.filter((existing) => getId(existing) !== id);
		const isNew = withoutExisting.length === data.items.length;

		if (isNew && canInsert?.(item, queryKey, data.items.length) === false) {
			continue;
		}

		const items = compare
			? [...withoutExisting, item].sort((a, b) => compare(a, b, queryKey))
			: [item, ...withoutExisting];

		queryClient.setQueryData(queryKey, {
			...data,
			items,
			page: {...data.page, totalItems: data.page.totalItems + (isNew ? 1 : 0)},
		});
	}
}

type ListQueryPage = {from?: number; limit?: number; before?: string; after?: string};

/**
 * Whether a brand-new item can be optimistically inserted into the page a list query requested:
 * only the first page, and only while it has room. On any other page the item may belong to a
 * neighbouring page, and on a full one it would push an item onto the next page - neither of
 * which a purely optimistic update can place safely.
 */
function isFirstPageWithRoom(page: ListQueryPage | undefined, currentItemCount: number): boolean {
	if (page === undefined) {
		return true;
	}
	if (page.from || page.before !== undefined || page.after !== undefined) {
		return false;
	}
	return page.limit === undefined || currentItemCount < page.limit;
}

type RemoveFromListCachesOptions<TItem> = {
	/** Partial query key every cached list query to patch shares, e.g. `['users']`. */
	queryKeyPrefix: QueryKey;
	/** The id of the just-confirmed-deleted entity to remove. */
	id: string;
	getId: (item: TItem) => string;
};

function removeFromListCaches<TItem>(
	queryClient: QueryClient,
	{queryKeyPrefix, id, getId}: RemoveFromListCachesOptions<TItem>,
): void {
	const matches = queryClient.getQueriesData<ListQueryData<TItem>>({queryKey: queryKeyPrefix});

	for (const [queryKey, data] of matches) {
		if (data === undefined) {
			continue;
		}

		const items = data.items.filter((existing) => getId(existing) !== id);
		if (items.length === data.items.length) {
			continue;
		}

		queryClient.setQueryData(queryKey, {
			...data,
			items,
			page: {...data.page, totalItems: Math.max(0, data.page.totalItems - 1)},
		});
	}
}

export {isFirstPageWithRoom, patchListCaches, removeFromListCaches};
