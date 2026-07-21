/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockCurrentUserEndpoint, mockGetDecisionInstanceEndpoint} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createDecisionInstance} from '#/shared-test-modules/api-mocks/decision-instances';
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {tracking} from '#/shared/tracking';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {DecisionInstance} from './DecisionInstance';

const DECISION_INSTANCE_ID = '4294980768';

function renderPage() {
	return renderWithRouter(() => <DecisionInstance decisionInstanceId={DECISION_INSTANCE_ID} />, {
		path: '/operate/decisions/$decisionInstanceId',
		initialEntry: `/operate/decisions/${DECISION_INSTANCE_ID}`,
	});
}

describe('<DecisionInstance />', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
		notificationsStore.reset();
	});

	it('should render the header and set the page title', async ({worker}) => {
		const decisionInstance = createDecisionInstance();
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(decisionInstance)}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByTestId('instance-header')).toBeVisible();
		await expect.element(screen.getByRole('heading', {name: 'Operate Decision Instance'})).toBeInTheDocument();
		await expect
			.poll(() => document.title)
			.toBe(`Operate: Decision Instance ${DECISION_INSTANCE_ID} of ${decisionInstance.decisionDefinitionName}`);
	});

	it('should track when the decision instance details are loaded', async ({worker}) => {
		const trackSpy = vi.spyOn(tracking, 'track');
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(createDecisionInstance({state: 'EVALUATED'})),
			}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByTestId('instance-header')).toBeVisible();
		expect(trackSpy).toHaveBeenCalledWith({eventName: 'operate:decision-instance-details-loaded', state: 'EVALUATED'});
	});

	it('should display forbidden content', async ({worker}) => {
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 403}), {status: 403}),
			}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByText('403 - You do not have permission to view this information')).toBeVisible();
		await expect.element(screen.getByText('Contact your administrator to get access.')).toBeVisible();
		await expect
			.element(screen.getByRole('link', {name: 'Learn more about permissions'}))
			.toHaveAttribute(
				'href',
				'https://docs.camunda.io/docs/self-managed/operate-deployment/operate-authentication/#resource-based-permissions',
			);
	});

	it('should redirect to the decisions page and display a notification if the decision instance is not found', async ({
		worker,
	}) => {
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 404}), {status: 404}),
			}),
		);

		const screen = await renderPage();

		await expect.poll(() => screen.router.state.location.pathname).toBe('/operate/decisions');
		await expect
			.poll(() => notificationsStore.notifications.map((notification) => notification.title))
			.toContain(`Decision instance ${DECISION_INSTANCE_ID} could not be found`);
	});
});
