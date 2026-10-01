/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, it, expect} from 'vitest';
import {PanelTitle} from './PanelTitle';

describe('<PanelTitle />', () => {
	it('should render the title text', async () => {
		const screen = await render(<PanelTitle>Instances</PanelTitle>);

		await expect.element(screen.getByText('Instances')).toBeVisible();
	});

	it('should render as a level-2 heading', async () => {
		const screen = await render(<PanelTitle>Instances</PanelTitle>);

		await expect.element(screen.getByRole('heading', {level: 2, name: 'Instances'})).toBeVisible();
	});

	it('should apply vertical writing mode when isVertical is set', async () => {
		const screen = await render(<PanelTitle isVertical>Instances</PanelTitle>);

		await expect.element(screen.getByText('Instances')).toHaveClass('[writing-mode:vertical-lr]');
	});

	it('should not apply vertical writing mode by default', async () => {
		const screen = await render(<PanelTitle>Instances</PanelTitle>);

		await expect.element(screen.getByText('Instances')).not.toHaveClass('[writing-mode:vertical-lr]');
	});
});
