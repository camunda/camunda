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
import {userEvent} from 'vitest/browser';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {Task} from './Task';

const currentUser = createCurrentUser({username: 'demo'});

type TaskProps = React.ComponentProps<typeof Task>;

const baseProps: TaskProps = {
	userTaskKey: 'task-42',
	displayName: 'Review invoice',
	businessId: null,
	processDisplayName: 'Invoice process',
	assignee: null,
	creationDate: '2024-01-06T12:00:00.000Z',
	followUpDate: null,
	dueDate: null,
	completionDate: null,
	priority: 50,
	isSuspended: false,
	currentUser,
};

const TestTask: React.FC<Partial<TaskProps>> = (props) => <Task {...baseProps} {...props} />;

describe('<Task />', () => {
	it('should render the task display name and process name', async () => {
		const screen = await renderWithRouter(() => <TestTask />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/other-task',
		});

		await expect.element(screen.getByText('Review invoice')).toBeVisible();
		await expect.element(screen.getByText('Invoice process')).toBeVisible();
	});

	it('should render a business id when provided', async () => {
		const screen = await renderWithRouter(() => <TestTask businessId="order-123" />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/other-task',
		});

		await expect.element(screen.getByText('order-123')).toBeVisible();
	});

	it('should render a link with an accessible label for an unassigned task', async () => {
		const screen = await renderWithRouter(() => <TestTask assignee={null} />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/other-task',
		});

		await expect.element(screen.getByRole('link', {name: 'Unassigned task: Review invoice'})).toBeVisible();
	});

	it('should render an "assigned to me" label', async () => {
		const screen = await renderWithRouter(() => <TestTask assignee={currentUser.username} />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/other-task',
		});

		await expect.element(screen.getByRole('link', {name: 'Task assigned to me: Review invoice'})).toBeVisible();
	});

	it('should render an "assigned task" label', async () => {
		const screen = await renderWithRouter(() => <TestTask assignee="john.doe" />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/other-task',
		});

		await expect.element(screen.getByRole('link', {name: 'Assigned task: Review invoice'})).toBeVisible();
	});

	it('should render the priority label', async () => {
		const screen = await renderWithRouter(() => <TestTask priority={80} />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/other-task',
		});

		await expect.element(screen.getByText('Critical', {exact: true})).toBeVisible();
	});

	it('should not render the priority label', async () => {
		const screen = await renderWithRouter(() => <TestTask priority={null} />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/other-task',
		});

		await expect.element(screen.getByText('Critical')).not.toBeInTheDocument();
		await expect.element(screen.getByText('High')).not.toBeInTheDocument();
		await expect.element(screen.getByText('Medium')).not.toBeInTheDocument();
		await expect.element(screen.getByText('Low')).not.toBeInTheDocument();
	});

	it('should render the suspended icon and announce it in the link name', async () => {
		const screen = await renderWithRouter(() => <TestTask isSuspended />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/other-task',
		});

		await expect.element(screen.getByRole('link', {name: 'Unassigned task: Review invoice (suspended)'})).toBeVisible();
		await expect.element(screen.getByText('Suspended', {exact: true})).not.toBeInTheDocument();

		await userEvent.hover(screen.getByRole('img', {name: 'Process instance suspended'}));

		await expect.element(screen.getByRole('tooltip', {name: 'Process instance suspended'})).toBeVisible();
	});

	it('should not render the suspended icon', async () => {
		const screen = await renderWithRouter(() => <TestTask isSuspended={false} />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/other-task',
		});

		await expect.element(screen.getByRole('link', {name: 'Unassigned task: Review invoice'})).toBeVisible();
		await expect.element(screen.getByRole('img', {name: 'Process instance suspended'})).not.toBeInTheDocument();
	});

	it.for([
		{path: '/tasklist/$userTaskKey' as const, initialEntry: '/tasklist/task-42'},
		{path: '/tasklist/$userTaskKey/process' as const, initialEntry: '/tasklist/task-42/process'},
		{path: '/tasklist/$userTaskKey/history' as const, initialEntry: '/tasklist/task-42/history'},
	])('should mark the task as selected at $initialEntry', async ({path, initialEntry}) => {
		const screen = await renderWithRouter(() => <TestTask />, {path, initialEntry});

		await expect
			.element(screen.getByRole('link', {name: 'Unassigned task: Review invoice'}))
			.toHaveAttribute('aria-current', 'page');
	});

	it('should not mark a different task as selected', async () => {
		const screen = await renderWithRouter(() => <TestTask />, {
			path: '/tasklist/$userTaskKey/process',
			initialEntry: '/tasklist/other-task/process',
		});

		await expect
			.element(screen.getByRole('link', {name: 'Unassigned task: Review invoice'}))
			.not.toHaveAttribute('aria-current');
	});
});
