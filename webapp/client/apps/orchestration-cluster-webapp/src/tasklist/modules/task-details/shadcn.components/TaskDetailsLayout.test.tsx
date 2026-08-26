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
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';
import {TaskDetailsLayout} from './TaskDetailsLayout';

const currentUser = createCurrentUser({username: 'demo'});
const task = createUserTask({userTaskKey: '123', name: 'Review invoice', processName: 'Invoice process'});

describe('<TaskDetailsLayout />', () => {
	it('should render header, tabs, aside, and content', async () => {
		const screen = await renderWithRouter(
			() => (
				<TaskDetailsLayout task={task} currentUser={currentUser}>
					<div data-testid="child-content">Child</div>
				</TaskDetailsLayout>
			),
			{path: '/shadcn/tasklist/$userTaskKey', initialEntry: '/shadcn/tasklist/123'},
		);

		await expect.element(screen.getByText('Review invoice')).toBeVisible();
		await expect.element(screen.getByText('Invoice process')).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Show task', exact: true})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Show associated BPMN process'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Show task history'})).toBeVisible();
		await expect.element(screen.getByRole('complementary', {name: 'Task details right panel'})).toBeVisible();
		await expect.element(screen.getByTestId('child-content')).toBeVisible();
		await expect.element(screen.getByTestId('details-info')).toBeVisible();
	});

	it.for([
		{
			path: '/shadcn/tasklist/$userTaskKey' as const,
			initialEntry: '/shadcn/tasklist/123',
			selectedTab: 'Show task',
		},
		{
			path: '/shadcn/tasklist/$userTaskKey/process' as const,
			initialEntry: '/shadcn/tasklist/123/process',
			selectedTab: 'Show associated BPMN process',
		},
		{
			path: '/shadcn/tasklist/$userTaskKey/history' as const,
			initialEntry: '/shadcn/tasklist/123/history',
			selectedTab: 'Show task history',
		},
	])('should mark $selectedTab as selected', async ({path, initialEntry, selectedTab}) => {
		const screen = await renderWithRouter(
			() => (
				<TaskDetailsLayout task={task} currentUser={currentUser}>
					<div />
				</TaskDetailsLayout>
			),
			{path, initialEntry},
		);

		await expect
			.element(screen.getByRole('tab', {name: selectedTab, exact: true}))
			.toHaveAttribute('aria-selected', 'true');
	});

	it('should render the aside panel with task details', async () => {
		const taskWithDetails = createUserTask({
			userTaskKey: '123',
			candidateUsers: ['alice'],
			candidateGroups: ['managers'],
			priority: 80,
		});
		const screen = await renderWithRouter(
			() => (
				<TaskDetailsLayout task={taskWithDetails} currentUser={currentUser}>
					<div />
				</TaskDetailsLayout>
			),
			{path: '/shadcn/tasklist/$userTaskKey', initialEntry: '/shadcn/tasklist/123'},
		);
		const aside = screen.getByRole('complementary', {name: 'Task details right panel'});

		await expect.element(aside.getByText('Creation date')).toBeVisible();
		await expect.element(aside.getByText('alice')).toBeVisible();
		await expect.element(aside.getByText('managers')).toBeVisible();
		await expect.element(aside.getByText('Critical')).toBeVisible();
	});

	it('should fall back to element and process definition ids when names are missing', async () => {
		const taskWithoutNames = createUserTask({
			userTaskKey: '123',
			name: null,
			processName: null,
			elementId: 'review-invoice',
			processDefinitionId: 'invoice-process',
		});
		const screen = await renderWithRouter(
			() => (
				<TaskDetailsLayout task={taskWithoutNames} currentUser={currentUser}>
					<div />
				</TaskDetailsLayout>
			),
			{path: '/shadcn/tasklist/$userTaskKey', initialEntry: '/shadcn/tasklist/123'},
		);

		await expect.element(screen.getByText('review-invoice')).toBeVisible();
		await expect.element(screen.getByText('invoice-process')).toBeVisible();
	});
});
