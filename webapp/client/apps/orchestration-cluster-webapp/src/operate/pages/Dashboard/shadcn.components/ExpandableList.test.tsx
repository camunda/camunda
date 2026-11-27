/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {describe, expect, vi} from 'vitest';
import {render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {ExpandableList} from './ExpandableList';
import type {ExpandableListRow} from './ExpandableList.types';
import {EXPANDABLE_LIST_VARIANT_IDS, type ExpandableListVariant} from './ExpandableList.variants';

const noop = () => {};

function buildRow({id, name}: {id: string; name: string}): ExpandableListRow {
	return {id, content: <span>{name}</span>};
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
			isFetchNextPageError={false}
			isFetchPreviousPageError={false}
			onLoadNextPage={noop}
			onLoadPreviousPage={noop}
			{...overrides}
		/>,
	);
}

const PAGE_NAMES = [
	'Order',
	'Shipping',
	'Payment',
	'Billing',
	'Invoice',
	'Audit',
	'Archive',
	'Notify',
	'Review',
	'Close',
];

function buildPage(prefix: string) {
	return PAGE_NAMES.map((name) => buildRow({id: `${prefix}-${name}`, name: `${prefix}${name} process`}));
}

const CURRENT_PAGE = buildPage('');
const PREVIOUS_PAGE = buildPage('Earlier ');
const NEXT_PAGE = buildPage('Later ');

const SizedContainer: React.FC<{children?: React.ReactNode}> = ({children}) => (
	<div style={{height: '240px', display: 'flex', flexDirection: 'column'}}>{children}</div>
);

const PrependingList: React.FC<{variant: ExpandableListVariant}> = ({variant}) => {
	const [rows, setRows] = useState([...CURRENT_PAGE, ...NEXT_PAGE]);
	const [hasPreviousPage, setHasPreviousPage] = useState(true);

	return (
		<ExpandableList
			variant={variant}
			isPending={false}
			isError={false}
			listTestId="list"
			dataTestId="table"
			header="Process name"
			rows={rows}
			expandedContents={{}}
			hasNextPage={false}
			hasPreviousPage={hasPreviousPage}
			isFetchingNextPage={false}
			isFetchingPreviousPage={false}
			isFetchNextPageError={false}
			isFetchPreviousPageError={false}
			onLoadNextPage={noop}
			onLoadPreviousPage={() => {
				setRows([...PREVIOUS_PAGE, ...CURRENT_PAGE]);
				setHasPreviousPage(false);
			}}
		/>
	);
};

const EvictingList: React.FC<{variant: ExpandableListVariant}> = ({variant}) => {
	const [rows, setRows] = useState(CURRENT_PAGE);
	const [hasNextPage, setHasNextPage] = useState(true);

	return (
		<ExpandableList
			variant={variant}
			isPending={false}
			isError={false}
			listTestId="list"
			dataTestId="table"
			header="Process name"
			rows={rows}
			expandedContents={{}}
			hasNextPage={hasNextPage}
			hasPreviousPage={false}
			isFetchingNextPage={false}
			isFetchingPreviousPage={false}
			isFetchNextPageError={false}
			isFetchPreviousPageError={false}
			onLoadNextPage={() => {
				setRows([...CURRENT_PAGE.slice(8), ...NEXT_PAGE]);
				setHasNextPage(false);
			}}
			onLoadPreviousPage={noop}
		/>
	);
};

const ORDER_DETAILS = {'-Order': <div style={{height: '400px'}}>Order process details</div>};

const PagingWithExpandedRowList: React.FC = () => {
	const [rows, setRows] = useState(CURRENT_PAGE);
	const [hasNextPage, setHasNextPage] = useState(true);
	const [hasPreviousPage, setHasPreviousPage] = useState(false);

	return (
		<ExpandableList
			variant="nativeExpansion"
			isPending={false}
			isError={false}
			listTestId="list"
			dataTestId="table"
			header="Process name"
			rows={rows}
			expandedContents={ORDER_DETAILS}
			hasNextPage={hasNextPage}
			hasPreviousPage={hasPreviousPage}
			isFetchingNextPage={false}
			isFetchingPreviousPage={false}
			isFetchNextPageError={false}
			isFetchPreviousPageError={false}
			onLoadNextPage={() => {
				setRows([...CURRENT_PAGE.slice(8), ...NEXT_PAGE]);
				setHasNextPage(false);
				setHasPreviousPage(true);
			}}
			onLoadPreviousPage={() => {
				setRows([...CURRENT_PAGE, ...NEXT_PAGE]);
				setHasPreviousPage(false);
			}}
		/>
	);
};

