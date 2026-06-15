/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {describe, expect, beforeEach, afterEach} from 'vitest';
import {storeStateLocally, clearStateLocally} from '#/shared/browser-storage/local-storage';
import {NoTaskSelectedPage} from './NoTaskSelectedPage';

describe('<NoTaskSelectedPage />', () => {
	beforeEach(() => {
		localStorage.clear();
	});

	afterEach(() => {
		clearStateLocally('hasCompletedTask');
	});

	it('should show the welcome header for a new user', async () => {
		const screen = await render(<NoTaskSelectedPage hasNoTasks={false} />);

		await expect.element(screen.getByRole('heading', {name: 'Welcome to Tasklist'})).toBeVisible();
	});

	it('should show the tutorial paragraph for a new user', async () => {
		const screen = await render(<NoTaskSelectedPage hasNoTasks={false} />);

		await expect.element(screen.getByTestId('tutorial-paragraph')).toBeVisible();
	});

	it('should show the task-available prompt for a new user when there are tasks', async () => {
		const screen = await render(<NoTaskSelectedPage hasNoTasks={false} />);

		await expect.element(screen.getByText('Select a task to view its details.')).toBeVisible();
	});

	it('should not show the task-available prompt for a new user when there are no tasks', async () => {
		const screen = await render(<NoTaskSelectedPage hasNoTasks={true} />);

		await expect.element(screen.getByText('Select a task to view its details.')).not.toBeInTheDocument();
	});

	it('should show the pick-a-task prompt for a returning user', async () => {
		storeStateLocally('hasCompletedTask', true);

		const screen = await render(<NoTaskSelectedPage hasNoTasks={false} />);

		await expect.element(screen.getByRole('heading', {name: 'Pick a task to work on'})).toBeVisible();
	});

	it('should not show the welcome header for a returning user', async () => {
		storeStateLocally('hasCompletedTask', true);

		const screen = await render(<NoTaskSelectedPage hasNoTasks={false} />);

		await expect.element(screen.getByRole('heading', {name: 'Welcome to Tasklist'})).not.toBeInTheDocument();
	});

	it('should render nothing for a returning user when there are no tasks', async () => {
		storeStateLocally('hasCompletedTask', true);

		const screen = await render(<NoTaskSelectedPage hasNoTasks={true} />);

		await expect.element(screen.getByRole('heading', {name: 'Pick a task to work on'})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('heading', {name: 'Welcome to Tasklist'})).not.toBeInTheDocument();
	});
});
