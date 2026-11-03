/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
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
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {DecisionInstance, DecisionInstanceShell} from './DecisionInstance';

const DECISION_INSTANCE_ID = '4294980768';
const DECISION_INSTANCE = createDecisionInstance({
	decisionEvaluationInstanceKey: DECISION_INSTANCE_ID,
	decisionDefinitionId: 'invoiceClassification',
});
const PATH = '/operate/decisions/$decisionInstanceId';
const INITIAL_ENTRY = `/operate/decisions/${DECISION_INSTANCE_ID}`;

function mockPage(worker: {use: (...handlers: ReturnType<typeof mockGetDecisionInstanceEndpoint>[]) => void}) {
	worker.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(DECISION_INSTANCE)}),
		mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}),
		mockQueryDecisionInstancesEndpoint({
			successResponse: HttpResponse.json(createQueryDecisionInstancesResponse({items: [DECISION_INSTANCE]})),
		}),
	);
}

function renderPage() {
	return renderWithRouter(
		() => (
			<div style={{height: '100vh'}}>
				<DecisionInstance decisionInstanceId={DECISION_INSTANCE_ID} />
			</div>
		),
		{path: PATH, initialEntry: INITIAL_ENTRY},
	);
}

describe('<DecisionInstance /> (design system)', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
		localStorage.removeItem('operate.panelStates');
	});

	afterEach(() => {
		sessionStorage.clear();
		notificationsStore.reset();
		localStorage.removeItem('operate.panelStates');
	});

	it('should announce a failed evaluation with a next step when no failure message exists', async ({worker}) => {
		// given
		const failed = createDecisionInstance({
			decisionEvaluationInstanceKey: DECISION_INSTANCE_ID,
			decisionDefinitionId: 'invoiceClassification',
			state: 'FAILED',
			evaluationFailure: undefined,
		});
		mockPage(worker);
		worker.use(mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(failed)}));

		// when
		const screen = await renderPage();

		// then
		await expect
			.element(screen.getByRole('alert'))
			.toHaveTextContent(
				'Evaluation failed for an unknown reason. Check the input values and the decision definition.',
			);
	});

	it('should show the loading states in the pending route shell', async () => {
		const screen = await renderWithRouter(
			() => (
				<div style={{height: '100vh'}}>
					<DecisionInstanceShell isPending header={<div>Loading decision instance</div>} />
				</div>
			),
			{path: PATH, initialEntry: INITIAL_ENTRY},
		);

		await expect.element(screen.getByRole('heading', {name: 'Operate Decision Instance'})).toBeInTheDocument();
		await expect.element(screen.getByTestId('diagram-spinner')).toBeVisible();
		await expect.element(screen.getByTestId('inputs-skeleton')).toBeVisible();
		await expect.element(screen.getByTestId('outputs-skeleton')).toBeVisible();
	});

	it('should render the header, evaluated decision panel and variables tabs', async ({worker}) => {
		mockPage(worker);

		const screen = await renderPage();

		await expect.element(screen.getByTestId('instance-header')).toBeVisible();
		await expect.element(screen.getByRole('heading', {name: 'Operate Decision Instance'})).toBeInTheDocument();
		await expect
			.element(screen.getByRole('region', {name: 'decision panel'}).getByText('Invoice Amount'))
			.toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Inputs and outputs'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Result'})).toBeVisible();
		await expect.element(screen.getByRole('region', {name: 'DRD panel'})).toBeVisible();
	});

	it('should keep the skip-to-content target when the decision instance fails to load', async ({worker}) => {
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json({}, {status: 500})}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByRole('main')).toHaveAttribute('id', 'main-content');
	});

	it('should keep the header and variables available when the decision XML cannot be imported', async ({worker}) => {
		mockPage(worker);
		worker.use(mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text('not a dmn document')}));

		const screen = await renderPage();

		await expect.element(screen.getByTestId('instance-header')).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Result'})).toBeVisible();
		await expect.element(screen.getByTestId('decision-panel').getByRole('button', {name: 'Try again'})).toBeVisible();
	});

	it('should refetch the decision XML when retrying after an import failure', async ({worker}) => {
		mockPage(worker);
		worker.use(mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text('not a dmn document')}));
		const screen = await renderPage();
		const retry = screen.getByTestId('decision-panel').getByRole('button', {name: 'Try again'});
		await expect.element(retry).toBeVisible();

		worker.use(mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}));
		await userEvent.click(retry);

		await expect
			.element(screen.getByRole('region', {name: 'decision panel'}).getByText('Invoice Amount'))
			.toBeVisible();
	});

	it('should switch between maximized and closed DRD layouts and restore focus', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();

		await expect.element(screen.getByRole('region', {name: 'DRD panel'})).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'Maximize DRD panel'}));

		await expect.element(screen.getByRole('button', {name: 'Minimize DRD panel'})).toHaveFocus();
		await expect.element(screen.getByTestId('instance-header')).not.toBeInTheDocument();
		await expect.element(screen.getByTestId('decision-panel')).not.toBeInTheDocument();
		expect(getStateLocally('operate.panelStates')?.drdPanelState).toBe('maximized');

		await userEvent.click(screen.getByRole('button', {name: 'Minimize DRD panel'}));
		await expect.element(screen.getByRole('button', {name: 'Maximize DRD panel'})).toHaveFocus();
		await expect.element(screen.getByTestId('instance-header')).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Close DRD panel'}));
		await expect.element(screen.getByRole('button', {name: 'Open decision requirements diagram'})).toHaveFocus();
		await expect.element(screen.getByTestId('drd')).not.toBeInTheDocument();
		expect(getStateLocally('operate.panelStates')?.drdPanelState).toBe('closed');
	});

	it('should show the instance key in the maximized DRD and minimize it on Escape', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Maximize DRD panel'}));
		await expect.element(screen.getByTestId('drd').getByText(new RegExp(DECISION_INSTANCE_ID))).toBeVisible();

		await userEvent.keyboard('{Escape}');

		await expect.element(screen.getByTestId('instance-header')).toBeVisible();
		expect(getStateLocally('operate.panelStates')?.drdPanelState).toBe('minimized');
	});

	it('should restore the closed DRD state from local storage', async ({worker}) => {
		mockPage(worker);
		storeStateLocally('operate.panelStates', {drdPanelState: 'closed'});

		const screen = await renderPage();

		await expect.element(screen.getByTestId('instance-header')).toBeVisible();
		await expect.element(screen.getByTestId('drd')).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Open decision requirements diagram'})).toBeVisible();
	});
});
