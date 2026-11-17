/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect} from 'vitest';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockCurrentUserEndpoint, mockGetDecisionInstanceEndpoint} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createDecisionInstance} from '#/shared-test-modules/api-mocks/decision-instances';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {Header} from './Header';

const DECISION_INSTANCE_ID = '123567';

function renderHeader() {
	return renderWithRouter(() => <Header decisionEvaluationInstanceKey={DECISION_INSTANCE_ID} onOpenDrd={() => {}} />, {
		path: '/operate/decisions/$decisionInstanceId',
		initialEntry: `/operate/decisions/${DECISION_INSTANCE_ID}`,
	});
}

describe('<Header /> - Business ID', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should render a Business ID when present', async ({worker}) => {
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(createDecisionInstance({businessId: 'order-12345'})),
			}),
		);

		const screen = await renderHeader();

		await expect.element(screen.getByText('Business ID')).toBeVisible();
		await expect.element(screen.getByText('order-12345')).toBeVisible();
	});

	it('should not render a Business ID when null', async ({worker}) => {
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(createDecisionInstance({businessId: null})),
			}),
		);

		const screen = await renderHeader();

		await expect.element(screen.getByText('Version')).toBeVisible();
		await expect.element(screen.getByText('Business ID')).not.toBeInTheDocument();
	});
});
