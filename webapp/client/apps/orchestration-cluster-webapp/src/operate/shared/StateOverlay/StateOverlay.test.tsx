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
import {StateOverlay} from './StateOverlay';

function getContainer() {
	return document.body.appendChild(document.createElement('div'));
}

describe('<StateOverlay />', () => {
	it('renders the count when provided', async () => {
		const screen = await render(
			<StateOverlay state="active" container={getContainer()} count={3} testId="active-overlay" />,
		);

		await expect.element(screen.getByTestId('active-overlay')).toHaveTextContent('3');
	});

	it('renders without a count', async () => {
		const screen = await render(
			<StateOverlay state="incidents" container={getContainer()} testId="incidents-overlay" />,
		);

		await expect.element(screen.getByTestId('incidents-overlay')).toBeVisible();
	});

	it('defaults the test id to state-overlay-<state>', async () => {
		const screen = await render(<StateOverlay state="canceled" container={getContainer()} />);

		await expect.element(screen.getByTestId('state-overlay-canceled')).toBeVisible();
	});

	it('renders the provided title', async () => {
		const screen = await render(
			<StateOverlay state="completed" container={getContainer()} title="3 finished instances" />,
		);

		await expect.element(screen.getByTitle('3 finished instances')).toBeVisible();
		await expect.element(screen.getByRole('img', {name: '3 finished instances'})).toBeVisible();
	});

	it('should include the count in the accessible name of a titled statistic', async () => {
		const screen = await render(
			<StateOverlay state="completed" container={getContainer()} title="Execution Count" count={3} />,
		);

		await expect.element(screen.getByRole('img', {name: 'Execution Count: 3'})).toBeVisible();
	});
});
