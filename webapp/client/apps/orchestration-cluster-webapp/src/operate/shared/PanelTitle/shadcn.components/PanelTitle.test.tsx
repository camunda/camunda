/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {Filter} from '@camunda/design-system/icons';
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

	it('should not render an icon by default', async () => {
		const screen = await render(<PanelTitle>Instances</PanelTitle>);

		expect(screen.getByRole('heading').element().querySelector('svg')).not.toBeInTheDocument();
	});

	it('should render the icon before the title when icon is set', async () => {
		const screen = await render(<PanelTitle icon={Filter}>Instances</PanelTitle>);

		const heading = screen.getByRole('heading', {name: 'Instances'});
		await expect.element(heading).toBeVisible();
		await expect.element(heading).toContainHTML('<svg');
	});

	it('should visually hide the title text but keep it accessible when isCollapsed is set', async () => {
		const screen = await render(
			<PanelTitle icon={Filter} isCollapsed>
				Instances
			</PanelTitle>,
		);

		const heading = screen.getByRole('heading', {name: 'Instances'});
		await expect.element(heading).toBeVisible();
		await expect.element(heading).toContainHTML('<svg');
		await expect.element(screen.getByText('Instances')).toHaveClass('sr-only');
	});
});
