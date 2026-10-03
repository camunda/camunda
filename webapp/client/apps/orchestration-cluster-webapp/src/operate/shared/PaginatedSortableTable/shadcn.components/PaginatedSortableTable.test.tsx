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
	const {getObserver} = setUpFakeIntersectionObserver();

	it('should render every row', async () => {
		// given / when
		const screen = await renderTable();

		// then
		await expect.element(screen.getByText('Order process')).toBeVisible();
		await expect.element(screen.getByText('Shipping process')).toBeVisible();
	});

	it('should fetch the previous page and scroll down to compensate when the top sentinel is reached', async () => {
		// given
		const fetchPreviousPage = vi.fn().mockResolvedValue(undefined);
		const pagination = {...defaultPagination(), hasPreviousPage: true, fetchPreviousPage};
		const screen = await renderTable({pagination, size: 'md'});
		const container = screen.getByTestId('table').element() as HTMLElement;
		const scrollToSpy = vi.spyOn(container, 'scrollTo').mockImplementation(() => {});

		// when — simulate scrolling up past the top sentinel
		const observer = getObserver();
		const topSentinel = screen.getByTestId('sortable-table-top-sentinel').element();
		Object.defineProperty(container, 'scrollTop', {value: 100, configurable: true});
		observer.intersect(topSentinel);
		Object.defineProperty(container, 'scrollTop', {value: 20, configurable: true});
		observer.intersect(topSentinel);

		// then
		await vi.waitFor(() => expect(fetchPreviousPage).toHaveBeenCalled());
		// `size: 'md'` is a 40px row; the scroll-step compensation is 5 rows tall
		expect(scrollToSpy).toHaveBeenCalledWith(0, 20 + 5 * 40);
	});

	it('should not fetch the previous page when there is no previous page', async () => {
		// given
		const fetchPreviousPage = vi.fn().mockResolvedValue(undefined);
		const pagination = {...defaultPagination(), hasPreviousPage: false, fetchPreviousPage};
		const screen = await renderTable({pagination});
		const container = screen.getByTestId('table').element() as HTMLElement;

		// when
		const observer = getObserver();
		const topSentinel = screen.getByTestId('sortable-table-top-sentinel').element();
		Object.defineProperty(container, 'scrollTop', {value: 100, configurable: true});
		observer.intersect(topSentinel);
		Object.defineProperty(container, 'scrollTop', {value: 20, configurable: true});
		observer.intersect(topSentinel);

		// then
		expect(fetchPreviousPage).not.toHaveBeenCalled();
	});

	it('should fetch the next page when the bottom sentinel is reached', async () => {
		// given
		const fetchNextPage = vi.fn().mockResolvedValue(undefined);
		const pagination = {...defaultPagination(), hasNextPage: true, fetchNextPage};
		const screen = await renderTable({pagination});
		const container = screen.getByTestId('table').element() as HTMLElement;

		// when — simulate scrolling down past the bottom sentinel
		const observer = getObserver();
		const bottomSentinel = screen.getByTestId('sortable-table-bottom-sentinel').element();
		Object.defineProperty(container, 'scrollTop', {value: 20, configurable: true});
		observer.intersect(bottomSentinel);
		Object.defineProperty(container, 'scrollTop', {value: 100, configurable: true});
		observer.intersect(bottomSentinel);

		// then
		expect(fetchNextPage).toHaveBeenCalled();
	});

	it('should not fetch the next page while already fetching it', async () => {
		// given
		const fetchNextPage = vi.fn().mockResolvedValue(undefined);
		const pagination = {...defaultPagination(), hasNextPage: true, isFetchingNextPage: true, fetchNextPage};
		const screen = await renderTable({pagination});
		const container = screen.getByTestId('table').element() as HTMLElement;

		// when
		const observer = getObserver();
		const bottomSentinel = screen.getByTestId('sortable-table-bottom-sentinel').element();
		Object.defineProperty(container, 'scrollTop', {value: 20, configurable: true});
		observer.intersect(bottomSentinel);
		Object.defineProperty(container, 'scrollTop', {value: 100, configurable: true});
		observer.intersect(bottomSentinel);

		// then
		expect(fetchNextPage).not.toHaveBeenCalled();
	});
});
