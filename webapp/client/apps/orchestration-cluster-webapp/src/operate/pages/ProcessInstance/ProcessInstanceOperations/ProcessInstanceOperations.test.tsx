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
import {holdResponse} from '#/vitest-modules/hold-response';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {
	mockCancelProcessInstanceEndpoint,
	mockDeleteProcessInstanceEndpoint,
	mockGetProcessInstanceCallHierarchyEndpoint,
	mockGetProcessInstanceEndpoint,
	mockResolveProcessInstanceIncidentsEndpoint,
	mockResumeProcessInstanceEndpoint,
	mockSuspendProcessInstanceEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {authenticationStore} from '#/shared/auth/authentication.store';
import {storeStateLocally} from '#/shared/browser-storage/local-storage';
import {Notifications} from '#/shared/notifications/components/Notifications';
import {ProcessInstanceContext} from '../useProcessInstancePage';
import {processInstanceQuery} from '../processInstance.queries';
import {ProcessInstanceOperations} from './ProcessInstanceOperations';

const ACTIONS = [
	{
		action: 'Retry',
		state: 'ACTIVE',
		pending: 'Retrying...',
		success: 'Incidents are scheduled for retry',
		mock: mockResolveProcessInstanceIncidentsEndpoint,
	},
	{
		action: 'Cancel',
		state: 'ACTIVE',
		pending: 'Canceling...',
		success: 'Instance is scheduled for cancellation',
		mock: mockCancelProcessInstanceEndpoint,
	},
	{
		action: 'Delete',
		state: 'COMPLETED',
		pending: 'Deleting...',
		success: 'Instance is scheduled for deletion',
		mock: mockDeleteProcessInstanceEndpoint,
	},
	{
		action: 'Suspend',
		state: 'ACTIVE',
		pending: 'Suspending...',
		success: 'Instance suspended',
		mock: mockSuspendProcessInstanceEndpoint,
	},
	{
		action: 'Resume',
		state: 'SUSPENDED',
		pending: 'Resuming...',
		success: 'Instance resumed',
		mock: mockResumeProcessInstanceEndpoint,
	},
] as const;

function renderOperations(instance: ProcessInstance, {isModificationModeEnabled = false, basepath = ''} = {}) {
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
				<ProcessInstanceOperations isModificationModeEnabled={isModificationModeEnabled} />
				<Notifications />
			</ProcessInstanceContext>
		);
	}
	return renderWithRouter(Preview, {
		path: '/operate/processes/$processInstanceId/variables',
		basepath,
		initialEntry: `${basepath}/operate/processes/${instance.processInstanceKey}/variables?tenantId=tenant-a&elementId=task`,
	});
}

function createInstance(state: ProcessInstance['state'], hasIncident = true) {
	return createProcessInstance({
		processInstanceKey: String(2251799813680000 + Math.floor(Math.random() * 100000)),
		state,
		hasIncident,
		tenantId: 'tenant-a',
	});
}

function mockViewport(isCollapsed: boolean) {
	const listeners = new Set<() => void>();
	const query = {
		matches: isCollapsed,
		media: '',
		onchange: null,
		addEventListener: vi.fn((_type: string, listener: () => void) => listeners.add(listener)),
		removeEventListener: vi.fn((_type: string, listener: () => void) => listeners.delete(listener)),
		addListener: vi.fn(),
		removeListener: vi.fn(),
		dispatchEvent: vi.fn(),
	};
	vi.spyOn(window, 'matchMedia').mockReturnValue(query);
	return (matches: boolean) => {
		query.matches = matches;
		listeners.forEach((listener) => listener());
	};
}

async function execute(screen: Awaited<ReturnType<typeof renderOperations>>, action: string, key: string) {
	await userEvent.click(screen.getByRole('button', {name: `${action} Instance ${key}`}));
	if (action === 'Cancel') {
		await userEvent.click(screen.getByRole('button', {name: 'Apply'}));
	} else if (action === 'Delete') {
		await userEvent.click(screen.getByRole('dialog').getByRole('button', {name: 'Delete'}));
	}
}

