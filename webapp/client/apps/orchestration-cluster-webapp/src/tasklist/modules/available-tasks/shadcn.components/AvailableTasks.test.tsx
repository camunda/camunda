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
import {describe, expect, vi} from 'vitest';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryUserTasksResponse, createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';
import {AvailableTasks} from './AvailableTasks';

const currentUser = createCurrentUser({username: 'demo'});
const noop = vi.fn().mockResolvedValue(undefined);

describe('<AvailableTasks />', () => {
	it('should render the list of tasks', async () => {
		const tasks = [
			createUserTask({userTaskKey: '1', name: 'First Task'}),
			createUserTask({userTaskKey: '2', name: 'Second Task'}),
		];

		const screen = await renderWithRouter(
			() => (
				<TooltipProvider>
					<AvailableTasks
						pages={[createQueryUserTasksResponse({items: tasks})]}
						currentUser={currentUser}
						hasNextPage={false}
						hasPreviousPage={false}
						onScrollDown={noop}
						onScrollUp={noop}
					/>
				</TooltipProvider>
			),
			{path: '/shadcn/tasklist/$userTaskKey', initialEntry: '/shadcn/tasklist/1'},
		);

		await expect.element(screen.getByText('First Task')).toBeVisible();
		await expect.element(screen.getByText('Second Task')).toBeVisible();
	});

	it('should render the empty state', async () => {
		const screen = await renderWithRouter(
			() => (
				<AvailableTasks
					pages={[createQueryUserTasksResponse()]}
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
});
