/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createActor, waitFor} from 'xstate';
import {HttpResponse} from 'msw';
import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {createProcessDefinition} from '#/shared-test-modules/api-mocks/process-definitions';
import {createProcessInstanceResponse} from '#/shared-test-modules/api-mocks/process-instances';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createQueryUserTasksResponse, createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';
import {mockCreateProcessInstanceEndpoint, mockQueryUserTasksEndpoint} from '#/shared-test-modules/mock-handlers';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {startProcessMachine} from './startProcessMachine';

describe('startProcessMachine', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		notificationsStore.reset();
		sessionStorage.clear();
	});

	it('should start a process and reset after displaying the success state', async ({worker}) => {
		const process = createProcessDefinition();
		worker.use(
			mockCreateProcessInstanceEndpoint({
				successResponse: HttpResponse.json(createProcessInstanceResponse()),
			}),
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(createQueryUserTasksResponse({items: [createUserTask()]})),
			}),
		);
		const actor = createActor(startProcessMachine, {input: undefined}).start();

		actor.send({type: 'process.start', process});

		await waitFor(actor, (snapshot) => snapshot.matches('Succeeded'));
		expect(actor.getSnapshot().context.selectedProcess).toEqual(process);
		expect(notificationsStore.notifications[0]?.title).toBe('Process has started');

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
		const actor = createActor(startProcessMachine, {input: undefined}).start();

		actor.send({type: 'process.start', process});

		await waitFor(actor, (snapshot) => snapshot.matches('Failed'));
		expect(notificationsStore.notifications).toHaveLength(0);

		await waitFor(actor, (snapshot) => snapshot.matches('Idle'));
		expect(notificationsStore.notifications[0]?.title).toBe('Process start failed');
		expect(notificationsStore.notifications[0]?.subtitle).toBe('Invoice review');
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
		const actor = createActor(startProcessMachine, {input: undefined}).start();

		actor.send({type: 'process.start', process});

		await waitFor(actor, (snapshot) => snapshot.matches('Failed'));
		expect(notificationsStore.notifications).toHaveLength(0);

		await waitFor(actor, (snapshot) => snapshot.matches('Idle'));
		const notification = notificationsStore.notifications[0];
		expect(notification?.title).toBe('Process start failed');
		expect(notification?.subtitle).toBe(
			"You don't have the necessary permissions. Contact your admin to request access.",
		);
		expect(notification?.isDismissable).toBe(true);
		expect(notification?.subtitle).not.toBe('Invoice review');
		expect(actor.getSnapshot().context.selectedProcess).toBeNull();
		expect(actor.getSnapshot().context.failureReason).toBeNull();
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
