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
import {TasksLayoutPage} from './TasksLayoutPage';

describe('<TasksLayoutPage />', () => {
	it('should display the tasks panel empty state', async () => {
		const screen = await renderWithRouter(TasksLayoutPage, {path: '/shadcn/tasklist'});

		await expect.element(screen.getByRole('heading', {name: 'Tasks', exact: true})).toBeInTheDocument();
		await expect.element(screen.getByRole('heading', {name: 'No tasks found'})).toBeVisible();
		await expect.element(screen.getByText('There are no tasks matching your filter criteria.')).toBeVisible();
	});

	it('should expose the tasks and details panels as labeled regions', async () => {
		const screen = await renderWithRouter(TasksLayoutPage, {path: '/shadcn/tasklist'});

		await expect.element(screen.getByRole('region', {name: 'Tasks side panel'})).toBeVisible();
		await expect.element(screen.getByRole('region', {name: 'Details'})).toBeInTheDocument();
	});
});
