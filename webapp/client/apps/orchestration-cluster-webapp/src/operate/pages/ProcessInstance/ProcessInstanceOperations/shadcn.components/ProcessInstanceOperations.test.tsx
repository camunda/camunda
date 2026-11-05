/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {useQuery} from '@tanstack/react-query';
import {HttpResponse} from 'msw';
import {cleanup} from 'vitest-browser-react';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {
	mockCancelProcessInstanceEndpoint,
	mockGetProcessInstanceCallHierarchyEndpoint,
	mockGetProcessInstanceEndpoint,
	mockSuspendProcessInstanceEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {authenticationStore} from '#/shared/auth/authentication.store';
import {Notifications} from '#/shared/notifications/components/Notifications';
import {ProcessInstanceContext} from '#/operate/pages/ProcessInstance/useProcessInstancePage';
import {processInstanceQuery} from '#/operate/pages/ProcessInstance/processInstance.queries';
import {ProcessInstanceOperations} from './ProcessInstanceOperations';

function renderOperations(instance: ProcessInstance) {
	function Preview() {
		const {data} = useQuery({
			...processInstanceQuery(instance.processInstanceKey),
			initialData: instance,
			staleTime: Infinity,
			refetchInterval: false,
		});
		return (
			<ProcessInstanceContext
				value={{processInstanceId: instance.processInstanceKey, processInstance: data, search: {}, selection: {}}}
			>
				<ProcessInstanceOperations />
				<Notifications />
			</ProcessInstanceContext>
		);
	}
	return renderWithRouter(Preview, {
		path: '/operate-preview/processes/$processInstanceId/variables',
		initialEntry: `/operate-preview/processes/${instance.processInstanceKey}/variables`,
	});
}

function createInstance(state: ProcessInstance['state'], hasIncident = false) {
	return createProcessInstance({
		processInstanceKey: String(2251799813680000 + Math.floor(Math.random() * 100000)),
		state,
		hasIncident,
	});
}

function mockViewport(isCollapsed: boolean) {
	vi.spyOn(window, 'matchMedia').mockReturnValue({
		matches: isCollapsed,
		media: '',
		onchange: null,
		addEventListener: vi.fn(),
		removeEventListener: vi.fn(),
		addListener: vi.fn(),
		removeListener: vi.fn(),
		dispatchEvent: vi.fn(),
	});
}

describe('<ProcessInstanceOperations /> (DS)', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
		authenticationStore.activateSession();
		mockViewport(false);
	});

	afterEach(async () => {
		await cleanup();
		vi.restoreAllMocks();
		notificationsStore.reset();
		authenticationStore.reset();
		sessionStorage.clear();
		localStorage.clear();
	});

	it.for([
		{state: 'ACTIVE', hasIncident: true, actions: ['Retry', 'Suspend', 'Cancel']},
		{state: 'ACTIVE', hasIncident: false, actions: ['Suspend', 'Cancel']},
		{state: 'SUSPENDED', hasIncident: false, actions: ['Resume', 'Cancel']},
		{state: 'COMPLETED', hasIncident: false, actions: ['Delete']},
		{state: 'TERMINATED', hasIncident: false, actions: ['Delete']},
	] as const)('should show $actions for $state with incident=$hasIncident', async ({state, hasIncident, actions}) => {
		const instance = createInstance(state, hasIncident);

		const screen = await renderOperations(instance);

		const buttonFor = (action: string) =>
			screen.getByRole('button', {name: `${action} Instance ${instance.processInstanceKey}`});
		const hiddenActions = ['Retry', 'Suspend', 'Resume', 'Cancel', 'Delete'].filter(
			(action) => !(actions as readonly string[]).includes(action),
		);

		for (const action of actions) {
			await expect.element(buttonFor(action)).toBeEnabled();
		}
		for (const action of hiddenActions) {
			await expect.element(buttonFor(action)).not.toBeInTheDocument();
		}
	});

	it('should show the lifecycle of a suspend operation', async ({worker}) => {
		const instance = createInstance('ACTIVE');
		worker.use(
			mockSuspendProcessInstanceEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetProcessInstanceEndpoint({successResponse: HttpResponse.json({...instance, state: 'SUSPENDED'})}),
		);
		const screen = await renderOperations(instance);

		await userEvent.click(screen.getByRole('button', {name: `Suspend Instance ${instance.processInstanceKey}`}));

		await expect.element(screen.getByText('Instance suspended')).toBeVisible();
		await expect
			.element(screen.getByRole('button', {name: `Resume Instance ${instance.processInstanceKey}`}))
			.toBeEnabled();
	});

	it('should only cancel after the confirmation', async ({worker}) => {
		const instance = createInstance('ACTIVE');
		const onRequest = vi.fn();
		worker.events.on('request:start', ({request}) => onRequest(request.url));
		worker.use(
			mockGetProcessInstanceCallHierarchyEndpoint({successResponse: HttpResponse.json([])}),
			mockCancelProcessInstanceEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetProcessInstanceEndpoint({successResponse: HttpResponse.json({...instance, state: 'TERMINATED'})}),
		);
		const screen = await renderOperations(instance);

		await userEvent.click(screen.getByRole('button', {name: `Cancel Instance ${instance.processInstanceKey}`}));
		await userEvent.click(screen.getByRole('button', {name: 'Cancel', exact: true}));

		expect(onRequest).not.toHaveBeenCalledWith(expect.stringContaining('/cancellation'));

		await userEvent.click(screen.getByRole('button', {name: `Cancel Instance ${instance.processInstanceKey}`}));
		await userEvent.click(screen.getByRole('button', {name: 'Apply'}));

		await expect.element(screen.getByText('Instance is scheduled for cancellation')).toBeVisible();
	});

	it('should collapse running instance actions into a menu', async () => {
		mockViewport(true);
		const instance = createInstance('ACTIVE', true);
		const screen = await renderOperations(instance);

		await userEvent.click(screen.getByRole('button', {name: 'Actions'}));

		for (const label of ['Retry', 'Suspend', 'Cancel']) {
			await expect.element(screen.getByRole('menuitem', {name: label})).toBeEnabled();
		}
		await userEvent.keyboard('{Escape}');
		await expect.element(screen.getByRole('button', {name: 'Actions'})).toHaveFocus();
	});

	it('should keep the live region in the collapsed menu layout while an operation runs', async ({worker}) => {
		mockViewport(true);
		const instance = createInstance('ACTIVE');
		worker.use(
			mockSuspendProcessInstanceEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetProcessInstanceEndpoint({successResponse: HttpResponse.json({...instance, state: 'SUSPENDED'})}),
		);
		const screen = await renderOperations(instance);

		await expect.element(screen.getByRole('status')).toBeInTheDocument();

		await userEvent.click(screen.getByRole('button', {name: 'Actions'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Suspend'}));

		await expect.element(screen.getByText('Instance suspended')).toBeVisible();
	});

	it('should show the failed state and an error notification when an operation fails', async ({worker}) => {
		mockViewport(true);
		const instance = createInstance('ACTIVE');
		worker.use(
			mockSuspendProcessInstanceEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);
		const screen = await renderOperations(instance);

		await userEvent.click(screen.getByRole('button', {name: 'Actions'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Suspend'}));

		await expect.element(screen.getByText('Failed', {exact: true})).toBeVisible();
		await expect.element(screen.getByText('Failed to suspend process instance')).toBeVisible();
	});

	it('should keep the delete button visible for finished instances when collapsed', async () => {
		mockViewport(true);
		const instance = createInstance('COMPLETED');
		const screen = await renderOperations(instance);

		await expect
			.element(screen.getByRole('button', {name: `Delete Instance ${instance.processInstanceKey}`}))
			.toBeEnabled();
		await expect.element(screen.getByRole('button', {name: 'Actions'})).not.toBeInTheDocument();
	});
});
