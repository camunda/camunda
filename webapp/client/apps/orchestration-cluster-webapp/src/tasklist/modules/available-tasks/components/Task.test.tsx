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

const baseProps = {
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

describe('<Task />', () => {
	it('should render the task display name and process name', async () => {
		const screen = await renderWithRouter(() => <Task {...baseProps} />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/other-task',
		});

		await expect.element(screen.getByText('Review invoice')).toBeVisible();
		await expect.element(screen.getByText('Invoice process')).toBeVisible();
	});

	it('should render a business id when provided', async () => {
		const screen = await renderWithRouter(() => <Task {...baseProps} businessId="order-123" />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/other-task',
		});

		await expect.element(screen.getByText('order-123')).toBeVisible();
	});

	it('should render a link with an accessible label for an unassigned task', async () => {
		const screen = await renderWithRouter(() => <Task {...baseProps} assignee={null} />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/other-task',
		});

		await expect.element(screen.getByRole('link', {name: 'Unassigned task: Review invoice'})).toBeVisible();
	});

	it('should render an "assigned to me" label', async () => {
		const screen = await renderWithRouter(() => <Task {...baseProps} assignee={currentUser.username} />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/other-task',
		});

		await expect.element(screen.getByRole('link', {name: 'Task assigned to me: Review invoice'})).toBeVisible();
	});

	it('should render an "assigned task" label', async () => {
		const screen = await renderWithRouter(() => <Task {...baseProps} assignee="john.doe" />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/other-task',
		});

		await expect.element(screen.getByRole('link', {name: 'Assigned task: Review invoice'})).toBeVisible();
	});

	it('should render the priority label', async () => {
		const screen = await renderWithRouter(() => <Task {...baseProps} priority={80} />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/other-task',
		});

		await expect.element(screen.getByText('Critical', {exact: true})).toBeVisible();
	});

	it('should preserve the task list search params when opening the task', async () => {
		const {router, ...screen} = await renderWithRouter(() => <Task {...baseProps} />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/other-task?filter=custom&sortBy=priority&processDefinitionKey=process-1',
		});

		await userEvent.click(screen.getByRole('link', {name: 'Unassigned task: Review invoice'}));

		await expect.poll(() => router.state.location.pathname).toBe('/tasklist/task-42');
		expect(router.state.location.search).toEqual({
			filter: 'custom',
			processDefinitionKey: 'process-1',
			sortBy: 'priority',
		});
	});

	it('should not render the priority label', async () => {
		const screen = await renderWithRouter(() => <Task {...baseProps} priority={null} />, {
			path: '/tasklist/$userTaskKey',
			initialEntry: '/tasklist/other-task',
		});

		await expect.element(screen.getByText('Critical')).not.toBeInTheDocument();
		await expect.element(screen.getByText('High')).not.toBeInTheDocument();
		await expect.element(screen.getByText('Medium')).not.toBeInTheDocument();
		await expect.element(screen.getByText('Low')).not.toBeInTheDocument();
	});
});
