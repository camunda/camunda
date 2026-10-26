/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, vi} from 'vitest';
import {render} from 'vitest-browser-react';
import {createMemoryHistory, createRootRoute, createRoute, createRouter, RouterProvider} from '@tanstack/react-router';
import {it} from '#/vitest-modules/test-extend';
import {setUpFakeIntersectionObserver} from '#/vitest-modules/fake-intersection-observer';
import {PaginatedSortableTable} from './PaginatedSortableTable';

type Row = {id: string; name: string};

const ROWS: Row[] = [
	{id: 'row-1', name: 'Order process'},
	{id: 'row-2', name: 'Shipping process'},
];

const COLUMNS: React.ComponentProps<typeof PaginatedSortableTable<Row>>['columns'] = [
	{key: 'name', label: 'Name', render: (row) => row.name},
];

type RenderOverrides = Partial<React.ComponentProps<typeof PaginatedSortableTable<Row>>>;

function defaultPagination(): React.ComponentProps<typeof PaginatedSortableTable<Row>>['pagination'] {
	return {
		hasPreviousPage: false,
		hasNextPage: false,
		isFetchingPreviousPage: false,
		isFetchingNextPage: false,
		fetchPreviousPage: vi.fn().mockResolvedValue(undefined),
		fetchNextPage: vi.fn().mockResolvedValue(undefined),
	};
}

async function renderTable(overrides: RenderOverrides = {}) {
	const props = {
		columns: COLUMNS,
		rows: ROWS,
		rowKey: (row: Row) => row.id,
		ariaLabel: 'Process instances',
		pagination: defaultPagination(),
		'data-testid': 'table',
		...overrides,
	} as React.ComponentProps<typeof PaginatedSortableTable<Row>>;
	const rootRoute = createRootRoute();
	const testRoute = createRoute({
		getParentRoute: () => rootRoute,
		path: '/',
		component: () => <PaginatedSortableTable<Row> {...props} />,
	});
	const router = createRouter({
		routeTree: rootRoute.addChildren([testRoute]),
		history: createMemoryHistory({initialEntries: ['/']}),
		defaultPendingMinMs: 0,
	});

	await router.load();

	return render(<RouterProvider router={router} />);
}

describe('<PaginatedSortableTable />', () => {
	it('should render every row', async () => {
		// given / when
		const screen = await renderTable();

		// then
		await expect.element(screen.getByText('Order process')).toBeVisible();
		await expect.element(screen.getByText('Shipping process')).toBeVisible();
	});

	describe('infinite scroll', () => {
		const {getObserver} = setUpFakeIntersectionObserver();

		it('should fetch the previous page when the top sentinel is reached', async () => {
			// given
			const fetchPreviousPage = vi.fn().mockResolvedValue(undefined);
			const pagination = {...defaultPagination(), hasPreviousPage: true, fetchPreviousPage};
			await renderTable({pagination, size: 'md'});

			// when
			const observer = getObserver();
			const topSentinel = document.querySelector('[data-slot="data-table-top-sentinel"]')!;
			observer.intersect(topSentinel);

			// then
			await expect.poll(() => fetchPreviousPage.mock.calls.length).toBeGreaterThan(0);
		});

		it('should not fetch the previous page when there is no previous page', async () => {
			// given
			const fetchPreviousPage = vi.fn().mockResolvedValue(undefined);
			const pagination = {...defaultPagination(), hasPreviousPage: false, fetchPreviousPage};
			await renderTable({pagination});

			// when
			const observer = getObserver();
			const topSentinel = document.querySelector('[data-slot="data-table-top-sentinel"]')!;
			observer.intersect(topSentinel);

			// then
			expect(fetchPreviousPage).not.toHaveBeenCalled();
		});

		it('should fetch the next page when the bottom sentinel is reached', async () => {
			// given
			const fetchNextPage = vi.fn().mockResolvedValue(undefined);
			const pagination = {...defaultPagination(), hasNextPage: true, fetchNextPage};
			await renderTable({pagination});

			// when
			const observer = getObserver();
			const bottomSentinel = document.querySelector('[data-slot="data-table-bottom-sentinel"]')!;
			observer.intersect(bottomSentinel);

			// then
			expect(fetchNextPage).toHaveBeenCalled();
		});

		it('should not fetch the next page while already fetching it', async () => {
			// given
			const fetchNextPage = vi.fn().mockResolvedValue(undefined);
			const pagination = {...defaultPagination(), hasNextPage: true, isFetchingNextPage: true, fetchNextPage};
			await renderTable({pagination});

			// when
			const observer = getObserver();
			const bottomSentinel = document.querySelector('[data-slot="data-table-bottom-sentinel"]')!;
			observer.intersect(bottomSentinel);

			// then
			expect(fetchNextPage).not.toHaveBeenCalled();
		});

		it('should not fetch the previous page while the next page is already being fetched', async () => {
			// given: an infinite-query fetch in one direction would be cancelled and discarded by
			// starting a fetch in the other direction, since both share one request pipeline
			const fetchPreviousPage = vi.fn().mockResolvedValue(undefined);
			const pagination = {
				...defaultPagination(),
				hasPreviousPage: true,
				isFetchingNextPage: true,
				fetchPreviousPage,
			};
			await renderTable({pagination});

			// when
			const observer = getObserver();
			const topSentinel = document.querySelector('[data-slot="data-table-top-sentinel"]')!;
			observer.intersect(topSentinel);

			// then
			expect(fetchPreviousPage).not.toHaveBeenCalled();
		});

		it('should not fetch the next page while the previous page is already being fetched', async () => {
			// given
			const fetchNextPage = vi.fn().mockResolvedValue(undefined);
			const pagination = {
				...defaultPagination(),
				hasNextPage: true,
				isFetchingPreviousPage: true,
				fetchNextPage,
			};
			await renderTable({pagination});

			// when
			const observer = getObserver();
			const bottomSentinel = document.querySelector('[data-slot="data-table-bottom-sentinel"]')!;
			observer.intersect(bottomSentinel);

			// then
			expect(fetchNextPage).not.toHaveBeenCalled();
		});
	});
});
