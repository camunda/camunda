/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {ExpandableList} from './ExpandableList';

const noop = () => {};

describe('<ExpandableList />', () => {
	it('shows a loading skeleton while pending', async () => {
		const screen = await render(
			<ExpandableList
				isPending
				isError={false}
				listTestId="list"
				dataTestId="table"
				header="Process name"
				rows={[]}
				expandedContents={{}}
				isFetchingNextPage={false}
				isFetchingPreviousPage={false}
				onScroll={noop}
			/>,
		);

		await expect.element(screen.getByTestId('list-skeleton')).toBeVisible();
		expect(screen.getByTestId('table').elements()).toHaveLength(0);
	});

	it('shows the fetch-error empty state on error', async () => {
		const screen = await render(
			<ExpandableList
				isPending={false}
				isError
				listTestId="list"
				dataTestId="table"
				header="Process name"
				rows={[]}
				expandedContents={{}}
				isFetchingNextPage={false}
				isFetchingPreviousPage={false}
				onScroll={noop}
			/>,
		);

		await expect.element(screen.getByText('Data could not be fetched')).toBeVisible();
		await expect.element(screen.getByText('Refresh the page to try again')).toBeVisible();
	});

	it('renders the caller-provided empty state instead of the table when given one', async () => {
		const screen = await render(
			<ExpandableList
				isPending={false}
				isError={false}
				emptyState={<div data-testid="custom-empty-state">Nothing here</div>}
				listTestId="list"
				dataTestId="table"
				header="Process name"
				rows={[]}
				expandedContents={{}}
				isFetchingNextPage={false}
				isFetchingPreviousPage={false}
				onScroll={noop}
			/>,
		);

		await expect.element(screen.getByTestId('custom-empty-state')).toBeVisible();
		expect(screen.getByTestId('table').elements()).toHaveLength(0);
	});

	it('renders each row content', async () => {
		const screen = await render(
			<ExpandableList
				isPending={false}
				isError={false}
				listTestId="list"
				dataTestId="table"
				header="Process name"
				rows={[
					{id: 'process-1', content: <span>Order process</span>},
					{id: 'process-2', content: <span>Shipping process</span>},
				]}
				expandedContents={{}}
				isFetchingNextPage={false}
				isFetchingPreviousPage={false}
				onScroll={noop}
			/>,
		);

		await expect.element(screen.getByText('Order process')).toBeVisible();
		await expect.element(screen.getByText('Shipping process')).toBeVisible();
	});

	it('reveals a row expandedContents entry when its toggle is expanded', async () => {
		const screen = await render(
			<ExpandableList
				isPending={false}
				isError={false}
				listTestId="list"
				dataTestId="table"
				header="Process name"
				rows={[{id: 'process-1', content: <span>Order process</span>}]}
				expandedContents={{
					'process-1': <div>Version details for order process</div>,
				}}
				isFetchingNextPage={false}
				isFetchingPreviousPage={false}
				onScroll={noop}
			/>,
		);

		expect(screen.getByText('Version details for order process').elements()).toHaveLength(0);

		await userEvent.click(screen.getByRole('button', {name: 'Expand row'}));

		await expect.element(screen.getByText('Version details for order process')).toBeVisible();
	});

	it('shows a loading indicator above the list while fetching the previous page', async () => {
		const screen = await render(
			<ExpandableList
				isPending={false}
				isError={false}
				listTestId="list"
				dataTestId="table"
				header="Process name"
				rows={[{id: 'process-1', content: <span>Order process</span>}]}
				expandedContents={{}}
				isFetchingNextPage={false}
				isFetchingPreviousPage
				onScroll={noop}
			/>,
		);

		await expect.element(screen.getByTestId('list-loading-previous')).toBeVisible();
		expect(screen.getByTestId('list-loading-next').elements()).toHaveLength(0);
	});

	it('shows a loading indicator below the list while fetching the next page', async () => {
		const screen = await render(
			<ExpandableList
				isPending={false}
				isError={false}
				listTestId="list"
				dataTestId="table"
				header="Process name"
				rows={[{id: 'process-1', content: <span>Order process</span>}]}
				expandedContents={{}}
				isFetchingNextPage
				isFetchingPreviousPage={false}
				onScroll={noop}
			/>,
		);

		await expect.element(screen.getByTestId('list-loading-next')).toBeVisible();
		expect(screen.getByTestId('list-loading-previous').elements()).toHaveLength(0);
	});
});
