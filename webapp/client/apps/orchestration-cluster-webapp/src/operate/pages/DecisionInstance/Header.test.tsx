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

function renderHeader({
	initialEntry = `/operate/decisions/${DECISION_INSTANCE_ID}`,
	basepath,
}: {initialEntry?: string; basepath?: string} = {}) {
	return renderWithRouter(() => <Header decisionEvaluationInstanceKey={DECISION_INSTANCE_ID} onOpenDrd={() => {}} />, {
		path: '/operate/decisions/$decisionInstanceId',
		initialEntry,
		basepath,
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
			.toHaveAttribute(
				'href',
				'/operate/decisions?decisionDefinitionId=invoiceClassification&decisionDefinitionVersion=1&evaluated=true&failed=true',
			);
		await expect.element(screen.getByText(formatEvaluationDate(decisionInstance.evaluationDate))).toBeVisible();
		await expect
			.element(
				screen.getByRole('link', {
					name: `View process instance ${decisionInstance.processInstanceKey}`,
				}),
			)
			.toBeVisible();
		await expect
			.element(
				screen.getByRole('link', {
					name: `View process instance ${decisionInstance.processInstanceKey}`,
				}),
			)
			.toHaveAttribute('href', `/operate/processes/${decisionInstance.processInstanceKey}`);
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

	it('should keep process instance links under the configured basepath', async ({worker}) => {
		const decisionInstance = createDecisionInstance({processInstanceKey: '2251799813685243'});
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(decisionInstance)}),
		);

		const screen = await renderHeader({
			basepath: '/camunda',
			initialEntry: `/camunda/operate/decisions/${DECISION_INSTANCE_ID}`,
		});

		await expect
			.element(
				screen.getByRole('link', {
					name: `View process instance ${decisionInstance.processInstanceKey}`,
				}),
			)
			.toHaveAttribute('href', `/camunda/operate/processes/${decisionInstance.processInstanceKey}`);
		await expect
			.element(
				screen.getByRole('link', {
					name: `View decision "${decisionInstance.decisionDefinitionName} version ${decisionInstance.decisionDefinitionVersion}" instances`,
				}),
			)
			.toHaveAttribute(
				'href',
				'/camunda/operate/decisions?decisionDefinitionId=invoiceClassification&decisionDefinitionVersion=1&evaluated=true&failed=true',
			);
	});

	it('should show None without a process instance link when the key is empty', async ({worker}) => {
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(createDecisionInstance({processInstanceKey: ''})),
			}),
		);

		const screen = await renderHeader();

		await expect.element(screen.getByText('None')).toBeVisible();
		await expect.element(screen.getByRole('link', {name: /View process instance/})).not.toBeInTheDocument();
	});
});
