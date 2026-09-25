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

function mockElementHeights({
	headerHeight = 999,
	rowHeight = 60,
	detailHeight = 500,
}: {headerHeight?: number; rowHeight?: number; detailHeight?: number} = {}) {
	return vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (this: HTMLElement) {
		let height = rowHeight;

		if (this.closest('[data-slot="table-header"]')) {
			height = headerHeight;
		} else if (
			this.getAttribute('data-slot') === 'data-table-expansion-row' ||
			this.querySelector('[data-row-kind="detail"]')
		) {
			height = detailHeight;
		}

		return {height} as DOMRect;
	});
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
		const rowHeight = 60;
		const getBoundingClientRect = mockElementHeights({rowHeight});
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

		// when
		getObserver().intersect(screen.getByTestId('list-top-sentinel').element());
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
		expect(container.scrollTop).toBe(50 + rowHeight);
		getBoundingClientRect.mockRestore();
	});

	it('should preserve the scroll offset when a previous-page fetch prepends a row while the capped page window evicts one from the bottom', async () => {
		// given
		const rowHeight = 60;
		const getBoundingClientRect = mockElementHeights({rowHeight});
		const rows = [
			buildRow({id: 'process-1', name: 'Order process'}),
			buildRow({id: 'process-2', name: 'Shipping process'}),
			buildRow({id: 'process-3', name: 'Payment process'}),
		];
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

		// when
		getObserver().intersect(screen.getByTestId('list-top-sentinel').element());
		// The capped infinite-query page window evicts the last row ('process-3')
		// while prepending a new one ('process-0'), so the total row count (and
		// thus, with uniform row heights, the total scrollHeight) stays unchanged.
		await screen.rerender(
			<ExpandableList
				variant={variant}
				isPending={false}
				isError={false}
				listTestId="list"
				dataTestId="table"
				header="Process name"
				rows={[buildRow({id: 'process-0', name: 'Refund process'}), rows[0]!, rows[1]!]}
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
		expect(container.scrollTop).toBe(50 + rowHeight);
		getBoundingClientRect.mockRestore();
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

	it('should preserve the scroll offset when a row prepended by a previous-page fetch is still expanded from before it was evicted', async () => {
		// given
		const rowHeight = 60;
		const detailHeight = 500;
		const getBoundingClientRect = mockElementHeights({rowHeight, detailHeight});
		const rowA = buildRow({id: 'process-a', name: 'Order process'});
		const rowB = buildRow({id: 'process-b', name: 'Shipping process'});
		const rowC = buildRow({id: 'process-c', name: 'Payment process'});
		const expandedContents = {'process-a': <span>Order process details</span>};
		const screen = await renderList({
			variant: 'nativeExpansion',
			rows: [rowA, rowB],
			expandedContents,
			hasPreviousPage: true,
		});
		// 'process-a' is expanded while it is still present...
		await userEvent.click(screen.getByRole('button', {name: 'Expand row'}));
		// ...then it is evicted from the list, but the component keeps its
		// expanded state internally (it is not remounted).
		await screen.rerender(
			<ExpandableList
				variant="nativeExpansion"
				isPending={false}
				isError={false}
				listTestId="list"
				dataTestId="table"
				header="Process name"
				rows={[rowB]}
				expandedContents={expandedContents}
				hasNextPage={false}
				hasPreviousPage={true}
				isFetchingNextPage={false}
				isFetchingPreviousPage={false}
				onLoadNextPage={noop}
				onLoadPreviousPage={noop}
			/>,
		);
		const container = screen.getByTestId('list').element() as HTMLDivElement;
		let virtualScrollTop = 50;
		Object.defineProperty(container, 'scrollTop', {
			get: () => virtualScrollTop,
			set: (value: number) => {
				virtualScrollTop = value;
			},
			configurable: true,
		});

		// when
		getObserver().intersect(screen.getByTestId('list-top-sentinel').element());
		// 'process-a' pages back in ahead of 'process-b', still expanded, and a
		// genuinely new row ('process-c') is prepended right after it. The
		// anchor row ('process-b') actually moves down by both new rows' real
		// heights *and* 'process-a''s detail row height, so that detail row's
		// height must still be included even though it isn't itself one of the
		// two newly-prepended logical rows.
		await screen.rerender(
			<ExpandableList
				variant="nativeExpansion"
				isPending={false}
				isError={false}
				listTestId="list"
				dataTestId="table"
				header="Process name"
				rows={[rowA, rowC, rowB]}
				expandedContents={expandedContents}
				hasNextPage={false}
				hasPreviousPage={true}
				isFetchingNextPage={false}
				isFetchingPreviousPage={false}
				onLoadNextPage={noop}
				onLoadPreviousPage={noop}
			/>,
		);

		// then
		expect(container.scrollTop).toBe(50 + rowHeight * 2 + detailHeight);
		getBoundingClientRect.mockRestore();
	});
});
