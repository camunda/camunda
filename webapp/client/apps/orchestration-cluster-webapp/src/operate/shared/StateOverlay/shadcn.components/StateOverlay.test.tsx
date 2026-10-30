/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import '#/shared/theme/tailwind.css';
import {describe, expect} from 'vitest';
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {StateOverlay} from './StateOverlay';

function getContainer() {
	return document.body.appendChild(document.createElement('div'));
}

describe('<StateOverlay />', () => {
	it.for(
		(
			[
				{state: 'active', label: 'Active'},
				{state: 'incidents', label: 'Incidents'},
				{state: 'canceled', label: 'Canceled'},
				{state: 'completed', label: 'Completed'},
				{state: 'completedEndEvents', label: 'Completed'},
				{state: 'EVALUATED', label: 'Evaluated'},
				{state: 'FAILED', label: 'Failed'},
			] as const
		).flatMap(({state, label}) => [undefined, 0, 3].map((count) => ({state, label, count}))),
	)('should name the $state overlay with count $count', async ({state, label, count}) => {
		const screen = await render(<StateOverlay state={state} container={getContainer()} count={count} />);

		await expect
			.element(screen.getByRole('img', {name: count === undefined ? label : `${label}: ${count}`, exact: true}))
			.toBeVisible();
	});

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

	it('should show a compact count while keeping the full count in the accessible name', async () => {
		const screen = await render(<StateOverlay state="incidents" container={getContainer()} count={12345} />);

		await expect.element(screen.getByText('12.3K')).toBeVisible();
		await expect.element(screen.getByRole('img', {name: 'Incidents: 12,345'})).toBeVisible();
	});

	it('should pulse a ring without reducing opacity when faded', async () => {
		const screen = await render(<StateOverlay state="active" container={getContainer()} count={1} isFaded />);

		const badge = screen.getByTestId('state-overlay-active').element();
		expect(getComputedStyle(badge).opacity).toBe('1');
		expect(getComputedStyle(badge).animationName).toBe('state-overlay-ring');
	});

	it('should mention the pending state in the accessible name when faded', async () => {
		const screen = await render(<StateOverlay state="active" container={getContainer()} count={3} isFaded />);

		await expect.element(screen.getByRole('img', {name: 'Active: 3, pending modification', exact: true})).toBeVisible();
	});

	it('should not pulse when not faded', async () => {
		const screen = await render(<StateOverlay state="active" container={getContainer()} count={1} />);

		expect(getComputedStyle(screen.getByTestId('state-overlay-active').element()).animationName).toBe('none');
	});

	it('should prefix the accessible name with the element name', async () => {
		const screen = await render(
			<StateOverlay state="active" container={getContainer()} count={2} elementName="Do work" />,
		);

		await expect.element(screen.getByRole('img', {name: 'Do work, Active: 2'})).toBeVisible();
	});

	it.for(['active', 'incidents', 'FAILED', 'EVALUATED', 'canceled'] as const)(
		'should give the %s overlay a visible background',
		async (state) => {
			const container = getContainer();
			container.className = 'c4-ui';
			await render(<StateOverlay state={state} container={container} count={1} />);

			await expect
				.poll(() => getComputedStyle(document.querySelector(`[data-testid="state-overlay-${state}"]`)!).backgroundColor)
				.not.toBe('rgba(0, 0, 0, 0)');
		},
	);
});
