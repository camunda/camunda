/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {describe, expect} from 'vitest';
import {MetricPanel} from './MetricPanel';

describe('<MetricPanel />', () => {
	it('should link the total instances count to the processes page showing running instances', async () => {
		const screen = await renderWithRouter(
			() => <MetricPanel count={{total: 10, withIncidents: 4, withoutIncidents: 6}} />,
			{path: '/operate'},
		);

		await expect
			.element(screen.getByTestId('total-instances-link'))
			.toHaveAttribute('href', '/operate/processes?active=true&incidents=true&completed=false&canceled=false');
	});

	it('should link the total instances count to all instances when there are none running', async () => {
		const screen = await renderWithRouter(
			() => <MetricPanel count={{total: 0, withIncidents: 0, withoutIncidents: 0}} />,
			{path: '/operate'},
		);

		await expect
			.element(screen.getByTestId('total-instances-link'))
			.toHaveAttribute('href', '/operate/processes?active=true&incidents=true&completed=true&canceled=true');
	});

	it('should link the incident instances label to the processes page filtered by incidents', async () => {
		const screen = await renderWithRouter(
			() => <MetricPanel count={{total: 10, withIncidents: 4, withoutIncidents: 6}} />,
			{path: '/operate'},
		);

		await expect
			.element(screen.getByTestId('incident-instances-link'))
			.toHaveAttribute('href', '/operate/processes?active=false&incidents=true&completed=false&canceled=false');
	});

	it('should link the active instances label to the processes page filtered by active instances', async () => {
		const screen = await renderWithRouter(
			() => <MetricPanel count={{total: 10, withIncidents: 4, withoutIncidents: 6}} />,
			{path: '/operate'},
		);

		await expect
			.element(screen.getByTestId('active-instances-link'))
			.toHaveAttribute('href', '/operate/processes?active=true&incidents=false&completed=false&canceled=false');
	});
});
