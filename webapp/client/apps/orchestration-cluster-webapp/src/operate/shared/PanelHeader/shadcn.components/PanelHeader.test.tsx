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
import {PanelHeader} from './PanelHeader';

describe('<PanelHeader />', () => {
	it('should render the title', async () => {
		const screen = await render(<PanelHeader title="Process definitions" />);

		await expect.element(screen.getByText('Process definitions')).toBeVisible();
	});

	it('should render the result count next to the title', async () => {
		const screen = await render(<PanelHeader title="Process definitions" count={3} />);

		await expect.element(screen.getByText(/Process definitions/)).toHaveTextContent('Process definitions - 3 results');
	});

	it('should render a single result count in singular form', async () => {
		const screen = await render(<PanelHeader title="Process definitions" count={1} />);

		await expect.element(screen.getByText(/Process definitions/)).toHaveTextContent('Process definitions - 1 result');
	});

	it('should render the "+" suffix when there are more total items than counted', async () => {
		const screen = await render(<PanelHeader title="Process definitions" count={50} hasMoreTotalItems />);

		await expect
			.element(screen.getByText(/Process definitions/))
			.toHaveTextContent('Process definitions - 50+ results');
	});

	it('should render the result count without a leading title when no title is given', async () => {
		const screen = await render(<PanelHeader count={3} />);

		await expect.element(screen.getByText('3 results')).toBeVisible();
	});

	it('should render children alongside the title', async () => {
		const screen = await render(
			<PanelHeader title="Process definitions">
				<button type="button">Action</button>
			</PanelHeader>,
		);

		await expect.element(screen.getByRole('button', {name: 'Action'})).toBeVisible();
	});
});
