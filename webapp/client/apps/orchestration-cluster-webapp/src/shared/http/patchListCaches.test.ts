/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient, QueryObserver, type QueryKey} from '@tanstack/react-query';
import {describe, expect, it} from 'vitest';
import {patchListCaches, removeFromListCaches} from './patchListCaches';

type Item = {id: string; name: string};

function observeAsActive(queryClient: QueryClient, queryKey: QueryKey, data: Item[]) {
	queryClient.setQueryData(queryKey, {items: data, page: {totalItems: data.length}});
	// A real fetch should never happen in these tests; this observer exists only to mark the query
	// "active" (so `patchListCaches`'s `type: 'active'` filter matches it), seeded from the key
	// itself so the lint rule guarding against stale closures over untracked data stays satisfied.
	const observer = new QueryObserver(queryClient, {
		queryKey,
		queryFn: () => queryClient.getQueryData(queryKey),
	});
	const unsubscribe = observer.subscribe(() => {});
	return unsubscribe;
}

describe('patchListCaches', () => {
	it('should prepend a new item and bump totalItems on every active matching list query', () => {
		// given
		const queryClient = new QueryClient();
		const unsubscribe = observeAsActive(queryClient, ['items', {page: 1}], [{id: 'a', name: 'Alpha'}]);

		// when
		patchListCaches(queryClient, {
			queryKeyPrefix: ['items'],
			item: {id: 'b', name: 'Beta'},
			getId: (item: Item) => item.id,
		});

		// then
		expect(queryClient.getQueryData(['items', {page: 1}])).toEqual({
			items: [
				{id: 'b', name: 'Beta'},
				{id: 'a', name: 'Alpha'},
			],
			page: {totalItems: 2},
		});
		unsubscribe();
	});

	it('should replace an existing item in place without changing totalItems', () => {
		// given
		const queryClient = new QueryClient();
		const unsubscribe = observeAsActive(queryClient, ['items', {page: 1}], [{id: 'a', name: 'Alpha'}]);

		// when
		patchListCaches(queryClient, {
			queryKeyPrefix: ['items'],
			item: {id: 'a', name: 'Alpha (renamed)'},
			getId: (item: Item) => item.id,
		});

		// then
		expect(queryClient.getQueryData(['items', {page: 1}])).toEqual({
			items: [{id: 'a', name: 'Alpha (renamed)'}],
			page: {totalItems: 1},
		});
		unsubscribe();
	});

	it('should insert a new item at its sorted position when a comparator is given', () => {
		// given
		const queryClient = new QueryClient();
		const unsubscribe = observeAsActive(
			queryClient,
			['items', {page: 1}],
			[
				{id: 'a', name: 'Alpha'},
				{id: 'c', name: 'Charlie'},
			],
		);

		// when
		patchListCaches(queryClient, {
			queryKeyPrefix: ['items'],
			item: {id: 'b', name: 'Bravo'},
			getId: (item: Item) => item.id,
			compare: (x: Item, y: Item) => x.name.localeCompare(y.name),
		});

		// then
		expect(queryClient.getQueryData(['items', {page: 1}])).toEqual({
			items: [
				{id: 'a', name: 'Alpha'},
				{id: 'b', name: 'Bravo'},
				{id: 'c', name: 'Charlie'},
			],
			page: {totalItems: 3},
		});
		unsubscribe();
	});

	it("should give the comparator the matched query's own key", () => {
		// given
		const queryClient = new QueryClient();
		const unsubscribe = observeAsActive(
			queryClient,
			['items', {sort: 'desc'}],
			[
				{id: 'a', name: 'Alpha'},
				{id: 'c', name: 'Charlie'},
			],
		);

		// when
		patchListCaches(queryClient, {
			queryKeyPrefix: ['items'],
			item: {id: 'b', name: 'Bravo'},
			getId: (item: Item) => item.id,
			compare: (x: Item, y: Item, queryKey: QueryKey) => {
				const [, params] = queryKey as [string, {sort: 'asc' | 'desc'}];
				return params.sort === 'desc' ? y.name.localeCompare(x.name) : x.name.localeCompare(y.name);
			},
		});

		// then
		expect(queryClient.getQueryData(['items', {sort: 'desc'}])).toEqual({
			items: [
				{id: 'c', name: 'Charlie'},
				{id: 'b', name: 'Bravo'},
				{id: 'a', name: 'Alpha'},
			],
			page: {totalItems: 3},
		});
		unsubscribe();
	});

	it('should leave list queries with no active observer untouched', () => {
		// given
		const queryClient = new QueryClient();
		queryClient.setQueryData(['items', {page: 1}], {items: [{id: 'a', name: 'Alpha'}], page: {totalItems: 1}});

		// when
		patchListCaches(queryClient, {
			queryKeyPrefix: ['items'],
			item: {id: 'b', name: 'Beta'},
			getId: (item: Item) => item.id,
		});

		// then
		expect(queryClient.getQueryData(['items', {page: 1}])).toEqual({
			items: [{id: 'a', name: 'Alpha'}],
			page: {totalItems: 1},
		});
	});
});

