/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {assignTaskRequestBodySchema} from '@camunda/camunda-api-zod-schemas/8.10';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import {render} from 'vitest-browser-react';
import {afterEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {
	mockAssignTaskEndpoint,
	mockGetUserTaskEndpoint,
	mockUnassignTaskEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';
import {Notifications} from '#/shared/notifications/components/Notifications';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {AssignButton} from './AssignButton';

const CURRENT_USER = 'demo';
const USER_TASK_KEY = '2251799813685281';
const PERMISSION_ERROR = "You don't have the necessary permissions. Contact your admin to request access.";

const assignmentRequestSchema = assignTaskRequestBodySchema.extend({
	assignee: z.literal(CURRENT_USER),
	allowOverride: z.literal(false),
});

function createProblemDetail({
	title = 'ERROR',
	status = 500,
	detail = 'Something went wrong',
	instance = `/v2/user-tasks/${USER_TASK_KEY}/assignment`,
}: {
	title?: string;
	status?: number;
	detail?: string;
	instance?: string;
} = {}) {
	return {
		type: 'about:blank',
		title,
		status,
		detail,
		instance,
	};
}

function getWrapper() {
	const queryClient = new QueryClient({
		defaultOptions: {
			queries: {retry: false},
		},
	});

	const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
		<QueryClientProvider client={queryClient}>
			{children}
			<Notifications />
		</QueryClientProvider>
	);

	return Wrapper;
}

describe('<AssignButton />', () => {
	afterEach(() => {
		notificationsStore.reset();
	});

	it('should render assign action for an unassigned task', async () => {
		const screen = await render(
			<AssignButton userTaskKey={USER_TASK_KEY} assignee={null} taskState="CREATED" currentUser={CURRENT_USER} />,
			{wrapper: getWrapper()},
		);

		await expect.element(screen.getByRole('button', {name: 'Assign to me'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Unassign'})).not.toBeInTheDocument();
	});

	it('should render unassign action for an assigned task', async () => {
		const screen = await render(
			<AssignButton
				userTaskKey={USER_TASK_KEY}
				assignee={CURRENT_USER}
				taskState="CREATED"
				currentUser={CURRENT_USER}
			/>,
			{wrapper: getWrapper()},
		);

		await expect.element(screen.getByRole('button', {name: 'Unassign'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Assign to me'})).not.toBeInTheDocument();
	});

	it('should assign the task to the current user', async ({worker}) => {
		worker.use(
			mockAssignTaskEndpoint({
				schema: assignmentRequestSchema,
				failureResponse: HttpResponse.json({error: 'bad request'}, {status: 400}),
				successResponse: new HttpResponse(null, {status: 200}),
			}),
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(createUserTask({assignee: CURRENT_USER, state: 'CREATED'})),
			}),
		);

		const screen = await render(
			<AssignButton userTaskKey={USER_TASK_KEY} assignee={null} taskState="CREATED" currentUser={CURRENT_USER} />,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Assign to me'}));

		await expect.element(screen.getByText('Assigning...')).toBeVisible();
		await expect.element(screen.getByText('Assignment successful')).toBeVisible();
	});

	it('should unassign the task', async ({worker}) => {
		worker.use(
			mockUnassignTaskEndpoint({
				successResponse: new HttpResponse(null, {status: 200}),
			}),
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(createUserTask({assignee: null, state: 'CREATED'})),
			}),
		);

		const screen = await render(
			<AssignButton
				userTaskKey={USER_TASK_KEY}
				assignee={CURRENT_USER}
				taskState="CREATED"
				currentUser={CURRENT_USER}
			/>,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Unassign'}));

		await expect.element(screen.getByText('Unassigning...')).toBeVisible();
		await expect.element(screen.getByText('Unassignment successful')).toBeVisible();
	});

	it('should show an assignment permission error notification', async ({worker}) => {
		worker.use(
			mockAssignTaskEndpoint({
				successResponse: HttpResponse.json(
					createProblemDetail({
						title: 'FORBIDDEN',
						status: 403,
						detail: "Unauthorized to perform operation 'UPDATE' on resource 'USER_TASK'",
					}),
					{status: 403},
				),
			}),
		);

		const screen = await render(
			<AssignButton userTaskKey={USER_TASK_KEY} assignee={null} taskState="CREATED" currentUser={CURRENT_USER} />,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Assign to me'}));

		await expect.element(screen.getByText('Task could not be assigned')).toBeVisible();
		await expect.element(screen.getByText(PERMISSION_ERROR)).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Assign to me'})).toBeVisible();
	});

	it('should show an unassignment permission error notification', async ({worker}) => {
		worker.use(
			mockUnassignTaskEndpoint({
				successResponse: HttpResponse.json(
					createProblemDetail({
						title: 'FORBIDDEN',
						status: 403,
						detail: "Unauthorized to perform operation 'UPDATE' on resource 'USER_TASK'",
						instance: `/v2/user-tasks/${USER_TASK_KEY}/assignee`,
					}),
					{status: 403},
				),
			}),
		);

		const screen = await render(
			<AssignButton
				userTaskKey={USER_TASK_KEY}
				assignee={CURRENT_USER}
				taskState="CREATED"
				currentUser={CURRENT_USER}
			/>,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Unassign'}));

		await expect.element(screen.getByText('Task could not be unassigned')).toBeVisible();
		await expect.element(screen.getByText(PERMISSION_ERROR)).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Unassign'})).toBeVisible();
	});

	it('should show the denial reason for an assignment conflict', async ({worker}) => {
		worker.use(
			mockAssignTaskEndpoint({
				successResponse: HttpResponse.json(
					createProblemDetail({
						title: 'CONFLICT',
						status: 409,
						detail: "Command rejected: Reason to deny: 'User not in candidate list'",
					}),
					{status: 409},
				),
			}),
		);

		const screen = await render(
			<AssignButton userTaskKey={USER_TASK_KEY} assignee={null} taskState="CREATED" currentUser={CURRENT_USER} />,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Assign to me'}));

		await expect.element(screen.getByText('Task could not be assigned')).toBeVisible();
		await expect.element(screen.getByText('User not in candidate list')).toBeVisible();
	});

	it('should show delayed notification and finish assignment after timeout', async ({worker}) => {
		worker.use(
			mockAssignTaskEndpoint({
				successResponse: HttpResponse.json(
					createProblemDetail({title: 'DEADLINE_EXCEEDED', status: 504, detail: 'Request timed out'}),
					{status: 504},
				),
			}),
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(createUserTask({assignee: null, state: 'CREATED'})),
			}),
		);

		const screen = await render(
			<AssignButton userTaskKey={USER_TASK_KEY} assignee={null} taskState="CREATED" currentUser={CURRENT_USER} />,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Assign to me'}));

		await expect.element(screen.getByText('Task assignment delayed')).toBeVisible();
		await expect
			.element(
				screen.getByText('The task assignment is taking longer than expected to process. It will complete shortly'),
			)
			.toBeVisible();

		worker.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(createUserTask({assignee: null, state: 'CREATED'})),
			}),
		);

		screen.rerender(
			<AssignButton
				userTaskKey={USER_TASK_KEY}
				assignee={CURRENT_USER}
				taskState="CREATED"
				currentUser={CURRENT_USER}
			/>,
		);

		await expect.element(screen.getByText('Assignment successful')).toBeVisible();
	});

	it('should show delayed notification and finish unassignment after timeout', async ({worker}) => {
		worker.use(
			mockUnassignTaskEndpoint({
				successResponse: HttpResponse.json(
					createProblemDetail({
						title: 'DEADLINE_EXCEEDED',
						status: 504,
						detail: 'Request timed out',
						instance: `/v2/user-tasks/${USER_TASK_KEY}/assignee`,
					}),
					{status: 504},
				),
			}),
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(createUserTask({assignee: CURRENT_USER, state: 'CREATED'})),
			}),
		);

		const screen = await render(
			<AssignButton
				userTaskKey={USER_TASK_KEY}
				assignee={CURRENT_USER}
				taskState="CREATED"
				currentUser={CURRENT_USER}
			/>,
			{wrapper: getWrapper()},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Unassign'}));

		await expect.element(screen.getByText('Task unassignment delayed')).toBeVisible();
		await expect
			.element(
				screen.getByText('The task unassignment is taking longer than expected to process. It will complete shortly'),
			)
			.toBeVisible();

		worker.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(createUserTask({assignee: CURRENT_USER, state: 'CREATED'})),
			}),
		);

		screen.rerender(
			<AssignButton
				userTaskKey={USER_TASK_KEY}
				assignee={CURRENT_USER}
				taskState="CREATED"
				currentUser={CURRENT_USER}
			/>,
		);

		await expect.element(screen.getByText('Unassignment successful')).toBeVisible();
	});

	it('should show assigning state when mounted with an assigning unassigned task', async ({worker}) => {
		worker.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(createUserTask({assignee: CURRENT_USER, state: 'ASSIGNING'})),
			}),
		);

		const screen = await render(
			<AssignButton userTaskKey={USER_TASK_KEY} assignee={null} taskState="ASSIGNING" currentUser={CURRENT_USER} />,
			{wrapper: getWrapper()},
		);

		await expect.element(screen.getByText('Assigning...')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Assign to me'})).not.toBeInTheDocument();

		worker.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(createUserTask({assignee: CURRENT_USER, state: 'CREATED'})),
			}),
		);

		screen.rerender(
			<AssignButton
				userTaskKey={USER_TASK_KEY}
				assignee={CURRENT_USER}
				taskState="CREATED"
				currentUser={CURRENT_USER}
			/>,
		);

		await expect.element(screen.getByRole('button', {name: 'Unassign'})).toBeVisible();
	});

	it('should show unassigning state when mounted with an assigning assigned task', async ({worker}) => {
		worker.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(createUserTask({assignee: null, state: 'ASSIGNING'})),
			}),
		);

		const screen = await render(
			<AssignButton
				userTaskKey={USER_TASK_KEY}
				assignee={CURRENT_USER}
				taskState="ASSIGNING"
				currentUser={CURRENT_USER}
			/>,
			{wrapper: getWrapper()},
		);

		await expect.element(screen.getByText('Unassigning...')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Assign to me'})).not.toBeInTheDocument();

		worker.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(createUserTask({assignee: null, state: 'CREATED'})),
			}),
		);

		screen.rerender(
			<AssignButton userTaskKey={USER_TASK_KEY} assignee={null} taskState="CREATED" currentUser={CURRENT_USER} />,
		);

		await expect.element(screen.getByRole('button', {name: 'Assign to me'})).toBeVisible();
	});
});
