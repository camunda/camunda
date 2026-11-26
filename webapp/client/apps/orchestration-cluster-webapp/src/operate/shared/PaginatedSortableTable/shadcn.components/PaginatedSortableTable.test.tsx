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
		it('should fetch the previous page when the top of the table is in view', async () => {
			const fetchPreviousPage = vi.fn().mockResolvedValue(undefined);
			const pagination = {...defaultPagination(), hasPreviousPage: true, fetchPreviousPage};

			await renderTable({pagination, size: 'md'});

			await expect.poll(() => fetchPreviousPage).toHaveBeenCalled();
		});

		it('should fetch only the next page when there is no previous page', async () => {
			const fetchPreviousPage = vi.fn().mockResolvedValue(undefined);
			const fetchNextPage = vi.fn().mockResolvedValue(undefined);
			const pagination = {...defaultPagination(), hasNextPage: true, fetchPreviousPage, fetchNextPage};

			await renderTable({pagination});

			await expect.poll(() => fetchNextPage).toHaveBeenCalled();
			expect(fetchPreviousPage).not.toHaveBeenCalled();
		});
	});
});
