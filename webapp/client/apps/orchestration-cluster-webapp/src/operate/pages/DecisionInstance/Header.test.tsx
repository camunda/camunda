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
import {formatEvaluationDate} from '#/operate/shared/utils/formatEvaluationDate';

const DECISION_INSTANCE_ID = '123567';

function renderHeader() {
	return renderWithRouter(() => <Header decisionEvaluationInstanceKey={DECISION_INSTANCE_ID} onOpenDrd={() => {}} />, {
		path: '/operate/decisions/$decisionInstanceId',
		initialEntry: `/operate/decisions/${DECISION_INSTANCE_ID}`,
	});
}

describe('<Header />', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should show a loading skeleton', async ({worker}) => {
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(createDecisionInstance()), delay: 500}),
		);

		const screen = await renderHeader();

		await expect.element(screen.getByTestId('instance-header-skeleton')).toBeVisible();
	});

	it('should show the decision instance details', async ({worker}) => {
		const decisionInstance = createDecisionInstance();
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(decisionInstance)}),
		);

		const screen = await renderHeader();

		await expect.element(screen.getByTestId('EVALUATED-icon')).toBeVisible();
		await expect.element(screen.getByText(decisionInstance.decisionDefinitionName)).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Open Decision Requirements Diagram'})).toBeVisible();
		await expect.element(screen.getByText('Decision Instance Key')).toBeVisible();
		await expect.element(screen.getByText('Version')).toBeVisible();
		await expect.element(screen.getByText('Evaluation Date')).toBeVisible();
		await expect.element(screen.getByText('Process Instance Key')).toBeVisible();
		await expect.element(screen.getByText(decisionInstance.decisionEvaluationInstanceKey)).toBeVisible();
		await expect
			.element(
				screen.getByRole('link', {
					name: `View decision "${decisionInstance.decisionDefinitionName} version ${decisionInstance.decisionDefinitionVersion}" instances`,
				}),
			)
			.toHaveTextContent(decisionInstance.decisionDefinitionVersion.toString());
		await expect.element(screen.getByText(formatEvaluationDate(decisionInstance.evaluationDate))).toBeVisible();
		await expect
			.element(
				screen.getByRole('link', {
					name: `View process instance ${decisionInstance.processInstanceKey}`,
				}),
			)
			.toBeVisible();
	});

	it('should display a failed evaluation state', async ({worker}) => {
		const failedInstance = createDecisionInstance({state: 'FAILED'});
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(failedInstance)}),
		);

		const screen = await renderHeader();

		await expect.element(screen.getByTestId('FAILED-icon')).toBeVisible();
		await expect.element(screen.getByText(failedInstance.decisionDefinitionName)).toBeVisible();
		await expect.element(screen.getByText('1 incident')).toBeVisible();
	});
});
