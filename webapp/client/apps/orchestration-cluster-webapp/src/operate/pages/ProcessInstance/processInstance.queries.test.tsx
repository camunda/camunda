/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {cleanup, render} from 'vitest-browser-react';
import {afterEach, beforeEach, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import {it} from '#/vitest-modules/test-extend';
import {ForbiddenError} from '#/shared/errors';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createPaginatedResponse, createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {
	mockGetProcessInstanceEndpoint,
	mockGetProcessInstanceWaitStateStatisticsEndpoint,
	mockQueryProcessInstanceIncidentsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {getWaitStateLabel} from '#/operate/shared/utils/waitStates';
import {
	processInstanceQuery,
	processInstanceIncidentsCountQuery,
	processInstanceWaitStateStatisticsQuery,
	useProcessInstance,
	useProcessInstanceWaitStateStatistics,
} from './processInstance.queries';

const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
const PROCESS_INSTANCE_KEY = '123';

beforeEach(() => {
	sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
});

afterEach(async () => {
	await cleanup();
	queryClient.clear();
	sessionStorage.clear();
});

function WaitStatePreview() {
	const [active, setActive] = useState(true);
	const instance = createProcessInstance({
		processInstanceKey: PROCESS_INSTANCE_KEY,
		state: active ? 'ACTIVE' : 'COMPLETED',
		hasIncident: false,
	});
	const {data} = useProcessInstanceWaitStateStatistics(instance);
	return (
		<>
			<button onClick={() => setActive(false)}>Complete</button>
			<p>{getWaitStateLabel(data?.[0]?.waitingCount ?? 0) ?? 'No waiting'}</p>
		</>
	);
}

function ProcessInstanceStatus() {
	const {query, isUnauthorized, isNotFound, isGenericError} = useProcessInstance(PROCESS_INSTANCE_KEY);
	return (
		<p>
			{isUnauthorized
				? 'Unauthorized'
				: isNotFound
					? 'Not found'
					: isGenericError
						? 'Generic error'
						: (query.data?.state ?? 'Loading')}
		</p>
	);
}

it('should read a process instance and preserve forbidden errors', async ({worker}) => {
	const instance = createProcessInstance();
	worker.use(mockGetProcessInstanceEndpoint({successResponse: HttpResponse.json(instance)}));
	expect(await queryClient.fetchQuery(processInstanceQuery(instance.processInstanceKey))).toEqual(instance);

	queryClient.clear();
	worker.use(
		mockGetProcessInstanceEndpoint({
			successResponse: HttpResponse.json(createProblemDetails({status: 403}), {status: 403}),
		}),
	);
	await expect(queryClient.fetchQuery(processInstanceQuery(instance.processInstanceKey))).rejects.toBeInstanceOf(
		ForbiddenError,
	);
});

it.for([
	{status: 403, expected: 'Unauthorized'},
	{status: 404, expected: 'Not found'},
	{status: 500, expected: 'Generic error'},
])('should classify $status process instance reads as $expected', async ({status, expected}, {worker}) => {
	worker.use(
		mockGetProcessInstanceEndpoint({
			successResponse: HttpResponse.json(createProblemDetails({status}), {status}),
		}),
	);
	const screen = await render(
		<QueryClientProvider client={queryClient}>
			<ProcessInstanceStatus />
		</QueryClientProvider>,
	);
	await expect.element(screen.getByText(expected)).toBeVisible();
});

it('should refresh suspended process instance metadata after it is resumed externally', async ({worker}) => {
	worker.use(
		mockGetProcessInstanceEndpoint({
			successResponse: HttpResponse.json(createProcessInstance({state: 'SUSPENDED', hasIncident: false})),
		}),
	);
	const screen = await render(
		<QueryClientProvider client={queryClient}>
			<ProcessInstanceStatus />
		</QueryClientProvider>,
	);
	await expect.element(screen.getByText('SUSPENDED')).toBeVisible();

	worker.use(
		mockGetProcessInstanceEndpoint({
			successResponse: HttpResponse.json(createProcessInstance({state: 'ACTIVE', hasIncident: false})),
		}),
	);
	await expect.element(screen.getByText('ACTIVE'), {timeout: 7000}).toBeVisible();
});

it('should count only active incidents without fetching incident rows', async ({worker}) => {
	const response = createPaginatedResponse();
	response.page.totalItems = 3;
	worker.use(
		mockQueryProcessInstanceIncidentsEndpoint({
			schema: z.object({filter: z.object({state: z.literal('ACTIVE')}), page: z.object({limit: z.literal(0)})}),
			successResponse: HttpResponse.json(response),
			failureResponse: HttpResponse.json({...response, page: {...response.page, totalItems: 8}}),
		}),
	);
	expect(await queryClient.fetchQuery(processInstanceIncidentsCountQuery('1'))).toBe(3);
});

it('should clear cached waiting state after the instance stops running', async ({worker}) => {
	worker.use(
		mockGetProcessInstanceWaitStateStatisticsEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse({items: [{elementId: 'process', waitingCount: 1}]})),
		}),
	);
	const screen = await render(
		<QueryClientProvider client={queryClient}>
			<WaitStatePreview />
		</QueryClientProvider>,
	);
	await expect.element(screen.getByText('Waiting', {exact: true})).toBeVisible();
	await userEvent.click(screen.getByRole('button', {name: 'Complete'}));
	await expect.element(screen.getByText('No waiting')).toBeVisible();
});

it('should ignore cached waiting state when the deployment disables wait states', async () => {
	sessionStorage.setItem(
		'clientConfig',
		JSON.stringify(createSystemConfiguration({deployment: {waitStatesEnabled: false}})),
	);
	queryClient.setQueryData(processInstanceWaitStateStatisticsQuery(PROCESS_INSTANCE_KEY).queryKey, [
		{elementId: 'process', waitingCount: 2},
	]);
	const screen = await render(
		<QueryClientProvider client={queryClient}>
			<WaitStatePreview />
		</QueryClientProvider>,
	);
	await expect.element(screen.getByText('No waiting')).toBeVisible();
	await expect.element(screen.getByText('2 waiting')).not.toBeInTheDocument();
});
