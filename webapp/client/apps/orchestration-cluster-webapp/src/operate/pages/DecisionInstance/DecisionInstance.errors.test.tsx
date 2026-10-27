/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {HttpResponse} from 'msw';
import type {SetupWorker} from 'msw/browser';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {createResponseGate} from '#/shared-test-modules/mock-endpoint';
import {
	mockCurrentUserEndpoint,
	mockGetDecisionDefinitionXmlEndpoint,
	mockGetDecisionInstanceEndpoint,
	mockQueryDecisionInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {DMN_XML} from '#/shared-test-modules/api-mocks/decision-definition-xmls';
import {
	createDecisionInstance,
	createQueryDecisionInstancesResponse,
} from '#/shared-test-modules/api-mocks/decision-instances';
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {DecisionInstance} from './DecisionInstance';
import {Header} from './Header';

const DECISION_INSTANCE_ID = '4294980768';
const INITIAL_ENTRY = `/operate/decisions/${DECISION_INSTANCE_ID}`;
const decisionInstance = createDecisionInstance({decisionEvaluationInstanceKey: DECISION_INSTANCE_ID});

function returnToPage() {
	const visibility = vi.spyOn(document, 'visibilityState', 'get');
	try {
		visibility.mockReturnValue('hidden');
		document.dispatchEvent(new Event('visibilitychange', {bubbles: true}));
		visibility.mockReturnValue('visible');
		document.dispatchEvent(new Event('visibilitychange', {bubbles: true}));
	} finally {
		visibility.mockRestore();
	}
}

async function retryPage(
	screen: Awaited<ReturnType<typeof renderWithRouter>>,
	worker: SetupWorker,
	response: Response,
) {
	const retryButton = screen.getByRole('button', {name: 'Try again'});
	await userEvent.click(retryButton, {trial: true});
	const responseGate = createResponseGate(worker);
	worker.use(mockGetDecisionInstanceEndpoint({successResponse: response, delay: responseGate.delay}));
	try {
		await userEvent.click(retryButton);
		await responseGate.requested;
	} finally {
		await responseGate.release();
	}
}

describe.each([
	{
		name: 'DecisionInstance',
		loadedContent: 'Invoice Amount',
		Component: () => (
			<div style={{height: '100vh'}}>
				<DecisionInstance decisionInstanceId={DECISION_INSTANCE_ID} />
			</div>
		),
	},
	{
		name: 'Header',
		loadedContent: DECISION_INSTANCE_ID,
		Component: () => <Header decisionEvaluationInstanceKey={DECISION_INSTANCE_ID} onOpenDrd={() => {}} />,
	},
])('$name page-query recovery', ({Component, loadedContent, name}) => {
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
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockGetDecisionInstanceEndpoint({successResponse: response}),
			mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}),
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionInstancesResponse({items: [decisionInstance]})),
			}),
		);

		const screen = await renderWithRouter(Component, {
			path: '/operate/decisions/$decisionInstanceId',
			initialEntry: INITIAL_ENTRY,
		});

		await expect.element(screen.getByRole('heading', {name: 'Something went wrong'})).toBeVisible();
		if (name === 'DecisionInstance') {
			await expect.element(screen.getByTestId('decision-instance-variables-panel')).not.toBeInTheDocument();
		}
		await expect.element(screen.getByText("Couldn't load the page. Try again later.")).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Try again'})).toBeVisible();
		await expect
			.element(screen.getByRole('button', {name: 'Open Decision Requirements Diagram'}))
			.not.toBeInTheDocument();
		expect(screen.router.state.location.href).toBe(INITIAL_ENTRY);

		await retryPage(screen, worker, HttpResponse.json(decisionInstance));

		await expect.element(screen.getByText(decisionInstance.decisionDefinitionName)).toBeVisible();
		await expect.element(screen.getByText(DECISION_INSTANCE_ID, {exact: true})).toBeVisible();
		await expect.element(screen.getByRole('heading', {name: 'Something went wrong'})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Try again'})).not.toBeInTheDocument();
		expect(screen.router.state.location.href).toBe(INITIAL_ENTRY);
		await expect.element(screen.getByText(loadedContent, {exact: true})).toBeVisible();
	});

	it('should remain recoverable when retry fails again', async ({worker}) => {
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
			mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}),
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionInstancesResponse({items: [decisionInstance]})),
			}),
		);

		const screen = await renderWithRouter(Component, {
			path: '/operate/decisions/$decisionInstanceId',
			initialEntry: INITIAL_ENTRY,
		});

		await expect.element(screen.getByRole('heading', {name: 'Something went wrong'})).toBeVisible();
		await retryPage(screen, worker, HttpResponse.json(createProblemDetails({status: 500}), {status: 500}));
		await expect.element(screen.getByRole('heading', {name: 'Something went wrong'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Try again'})).toBeVisible();

		await retryPage(screen, worker, HttpResponse.json(decisionInstance));

		await expect.element(screen.getByText(decisionInstance.decisionDefinitionName)).toBeVisible();
		await expect.element(screen.getByText(loadedContent, {exact: true})).toBeVisible();
	});

	it('should show recovery rather than stale details after a failed refetch', async ({worker}) => {
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(decisionInstance)}),
			mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}),
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionInstancesResponse({items: [decisionInstance]})),
			}),
		);

		const screen = await renderWithRouter(Component, {
			path: '/operate/decisions/$decisionInstanceId',
			initialEntry: INITIAL_ENTRY,
		});

		await expect.element(screen.getByText(decisionInstance.decisionDefinitionName)).toBeVisible();
		await expect.element(screen.getByText(loadedContent, {exact: true})).toBeVisible();
		worker.use(mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.error()}));
		returnToPage();

		await expect.element(screen.getByRole('heading', {name: 'Something went wrong'})).toBeVisible();
		await expect.element(screen.getByText(decisionInstance.decisionDefinitionName)).not.toBeInTheDocument();

		await retryPage(screen, worker, HttpResponse.json(decisionInstance));

		await expect.element(screen.getByText(decisionInstance.decisionDefinitionName)).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Try again'})).not.toBeInTheDocument();
		await expect.element(screen.getByText(loadedContent, {exact: true})).toBeVisible();
	});
});
