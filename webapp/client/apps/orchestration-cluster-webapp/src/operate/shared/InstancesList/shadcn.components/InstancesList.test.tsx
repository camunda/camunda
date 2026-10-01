/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {clearStateLocally, getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';

vi.mock('@devbookhq/splitter', () => ({
	default: ({
		children,
		initialSizes,
		onResizeStarted,
		onResizeFinished,
	}: {
		children: React.ReactNode;
		initialSizes?: number[];
		onResizeStarted?: (pairIdx: number) => void;
		onResizeFinished?: (pairIdx: number, newSizes: number[]) => void;
	}) => (
		<div>
			<span data-testid="splitter-initial-sizes">{JSON.stringify(initialSizes)}</span>
			{children}
			<button data-testid="simulate-resize-started" onClick={() => onResizeStarted?.(0)}>
				simulate resize started
			</button>
			<button data-testid="simulate-resize-finished" onClick={() => onResizeFinished?.(0, [30, 70])}>
				simulate resize finished
			</button>
		</div>
	),
	SplitDirection: {Horizontal: 'Horizontal', Vertical: 'Vertical'},
}));

const {InstancesList} = await import('./InstancesList');

describe('<InstancesList />', () => {
	beforeEach(() => {
		clearStateLocally('operate.panelStates');
	});

	afterEach(() => {
		clearStateLocally('operate.panelStates');
		document.body.style.cursor = '';
	});

	it('should render the top and bottom panels', async () => {
		// given / when
		const screen = await render(
			<InstancesList type="process" topPanel={<div>Top panel</div>} bottomPanel={<div>Bottom panel</div>} />,
		);

		// then
		await expect.element(screen.getByText('Top panel')).toBeVisible();
		await expect.element(screen.getByText('Bottom panel')).toBeVisible();
	});

	it('should use a single-column grid without a left panel', async () => {
		// given / when
		const screen = await render(
			<InstancesList type="process" topPanel={<div>Top</div>} bottomPanel={<div>Bottom</div>} />,
		);

		// then
		await expect.element(screen.getByTestId('instances-list')).not.toHaveClass(/grid-cols-\[auto/);
	});

	it('should render the left panel and switch to a two-column grid when provided', async () => {
		// given / when
		const screen = await render(
			<InstancesList
				type="process"
				leftPanel={<div>Filters</div>}
				topPanel={<div>Top</div>}
				bottomPanel={<div>Bottom</div>}
			/>,
		);

		// then
		await expect.element(screen.getByText('Filters')).toBeVisible();
		await expect.element(screen.getByTestId('instances-list')).toHaveClass(/grid-cols-\[auto/);
	});

	it('should render additionalTopContent and footer when provided', async () => {
		// given / when
		const screen = await render(
			<InstancesList
				type="decision"
				topPanel={<div>Top</div>}
				bottomPanel={<div>Bottom</div>}
				additionalTopContent={<div>Extra top content</div>}
				footer={<div>Footer content</div>}
			/>,
		);

		// then
		await expect.element(screen.getByText('Extra top content')).toBeVisible();
		await expect.element(screen.getByText('Footer content')).toBeVisible();
	});

	it('should not render a frame header by default', async () => {
		// given / when
		const screen = await render(
			<InstancesList type="process" topPanel={<div>Top</div>} bottomPanel={<div>Bottom</div>} />,
		);

		// then
		await expect.element(screen.getByTestId('frame-container')).not.toBeInTheDocument();
	});

	it('should render the frame header title when a visible frame is provided', async () => {
		// given / when
		const screen = await render(
			<InstancesList
				type="migrate"
				topPanel={<div>Top</div>}
				bottomPanel={<div>Bottom</div>}
				frame={{headerTitle: 'Migration preview'}}
			/>,
		);

		// then
		await expect.element(screen.getByTestId('frame-container')).toBeVisible();
		await expect.element(screen.getByText('Migration preview')).toBeVisible();
	});

	it('should hide the frame header when the frame is explicitly not visible', async () => {
		// given / when
		const screen = await render(
			<InstancesList
				type="migrate"
				topPanel={<div>Top</div>}
				bottomPanel={<div>Bottom</div>}
				frame={{headerTitle: 'Migration preview', isVisible: false}}
			/>,
		);

		// then
		await expect.element(screen.getByText('Top')).toBeVisible();
		await expect.element(screen.getByText('Migration preview')).not.toBeInTheDocument();
	});

	it('should restore the persisted panel sizes for the type-scoped key', async () => {
		// given
		storeStateLocally('operate.panelStates', {'process-instances-vertical-panel': [35, 65]});

		// when
		const screen = await render(
			<InstancesList type="process" topPanel={<div>Top</div>} bottomPanel={<div>Bottom</div>} />,
		);

		// then
		await expect.element(screen.getByTestId('splitter-initial-sizes')).toHaveTextContent('[35,65]');
	});

	it('should fall back to an even split when no panel sizes are persisted', async () => {
		// given / when
		const screen = await render(
			<InstancesList type="process" topPanel={<div>Top</div>} bottomPanel={<div>Bottom</div>} />,
		);

		// then
		await expect.element(screen.getByTestId('splitter-initial-sizes')).toHaveTextContent('[50,50]');
	});

	it('should set the drag cursor when a resize starts', async () => {
		// given
		const screen = await render(
			<InstancesList type="process" topPanel={<div>Top</div>} bottomPanel={<div>Bottom</div>} />,
		);

		// when
		await screen.getByTestId('simulate-resize-started').click();

		// then
		expect(document.body.style.cursor).toBe('ns-resize');
	});

	it('should persist resized panel sizes to local storage under a type-scoped key, and reset the drag cursor', async () => {
		// given
		const screen = await render(
			<InstancesList type="process" topPanel={<div>Top</div>} bottomPanel={<div>Bottom</div>} />,
		);
		document.body.style.cursor = 'ns-resize';

		// when
		await screen.getByTestId('simulate-resize-finished').click();

		// then
		expect(getStateLocally('operate.panelStates')?.['process-instances-vertical-panel']).toEqual([30, 70]);
		expect(document.body.style.cursor).toBe('');
	});

	it('should merge the persisted size into existing panelStates instead of overwriting unrelated entries', async () => {
		// given
		storeStateLocally('operate.panelStates', {'decision-instances-vertical-panel': [40, 60]});
		const screen = await render(
			<InstancesList type="process" topPanel={<div>Top</div>} bottomPanel={<div>Bottom</div>} />,
		);

		// when
		await screen.getByTestId('simulate-resize-finished').click();

		// then
		expect(getStateLocally('operate.panelStates')).toEqual({
			'decision-instances-vertical-panel': [40, 60],
			'process-instances-vertical-panel': [30, 70],
		});
	});
});