describe('<ProcessInstanceOperations />', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
		authenticationStore.activateSession();
	});

	afterEach(() => {
		vi.restoreAllMocks();
		notificationsStore.reset();
		authenticationStore.reset();
		sessionStorage.clear();
		localStorage.clear();
	});

	it.for([
		{state: 'ACTIVE', hasIncident: true, actions: ['Retry', 'Suspend', 'Cancel', 'Migrate']},
		{state: 'ACTIVE', hasIncident: false, actions: ['Suspend', 'Cancel', 'Migrate']},
		{state: 'SUSPENDED', hasIncident: true, actions: ['Resume', 'Cancel']},
		{state: 'SUSPENDED', hasIncident: false, actions: ['Resume', 'Cancel']},
		{state: 'COMPLETED', hasIncident: true, actions: ['Delete']},
		{state: 'COMPLETED', hasIncident: false, actions: ['Delete']},
		{state: 'TERMINATED', hasIncident: true, actions: ['Delete']},
		{state: 'TERMINATED', hasIncident: false, actions: ['Delete']},
	] as const)(
		'should preserve actions for $state with incident=$hasIncident',
		async ({state, hasIncident, actions}) => {
			mockViewport(false);
			const instance = createInstance(state, hasIncident);
			const screen = await renderOperations(instance);
			for (const action of [...ACTIONS.map(({action}) => action), 'Migrate']) {
				const button = screen.getByRole('button', {name: `${action} Instance ${instance.processInstanceKey}`});
				if (actions.some((name) => name === action)) {
					await expect.element(button).toBeEnabled();
				} else {
					await expect.element(button).not.toBeInTheDocument();
				}
			}
			await expect.element(screen.getByRole('button', {name: /Modify/})).not.toBeInTheDocument();
		},
	);

	it('should hide operations in modification mode', async () => {
		const screen = await renderOperations(createInstance('ACTIVE'), {isModificationModeEnabled: true});
		await expect.element(screen.getByRole('button')).not.toBeInTheDocument();
	});

	it.for(ACTIONS)(
		'should submit $action with no request body and preserve its acceptance lifecycle',
		async ({action, state, pending, success, mock}, {worker}) => {
			mockViewport(false);
			const instance = createInstance(state);
			const command = holdResponse();
			const requests: Request[] = [];
			const onRequest = ({request}: {request: Request}) => {
				if (request.method !== 'GET') {
					requests.push(request.clone());
				}
			};
			worker.events.on('request:start', onRequest);
			try {
				worker.use(
					mock({
						successResponse:
							action === 'Retry'
								? HttpResponse.json({batchOperationKey: 'retry-batch'}, {status: 202})
								: new HttpResponse(null, {status: 204}),
						delay: command.held,
					}),
					mockGetProcessInstanceCallHierarchyEndpoint({successResponse: HttpResponse.json([])}),
					mockGetProcessInstanceEndpoint({
						successResponse: HttpResponse.json({...instance, state: action === 'Suspend' ? 'SUSPENDED' : 'ACTIVE'}),
					}),
				);
				const screen = await renderOperations(instance, {basepath: '/deployment'});
				await execute(screen, action, instance.processInstanceKey);
				await expect.element(screen.getByText(pending)).toBeVisible();
				command.release();
				if (action === 'Delete') {
					await expect.poll(() => notificationsStore.notifications[0]?.title).toBe(success);
				} else {
					await expect.element(screen.getByText(success)).toBeVisible();
				}
				expect(requests).toHaveLength(1);
				expect(requests[0]!.method).toBe('POST');
				expect(await requests[0]!.text()).toBe('');
				expect(requests[0]!.headers.has('content-type')).toBe(false);
				expect(requests[0]!.url).toContain(`/process-instances/${instance.processInstanceKey}`);
				if (action === 'Delete') {
					await expect.poll(() => screen.router.history.location.pathname).toBe('/deployment/operate/processes');
					expect(screen.router.state.location.search).toEqual({
						active: true,
						incidents: true,
						suspended: true,
						completed: false,
						canceled: false,
					});
				} else {
					expect(screen.router.state.location.search).toEqual({tenantId: 'tenant-a', elementId: 'task'});
				}
			} finally {
				worker.events.removeListener('request:start', onRequest);
			}
		},
	);

	it.for(ACTIONS.flatMap((action) => [401, 403, 500, 0].map((status) => ({...action, status}))))(
		'should surface $action failures with HTTP status $status and recover',
		async ({action, state, mock, status}, {worker}) => {
			mockViewport(false);
			const instance = createInstance(state);
			worker.use(
				mock({
					successResponse:
						status === 0 ? HttpResponse.error() : new HttpResponse(null, {status, statusText: 'Command failed'}),
				}),
				mockGetProcessInstanceCallHierarchyEndpoint({successResponse: HttpResponse.json([])}),
			);
			const screen = await renderOperations(instance);
			await execute(screen, action, instance.processInstanceKey);
			const isForbidden = status === 403 && ['Retry', 'Suspend', 'Resume'].includes(action);
			const title = isForbidden
				? "You don't have permission to perform this operation"
				: action === 'Retry'
					? "Couldn't create operation"
					: `Failed to ${action.toLowerCase()} process instance`;
			const subtitle = isForbidden
				? 'Contact the administrator if you need access.'
				: action === 'Retry'
					? undefined
					: status === 0
						? action === 'Cancel' || action === 'Delete'
							? 'Failed to fetch'
							: undefined
						: 'Command failed';
			await expect.element(screen.getByText(title)).toBeVisible();
			if (subtitle !== undefined) {
				await expect.element(screen.getByText(subtitle)).toBeVisible();
			}
			expect(notificationsStore.notifications[0]?.kind).toBe(isForbidden ? 'warning' : 'error');
			expect(notificationsStore.notifications[0]?.subtitle).toBe(subtitle);
			await expect.element(screen.getByText('Failed', {exact: true})).toBeVisible();
			await expect
				.element(screen.getByRole('button', {name: `${action} Instance ${instance.processInstanceKey}`}))
				.toBeEnabled();
			if (status === 401) {
				expect(authenticationStore.status).toBe('session-expired');
			}
		},
	);

	it.for(ACTIONS.filter(({action}) => action === 'Suspend' || action === 'Resume'))(
		'should warn and recover when $action state polling is forbidden',
		async ({action, state, mock}, {worker}) => {
			mockViewport(false);
			vi.useFakeTimers({shouldAdvanceTime: true});
			const instance = createInstance(state);
			let reads = 0;
			const onRequest = ({request}: {request: Request}) => {
				if (request.method === 'GET') {
					reads++;
				}
			};
			worker.events.on('request:start', onRequest);
			try {
				worker.use(
					mock({successResponse: new HttpResponse(null, {status: 204})}),
					mockGetProcessInstanceEndpoint({
						successResponse: new HttpResponse(null, {status: 403, statusText: 'Forbidden'}),
					}),
				);
				const screen = await renderOperations(instance);
				await execute(screen, action, instance.processInstanceKey);
				for (let attempt = 1; attempt <= 31; attempt++) {
					await expect.poll(() => reads).toBe(attempt);
					if (attempt < 31) {
						await vi.advanceTimersByTimeAsync(1000);
					}
				}
				await expect.element(screen.getByText("You don't have permission to perform this operation")).toBeVisible();
				await expect.element(screen.getByText('Contact the administrator if you need access.')).toBeVisible();
				expect(notificationsStore.notifications).toHaveLength(1);
				expect(notificationsStore.notifications[0]?.kind).toBe('warning');
				expect(notificationsStore.notifications[0]?.subtitle).toBe('Contact the administrator if you need access.');
				await vi.advanceTimersByTimeAsync(2000);
				await expect
					.element(screen.getByRole('button', {name: `${action} Instance ${instance.processInstanceKey}`}))
					.toBeEnabled();
			} finally {
				worker.events.removeListener('request:start', onRequest);
				vi.useRealTimers();
			}
		},
	);

	it.for([
		{action: 'Suspend', state: 'ACTIVE', target: 'SUSPENDED', mock: mockSuspendProcessInstanceEndpoint},
		{action: 'Resume', state: 'SUSPENDED', target: 'ACTIVE', mock: mockResumeProcessInstanceEndpoint},
		{action: 'Resume', state: 'SUSPENDED', target: 'COMPLETED', mock: mockResumeProcessInstanceEndpoint},
		{action: 'Resume', state: 'SUSPENDED', target: 'TERMINATED', mock: mockResumeProcessInstanceEndpoint},
	] as const)(
		'should keep $action pending until secondary storage reaches $target',
		async ({action, state, target, mock}, {worker}) => {
			mockViewport(false);
			const instance = createInstance(state);
			const targetRead = holdResponse();
			worker.use(
				mock({successResponse: new HttpResponse(null, {status: 204})}),
				// Rendering reads the instance from initialData, so the operation's first state read takes this `once` GET.
				mockGetProcessInstanceEndpoint({successResponse: HttpResponse.json(instance), once: true}),
				mockGetProcessInstanceEndpoint({
					successResponse: HttpResponse.json({...instance, state: target}),
					delay: targetRead.held,
				}),
			);
			const screen = await renderOperations(instance);
			await execute(screen, action, instance.processInstanceKey);
			await expect.element(screen.getByText(action === 'Suspend' ? 'Suspending...' : 'Resuming...')).toBeVisible();
			targetRead.release();
			await expect
				.element(screen.getByText(action === 'Suspend' ? 'Instance suspended' : 'Instance resumed'))
				.toBeVisible();
			await expect
				.element(
					screen.getByRole('button', {
						name: `${target === 'SUSPENDED' ? 'Resume' : target === 'ACTIVE' ? 'Suspend' : 'Delete'} Instance ${instance.processInstanceKey}`,
					}),
				)
				.toBeEnabled();
		},
	);

	it('should support the collapsed menu with keyboard and preserve pending state across layout changes', async ({
		worker,
	}) => {
		const setCollapsed = mockViewport(true);
		const instance = createInstance('ACTIVE');
		const command = holdResponse();
		worker.use(
			mockSuspendProcessInstanceEndpoint({successResponse: new HttpResponse(null, {status: 204}), delay: command.held}),
			mockGetProcessInstanceEndpoint({successResponse: HttpResponse.json({...instance, state: 'SUSPENDED'})}),
		);
		const screen = await renderOperations(instance);
		await userEvent.tab();
		await userEvent.keyboard('{Enter}');
		await expect.element(screen.getByRole('menuitem', {name: 'Suspend'})).toBeEnabled();
		await userEvent.click(screen.getByRole('menuitem', {name: 'Suspend'}));
		await userEvent.click(screen.getByRole('button', {name: 'Actions'}));
		await expect.element(screen.getByRole('menuitem', {name: 'Suspend'})).toBeDisabled();
		await expect.element(screen.getByRole('menuitem', {name: 'Cancel'})).toBeEnabled();
		setCollapsed(false);
		await expect.element(screen.getByText('Suspending...')).toBeVisible();
		command.release();
		await expect.element(screen.getByText('Instance suspended')).toBeVisible();
	});

	it.for(['ACTIVE', 'SUSPENDED', 'COMPLETED', 'TERMINATED'] as const)(
		'should preserve collapsed %s actions and keyboard focus',
		async (state) => {
			mockViewport(true);
			const instance = createInstance(state);
			const screen = await renderOperations(instance);
			if (state === 'COMPLETED' || state === 'TERMINATED') {
				await expect
					.element(screen.getByRole('button', {name: `Delete Instance ${instance.processInstanceKey}`}))
					.toBeEnabled();
				await expect.element(screen.getByRole('button', {name: 'Actions'})).not.toBeInTheDocument();
			} else {
				await userEvent.tab();
				await userEvent.keyboard('{Enter}');
				for (const label of state === 'ACTIVE' ? ['Retry', 'Suspend', 'Cancel', 'Migrate'] : ['Resume', 'Cancel']) {
					await expect.element(screen.getByRole('menuitem', {name: label})).toBeEnabled();
				}
				if (state === 'SUSPENDED') {
					await expect.element(screen.getByRole('menuitem', {name: 'Migrate'})).not.toBeInTheDocument();
				}
				await userEvent.keyboard('{Escape}');
				await expect.element(screen.getByRole('button', {name: 'Actions'})).toHaveFocus();
				await expect.element(screen.getByRole('menuitem')).not.toBeInTheDocument();
			}
		},
	);

	it('should stop state polling and avoid stale notifications after leaving the instance', async ({worker}) => {
		mockViewport(false);
		vi.useFakeTimers({shouldAdvanceTime: true});
		const instance = createInstance('ACTIVE');
		let reads = 0;
		const onRequest = ({request}: {request: Request}) => {
			if (request.method === 'GET') {
				reads++;
			}
		};
		worker.events.on('request:start', onRequest);
		try {
			worker.use(
				mockSuspendProcessInstanceEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
				mockGetProcessInstanceEndpoint({successResponse: HttpResponse.json(instance)}),
			);
			const screen = await renderOperations(instance);
			await execute(screen, 'Suspend', instance.processInstanceKey);
			await expect.poll(() => reads).toBe(1);
			await cleanup();
			await vi.advanceTimersByTimeAsync(4000);
			expect(reads).toBe(1);
			expect(notificationsStore.notifications).toEqual([]);
		} finally {
			worker.events.removeListener('request:start', onRequest);
			vi.useRealTimers();
		}
	});

	it('should fail and recover after thirty unsuccessful state retries', async ({worker}) => {
		mockViewport(false);
		vi.useFakeTimers({shouldAdvanceTime: true});
		const instance = createInstance('ACTIVE');
		let reads = 0;
		const onRequest = ({request}: {request: Request}) => {
			if (request.method === 'GET') {
				reads++;
			}
		};
		worker.events.on('request:start', onRequest);
		try {
			worker.use(
				mockSuspendProcessInstanceEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
				mockGetProcessInstanceEndpoint({successResponse: HttpResponse.json(instance)}),
			);
			const screen = await renderOperations(instance);
			await execute(screen, 'Suspend', instance.processInstanceKey);
			for (let attempt = 1; attempt <= 31; attempt++) {
				await expect.poll(() => reads).toBe(attempt);
				if (attempt < 31) {
					await vi.advanceTimersByTimeAsync(1000);
				}
			}
			await expect.element(screen.getByText('Failed to suspend process instance')).toBeVisible();
			await expect.element(screen.getByText('Process instance has not reached SUSPENDED state')).toBeVisible();
			await vi.advanceTimersByTimeAsync(2000);
			await expect
				.element(screen.getByRole('button', {name: `Suspend Instance ${instance.processInstanceKey}`}))
				.toBeEnabled();
		} finally {
			worker.events.removeListener('request:start', onRequest);
			vi.useRealTimers();
		}
	});

	it.for(['Cancel', 'Delete'] as const)(
		'should dismiss %s confirmation without submitting',
		async (action, {worker}) => {
			mockViewport(false);
			const instance = createInstance(action === 'Cancel' ? 'ACTIVE' : 'COMPLETED');
			worker.use(mockGetProcessInstanceCallHierarchyEndpoint({successResponse: HttpResponse.json([])}));
			const screen = await renderOperations(instance);
			await userEvent.click(screen.getByRole('button', {name: `${action} Instance ${instance.processInstanceKey}`}));
			await expect.element(screen.getByRole('dialog')).toBeVisible();
			await userEvent.click(screen.getByRole('dialog').getByRole('button', {name: 'Cancel'}));
			await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
			await expect
				.element(screen.getByRole('button', {name: `${action} Instance ${instance.processInstanceKey}`}))
				.toBeEnabled();
		},
	);

	it.for([
		{multiTenancy: 'disabled', isMultiTenancyEnabled: false, tenantId: undefined},
		{multiTenancy: 'enabled', isMultiTenancyEnabled: true, tenantId: 'tenant-a'},
	])(
		'should explain migration before handing the instance to the wizard with multi-tenancy $multiTenancy',
		async ({isMultiTenancyEnabled, tenantId}) => {
			sessionStorage.setItem(
				'clientConfig',
				JSON.stringify(
					createSystemConfiguration({deployment: {...createSystemConfiguration().deployment, isMultiTenancyEnabled}}),
				),
			);
			mockViewport(false);
			const instance = createProcessInstance({
				...createInstance('ACTIVE'),
				processDefinitionKey: 'invoice-key',
				processDefinitionId: 'invoice',
				processDefinitionName: 'Invoice',
				processDefinitionVersion: 2,
				processDefinitionVersionTag: 'v2',
			});
			const screen = await renderOperations(instance);
			const migrate = screen.getByRole('button', {name: `Migrate Instance ${instance.processInstanceKey}`});

			await userEvent.click(migrate);
			const helper = screen.getByRole('dialog', {name: 'Migrate process instance versions'});
			await expect.element(helper).toBeVisible();
			await userEvent.click(helper.getByRole('button', {name: 'Cancel'}));

			await expect.element(helper).not.toBeInTheDocument();
			expect(screen.router.state.location.pathname).toBe(`/operate/processes/${instance.processInstanceKey}/variables`);

			await userEvent.click(migrate);
			await userEvent.click(helper.getByRole('button', {name: 'Continue'}), {force: true});

			await expect.poll(() => screen.router.state.location.pathname).toBe('/operate/processes');
			expect(screen.router.state.location.search).toEqual({
				active: true,
				incidents: true,
				suspended: true,
				completed: false,
				canceled: false,
				process: 'invoice',
				version: 2,
				tenantId,
			});
			expect(screen.router.state.location.state.operateInstanceMigration).toEqual({
				processInstanceKey: instance.processInstanceKey,
				processDefinitionKey: 'invoice-key',
				processDefinitionId: 'invoice',
				processDefinitionName: 'Invoice',
				processDefinitionVersion: 2,
				processDefinitionVersionTag: 'v2',
				tenantId: 'tenant-a',
			});
		},
	);

	it.for(
		(['shown', 'hidden by the unified preference', 'hidden by the legacy preference'] as const).flatMap((helper) =>
			(['header button', 'Actions menu'] as const).map((layout) => ({helper, layout})),
		),
	)('should hand the instance to the wizard from the $layout when the helper is $helper', async ({helper, layout}) => {
		mockViewport(layout === 'Actions menu');
		if (helper === 'hidden by the unified preference') {
			storeStateLocally('operate.hideMigrationHelperModal', true);
		} else if (helper === 'hidden by the legacy preference') {
			localStorage.setItem('sharedState', JSON.stringify({hideMigrationHelperModal: true}));
		}
		const instance = createInstance('ACTIVE');
		const screen = await renderOperations(instance);

		if (layout === 'Actions menu') {
			await userEvent.click(screen.getByRole('button', {name: 'Actions'}));
			await userEvent.click(screen.getByRole('menuitem', {name: 'Migrate'}));
		} else {
			await userEvent.click(screen.getByRole('button', {name: `Migrate Instance ${instance.processInstanceKey}`}));
		}
		if (helper === 'shown') {
			await userEvent.click(
				screen.getByRole('dialog', {name: 'Migrate process instance versions'}).getByRole('button', {name: 'Continue'}),
				{force: true},
			);
		}

		await expect.poll(() => screen.router.state.location.pathname).toBe('/operate/processes');
		expect(screen.router.state.location.state.operateInstanceMigration).toMatchObject({
			processInstanceKey: instance.processInstanceKey,
		});
	});
});
