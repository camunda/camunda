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
import {TasklistProcessesPage} from './TasklistProcessesPage';

describe('<TasklistProcessesPage />', () => {
	it('should display the page heading and description', async () => {
		const screen = await renderWithRouter(TasklistProcessesPage, {path: '/shadcn/tasklist'});

		await expect.element(screen.getByRole('heading', {name: 'Processes'})).toBeVisible();
		await expect.element(screen.getByText('Browse and run processes published by your organization.')).toBeVisible();
	});

	it('should display placeholder process tiles with a disabled start-process action', async () => {
		const screen = await renderWithRouter(TasklistProcessesPage, {path: '/shadcn/tasklist'});

		const startProcessButtons = screen.getByRole('button', {name: 'Start process'});
		await expect.element(startProcessButtons.first()).toBeVisible();
		await expect.element(startProcessButtons.first()).toBeDisabled();
		expect(startProcessButtons.elements()).toHaveLength(3);
	});
});
