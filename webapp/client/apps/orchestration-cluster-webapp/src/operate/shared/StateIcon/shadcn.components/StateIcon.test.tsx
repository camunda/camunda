/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {StateIcon} from './StateIcon';

describe('<StateIcon />', () => {
	const colorizedStates = [
		{state: 'FAILED', colorClassName: 'text-danger-foreground-strong'},
		{state: 'INCIDENT', colorClassName: 'text-danger-foreground-strong'},
		{state: 'ACTIVE', colorClassName: 'text-success-foreground-strong'},
		{state: 'COMPLETED', colorClassName: 'text-neutral-foreground-strong'},
		{state: 'EVALUATED', colorClassName: 'text-neutral-foreground-strong'},
		{state: 'SUSPENDED', colorClassName: 'text-neutral-foreground-subtle'},
	] as const;

	for (const {state, colorClassName} of colorizedStates) {
		it(`should render ${state} with the expected styling`, async () => {
			const screen = await render(<StateIcon state={state} size={24} />);

			const icon = screen.getByTestId(`${state}-icon`);
			await expect.element(icon).toBeVisible();
			await expect.element(icon).toHaveClass('flex-shrink-0');
			await expect.element(icon).toHaveClass(colorClassName);
			await expect.element(icon).toHaveAttribute('aria-hidden', 'true');
			await expect.element(icon).toHaveAttribute('focusable', 'false');
		});
	}

	for (const state of ['TERMINATED', 'UNSPECIFIED', 'UNKNOWN'] as const) {
		it(`should render ${state} without an explicit color class`, async () => {
			const screen = await render(<StateIcon state={state} size={24} />);

			const icon = screen.getByTestId(`${state}-icon`);
			await expect.element(icon).toBeVisible();
			await expect.element(icon).toHaveClass('flex-shrink-0');
			expect(icon.element().className.baseVal).not.toMatch(/text-(danger|success|neutral)-foreground-(strong|subtle)/);
		});
	}

	it('should forward the requested numeric size to the icon', async () => {
		const screen = await render(<StateIcon state="ACTIVE" size={24} />);

		const icon = screen.getByTestId('ACTIVE-icon');
		await expect.element(icon).toHaveAttribute('width', '24');
		await expect.element(icon).toHaveAttribute('height', '24');
	});
});
