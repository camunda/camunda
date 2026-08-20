/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {completeTaskRequestBodySchema} from '@camunda/camunda-api-zod-schemas/8.10';
import {HttpResponse} from 'msw';
import {afterEach, beforeEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {z} from 'zod';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';
import {mockCompleteTaskEndpoint, mockGetUserTaskEndpoint} from '#/shared-test-modules/mock-handlers';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {TaskDetailsTaskPage} from './TaskDetailsTaskPage';

const USER_TASK_KEY = '2251799813685281';
const currentUser = createCurrentUser({username: 'demo'});
const search = {filter: 'assigned-to-me', sortBy: 'priority'} as const;
const emptyVariablesCompletionSchema = completeTaskRequestBodySchema.extend({
	variables: z.record(z.string(), z.never()),
});

describe('<TaskDetailsTaskPage />', () => {
	beforeEach(() => {
		localStorage.clear();
	});

	afterEach(() => {
		localStorage.clear();
	});

	it('should enable completion for an assigned created task', async () => {
		const screen = await renderWithRouter(
			() => (
				<TaskDetailsTaskPage
					formSchema={null}
					variables={[]}
					task={createUserTask({assignee: currentUser.username, state: 'CREATED'})}
					currentUser={currentUser}
					search={search}
				/>
			),
			{path: '/tasklist/$userTaskKey', initialEntry: `/tasklist/${USER_TASK_KEY}`},
		);

		await expect.element(screen.getByRole('button', {name: 'Complete Task'})).toBeEnabled();
	});

	it('should disable completion for an unassigned task', async () => {
		const screen = await renderWithRouter(
			() => (
				<TaskDetailsTaskPage
					formSchema={null}
					variables={[]}
					task={createUserTask({assignee: null, state: 'CREATED'})}
					currentUser={currentUser}
					search={search}
				/>
			),
			{path: '/tasklist/$userTaskKey', initialEntry: `/tasklist/${USER_TASK_KEY}`},
		);

		await expect.element(screen.getByRole('button', {name: 'Complete Task'})).toBeDisabled();
	});

	it('should disable completion for a task assigned to another user', async () => {
		const screen = await renderWithRouter(
			() => (
				<TaskDetailsTaskPage
					formSchema={null}
					variables={[]}
					task={createUserTask({assignee: 'alice', state: 'CREATED'})}
					currentUser={currentUser}
					search={search}
				/>
			),
			{path: '/tasklist/$userTaskKey', initialEntry: `/tasklist/${USER_TASK_KEY}`},
		);

		await expect.element(screen.getByRole('button', {name: 'Complete Task'})).toBeDisabled();
	});

	it('should disable completion for a non-created task', async () => {
		const screen = await renderWithRouter(
			() => (
				<TaskDetailsTaskPage
					formSchema={null}
					variables={[]}
					task={createUserTask({assignee: currentUser.username, state: 'UPDATING'})}
					currentUser={currentUser}
					search={search}
				/>
			),
			{path: '/tasklist/$userTaskKey', initialEntry: `/tasklist/${USER_TASK_KEY}`},
		);

		await expect.element(screen.getByRole('button', {name: 'Complete Task'})).toBeDisabled();
	});

	it('should hide completion for a completed task', async () => {
		const screen = await renderWithRouter(
			() => (
				<TaskDetailsTaskPage
					formSchema={null}
					variables={[]}
					task={createUserTask({assignee: currentUser.username, state: 'COMPLETED'})}
					currentUser={currentUser}
					search={search}
				/>
			),
			{path: '/tasklist/$userTaskKey', initialEntry: `/tasklist/${USER_TASK_KEY}`},
		);

		await expect.element(screen.getByText('Complete Task')).not.toBeVisible();
	});

	it('should complete a task without variables', async ({worker}) => {
		worker.use(
			mockCompleteTaskEndpoint({
				schema: emptyVariablesCompletionSchema,
				failureResponse: HttpResponse.json({error: 'bad request'}, {status: 400}),
				successResponse: new HttpResponse(null, {status: 200}),
			}),
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(createUserTask({assignee: currentUser.username, state: 'COMPLETED'})),
			}),
		);
		const {router, ...screen} = await renderWithRouter(
			() => (
				<TaskDetailsTaskPage
					formSchema={null}
					variables={[]}
					task={createUserTask({assignee: currentUser.username, state: 'CREATED'})}
					currentUser={currentUser}
					search={search}
				/>
			),
			{path: '/tasklist/$userTaskKey', initialEntry: `/tasklist/${USER_TASK_KEY}`},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Complete Task'}));

		await expect.poll(() => router.state.location.pathname).toBe('/tasklist');
	});

	it('should preserve tasklist search when completion succeeds', async ({worker}) => {
		worker.use(
			mockCompleteTaskEndpoint({
				successResponse: new HttpResponse(null, {status: 200}),
			}),
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(createUserTask({assignee: currentUser.username, state: 'COMPLETED'})),
			}),
		);
		const {router, ...screen} = await renderWithRouter(
			() => (
				<TaskDetailsTaskPage
					formSchema={null}
					variables={[]}
					task={createUserTask({assignee: currentUser.username, state: 'CREATED'})}
					currentUser={currentUser}
					search={search}
				/>
			),
			{
				path: '/tasklist/$userTaskKey',
				initialEntry: `/tasklist/${USER_TASK_KEY}?filter=assigned-to-me&sortBy=priority`,
			},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Complete Task'}));

		await expect.poll(() => router.state.location.pathname).toBe('/tasklist');
		expect(router.state.location.search).toEqual(search);
	});

	it('should show a failed state when completion is forbidden', async ({worker}) => {
		worker.use(
			mockCompleteTaskEndpoint({
				successResponse: HttpResponse.json(
					createProblemDetails({
						title: 'FORBIDDEN',
						status: 403,
						detail: "Unauthorized to perform operation 'UPDATE' on resource 'USER_TASK'",
						instance: `/v2/user-tasks/${USER_TASK_KEY}/completion`,
					}),
					{status: 403},
				),
			}),
		);
		const screen = await renderWithRouter(
			() => (
				<TaskDetailsTaskPage
					formSchema={null}
					variables={[]}
					task={createUserTask({assignee: currentUser.username, state: 'CREATED'})}
					currentUser={currentUser}
					search={search}
				/>
			),
			{path: '/tasklist/$userTaskKey', initialEntry: `/tasklist/${USER_TASK_KEY}`},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Complete Task'}));

		await expect.element(screen.getByText('Completion failed')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Complete Task'})).toBeVisible();
	});

	it('should handle completion listeners', async ({worker}) => {
		worker.use(
			mockCompleteTaskEndpoint({
				successResponse: HttpResponse.json(
					createProblemDetails({title: 'DEADLINE_EXCEEDED', status: 504, detail: 'Request timed out'}),
					{status: 504},
				),
			}),
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(createUserTask({assignee: currentUser.username, state: 'COMPLETED'})),
			}),
		);
		const {router, ...screen} = await renderWithRouter(
			() => (
				<TaskDetailsTaskPage
					formSchema={null}
					variables={[]}
					task={createUserTask({assignee: currentUser.username, state: 'CREATED'})}
					currentUser={currentUser}
					search={search}
				/>
			),
			{path: '/tasklist/$userTaskKey', initialEntry: `/tasklist/${USER_TASK_KEY}`},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Complete Task'}));

		await expect.poll(() => router.state.location.pathname).toBe('/tasklist');
	});

	it('should handle initially completing tasks', async ({worker}) => {
		worker.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(createUserTask({assignee: currentUser.username, state: 'COMPLETED'})),
			}),
		);
		const {router, ...screen} = await renderWithRouter(
			() => (
				<TaskDetailsTaskPage
					formSchema={null}
					variables={[]}
					task={createUserTask({assignee: currentUser.username, state: 'COMPLETING'})}
					currentUser={currentUser}
					search={search}
				/>
			),
			{path: '/tasklist/$userTaskKey', initialEntry: `/tasklist/${USER_TASK_KEY}`},
		);

		await expect.element(screen.getByText('Completing task...')).toBeVisible();
		await expect.poll(() => router.state.location.pathname).toBe('/tasklist');
	});
});
