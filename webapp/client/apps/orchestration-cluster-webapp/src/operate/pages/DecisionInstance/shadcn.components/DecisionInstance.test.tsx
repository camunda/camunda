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
const PATH = '/operate-preview/decisions/$decisionInstanceId';
const INITIAL_ENTRY = `/operate-preview/decisions/${DECISION_INSTANCE_ID}`;

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
	let previousPanelStates: string | null;

	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
		previousPanelStates = localStorage.getItem('operate.panelStates');
		localStorage.removeItem('operate.panelStates');
	});

	afterEach(() => {
		sessionStorage.clear();
		notificationsStore.reset();
		if (previousPanelStates === null) {
			localStorage.removeItem('operate.panelStates');
		} else {
			localStorage.setItem('operate.panelStates', previousPanelStates);
		}
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
		await expect.element(screen.getByRole('tab', {name: 'Inputs and Outputs'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Result'})).toBeVisible();
		await expect.element(screen.getByRole('region', {name: 'DRD panel'})).toBeVisible();
	});

	it('should switch between maximized and closed DRD layouts and restore focus', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();

		await expect.element(screen.getByRole('region', {name: 'DRD panel'})).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'Maximize DRD Panel'}));

		await expect.element(screen.getByRole('button', {name: 'Minimize DRD Panel'})).toHaveFocus();
		await expect.element(screen.getByTestId('instance-header')).not.toBeInTheDocument();
		await expect.element(screen.getByTestId('decision-panel')).not.toBeInTheDocument();
		expect(getStateLocally('operate.panelStates')?.drdPanelState).toBe('maximized');

		await userEvent.click(screen.getByRole('button', {name: 'Minimize DRD Panel'}));
		await expect.element(screen.getByRole('button', {name: 'Maximize DRD Panel'})).toHaveFocus();
		await expect.element(screen.getByTestId('instance-header')).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Close DRD Panel'}));
		await expect.element(screen.getByRole('button', {name: 'Open Decision Requirements Diagram'})).toHaveFocus();
		await expect.element(screen.getByTestId('drd')).not.toBeInTheDocument();
		expect(getStateLocally('operate.panelStates')?.drdPanelState).toBe('closed');
	});

	it('should restore the closed DRD state from local storage', async ({worker}) => {
		mockPage(worker);
		storeStateLocally('operate.panelStates', {drdPanelState: 'closed'});

		const screen = await renderPage();

		await expect.element(screen.getByTestId('instance-header')).toBeVisible();
		await expect.element(screen.getByTestId('drd')).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Open Decision Requirements Diagram'})).toBeVisible();
	});
});
