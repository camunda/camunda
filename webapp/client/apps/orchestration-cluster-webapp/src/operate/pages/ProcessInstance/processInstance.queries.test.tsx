/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {QueryClient, QueryClientProvider, useQuery} from '@tanstack/react-query';
import {cleanup, render} from 'vitest-browser-react';
import {afterEach, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import {it} from '#/vitest-modules/test-extend';
import {ForbiddenError} from '#/shared/errors';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
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

afterEach(async () => {
	await cleanup();
	queryClient.clear();
});

function WaitStatePreview({initialState = 'ACTIVE'}: {initialState?: ProcessInstance['state']}) {
	const [state, setState] = useState(initialState);
	const instance = createProcessInstance({
		processInstanceKey: PROCESS_INSTANCE_KEY,
		state,
		hasIncident: false,
	});
	const {data, isFetching} = useProcessInstanceWaitStateStatistics(instance);
	return (
		<>
			<button onClick={() => setState('COMPLETED')}>Complete</button>
			<p>{isFetching ? 'Loading' : (getWaitStateLabel(data?.[0]?.waitingCount ?? 0) ?? 'No waiting')}</p>
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

it('should stop polling process instance metadata after it reaches a terminal state', async ({worker}) => {
	vi.useFakeTimers({shouldAdvanceTime: true});
	const requests = vi.fn();
	const onRequest = ({request}: {request: Request}) => {
		if (request.method === 'GET' && request.url.endsWith(`/process-instances/${PROCESS_INSTANCE_KEY}`)) {
			requests();
		}
	};
	worker.events.on('request:start', onRequest);

	try {
		worker.use(
			mockGetProcessInstanceEndpoint({
				successResponse: HttpResponse.json(createProcessInstance({state: 'ACTIVE'})),
			}),
		);
		const screen = await render(
			<QueryClientProvider client={queryClient}>
				<ProcessInstanceStatus />
			</QueryClientProvider>,
		);
		await expect.element(screen.getByText('ACTIVE')).toBeVisible();
		expect(requests).toHaveBeenCalledTimes(1);

		worker.use(
			mockGetProcessInstanceEndpoint({
				successResponse: HttpResponse.json(createProcessInstance({state: 'COMPLETED'})),
			}),
		);
		await vi.advanceTimersByTimeAsync(5000);
		await expect.element(screen.getByText('COMPLETED')).toBeVisible();
		expect(requests).toHaveBeenCalledTimes(2);

		await vi.advanceTimersByTimeAsync(10000);
		expect(requests).toHaveBeenCalledTimes(2);
	} finally {
		worker.events.removeListener('request:start', onRequest);
		vi.useRealTimers();
	}
});

it.for(['COMPLETED', 'TERMINATED'] as const)(
	'should poll a %s instance until its incident flag clears',
	async (state, {worker}) => {
		vi.useFakeTimers({toFake: ['setInterval', 'clearInterval']});
		const requests = vi.fn();
		const onRequest = ({request}: {request: Request}) => {
			if (request.method === 'GET' && request.url.endsWith(`/process-instances/${PROCESS_INSTANCE_KEY}`)) {
				requests();
			}
		};
		worker.events.on('request:start', onRequest);

		try {
			const instance = createProcessInstance({
				processInstanceKey: PROCESS_INSTANCE_KEY,
				state,
				hasIncident: true,
			});
			const {queryKey} = processInstanceQuery(PROCESS_INSTANCE_KEY);
			worker.use(mockGetProcessInstanceEndpoint({successResponse: HttpResponse.json(instance)}));
			const screen = await render(
				<QueryClientProvider client={queryClient}>
					<ProcessInstanceStatus />
				</QueryClientProvider>,
			);

			await expect.element(screen.getByText(state)).toBeVisible();
			expect(requests).toHaveBeenCalledTimes(1);

			await vi.advanceTimersByTimeAsync(5000);
			await expect.poll(() => requests.mock.calls.length).toBe(2);
			await expect.poll(() => queryClient.isFetching({queryKey})).toBe(0);

			worker.use(
				mockGetProcessInstanceEndpoint({
					successResponse: HttpResponse.json({...instance, hasIncident: false}),
				}),
			);
			await vi.advanceTimersByTimeAsync(5000);

			await expect.poll(() => queryClient.getQueryData(queryKey)).toMatchObject({state, hasIncident: false});
			expect(requests).toHaveBeenCalledTimes(3);

			await vi.advanceTimersByTimeAsync(10000);
			expect(requests).toHaveBeenCalledTimes(3);
		} finally {
			worker.events.removeListener('request:start', onRequest);
			vi.useRealTimers();
		}
	},
);

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
	expect(
		await queryClient.fetchQuery(processInstanceIncidentsCountQuery({processInstanceKey: '1', hasIncident: true})),
	).toBe(3);
});

it('should load counts only for reported incidents and hide resolved counts', async ({worker}) => {
	function Preview() {
		const [hasIncident, setHasIncident] = useState(false);
		const {data = 0, isFetching} = useQuery(
			processInstanceIncidentsCountQuery({processInstanceKey: PROCESS_INSTANCE_KEY, hasIncident}),
		);
		return (
			<>
				<button onClick={() => setHasIncident(!hasIncident)}>
					{hasIncident ? 'Resolve incidents' : 'Report incident'}
				</button>
				<output>{isFetching ? 'Loading' : `${data} incidents`}</output>
			</>
		);
	}
	const response = createPaginatedResponse();
	response.page.totalItems = 3;
	worker.use(mockQueryProcessInstanceIncidentsEndpoint({successResponse: HttpResponse.json(response)}));
	const screen = await render(
		<QueryClientProvider client={queryClient}>
			<Preview />
		</QueryClientProvider>,
	);
	await expect.element(screen.getByRole('status')).toHaveTextContent('0 incidents');
	await userEvent.click(screen.getByRole('button', {name: 'Report incident'}));
	await expect.element(screen.getByRole('status')).toHaveTextContent('3 incidents');
	await userEvent.click(screen.getByRole('button', {name: 'Resolve incidents'}));
	await expect.element(screen.getByRole('status')).toHaveTextContent('0 incidents');
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

it('should replace cached waiting state when the existing API returns no statistics', async ({worker}) => {
	worker.use(
		mockGetProcessInstanceWaitStateStatisticsEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
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

it.for(['ACTIVE', 'COMPLETED', 'TERMINATED', 'SUSPENDED'] as const)(
	'should fetch waiting state only for running instances (%s)',
	async (state, {worker}) => {
		const requests = vi.fn();
		const onRequest = ({request}: {request: Request}) => {
			if (request.url.endsWith('/statistics/wait-states')) {
				requests();
			}
		};
		const items = [{elementId: 'process', waitingCount: 2}];
		worker.use(
			mockGetProcessInstanceWaitStateStatisticsEndpoint({
				successResponse: HttpResponse.json(createPaginatedResponse({items})),
			}),
		);
		worker.events.on('request:start', onRequest);
		try {
			queryClient.setQueryData(processInstanceWaitStateStatisticsQuery(PROCESS_INSTANCE_KEY).queryKey, items);
			const screen = await render(
				<QueryClientProvider client={queryClient}>
					<WaitStatePreview initialState={state} />
				</QueryClientProvider>,
			);
			await expect.element(screen.getByText(state === 'ACTIVE' ? '2 waiting' : 'No waiting')).toBeVisible();
			expect(requests).toHaveBeenCalledTimes(state === 'ACTIVE' ? 1 : 0);
		} finally {
			worker.events.removeListener('request:start', onRequest);
		}
	},
);
