/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {TooltipProvider} from '@camunda/design-system';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {describe, expect} from 'vitest';
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
	currentUser,
};

const TestTask: React.FC<Partial<TaskProps>> = (props) => (
	<TooltipProvider>
		<Task {...baseProps} {...props} />
	</TooltipProvider>
);

describe('<Task />', () => {
	it('should render the task display name and process name', async () => {
		const screen = await renderWithRouter(() => <TestTask />, {
			path: '/shadcn/tasklist/$userTaskKey',
			initialEntry: '/shadcn/tasklist/other-task',
		});

		await expect.element(screen.getByText('Review invoice')).toBeVisible();
		await expect.element(screen.getByText('Invoice process')).toBeVisible();
	});

	it('should render a business id when provided', async () => {
		const screen = await renderWithRouter(() => <TestTask businessId="order-123" />, {
			path: '/shadcn/tasklist/$userTaskKey',
			initialEntry: '/shadcn/tasklist/other-task',
		});

		await expect.element(screen.getByText('order-123')).toBeVisible();
	});

	it('should render a link with an accessible label for an unassigned task', async () => {
		const screen = await renderWithRouter(() => <TestTask assignee={null} />, {
			path: '/shadcn/tasklist/$userTaskKey',
			initialEntry: '/shadcn/tasklist/other-task',
		});

		await expect.element(screen.getByRole('link', {name: 'Unassigned task: Review invoice'})).toBeVisible();
	});

	it('should render an "assigned to me" label', async () => {
		const screen = await renderWithRouter(() => <TestTask assignee={currentUser.username} />, {
			path: '/shadcn/tasklist/$userTaskKey',
			initialEntry: '/shadcn/tasklist/other-task',
		});

		await expect.element(screen.getByRole('link', {name: 'Task assigned to me: Review invoice'})).toBeVisible();
	});

	it('should render an "assigned task" label', async () => {
		const screen = await renderWithRouter(() => <TestTask assignee="john.doe" />, {
			path: '/shadcn/tasklist/$userTaskKey',
			initialEntry: '/shadcn/tasklist/other-task',
		});

		await expect.element(screen.getByRole('link', {name: 'Assigned task: Review invoice'})).toBeVisible();
	});

	it('should render the priority label', async () => {
		const screen = await renderWithRouter(() => <TestTask priority={80} />, {
			path: '/shadcn/tasklist/$userTaskKey',
			initialEntry: '/shadcn/tasklist/other-task',
		});

		await expect.element(screen.getByText('Critical', {exact: true})).toBeVisible();
	});

	it('should not render the priority label', async () => {
		const screen = await renderWithRouter(() => <TestTask priority={null} />, {
			path: '/shadcn/tasklist/$userTaskKey',
			initialEntry: '/shadcn/tasklist/other-task',
		});

		await expect.element(screen.getByText('Critical')).not.toBeInTheDocument();
		await expect.element(screen.getByText('High')).not.toBeInTheDocument();
		await expect.element(screen.getByText('Medium')).not.toBeInTheDocument();
		await expect.element(screen.getByText('Low')).not.toBeInTheDocument();
	});

	it.for([
		{path: '/shadcn/tasklist/$userTaskKey' as const, initialEntry: '/shadcn/tasklist/task-42'},
		{path: '/shadcn/tasklist/$userTaskKey/process' as const, initialEntry: '/shadcn/tasklist/task-42/process'},
		{path: '/shadcn/tasklist/$userTaskKey/history' as const, initialEntry: '/shadcn/tasklist/task-42/history'},
	])('should mark the task as selected at $initialEntry', async ({path, initialEntry}) => {
		const screen = await renderWithRouter(() => <TestTask />, {path, initialEntry});

		await expect
			.element(screen.getByRole('link', {name: 'Unassigned task: Review invoice'}))
			.toHaveAttribute('aria-current', 'page');
	});

	it('should not mark a different task as selected', async () => {
		const screen = await renderWithRouter(() => <TestTask />, {
			path: '/shadcn/tasklist/$userTaskKey/process',
			initialEntry: '/shadcn/tasklist/other-task/process',
		});

		await expect
			.element(screen.getByRole('link', {name: 'Unassigned task: Review invoice'}))
			.not.toHaveAttribute('aria-current');
	});
});
