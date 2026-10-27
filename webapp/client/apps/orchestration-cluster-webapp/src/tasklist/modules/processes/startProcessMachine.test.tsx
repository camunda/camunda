/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createActor, waitFor} from 'xstate';
import {Toaster, toast} from '@camunda/design-system';
import {HttpResponse} from 'msw';
import {render} from 'vitest-browser-react';
import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {createProcessDefinition} from '#/shared-test-modules/api-mocks/process-definitions';
import {createProcessInstanceResponse} from '#/shared-test-modules/api-mocks/process-instances';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createQueryUserTasksResponse, createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';
import {mockCreateProcessInstanceEndpoint, mockQueryUserTasksEndpoint} from '#/shared-test-modules/mock-handlers';
import {startProcessMachine} from './startProcessMachine';

describe('startProcessMachine', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(async () => {
		toast.dismiss();
		sessionStorage.clear();
	});

	it('should start a process and reset after displaying the success state', async ({worker}) => {
		const process = createProcessDefinition();
		const task = createUserTask();
		worker.use(
			mockCreateProcessInstanceEndpoint({
				successResponse: HttpResponse.json(createProcessInstanceResponse()),
			}),
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(createQueryUserTasksResponse({items: [task]})),
			}),
		);
		const navigate = vi.fn();
		const screen = await render(<Toaster />);
		const actor = createActor(startProcessMachine, {input: {navigate}}).start();

		actor.send({type: 'process.start', process});

		await waitFor(actor, (snapshot) => snapshot.matches('Succeeded'));
		expect(actor.getSnapshot().context.selectedProcess).toEqual(process);
		await expect.element(screen.getByText('Process has started')).toBeVisible();
		expect(navigate).toHaveBeenCalledWith({
			to: '/tasklist/$userTaskKey',
			params: {userTaskKey: task.userTaskKey},
			search: {filter: 'all-open', sortBy: 'creation'},
		});

		await waitFor(actor, (snapshot) => snapshot.matches('Idle'));
		expect(actor.getSnapshot().context.selectedProcess).toBeNull();
		actor.stop();
	});

	it('should display a failure state and notify the user before resetting', async ({worker}) => {
		const process = createProcessDefinition({name: 'Invoice review'});
		worker.use(
			mockCreateProcessInstanceEndpoint({
				successResponse: new HttpResponse(null, {status: 500}),
			}),
		);
		const screen = await render(<Toaster />);
		const actor = createActor(startProcessMachine, {input: undefined}).start();

		actor.send({type: 'process.start', process});

		await waitFor(actor, (snapshot) => snapshot.matches('Failed'));
		expect(screen.getByText('Process start failed').elements()).toHaveLength(0);

		await waitFor(actor, (snapshot) => snapshot.matches('Idle'));
		await expect.element(screen.getByText('Process start failed')).toBeVisible();
		await expect.element(screen.getByText('Invoice review')).toBeVisible();
		expect(actor.getSnapshot().context.selectedProcess).toBeNull();
		actor.stop();
	});

	it('should display a permission error notification when process start is forbidden', async ({worker}) => {
		const process = createProcessDefinition({name: 'Invoice review'});
		worker.use(
			mockCreateProcessInstanceEndpoint({
				successResponse: new HttpResponse(null, {status: 403}),
			}),
		);
		const screen = await render(<Toaster />);
		const actor = createActor(startProcessMachine, {input: undefined}).start();

		actor.send({type: 'process.start', process});

		await waitFor(actor, (snapshot) => snapshot.matches('Failed'));
		expect(screen.getByText('Process start failed').elements()).toHaveLength(0);

		await waitFor(actor, (snapshot) => snapshot.matches('Idle'));
		await expect.element(screen.getByText('Process start failed')).toBeVisible();
		await expect
			.element(screen.getByText("You don't have the necessary permissions. Contact your admin to request access."))
			.toBeVisible();
		expect(screen.getByText('Invoice review').elements()).toHaveLength(0);
		expect(actor.getSnapshot().context.selectedProcess).toBeNull();
		expect(actor.getSnapshot().context.failureReason).toBeNull();
		actor.stop();
	});

	it('should notify about multiple tasks and navigate from their actions', async ({worker}) => {
		const tasks = [
			createUserTask({userTaskKey: '1', name: 'Review invoice'}),
			createUserTask({userTaskKey: '2', name: 'Approve invoice'}),
		];
		worker.use(
			mockCreateProcessInstanceEndpoint({
				successResponse: HttpResponse.json(createProcessInstanceResponse()),
			}),
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(createQueryUserTasksResponse({items: tasks})),
			}),
		);
		const navigate = vi.fn();
		const screen = await render(<Toaster />);
		const actor = createActor(startProcessMachine, {input: {navigate}}).start();

		actor.send({type: 'process.start', process: createProcessDefinition()});

		await waitFor(actor, (snapshot) => snapshot.matches('Succeeded'));
		await expect.element(screen.getByText('Process "My Process" reached task "Review invoice"')).toBeVisible();
		await expect.element(screen.getByText('Process "My Process" reached task "Approve invoice"')).toBeVisible();
		for (const button of screen.getByRole('button', {name: 'Open task'}).elements()) {
			await userEvent.click(button);
		}
		expect(navigate).toHaveBeenCalledWith({
			to: '/tasklist/$userTaskKey',
			params: {userTaskKey: '1'},
			search: {filter: 'all-open', sortBy: 'creation'},
		});
		expect(navigate).toHaveBeenCalledWith({
			to: '/tasklist/$userTaskKey',
			params: {userTaskKey: '2'},
			search: {filter: 'all-open', sortBy: 'creation'},
		});
		actor.stop();
	});

	it('should start processes that require a start form and close the form', async ({worker}) => {
		worker.use(
			mockCreateProcessInstanceEndpoint({
				successResponse: HttpResponse.json(createProcessInstanceResponse()),
			}),
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(createQueryUserTasksResponse({items: [createUserTask()]})),
			}),
		);
		const navigate = vi.fn();
		const process = createProcessDefinition({hasStartForm: true});
		const actor = createActor(startProcessMachine, {input: {navigate}}).start();

		actor.send({
			type: 'process.start',
			process,
		});

		expect(actor.getSnapshot().matches('Starting')).toBe(true);
		expect(actor.getSnapshot().context.selectedProcess).toEqual(process);
		expect(navigate).toHaveBeenCalledWith({to: '/tasklist/processes', search: true});
		await waitFor(actor, (snapshot) => snapshot.matches('Succeeded'));
		actor.stop();
	});

	it('should ignore another process while a process start is in progress', async ({worker}) => {
		const selectedProcess = createProcessDefinition({processDefinitionKey: '1'});
		worker.use(
			mockCreateProcessInstanceEndpoint({
				delay: 100,
				successResponse: HttpResponse.json(createProcessInstanceResponse()),
			}),
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(createQueryUserTasksResponse({items: [createUserTask()]})),
			}),
		);
		const actor = createActor(startProcessMachine, {input: undefined}).start();

		actor.send({type: 'process.start', process: selectedProcess});
		actor.send({
			type: 'process.start',
			process: createProcessDefinition({processDefinitionKey: '2'}),
		});

		expect(actor.getSnapshot().matches('Starting')).toBe(true);
		expect(actor.getSnapshot().context.selectedProcess).toEqual(selectedProcess);
		await waitFor(actor, (snapshot) => snapshot.matches('Succeeded'));
		actor.stop();
	});
});
