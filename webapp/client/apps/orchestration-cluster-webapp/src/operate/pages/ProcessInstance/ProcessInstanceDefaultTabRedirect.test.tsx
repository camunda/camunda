/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup} from 'vitest-browser-react';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockGetProcessInstanceWaitStateStatisticsEndpoint} from '#/shared-test-modules/mock-handlers';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createPaginatedResponse, createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {ProcessInstanceDefaultTabRedirect} from './ProcessInstanceDefaultTabRedirect';
import {ProcessInstanceContext} from './useProcessInstancePage';

afterEach(async () => {
	await cleanup();
	vi.useRealTimers();
});

it('should refetch failed waiting statistics on retry without polling', async ({worker}) => {
	vi.useFakeTimers({toFake: ['setInterval', 'clearInterval']});
	const requests = vi.fn();
	const onRequest = ({request}: {request: Request}) => {
		if (request.url.endsWith('/statistics/wait-states')) {
			requests();
		}
	};
	worker.events.on('request:start', onRequest);

	try {
		const processInstance = createProcessInstance({state: 'ACTIVE', hasIncident: false});
		const processInstanceId = processInstance.processInstanceKey;
		worker.use(
			mockGetProcessInstanceWaitStateStatisticsEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 503}), {status: 503}),
			}),
		);
		const screen = await renderWithRouter(
			() => (
				<ProcessInstanceContext value={{processInstanceId, processInstance, search: {}, selection: {}}}>
					<ProcessInstanceDefaultTabRedirect />
				</ProcessInstanceContext>
			),
			{
				path: '/operate/processes/$processInstanceId',
				initialEntry: `/operate/processes/${processInstanceId}`,
			},
		);

		await expect.element(screen.getByRole('heading', {name: 'Something went wrong'})).toBeVisible();
		expect(screen.router.state.location.pathname).toBe(`/operate/processes/${processInstanceId}`);
		expect(requests).toHaveBeenCalledTimes(1);

		worker.use(
			mockGetProcessInstanceWaitStateStatisticsEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({
						items: [{elementId: processInstance.processDefinitionId, waitingCount: 1}],
					}),
				),
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Try again'}));

		await expect.poll(() => requests.mock.calls.length).toBe(2);
		await expect
			.poll(() => screen.router.state.location.pathname)
			.toBe(`/operate/processes/${processInstanceId}/details`);
		await expect.element(screen.getByRole('heading', {name: 'Something went wrong'})).not.toBeInTheDocument();
	} finally {
		worker.events.removeListener('request:start', onRequest);
	}
});
