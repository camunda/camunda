/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';
import {TaskDetailsLayout} from './TaskDetailsLayout';

const currentUser = createCurrentUser({username: 'demo'});
const task = createUserTask({name: 'Review invoice', processName: 'Invoice process'});

describe('<TaskDetailsLayout />', () => {
	beforeEach(() => {
		vi.stubGlobal('Notification', {permission: 'granted'});
	});

	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it('should render header, tabs, aside, and content', async () => {
		const screen = await renderWithRouter(
			() => (
				<TaskDetailsLayout task={task} currentUser={currentUser} assignButton={null}>
					<div data-testid="child-content">Child</div>
				</TaskDetailsLayout>
			),
			{path: '/tasklist/$userTaskKey', initialEntry: '/tasklist/123'},
		);

		await expect.element(screen.getByText('Review invoice')).toBeVisible();
		await expect.element(screen.getByText('Invoice process')).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'Show task', exact: true})).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'Show associated BPMN process'})).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'Show task history'})).toBeVisible();
		await expect.element(screen.getByRole('complementary', {name: 'Task details right panel'})).toBeVisible();
		await expect.element(screen.getByTestId('child-content')).toBeVisible();
		await expect.element(screen.getByTestId('details-info')).toBeVisible();
	});

	it('should mark the task tab as selected', async () => {
		const screen = await renderWithRouter(
			() => (
				<TaskDetailsLayout task={task} currentUser={currentUser} assignButton={null}>
					<div />
				</TaskDetailsLayout>
			),
			{path: '/tasklist/$userTaskKey', initialEntry: '/tasklist/123'},
		);

		await expect
			.element(screen.getByRole('link', {name: 'Show task', exact: true}))
			.toHaveAttribute('aria-current', 'page');
		await expect
			.element(screen.getByRole('link', {name: 'Show associated BPMN process'}))
			.not.toHaveAttribute('aria-current');
	});

	it('should render the aside panel with task details', async () => {
		const taskWithDetails = createUserTask({
			candidateUsers: ['alice'],
			candidateGroups: ['managers'],
			priority: 80,
		});

		const screen = await renderWithRouter(
			() => (
				<TaskDetailsLayout task={taskWithDetails} currentUser={currentUser} assignButton={null}>
					<div />
				</TaskDetailsLayout>
			),
			{path: '/tasklist/$userTaskKey', initialEntry: '/tasklist/123'},
		);

		await expect.element(screen.getByText('Creation date')).toBeVisible();
		await expect.element(screen.getByText('alice')).toBeVisible();
		await expect.element(screen.getByText('managers')).toBeVisible();
		await expect.element(screen.getByText('Critical')).toBeVisible();
	});
});
