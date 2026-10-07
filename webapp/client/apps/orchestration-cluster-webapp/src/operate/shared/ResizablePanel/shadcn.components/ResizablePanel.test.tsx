/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, expect, afterEach} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {ResizablePanel, SplitDirection} from './ResizablePanel';

function getGutter(container: Element) {
	return container.querySelector<HTMLElement>('.__dbk__gutter');
}

function getDragger(container: Element) {
	return container.querySelector<HTMLElement>('.__dbk__dragger');
}

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

	it('should persist the new sizes and clear the drag cursor after dragging the gutter', async () => {
		const screen = await render(
			<div data-testid="container" style={{width: '400px', height: '400px'}}>
				<ResizablePanel direction={SplitDirection.Horizontal} panelId="test-panel-drag">
					<div data-testid="left">left</div>
					<div data-testid="right">right</div>
				</ResizablePanel>
			</div>,
		);

		const container = screen.getByTestId('container').element();
		await expect.element(screen.getByTestId('right')).toBeVisible();
		const dragger = getDragger(container);
		const right = screen.getByTestId('right').element();
		if (dragger === null) {
			throw new Error('Expected a dragger to be rendered');
		}

		await userEvent.dragAndDrop(dragger, right);

		await expect.poll(() => getStateLocally('operate.panelStates')?.['test-panel-drag']).not.toBeUndefined();
		const persistedSizes = getStateLocally('operate.panelStates')?.['test-panel-drag'];
		if (!Array.isArray(persistedSizes)) {
			throw new Error('Expected persisted sizes to be an array');
		}
		const [leftSize, rightSize] = persistedSizes;
		if (leftSize === undefined || rightSize === undefined) {
			throw new Error('Expected two persisted sizes');
		}
		expect(leftSize).not.toBe(50);
		expect(leftSize + rightSize).toBeCloseTo(100, 0);
		expect(document.body.classList.contains('cursor-col-resize')).toBe(false);
	});

	it('should force the resize cursor on the body and highlight the gutter while actively dragging', async () => {
		const screen = await render(
			<div data-testid="container" style={{width: '400px', height: '400px'}}>
				<ResizablePanel direction={SplitDirection.Horizontal} panelId="test-panel-highlight">
					<div data-testid="left">left</div>
					<div data-testid="right">right</div>
				</ResizablePanel>
			</div>,
		);

		const container = screen.getByTestId('container').element();
		await expect.element(screen.getByTestId('left')).toBeVisible();
		const gutter = getGutter(container);
		if (gutter === null) {
			throw new Error('Expected a gutter to be rendered');
		}

		expect(document.body.classList.contains('cursor-col-resize')).toBe(false);

		gutter.dispatchEvent(new MouseEvent('mousedown', {bubbles: true, clientX: 200, clientY: 200}));
		await expect.poll(() => document.body.classList.contains('cursor-col-resize')).toBe(true);

		window.dispatchEvent(new MouseEvent('mousemove', {bubbles: true, clientX: 220, clientY: 200}));
		window.dispatchEvent(new MouseEvent('mouseup', {bubbles: true, clientX: 220, clientY: 200}));

		await expect.poll(() => document.body.classList.contains('cursor-col-resize')).toBe(false);
	});
});
