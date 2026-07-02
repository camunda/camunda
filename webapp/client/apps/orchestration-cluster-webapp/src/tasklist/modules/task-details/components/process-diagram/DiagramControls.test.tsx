/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {DiagramControls} from './DiagramControls';

describe('<DiagramControls />', () => {
	it('should expose accessible labels for all diagram actions', async () => {
		const screen = await render(<DiagramControls onZoomReset={vi.fn()} onZoomIn={vi.fn()} onZoomOut={vi.fn()} />);

		await expect.element(screen.getByRole('button', {name: 'Reset diagram zoom'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Zoom in diagram'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Zoom out diagram'})).toBeVisible();
	});

	it('should allow users to reset the diagram zoom', async () => {
		const onZoomReset = vi.fn();
		const screen = await render(<DiagramControls onZoomReset={onZoomReset} onZoomIn={vi.fn()} onZoomOut={vi.fn()} />);

		await userEvent.click(screen.getByRole('button', {name: 'Reset diagram zoom'}));

		expect(onZoomReset).toHaveBeenCalledOnce();
	});

	it('should allow users to zoom into the diagram', async () => {
		const onZoomIn = vi.fn();
		const screen = await render(<DiagramControls onZoomReset={vi.fn()} onZoomIn={onZoomIn} onZoomOut={vi.fn()} />);

		await userEvent.click(screen.getByRole('button', {name: 'Zoom in diagram'}));

		expect(onZoomIn).toHaveBeenCalledOnce();
	});

	it('should allow users to zoom out of the diagram', async () => {
		const onZoomOut = vi.fn();
		const screen = await render(<DiagramControls onZoomReset={vi.fn()} onZoomIn={vi.fn()} onZoomOut={onZoomOut} />);

		await userEvent.click(screen.getByRole('button', {name: 'Zoom out diagram'}));

		expect(onZoomOut).toHaveBeenCalledOnce();
	});
});
