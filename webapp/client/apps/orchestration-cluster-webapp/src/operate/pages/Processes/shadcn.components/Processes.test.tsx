/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {Processes} from './Processes';

describe('<Processes />', () => {
	it('should render the sr-only processes title', async () => {
		const screen = await renderWithRouter(Processes, {path: '/operate-preview/processes'});

		await expect.element(screen.getByRole('heading', {name: 'Processes'})).toBeInTheDocument();
	});

	it('should render all scaffold placeholders', async () => {
		const screen = await renderWithRouter(Processes, {path: '/operate-preview/processes'});

		await expect.element(screen.getByText('Filters panel placeholder')).toBeVisible();
		await expect.element(screen.getByText('Diagram panel placeholder')).toBeVisible();
		await expect.element(screen.getByText('Instances table placeholder')).toBeVisible();
	});
});
