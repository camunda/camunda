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
	it('should expose accessible controls for all diagram actions', async () => {
		const onZoomReset = vi.fn();
		const onZoomIn = vi.fn();
		const onZoomOut = vi.fn();
		const screen = await render(
			<DiagramControls onZoomReset={onZoomReset} onZoomIn={onZoomIn} onZoomOut={onZoomOut} />,
		);

		await expect.element(screen.getByRole('button', {name: 'Reset diagram zoom'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Zoom in diagram'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Zoom out diagram'})).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Reset diagram zoom'}));
		await userEvent.click(screen.getByRole('button', {name: 'Zoom in diagram'}));
		await userEvent.click(screen.getByRole('button', {name: 'Zoom out diagram'}));

		expect(onZoomReset).toHaveBeenCalledOnce();
		expect(onZoomIn).toHaveBeenCalledOnce();
		expect(onZoomOut).toHaveBeenCalledOnce();
	});
});
