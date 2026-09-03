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
import {HttpResponse} from 'msw';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryUserTasksResponse} from '#/shared-test-modules/api-mocks/user-tasks';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {mockCurrentUserEndpoint} from '#/shared-test-modules/mock-handlers';
import {TasksLayoutPage} from './TasksLayoutPage';

const currentUser = createCurrentUser({username: 'demo'});
const noop = vi.fn().mockResolvedValue(undefined);

describe('<TasksLayoutPage />', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should display the tasks panel empty state', async ({worker}) => {
		worker.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(currentUser)}));
		const screen = await renderWithRouter(
			() => (
				<TasksLayoutPage
					pages={[createQueryUserTasksResponse()]}
					currentUser={currentUser}
					hasNextPage={false}
					hasPreviousPage={false}
					onScrollDown={noop}
					onScrollUp={noop}
				/>
			),
			{
				path: '/shadcn/_auth/tasklist/_tasks',
				initialEntry: '/shadcn/_auth/tasklist/_tasks?filter=all-open&sortBy=creation',
			},
		);

		await expect.element(screen.getByRole('heading', {name: 'Tasks', exact: true})).toBeInTheDocument();
		await expect.element(screen.getByRole('heading', {name: 'No tasks found'})).toBeVisible();
		await expect.element(screen.getByText('There are no tasks matching your filter criteria.')).toBeVisible();
	});
});