describe('removeFromListCaches', () => {
	it('should remove the matching item and decrement totalItems on every active matching list query', () => {
		// given
		const queryClient = new QueryClient();
		const unsubscribe = observeAsActive(
			queryClient,
			['items', {page: 1}],
			[
				{id: 'a', name: 'Alpha'},
				{id: 'b', name: 'Beta'},
			],
		);

		// when
		removeFromListCaches(queryClient, {
			queryKeyPrefix: ['items'],
			id: 'a',
			getId: (item: Item) => item.id,
		});

		// then
		expect(queryClient.getQueryData(['items', {page: 1}])).toEqual({
			items: [{id: 'b', name: 'Beta'}],
			page: {totalItems: 1},
		});
		unsubscribe();
	});

	it('should not go below zero totalItems', () => {
		// given
		const queryClient = new QueryClient();
		const unsubscribe = observeAsActive(queryClient, ['items', {page: 1}], [{id: 'a', name: 'Alpha'}]);
		queryClient.setQueryData(['items', {page: 1}], {items: [{id: 'a', name: 'Alpha'}], page: {totalItems: 0}});

		// when
		removeFromListCaches(queryClient, {
			queryKeyPrefix: ['items'],
			id: 'a',
			getId: (item: Item) => item.id,
		});

		// then
		expect(queryClient.getQueryData(['items', {page: 1}])).toEqual({items: [], page: {totalItems: 0}});
		unsubscribe();
	});

	it('should leave the cache untouched when the id is not present', () => {
		// given
		const queryClient = new QueryClient();
		const unsubscribe = observeAsActive(queryClient, ['items', {page: 1}], [{id: 'a', name: 'Alpha'}]);

		// when
		removeFromListCaches(queryClient, {
			queryKeyPrefix: ['items'],
			id: 'missing',
			getId: (item: Item) => item.id,
		});

		// then
		expect(queryClient.getQueryData(['items', {page: 1}])).toEqual({
			items: [{id: 'a', name: 'Alpha'}],
			page: {totalItems: 1},
		});
		unsubscribe();
	});

	it('should also remove the matching item from a list query with no active observer', () => {
		// A query goes inactive (but stays cached) once its last observer unmounts, e.g. navigating
		// from the list to a detail route. Deleting there must still clean up that cached page so a
		// Back navigation doesn't rehydrate the just-deleted row before the next refetch.
		// given
		const queryClient = new QueryClient();
		queryClient.setQueryData(['items', {page: 1}], {items: [{id: 'a', name: 'Alpha'}], page: {totalItems: 1}});

		// when
		removeFromListCaches(queryClient, {
			queryKeyPrefix: ['items'],
			id: 'a',
			getId: (item: Item) => item.id,
		});

		// then
		expect(queryClient.getQueryData(['items', {page: 1}])).toEqual({items: [], page: {totalItems: 0}});
	});
});
