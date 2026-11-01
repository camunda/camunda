/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, vi} from 'vitest';
import {render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {http, HttpResponse} from 'msw';
import {endpoints} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '#/vitest-modules/test-extend';
import {
	mockGetProcessInstanceCallHierarchyEndpoint,
	mockGetProcessInstanceEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {InstanceOperations} from './InstanceOperations';

const PROCESS_INSTANCE_KEY = '2251799813685249';

function renderInstanceOperations(
	processInstance: Parameters<typeof InstanceOperations>[0]['processInstance'],
	activeOperations: Parameters<typeof InstanceOperations>[0]['activeOperations'] = [],
) {
	const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});

	return render(
		<QueryClientProvider client={queryClient}>
			<div style={{width: 400}}>
				<InstanceOperations processInstance={processInstance} activeOperations={activeOperations} />
			</div>
		</QueryClientProvider>,
	);
}

describe('<InstanceOperations /> (design system)', () => {
	it.beforeEach(({worker}) => {
		worker.use(mockGetProcessInstanceCallHierarchyEndpoint({successResponse: HttpResponse.json([])}));
	});

	it('should offer suspend and cancel for an active instance without an incident', async () => {
		const screen = await renderInstanceOperations(
			createProcessInstance({processInstanceKey: PROCESS_INSTANCE_KEY, state: 'ACTIVE', hasIncident: false}),
		);

		await expect.element(screen.getByRole('button', {name: 'Suspend Instance', exact: true})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Cancel Instance', exact: true})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Retry Instance', exact: true})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Resume Instance', exact: true})).not.toBeInTheDocument();
	});

	it('should offer retry for an active instance with an incident', async () => {
		const screen = await renderInstanceOperations(
			createProcessInstance({processInstanceKey: PROCESS_INSTANCE_KEY, state: 'ACTIVE', hasIncident: true}),
		);

		await expect.element(screen.getByRole('button', {name: 'Retry Instance', exact: true})).toBeVisible();
	});

	it('should offer resume and cancel for a suspended instance', async () => {
		const screen = await renderInstanceOperations(
			createProcessInstance({processInstanceKey: PROCESS_INSTANCE_KEY, state: 'SUSPENDED', hasIncident: false}),
		);

		await expect.element(screen.getByRole('button', {name: 'Resume Instance', exact: true})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Cancel Instance', exact: true})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Suspend Instance', exact: true})).not.toBeInTheDocument();
	});

	it.for(['COMPLETED', 'TERMINATED'] as const)('should only offer delete for a %s instance', async (state) => {
		const screen = await renderInstanceOperations(
			createProcessInstance({processInstanceKey: PROCESS_INSTANCE_KEY, state, hasIncident: false}),
		);

		await expect.element(screen.getByRole('button', {name: 'Delete Instance', exact: true})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Cancel Instance', exact: true})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Suspend Instance', exact: true})).not.toBeInTheDocument();
	});

	it('should disable the action matching an operation that is already running', async () => {
		const screen = await renderInstanceOperations(
			createProcessInstance({processInstanceKey: PROCESS_INSTANCE_KEY, state: 'ACTIVE', hasIncident: false}),
			['SUSPEND_PROCESS_INSTANCE'],
		);

		await expect.element(screen.getByRole('button', {name: 'Suspend Instance', exact: true})).toBeDisabled();
	});

	it('should send the suspend command for the row instance', async ({worker}) => {
		const onSuspend = vi.fn();
		worker.use(
			http.post(endpoints.suspendProcessInstance.getUrl({processInstanceKey: PROCESS_INSTANCE_KEY}), () => {
				onSuspend();
				return new HttpResponse(null, {status: 204});
			}),
			mockGetProcessInstanceEndpoint({
				successResponse: HttpResponse.json(
					createProcessInstance({processInstanceKey: PROCESS_INSTANCE_KEY, state: 'SUSPENDED'}),
				),
			}),
		);
		const screen = await renderInstanceOperations(
			createProcessInstance({processInstanceKey: PROCESS_INSTANCE_KEY, state: 'ACTIVE', hasIncident: false}),
		);

		screen.getByRole('button', {name: 'Suspend Instance', exact: true}).element().focus();
		await userEvent.keyboard('{Enter}');

		await vi.waitFor(() => expect(onSuspend).toHaveBeenCalledTimes(1));
	});
});