describe.each(EXPANDABLE_LIST_VARIANT_IDS)('<ExpandableList /> scroll anchoring (variant: %s)', (variant) => {
	it('should keep the first visible row in view when a previous page is prepended and the last page is evicted', async () => {
		const screen = await render(<PrependingList variant={variant} />, {wrapper: SizedContainer});

		await expect.element(screen.getByText('Earlier Order process')).toBeInTheDocument();
		await expect.element(screen.getByText('Earlier Order process')).not.toBeInViewport();
		await expect.element(screen.getByText('Order process', {exact: true})).toBeInViewport({ratio: 1});
	});

	it('should keep the last visible row in view when a next page evicts rows from the top', async () => {
		const screen = await render(<EvictingList variant={variant} />, {wrapper: SizedContainer});
		await expect.element(screen.getByText('Close process')).toBeVisible();

		await userEvent.wheel(screen.getByTestId('list'), {delta: {y: 2000}});

		await expect.element(screen.getByText('Later Order process')).toBeInTheDocument();
		await expect.element(screen.getByText('Close process')).toBeInViewport({ratio: 1});
	});
});

describe('<ExpandableList /> nativeExpansion scroll anchoring', () => {
	it('should keep the visible rows in view when an evicted expanded row is prepended again', async () => {
		const screen = await render(<PagingWithExpandedRowList />, {wrapper: SizedContainer});
		await userEvent.click(screen.getByRole('button', {name: 'Expand row'}));
		await expect.element(screen.getByText('Order process details')).toBeVisible();

		await userEvent.wheel(screen.getByTestId('list'), {delta: {y: 5000}});

		await expect.element(screen.getByText('Later Order process')).toBeInTheDocument();
		await expect.element(screen.getByText('Order process details')).not.toBeInViewport();
		await expect.element(screen.getByText('Review process')).toBeInViewport({ratio: 1});
	});
});

