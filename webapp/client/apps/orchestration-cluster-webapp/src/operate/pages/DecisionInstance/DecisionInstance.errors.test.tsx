/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect, onTestFinished} from 'vitest';
import {userEvent} from 'vitest/browser';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockCurrentUserEndpoint, mockGetDecisionInstanceEndpoint} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createDecisionInstance} from '#/shared-test-modules/api-mocks/decision-instances';
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {DecisionInstance} from './DecisionInstance';
import {Header} from './Header';
import {decisionInstanceQuery} from './decisionInstance.queries';

const DECISION_INSTANCE_ID = '4294980768';
const INITIAL_ENTRY = `/operate/decisions/${DECISION_INSTANCE_ID}`;
const decisionInstance = createDecisionInstance({decisionEvaluationInstanceKey: DECISION_INSTANCE_ID});

describe.each([
	{
		name: 'DecisionInstance',
		Component: () => <DecisionInstance decisionInstanceId={DECISION_INSTANCE_ID} />,
	},
	{
		name: 'Header',
		Component: () => <Header decisionEvaluationInstanceKey={DECISION_INSTANCE_ID} onOpenDrd={() => {}} />,
	},
])('$name page-query recovery', ({Component}) => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it.for([
		{name: '500 backend error', response: HttpResponse.json(createProblemDetails({status: 500}), {status: 500})},
		{name: '503 backend error', response: HttpResponse.json(createProblemDetails({status: 503}), {status: 503})},
		{name: '400 error', response: HttpResponse.json(createProblemDetails({status: 400}), {status: 400})},
		{name: 'network failure', response: HttpResponse.error()},
		{name: 'invalid JSON response', response: new HttpResponse('invalid JSON')},
	])('should recover from $name by retrying the same page query', async ({response}, {worker}) => {
		const requests: string[] = [];
		worker.events.on('request:match', ({request}) => {
			const pathname = new URL(request.url).pathname;
			if (pathname.startsWith('/v2/decision-instances/')) {
				requests.push(`${request.method} ${pathname}`);
			}
		});
		onTestFinished(() => worker.events.removeAllListeners('request:match'));
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockGetDecisionInstanceEndpoint({successResponse: response}),
		);

		const screen = await renderWithRouter(Component, {
			path: '/operate/decisions/$decisionInstanceId',
			initialEntry: INITIAL_ENTRY,
		});

		await expect.element(screen.getByRole('heading', {name: 'Something went wrong'})).toBeVisible();
		await expect.element(screen.getByText("The page couldn't be loaded. Please try again later.")).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Try again'})).toBeVisible();
		await expect
			.element(screen.getByRole('button', {name: 'Open Decision Requirements Diagram'}))
			.not.toBeInTheDocument();
		expect(screen.router.state.location.href).toBe(INITIAL_ENTRY);
		expect(requests).toEqual([`GET /v2/decision-instances/${DECISION_INSTANCE_ID}`]);

		worker.use(mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(decisionInstance)}));
		await userEvent.click(screen.getByRole('button', {name: 'Try again'}));

		await expect.element(screen.getByText(decisionInstance.decisionDefinitionName)).toBeVisible();
		await expect.element(screen.getByText(DECISION_INSTANCE_ID, {exact: true})).toBeVisible();
		await expect.element(screen.getByRole('heading', {name: 'Something went wrong'})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Try again'})).not.toBeInTheDocument();
		expect(screen.router.state.location.href).toBe(INITIAL_ENTRY);
		expect(requests).toEqual([
			`GET /v2/decision-instances/${DECISION_INSTANCE_ID}`,
			`GET /v2/decision-instances/${DECISION_INSTANCE_ID}`,
		]);
	});

	it('should remain recoverable when retry fails again', async ({worker}) => {
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);

		const screen = await renderWithRouter(Component, {
			path: '/operate/decisions/$decisionInstanceId',
			initialEntry: INITIAL_ENTRY,
		});

		await expect.element(screen.getByRole('heading', {name: 'Something went wrong'})).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'Try again'}));
		await expect.poll(() => screen.queryClient.isFetching()).toBe(0);
		await expect.element(screen.getByRole('heading', {name: 'Something went wrong'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Try again'})).toBeVisible();

		worker.use(mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(decisionInstance)}));
		await userEvent.click(screen.getByRole('button', {name: 'Try again'}));

		await expect.element(screen.getByText(decisionInstance.decisionDefinitionName)).toBeVisible();
	});

	it('should show recovery rather than stale details after a failed refetch', async ({worker}) => {
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(decisionInstance)}),
		);

		const screen = await renderWithRouter(Component, {
			path: '/operate/decisions/$decisionInstanceId',
			initialEntry: INITIAL_ENTRY,
		});

		await expect.element(screen.getByText(decisionInstance.decisionDefinitionName)).toBeVisible();
		worker.use(mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.error()}));
		await screen.queryClient.invalidateQueries({queryKey: decisionInstanceQuery(DECISION_INSTANCE_ID).queryKey});

		await expect.element(screen.getByRole('heading', {name: 'Something went wrong'})).toBeVisible();
		await expect.element(screen.getByText(decisionInstance.decisionDefinitionName)).not.toBeInTheDocument();

		worker.use(mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(decisionInstance)}));
		await userEvent.click(screen.getByRole('button', {name: 'Try again'}));

		await expect.element(screen.getByText(decisionInstance.decisionDefinitionName)).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Try again'})).not.toBeInTheDocument();
	});
});
