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
import {PanelHeader} from './PanelHeader';

describe('<PanelHeader />', () => {
	it('should render the title as a heading', async () => {
		const screen = await render(<PanelHeader title="Process" />);

		await expect.element(screen.getByRole('heading', {name: 'Process'})).toBeVisible();
	});

	it('should render the result count as a heading', async () => {
		const screen = await render(<PanelHeader title="Process Instances" count={3} />);

		await expect.element(screen.getByRole('heading')).toHaveTextContent('Process Instances - 3 results');
	});

	it('should not render an empty heading without a title or count', async () => {
		const screen = await render(
			<PanelHeader>
				<span>Actions</span>
			</PanelHeader>,
		);

		await expect.element(screen.getByText('Actions')).toBeVisible();
		await expect.element(screen.getByRole('heading')).not.toBeInTheDocument();
	});
});
