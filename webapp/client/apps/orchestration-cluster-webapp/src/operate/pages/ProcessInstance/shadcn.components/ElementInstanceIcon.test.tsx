/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect, it} from 'vitest';
import {cleanup, render} from 'vitest-browser-react';
import {ElementInstanceIcon} from './ElementInstanceIcon';

afterEach(async () => {
	await cleanup();
});

describe('ElementInstanceIcon (design system)', () => {
	it('should render a decorative svg icon for the process root', async () => {
		const screen = await render(<ElementInstanceIcon root />);

		const icon = screen.getByTestId('element-instance-icon');
		await expect.element(icon).toHaveAttribute('aria-hidden', 'true');
		await expect.element(icon).toHaveAttribute('data-icon', 'process-root');
	});

	it('should render the generic task icon when the element is unknown', async () => {
		const screen = await render(<ElementInstanceIcon />);

		await expect.element(screen.getByTestId('element-instance-icon')).toBeInTheDocument();
	});
});
