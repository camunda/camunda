/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {AvailableTasks} from './AvailableTasks';
import {createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';

const currentUser = createCurrentUser({username: 'demo'});

const noop = vi.fn().mockResolvedValue([]);

function createTasks(count: number) {
	return Array.from({length: count}, (_, i) => createUserTask({userTaskKey: String(i), name: `Task ${i}`}));
}

describe('<AvailableTasks />', () => {
	it('should render the list of tasks', async () => {
		const tasks = [
			createUserTask({userTaskKey: '1', name: 'First Task'}),
			createUserTask({userTaskKey: '2', name: 'Second Task'}),
		];

		const screen = await renderWithRouter(
			() => (
				<AvailableTasks
					tasks={tasks}
					currentUser={currentUser}
					hasNextPage={false}
					hasPreviousPage={false}
					onScrollDown={noop}
					onScrollUp={noop}
				/>
			),
			{path: '/tasklist/$id', initialEntry: '/tasklist/1'},
		);

		await expect.element(screen.getByText('First Task')).toBeVisible();
		await expect.element(screen.getByText('Second Task')).toBeVisible();
	});

	it('should render the empty state', async () => {
		const screen = await renderWithRouter(
			() => (
				<AvailableTasks
					tasks={[]}
					currentUser={currentUser}
					hasNextPage={false}
					hasPreviousPage={false}
					onScrollDown={noop}
					onScrollUp={noop}
				/>
			),
			{path: '/', initialEntry: '/'},
		);

		await expect.element(screen.getByText('No tasks found')).toBeVisible();
	});

	it('should call onScrollDown', async () => {
		const tasks = createTasks(20);
		const onScrollDown = vi.fn().mockResolvedValue([]);
		const onScrollUp = vi.fn().mockResolvedValue([]);

		const screen = await renderWithRouter(
			() => (
				<div style={{height: '100px'}}>
					<AvailableTasks
						tasks={tasks}
						currentUser={currentUser}
						hasNextPage={true}
						hasPreviousPage={false}
						onScrollDown={onScrollDown}
						onScrollUp={onScrollUp}
					/>
				</div>
			),
			{path: '/tasklist/$id', initialEntry: '/tasklist/1'},
		);

		await userEvent.wheel(screen.getByTestId('scrollable-list'), {delta: {y: 10000}});

		expect(onScrollDown).toHaveBeenCalled();
		expect(onScrollUp).not.toHaveBeenCalled();
	});

	it('should not call onScrollDown when there is no next page', async () => {
		const tasks = createTasks(20);
		const onScrollDown = vi.fn().mockResolvedValue([]);
		const onScrollUp = vi.fn().mockResolvedValue([]);

		const screen = await renderWithRouter(
			() => (
				<div style={{height: '100px'}}>
					<AvailableTasks
						tasks={tasks}
						currentUser={currentUser}
						hasNextPage={false}
						hasPreviousPage={false}
						onScrollDown={onScrollDown}
						onScrollUp={onScrollUp}
					/>
				</div>
			),
			{path: '/tasklist/$id', initialEntry: '/tasklist/1'},
		);

		await userEvent.wheel(screen.getByTestId('scrollable-list'), {delta: {y: 10000}});

		expect(onScrollDown).not.toHaveBeenCalled();
	});

	it('should call onScrollUp', async () => {
		const tasks = createTasks(20);
		const onScrollDown = vi.fn().mockResolvedValue([]);
		const onScrollUp = vi.fn().mockResolvedValue([]);

		const screen = await renderWithRouter(
			() => (
				<div style={{height: '100px'}}>
					<AvailableTasks
						tasks={tasks}
						currentUser={currentUser}
						hasNextPage={false}
						hasPreviousPage={true}
						onScrollDown={onScrollDown}
						onScrollUp={onScrollUp}
					/>
				</div>
			),
			{path: '/tasklist/$id', initialEntry: '/tasklist/1'},
		);

		const scrollableList = screen.getByTestId('scrollable-list');

		await userEvent.wheel(scrollableList, {delta: {y: 200}});
		await userEvent.wheel(scrollableList, {delta: {y: -10000}});

		expect(onScrollUp).toHaveBeenCalled();
		expect(onScrollDown).not.toHaveBeenCalled();
	});

	it('should not call onScrollUp', async () => {
		const tasks = createTasks(20);
		const onScrollDown = vi.fn().mockResolvedValue([]);
		const onScrollUp = vi.fn().mockResolvedValue([]);

		const screen = await renderWithRouter(
			() => (
				<div style={{height: '100px'}}>
					<AvailableTasks
						tasks={tasks}
						currentUser={currentUser}
						hasNextPage={false}
						hasPreviousPage={false}
						onScrollDown={onScrollDown}
						onScrollUp={onScrollUp}
					/>
				</div>
			),
			{path: '/tasklist/$id', initialEntry: '/tasklist/1'},
		);

		const scrollableList = screen.getByTestId('scrollable-list');
		await userEvent.wheel(scrollableList, {delta: {y: 200}});
		await userEvent.wheel(scrollableList, {delta: {y: -10000}});

		expect(onScrollUp).not.toHaveBeenCalled();
	});
});
