/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {ExpandableList} from './ExpandableList';

const noop = () => {};

class FakeIntersectionObserver implements IntersectionObserver {
	static instances: FakeIntersectionObserver[] = [];

	readonly root = null;
	readonly rootMargin = '';
	readonly scrollMargin = '';
	readonly thresholds = [];
	observedTargets: Element[] = [];
	private readonly callback: IntersectionObserverCallback;

	constructor(callback: IntersectionObserverCallback) {
		this.callback = callback;
		FakeIntersectionObserver.instances.push(this);
	}

	observe(target: Element) {
		this.observedTargets.push(target);
	}

	unobserve() {}
	disconnect() {}
	takeRecords(): IntersectionObserverEntry[] {
		return [];
	}

	intersect(target: Element) {
		this.callback([{target, isIntersecting: true} as IntersectionObserverEntry], this);
	}
}

function getObserver(): FakeIntersectionObserver {
	const observer = FakeIntersectionObserver.instances[0];

	if (!observer) {
		throw new Error('No IntersectionObserver was created');
	}

	return observer;
}

let originalIntersectionObserver: typeof IntersectionObserver;

beforeEach(() => {
	originalIntersectionObserver = window.IntersectionObserver;
	FakeIntersectionObserver.instances = [];
	window.IntersectionObserver = FakeIntersectionObserver as unknown as typeof IntersectionObserver;
});

afterEach(() => {
	window.IntersectionObserver = originalIntersectionObserver;
});

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
				hasNextPage={false}
				hasPreviousPage={false}
				isFetchingNextPage={false}
				isFetchingPreviousPage={false}
				onLoadNextPage={noop}
				onLoadPreviousPage={noop}
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
				hasNextPage={false}
				hasPreviousPage={false}
				isFetchingNextPage={false}
				isFetchingPreviousPage={false}
				onLoadNextPage={noop}
				onLoadPreviousPage={noop}
			/>,
		);

		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
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
				hasNextPage={false}
				hasPreviousPage={false}
				isFetchingNextPage={false}
				isFetchingPreviousPage={false}
				onLoadNextPage={noop}
				onLoadPreviousPage={noop}
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
				hasNextPage={false}
				hasPreviousPage={false}
				isFetchingNextPage={false}
				isFetchingPreviousPage={false}
				onLoadNextPage={noop}
				onLoadPreviousPage={noop}
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
				hasNextPage={false}
				hasPreviousPage={false}
				isFetchingNextPage={false}
				isFetchingPreviousPage={false}
				onLoadNextPage={noop}
				onLoadPreviousPage={noop}
			/>,
		);

		expect(screen.getByText('Version details for order process').elements()).toHaveLength(0);

		await userEvent.click(screen.getByRole('button', {name: 'Expand row'}));

		await expect.element(screen.getByText('Version details for order process')).toBeVisible();
	});

	it('does not render an expand toggle for a row with no expandedContents entry', async () => {
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
				expandedContents={{
					'process-1': <div>Version details for order process</div>,
				}}
				hasNextPage={false}
				hasPreviousPage={false}
				isFetchingNextPage={false}
				isFetchingPreviousPage={false}
				onLoadNextPage={noop}
				onLoadPreviousPage={noop}
			/>,
		);

		expect(screen.getByRole('button', {name: 'Expand row'}).elements()).toHaveLength(1);
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
				hasNextPage={false}
				hasPreviousPage={false}
				isFetchingNextPage={false}
				isFetchingPreviousPage
				onLoadNextPage={noop}
				onLoadPreviousPage={noop}
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
				hasNextPage={false}
				hasPreviousPage={false}
				isFetchingNextPage
				isFetchingPreviousPage={false}
				onLoadNextPage={noop}
				onLoadPreviousPage={noop}
			/>,
		);

		await expect.element(screen.getByTestId('list-loading-next')).toBeVisible();
		expect(screen.getByTestId('list-loading-previous').elements()).toHaveLength(0);
	});

	it('does not render pagination sentinels when there is nothing more to load', async () => {
		const screen = await render(
			<ExpandableList
				isPending={false}
				isError={false}
				listTestId="list"
				dataTestId="table"
				header="Process name"
				rows={[{id: 'process-1', content: <span>Order process</span>}]}
				expandedContents={{}}
				hasNextPage={false}
				hasPreviousPage={false}
				isFetchingNextPage={false}
				isFetchingPreviousPage={false}
				onLoadNextPage={noop}
				onLoadPreviousPage={noop}
			/>,
		);

		expect(screen.getByTestId('list-top-sentinel').elements()).toHaveLength(0);
		expect(screen.getByTestId('list-bottom-sentinel').elements()).toHaveLength(0);
	});

	it('loads the next page when the bottom sentinel intersects', async () => {
		const onLoadNextPage = vi.fn();
		const screen = await render(
			<ExpandableList
				isPending={false}
				isError={false}
				listTestId="list"
				dataTestId="table"
				header="Process name"
				rows={[{id: 'process-1', content: <span>Order process</span>}]}
				expandedContents={{}}
				hasNextPage
				hasPreviousPage={false}
				isFetchingNextPage={false}
				isFetchingPreviousPage={false}
				onLoadNextPage={onLoadNextPage}
				onLoadPreviousPage={noop}
			/>,
		);

		const sentinel = screen.getByTestId('list-bottom-sentinel').element();
		getObserver().intersect(sentinel);

		expect(onLoadNextPage).toHaveBeenCalledOnce();
	});

	it('loads the previous page when the top sentinel intersects', async () => {
		const onLoadPreviousPage = vi.fn();
		const screen = await render(
			<ExpandableList
				isPending={false}
				isError={false}
				listTestId="list"
				dataTestId="table"
				header="Process name"
				rows={[{id: 'process-1', content: <span>Order process</span>}]}
				expandedContents={{}}
				hasNextPage={false}
				hasPreviousPage
				isFetchingNextPage={false}
				isFetchingPreviousPage={false}
				onLoadNextPage={noop}
				onLoadPreviousPage={onLoadPreviousPage}
			/>,
		);

		const sentinel = screen.getByTestId('list-top-sentinel').element();
		getObserver().intersect(sentinel);

		expect(onLoadPreviousPage).toHaveBeenCalledOnce();
	});

	it('does not load the next page again while already fetching it', async () => {
		const onLoadNextPage = vi.fn();
		const screen = await render(
			<ExpandableList
				isPending={false}
				isError={false}
				listTestId="list"
				dataTestId="table"
				header="Process name"
				rows={[{id: 'process-1', content: <span>Order process</span>}]}
				expandedContents={{}}
				hasNextPage
				hasPreviousPage={false}
				isFetchingNextPage
				isFetchingPreviousPage={false}
				onLoadNextPage={onLoadNextPage}
				onLoadPreviousPage={noop}
			/>,
		);

		const sentinel = screen.getByTestId('list-bottom-sentinel').element();
		getObserver().intersect(sentinel);

		expect(onLoadNextPage).not.toHaveBeenCalled();
	});
});