describe.each(EXPANDABLE_LIST_VARIANT_IDS)('<ExpandableList /> (variant: %s)', (variant: ExpandableListVariant) => {
	it('should show loading skeleton rows in the table while pending', async () => {
		// given / when
		const screen = await renderList({variant, isPending: true});

		// then
		await expect.element(screen.getByRole('table', {name: 'Process name'})).toHaveAttribute('aria-busy', 'true');
	});

	it('should show the fetch-error empty state on error', async () => {
		// given / when
		const screen = await renderList({variant, isError: true});

		// then
		await expect.element(screen.getByRole('heading', {name: 'Process name'})).toBeVisible();
		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
		await expect.element(screen.getByText('Refresh the page to try again')).toBeVisible();
		await expect.element(screen.getByRole('table', {name: 'Process name'})).not.toBeInTheDocument();
	});

	it('should render the caller-provided empty state instead of the table when given one', async () => {
		// given / when
		const screen = await renderList({
			variant,
			emptyState: <div data-testid="custom-empty-state">Nothing here</div>,
		});

		// then
		await expect.element(screen.getByRole('heading', {name: 'Process name'})).toBeVisible();
		await expect.element(screen.getByTestId('custom-empty-state')).toBeVisible();
		await expect.element(screen.getByRole('table', {name: 'Process name'})).not.toBeInTheDocument();
	});

	it('should render every row', async () => {
		// given
		const rows = [
			buildRow({id: 'process-1', name: 'Order process'}),
			buildRow({id: 'process-2', name: 'Shipping process'}),
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
		await expect.element(screen.getByRole('button', {name: 'Expand row'})).toBeVisible();
	});

	it('should not expose an expand toggle at all for a row with no expandedContents entry', async () => {
		// given / when
		const screen = await renderList({
			variant,
			rows: [buildRow({id: 'process-1', name: 'Order process'})],
		});

		// then
		await expect.element(screen.getByText('Order process')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Expand row'})).not.toBeInTheDocument();
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
		await expect.element(screen.getByText('Order process')).toBeVisible();
		await expect.element(screen.getByTestId('list-top-sentinel')).not.toBeInTheDocument();
		await expect.element(screen.getByTestId('list-bottom-sentinel')).not.toBeInTheDocument();
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

		// then
		await expect.element(screen.getByTestId('list-bottom-sentinel')).toBeInViewport();
		await expect.poll(() => onLoadNextPage).toHaveBeenCalledOnce();
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

		// then
		await expect.element(screen.getByTestId('list-top-sentinel')).toBeInViewport();
		await expect.poll(() => onLoadPreviousPage).toHaveBeenCalledOnce();
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
		await expect.element(screen.getByTestId('list-top-sentinel')).toBeInViewport();
		await expect.element(screen.getByTestId('list-bottom-sentinel')).toBeInViewport();

		// then
		await expect.poll(() => onLoadPreviousPage).toHaveBeenCalledOnce();
		expect(onLoadNextPage).not.toHaveBeenCalled();
	});

	it('should re-observe a freshly-mounted bottom sentinel after it was unmounted for an error state', async () => {
		// given
		const onLoadNextPage = vi.fn();
		const rows = [buildRow({id: 'process-1', name: 'Order process'})];
		const screen = await renderList({variant, rows, hasNextPage: true, onLoadNextPage});
		await expect.poll(() => onLoadNextPage).toHaveBeenCalledOnce();

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
				isFetchNextPageError={false}
				isFetchPreviousPageError={false}
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
				isFetchNextPageError={false}
				isFetchPreviousPageError={false}
				onLoadNextPage={onLoadNextPage}
				onLoadPreviousPage={noop}
			/>,
		);

		// then
		await expect.poll(() => onLoadNextPage).toHaveBeenCalledTimes(2);
	});
});

describe('<ExpandableList /> composed variant', () => {
	it('should render the caller-composed content node', async () => {
		// given
		const row: ExpandableListRow = {
			id: 'process-1',
			content: <span>Fully composed row</span>,
		};

		// when
		const screen = await renderList({variant: 'composed', rows: [row]});

		// then
		await expect.element(screen.getByText('Fully composed row')).toBeVisible();
	});

	it('should show the header as a visible column header and name the table for screen readers', async () => {
		// given / when
		const screen = await renderList({
			variant: 'composed',
			rows: [buildRow({id: 'process-1', name: 'Order process'})],
		});

		// then
		await expect.element(screen.getByRole('row').nth(0).getByText('Process name')).toBeVisible();
		await expect.element(screen.getByRole('table', {name: 'Process name'})).toBeVisible();
	});

	it('should hide the toggle button via CSS for a row with nothing to expand', async () => {
		// given / when
		const screen = await renderList({
			variant: 'composed',
			rows: [buildRow({id: 'process-1', name: 'Order process'})],
		});

		// then
		await expect.element(screen.getByText('Order process')).toBeVisible();
		const toggle = screen.getByRole('button', {name: 'Expand row', includeHidden: true});
		await expect.element(toggle).toBeInTheDocument();
		await expect.element(toggle).not.toBeVisible();
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
		await expect.element(screen.getByRole('button', {includeHidden: true})).not.toBeInTheDocument();
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
		const bodyRows = screen.getByRole('rowgroup').filter({hasText: 'Order process'}).getByRole('row');
		const processRow = bodyRows.nth(0);
		const detailRow = bodyRows.nth(1);
		await expect.element(processRow).toMatchTextContent('Order process');
		await expect.element(processRow).not.toMatchTextContent('Version details for order process');
		await expect.element(processRow.getByRole('row')).not.toBeInTheDocument();
		await expect.element(detailRow).toMatchTextContent('Version details for order process');
		await expect.element(bodyRows.nth(2)).not.toBeInTheDocument();
	});
});
