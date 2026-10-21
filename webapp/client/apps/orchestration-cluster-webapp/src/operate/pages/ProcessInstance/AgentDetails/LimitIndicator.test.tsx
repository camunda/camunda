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
import {LimitIndicator} from './LimitIndicator';

describe('<LimitIndicator />', () => {
	it('should render the usage relative to the limit', async () => {
		const screen = await render(<LimitIndicator current={5} limit={10} />);

		const meter = screen.getByRole('meter', {name: 'Usage limit'});
		await expect.element(meter).toHaveAttribute('aria-valuetext', '5 of 10 limit (50%)');
		await expect.element(meter).toHaveAttribute('aria-valuenow', '50');
		await expect.element(screen.getByText('of 10 limit')).toBeInTheDocument();
	});

	it('should clamp the meter but keep the real percentage when the limit is exceeded', async () => {
		const screen = await render(<LimitIndicator current={15} limit={10} />);

		const meter = screen.getByRole('meter', {name: 'Usage limit'});
		await expect.element(meter).toHaveAttribute('aria-valuenow', '100');
		await expect.element(meter).toHaveAttribute('aria-valuetext', '15 of 10 limit (150%)');
	});

	it('should round the percentage', async () => {
		const screen = await render(<LimitIndicator current={1} limit={3} />);

		await expect
			.element(screen.getByRole('meter', {name: 'Usage limit'}))
			.toHaveAttribute('aria-valuetext', '1 of 3 limit (33%)');
	});

	it('should show full usage when the limit is 0', async () => {
		const screen = await render(<LimitIndicator current={3} limit={0} />);

		await expect
			.element(screen.getByRole('meter', {name: 'Usage limit'}))
			.toHaveAttribute('aria-valuetext', '3 of 0 limit (100%)');
	});

	it('should render nothing when no limit is set', async () => {
		const screen = await render(<LimitIndicator current={3} limit={-1} />);

		await expect.element(screen.getByRole('meter')).not.toBeInTheDocument();
	});
});
