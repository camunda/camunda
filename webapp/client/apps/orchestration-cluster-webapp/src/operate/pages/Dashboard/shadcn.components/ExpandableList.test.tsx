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
import type {ExpandableListRow} from './ExpandableList.types';
import {EXPANDABLE_LIST_VARIANT_IDS, type ExpandableListVariant} from './ExpandableList.variants';

const noop = () => {};

function buildRow({
	id,
	name,
	activeCount = 0,
	incidentsCount = 0,
}: {
	id: string;
	name: string;
	activeCount?: number;
	incidentsCount?: number;
}): ExpandableListRow {
	return {id, name, activeCount, incidentsCount, content: <span>{name}</span>};
}

type RenderOverrides = Partial<React.ComponentProps<typeof ExpandableList>>;

function renderList(overrides: RenderOverrides = {}) {
	return render(
		<ExpandableList
			isPending={false}
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
			{...overrides}
		/>,
	);
}

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

	intersectAll(targets: Element[]) {
		this.callback(
			targets.map((target) => ({target, isIntersecting: true}) as IntersectionObserverEntry),
			this,
		);
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

describe.each(EXPANDABLE_LIST_VARIANT_IDS)('<ExpandableList /> (variant: %s)', (variant: ExpandableListVariant) => {
	it('should show loading skeleton rows in the table while pending', async () => {
		// given / when
		const screen = await renderList({variant, isPending: true});

		// then
		await expect.element(screen.getByTestId('table')).toBeVisible();
		expect(document.querySelectorAll('[data-slot="data-table-skeleton-row"]').length).toBeGreaterThan(0);
	});

	it('should show the fetch-error empty state on error', async () => {
		// given / when
		const screen = await renderList({variant, isError: true});

		// then
		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
		await expect.element(screen.getByText('Refresh the page to try again')).toBeVisible();
		expect(screen.getByTestId('table').elements()).toHaveLength(0);
	});

	it('should render the caller-provided empty state instead of the table when given one', async () => {
		// given / when
		const screen = await renderList({
			variant,
			emptyState: <div data-testid="custom-empty-state">Nothing here</div>,
		});

		// then
		await expect.element(screen.getByTestId('custom-empty-state')).toBeVisible();
		expect(screen.getByTestId('table').elements()).toHaveLength(0);
	});

	it('should render every row', async () => {
		// given
		const rows = [
			buildRow({id: 'process-1', name: 'Order process', activeCount: 42, incidentsCount: 3}),
			buildRow({id: 'process-2', name: 'Shipping process', activeCount: 18, incidentsCount: 0}),
		];

		// when
		const screen = await renderList({variant, rows});

		// then
		await expect.element(screen.getByText('Order process')).toBeVisible();
		await expect.element(screen.getByText('Shipping process')).toBeVisible();
	});

	it('should reveal a row expandedContents entry when its toggle is expanded', async () => {
		// given
		const screen = await renderList({
			variant,
			rows: [buildRow({id: 'process-1', name: 'Order process'})],
			expandedContents: {'process-1': <span>Version details for order process</span>},
		});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Expand row'}));

		// then
		await expect.element(screen.getByText('Version details for order process')).toBeVisible();
	});

	it('should hide the expand toggle for a row with no expandedContents entry', async () => {
		// given
		const rows = [
			buildRow({id: 'process-1', name: 'Order process'}),
			buildRow({id: 'process-2', name: 'Shipping process'}),
		];

		// when
		const screen = await renderList({
			variant,
			rows,
			expandedContents: {'process-1': <span>Version details</span>},
		});

		// then
		expect(screen.getByRole('button', {name: 'Expand row'}).elements()).toHaveLength(1);
	});

	it('should not expose an expand toggle at all for a row with no expandedContents entry', async () => {
		// given / when
		const screen = await renderList({
			variant,
			rows: [buildRow({id: 'process-1', name: 'Order process'})],
		});

		// then
		expect(screen.getByRole('button', {name: 'Expand row'}).elements()).toHaveLength(0);
	});

	it('should show a loading indicator above the list while fetching the previous page', async () => {
		// given / when
		const screen = await renderList({
			variant,
			rows: [buildRow({id: 'process-1', name: 'Order process'})],
			hasPreviousPage: true,
			isFetchingPreviousPage: true,
		});

		// then
		await expect.element(screen.getByTestId('list-loading-previous')).toBeVisible();
	});

	it('should show a loading indicator below the list while fetching the next page', async () => {
		// given / when
		const screen = await renderList({
			variant,
			rows: [buildRow({id: 'process-1', name: 'Order process'})],
			hasNextPage: true,
			isFetchingNextPage: true,
		});

		// then
		await expect.element(screen.getByTestId('list-loading-next')).toBeVisible();
	});

	it('should not render pagination sentinels when there is nothing more to load', async () => {
		// given / when
		const screen = await renderList({
			variant,
			rows: [buildRow({id: 'process-1', name: 'Order process'})],
		});

		// then
		expect(screen.getByTestId('list-top-sentinel').elements()).toHaveLength(0);
		expect(screen.getByTestId('list-bottom-sentinel').elements()).toHaveLength(0);
	});

	it('should load the next page when the bottom sentinel intersects', async () => {
		// given
		const onLoadNextPage = vi.fn();
		const screen = await renderList({
			variant,
			rows: [buildRow({id: 'process-1', name: 'Order process'})],
			hasNextPage: true,
			onLoadNextPage,
		});

		// when
		getObserver().intersect(screen.getByTestId('list-bottom-sentinel').element());

		// then
		expect(onLoadNextPage).toHaveBeenCalledOnce();
	});

	it('should load the previous page when the top sentinel intersects', async () => {
		// given
		const onLoadPreviousPage = vi.fn();
		const screen = await renderList({
			variant,
			rows: [buildRow({id: 'process-1', name: 'Order process'})],
			hasPreviousPage: true,
			onLoadPreviousPage,
		});

		// when
		getObserver().intersect(screen.getByTestId('list-top-sentinel').element());

		// then
		expect(onLoadPreviousPage).toHaveBeenCalledOnce();
	});

	it('should not load the next page again while already fetching it', async () => {
		// given
		const onLoadNextPage = vi.fn();
		const screen = await renderList({
			variant,
			rows: [buildRow({id: 'process-1', name: 'Order process'})],
			hasNextPage: true,
			isFetchingNextPage: true,
			onLoadNextPage,
		});

		// when
		getObserver().intersect(screen.getByTestId('list-bottom-sentinel').element());

		// then
		expect(onLoadNextPage).not.toHaveBeenCalled();
	});

	it('should trigger only one direction when both sentinels intersect in the same batch', async () => {
		// given
		const onLoadNextPage = vi.fn();
		const onLoadPreviousPage = vi.fn();
		const screen = await renderList({
			variant,
			rows: [buildRow({id: 'process-1', name: 'Order process'})],
			hasNextPage: true,
			hasPreviousPage: true,
			onLoadNextPage,
			onLoadPreviousPage,
		});

		// when
		getObserver().intersectAll([
			screen.getByTestId('list-top-sentinel').element(),
			screen.getByTestId('list-bottom-sentinel').element(),
		]);

		// then
		const totalFetchCalls = onLoadNextPage.mock.calls.length + onLoadPreviousPage.mock.calls.length;
		expect(totalFetchCalls).toBe(1);
	});

	it('should re-observe a freshly-mounted bottom sentinel after it was unmounted for an error state', async () => {
		// given
		const onLoadNextPage = vi.fn();
		const rows = [buildRow({id: 'process-1', name: 'Order process'})];
		const screen = await renderList({variant, rows, hasNextPage: true, onLoadNextPage});

		await screen.rerender(
			<ExpandableList
				variant={variant}
				isPending={false}
				isError
				listTestId="list"
				dataTestId="table"
				header="Process name"
				rows={rows}
				expandedContents={{}}
				hasNextPage={true}
				hasPreviousPage={false}
				isFetchingNextPage={false}
				isFetchingPreviousPage={false}
				onLoadNextPage={onLoadNextPage}
				onLoadPreviousPage={noop}
			/>,
		);

		// when
		await screen.rerender(
			<ExpandableList
				variant={variant}
				isPending={false}
				isError={false}
				listTestId="list"
				dataTestId="table"
				header="Process name"
				rows={rows}
				expandedContents={{}}
				hasNextPage={true}
				hasPreviousPage={false}
				isFetchingNextPage={false}
				isFetchingPreviousPage={false}
				onLoadNextPage={onLoadNextPage}
				onLoadPreviousPage={noop}
			/>,
		);
		FakeIntersectionObserver.instances[FakeIntersectionObserver.instances.length - 1]?.intersect(
			screen.getByTestId('list-bottom-sentinel').element(),
		);

		// then
		expect(onLoadNextPage).toHaveBeenCalledOnce();
	});

	it('should preserve the scroll offset when previous-page rows are prepended', async () => {
		// given
		const rows = [buildRow({id: 'process-2', name: 'Shipping process'})];
		const screen = await renderList({variant, rows, hasPreviousPage: true});
		const container = screen.getByTestId('list').element() as HTMLDivElement;
		let virtualScrollTop = 50;
		Object.defineProperty(container, 'scrollTop', {
			get: () => virtualScrollTop,
			set: (value: number) => {
				virtualScrollTop = value;
			},
			configurable: true,
		});
		Object.defineProperty(container, 'scrollHeight', {value: 200, configurable: true});

		// when
		getObserver().intersect(screen.getByTestId('list-top-sentinel').element());
		Object.defineProperty(container, 'scrollHeight', {value: 260, configurable: true});
		await screen.rerender(
			<ExpandableList
				variant={variant}
				isPending={false}
				isError={false}
				listTestId="list"
				dataTestId="table"
				header="Process name"
				rows={[buildRow({id: 'process-1', name: 'Order process'}), ...rows]}
				expandedContents={{}}
				hasNextPage={false}
				hasPreviousPage={true}
				isFetchingNextPage={false}
				isFetchingPreviousPage={false}
				onLoadNextPage={noop}
				onLoadPreviousPage={noop}
			/>,
		);

		// then
		expect(container.scrollTop).toBe(110);
	});
});

describe('<ExpandableList /> composed variant', () => {
	it('should render the caller-composed content node rather than the row fields', async () => {
		// given
		const row: ExpandableListRow = {
			id: 'process-1',
			name: 'Unused name',
			activeCount: 42,
			incidentsCount: 3,
			content: <span>Fully composed row</span>,
		};

		// when
		const screen = await renderList({variant: 'composed', rows: [row]});

		// then
		await expect.element(screen.getByText('Fully composed row')).toBeVisible();
		expect(screen.getByText('Unused name').elements()).toHaveLength(0);
	});

	it('should name the table for screen readers without a visible column header', async () => {
		// given / when
		const screen = await renderList({
			variant: 'composed',
			rows: [buildRow({id: 'process-1', name: 'Order process'})],
		});

		// then
		await expect.element(screen.getByRole('table', {name: 'Process name'})).toBeVisible();
	});

	it('should hide the toggle button via CSS for a row with nothing to expand', async () => {
		// given / when
		await renderList({
			variant: 'composed',
			rows: [buildRow({id: 'process-1', name: 'Order process'})],
		});

		// then
		const toggles = document.querySelectorAll('[data-slot="data-table-expand-toggle"]');
		expect(toggles).toHaveLength(1);
		expect(getComputedStyle(toggles[0]!).display).toBe('none');
	});
});

describe('<ExpandableList /> nativeExpansion variant', () => {
	it('should not inject any toggle button into the DOM for a row with nothing to expand', async () => {
		// given / when
		const screen = await renderList({
			variant: 'nativeExpansion',
			rows: [buildRow({id: 'process-1', name: 'Order process'})],
		});

		// then
		await expect.element(screen.getByText('Order process')).toBeVisible();
		expect(document.querySelectorAll('button')).toHaveLength(0);
	});

	it('should insert the expanded content as a sibling row instead of a nested one', async () => {
		// given
		const screen = await renderList({
			variant: 'nativeExpansion',
			rows: [buildRow({id: 'process-1', name: 'Order process'})],
			expandedContents: {'process-1': <span>Version details for order process</span>},
		});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Expand row'}));

		// then
		await expect.element(screen.getByRole('button', {name: 'Collapse row'})).toBeVisible();
		const tableBody = document.querySelector('[data-slot="table-body"]');
		const bodyRows = tableBody?.querySelectorAll(':scope > tr') ?? [];
		expect(bodyRows).toHaveLength(2);
		expect(bodyRows[0]!.textContent).not.toContain('Version details for order process');
		expect(bodyRows[0]!.querySelector('tr')).toBeNull();
		expect(bodyRows[1]!.textContent).toContain('Version details for order process');
	});
});
