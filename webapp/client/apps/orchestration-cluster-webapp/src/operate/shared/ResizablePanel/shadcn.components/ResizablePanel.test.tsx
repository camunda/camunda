/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, expect, afterEach} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {storeStateLocally} from '#/shared/browser-storage/local-storage';
import {ResizablePanel, SplitDirection} from './ResizablePanel';

describe('<ResizablePanel />', () => {
	afterEach(() => {
		localStorage.removeItem('operate.panelStates');
	});

	it('should render both children', async () => {
		const screen = await render(
			<div style={{width: '400px', height: '400px'}}>
				<ResizablePanel direction={SplitDirection.Horizontal} panelId="test-panel">
					<div data-testid="left">left</div>
					<div data-testid="right">right</div>
				</ResizablePanel>
			</div>,
		);

		await expect.element(screen.getByTestId('left')).toBeVisible();
		await expect.element(screen.getByTestId('right')).toBeVisible();
	});

	it('should default to equal 50/50 sizes when nothing is stored', async () => {
		const screen = await render(
			<div style={{width: '400px', height: '400px'}}>
				<ResizablePanel direction={SplitDirection.Horizontal} panelId="test-panel-default">
					<div data-testid="left">left</div>
					<div data-testid="right">right</div>
				</ResizablePanel>
			</div>,
		);

		await expect.element(screen.getByTestId('left')).toBeVisible();
		const left = screen.getByTestId('left').element().parentElement;
		expect(left?.style.width).toContain('50%');
	});

	it('should restore previously persisted sizes for the given panel id', async () => {
		storeStateLocally('operate.panelStates', {
			'test-panel-restored': [70, 30],
		});

		const screen = await render(
			<div style={{width: '400px', height: '400px'}}>
				<ResizablePanel direction={SplitDirection.Horizontal} panelId="test-panel-restored">
					<div data-testid="left">left</div>
					<div data-testid="right">right</div>
				</ResizablePanel>
			</div>,
		);

		await expect.element(screen.getByTestId('left')).toBeVisible();
		const left = screen.getByTestId('left').element().parentElement;
		expect(left?.style.width).toContain('70%');
	});
});
