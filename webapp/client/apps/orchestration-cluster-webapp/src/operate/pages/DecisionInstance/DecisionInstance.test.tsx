/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {createInstance} from 'i18next';
import {HttpResponse} from 'msw';
import {I18nextProvider} from 'react-i18next';
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
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {translationResources} from '#/shared/i18n';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {DecisionInstance, DecisionInstanceShell} from './DecisionInstance';

const DECISION_INSTANCE_ID = '4294980768';
const DECISION_INSTANCE = createDecisionInstance({
	decisionEvaluationInstanceKey: DECISION_INSTANCE_ID,
	decisionDefinitionId: 'invoiceClassification',
});
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

async function createTranslations(language: 'de' | 'fr' | 'es') {
	const translations = createInstance();
	await translations.init({
		lng: language,
		resources: translationResources,
		interpolation: {escapeValue: false},
	});

	return translations;
}

function renderPage() {
	return renderWithRouter(
		() => (
			<div style={{height: '100vh'}}>
				<DecisionInstance decisionInstanceId={DECISION_INSTANCE_ID} />
			</div>
		),
		{
			path: '/operate/decisions/$decisionInstanceId',
			initialEntry: `/operate/decisions/${DECISION_INSTANCE_ID}`,
		},
	);
}

describe('<DecisionInstance />', () => {
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

	it('should show the decision panel loading state in the pending route shell', async () => {
		const screen = await renderWithRouter(
			() => (
				<div style={{height: '100vh'}}>
					<DecisionInstanceShell header={<div>Loading decision instance</div>} />
				</div>
			),
			{path: '/operate/decisions/$decisionInstanceId', initialEntry: `/operate/decisions/${DECISION_INSTANCE_ID}`},
		);

		await expect
			.element(screen.getByRole('region', {name: 'decision panel'}).getByRole('img', {name: 'loading'}))
			.toBeVisible();
		await expect.element(screen.getByTestId('inputs-skeleton')).toBeVisible();
		await expect.element(screen.getByTestId('outputs-skeleton')).toBeVisible();
		await userEvent.click(screen.getByRole('tab', {name: 'Result'}));
		await expect.element(screen.getByTestId('result-loading-spinner')).toBeVisible();
	});

	it.for(['de', 'fr', 'es'] as const)('should localize pending route shell landmark label for %s', async (language) => {
		const translations = await createTranslations(language);
		const screen = await renderWithRouter(
			() => (
				<I18nextProvider i18n={translations}>
					<div style={{height: '100vh'}}>
						<DecisionInstanceShell header={<div>Loading decision instance</div>} />
					</div>
				</I18nextProvider>
			),
			{path: '/operate/decisions/$decisionInstanceId', initialEntry: `/operate/decisions/${DECISION_INSTANCE_ID}`},
		);

		await expect
			.element(
				screen
					.getByRole('region', {name: translations.t('operate.decisionInstance.panel.label')})
					.getByRole('img', {name: 'loading'}),
			)
			.toBeVisible();
	});

	it('should render the header and evaluated decision panel', async ({worker}) => {
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
		await expect.element(screen.getByTestId('drd-viewer')).toBeVisible();
		await expect.element(screen.getByTestId('state-overlay-EVALUATED')).toBeVisible();
	});

	it('should switch between minimized, maximized and closed layouts without losing the decision panels', async ({
		worker,
	}) => {
		mockPage(worker);
		const screen = await renderPage();

		await expect.element(screen.getByRole('region', {name: 'DRD panel'})).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'Maximize DRD Panel'}));

		await expect.element(screen.getByTestId('drd-viewer')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Minimize DRD Panel'})).toHaveFocus();
		await expect.element(screen.getByRole('region', {name: 'DRD panel'})).not.toBeInTheDocument();
		await expect.element(screen.getByTestId('instance-header')).not.toBeInTheDocument();
		await expect.element(screen.getByTestId('decision-panel')).not.toBeInTheDocument();
		await expect.element(screen.getByTestId('decision-instance-variables-panel')).not.toBeInTheDocument();
		expect(getStateLocally('operate.panelStates')?.drdPanelState).toBe('maximized');

		await userEvent.click(screen.getByRole('button', {name: 'Minimize DRD Panel'}));
		await expect.element(screen.getByRole('button', {name: 'Maximize DRD Panel'})).toHaveFocus();
		await expect.element(screen.getByRole('region', {name: 'DRD panel'})).toBeVisible();
		await expect.element(screen.getByTestId('instance-header')).toBeVisible();
		await expect.element(screen.getByTestId('decision-panel')).toBeVisible();
		await expect.element(screen.getByTestId('decision-instance-variables-panel')).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Close DRD Panel'}));
		await expect.element(screen.getByRole('button', {name: 'Open Decision Requirements Diagram'})).toHaveFocus();
		await expect.element(screen.getByTestId('drd')).not.toBeInTheDocument();
		await expect.element(screen.getByTestId('decision-panel')).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'Open Decision Requirements Diagram'}));
		await expect.element(screen.getByRole('button', {name: 'Maximize DRD Panel'})).toHaveFocus();
		await expect.element(screen.getByRole('region', {name: 'DRD panel'})).toBeVisible();
		await expect.element(screen.getByTestId('state-overlay-EVALUATED')).toBeVisible();
		await expect.poll(() => screen.queryClient.isFetching()).toBe(0);
		expect(getStateLocally('operate.panelStates')?.drdPanelState).toBe('minimized');
	});

	it('should restore closed and maximized DRD states from local storage on remount', async ({worker}) => {
		mockPage(worker);
		storeStateLocally('operate.panelStates', {drdPanelState: 'closed', isDecisionsFiltersCollapsed: true});
		const screen = await renderPage();

		await expect.element(screen.getByTestId('instance-header')).toBeVisible();
		await expect.element(screen.getByTestId('drd')).not.toBeInTheDocument();
		await userEvent.click(screen.getByRole('button', {name: 'Open Decision Requirements Diagram'}));
		await userEvent.click(screen.getByRole('button', {name: 'Maximize DRD Panel'}));
		expect(getStateLocally('operate.panelStates')).toMatchObject({
			drdPanelState: 'maximized',
			isDecisionsFiltersCollapsed: true,
		});

		await screen.unmount();
		const restored = await renderPage();
		await expect.element(restored.getByRole('button', {name: 'Minimize DRD Panel'})).toBeVisible();
		await expect.element(restored.getByTestId('instance-header')).not.toBeInTheDocument();
		await expect.element(restored.getByTestId('drd-viewer')).toBeVisible();
		await expect.element(restored.getByTestId('state-overlay-EVALUATED')).toBeVisible();
		await expect.poll(() => restored.queryClient.isFetching()).toBe(0);
	});

	it('should focus the header Open action after closing while the header data is still pending', async ({worker}) => {
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(DECISION_INSTANCE), delay: 500}),
			mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}),
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionInstancesResponse({items: [DECISION_INSTANCE]})),
			}),
		);
		const screen = await renderPage();

		await expect.element(screen.getByRole('button', {name: 'Close DRD Panel'})).toBeVisible();
		await expect
			.element(screen.getByRole('button', {name: 'Open Decision Requirements Diagram'}))
			.not.toBeInTheDocument();
		await userEvent.click(screen.getByRole('button', {name: 'Close DRD Panel'}));

		await expect.element(screen.getByRole('button', {name: 'Open Decision Requirements Diagram'})).toHaveFocus();
		await expect.poll(() => screen.queryClient.isFetching()).toBe(0);
	});

	it('should keep the interactive diagram mounted during a background refresh and after a failed refresh', async ({
		worker,
	}) => {
		mockPage(worker);
		const screen = await renderPage();
		await expect.element(screen.getByTestId('state-overlay-EVALUATED')).toBeVisible();
		const viewer = screen.getByTestId('drd-viewer').element();
		const queryKey = ['decisionInstances', 'drdData', DECISION_INSTANCE.decisionEvaluationKey];

		worker.use(
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionInstancesResponse({items: [DECISION_INSTANCE]})),
				delay: 500,
			}),
		);
		const refresh = screen.queryClient.invalidateQueries({queryKey});
		await expect.poll(() => screen.queryClient.isFetching({queryKey})).toBeGreaterThan(0);
		expect(screen.getByTestId('drd-viewer').element()).toBe(viewer);
		await refresh;
		expect(screen.getByTestId('drd-viewer').element()).toBe(viewer);

		worker.use(
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);
		await screen.queryClient.invalidateQueries({queryKey});

		await expect
			.element(screen.getByRole('region', {name: 'DRD panel'}).getByText("Couldn't fetch data"))
			.toBeVisible();
		expect(screen.getByTestId('drd-viewer').element()).toBe(viewer);
		await expect.element(screen.getByTestId('state-overlay-EVALUATED')).toBeVisible();

		worker.use(
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionInstancesResponse({items: [DECISION_INSTANCE]})),
			}),
		);
		await userEvent.click(screen.getByRole('region', {name: 'DRD panel'}).getByRole('button', {name: 'Try again'}));
		await expect
			.element(screen.getByRole('region', {name: 'DRD panel'}).getByText("Couldn't fetch data"))
			.not.toBeInTheDocument();
		await expect.poll(() => screen.queryClient.isFetching()).toBe(0);
		expect(screen.getByTestId('drd-viewer').element()).toBe(viewer);
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
		await expect.element(screen.getByRole('button', {name: 'Try again'})).not.toBeInTheDocument();
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
		await expect.poll(() => screen.router.state.location.search).toMatchObject({evaluated: true, failed: true});
		await expect.element(screen.getByRole('button', {name: 'Try again'})).not.toBeInTheDocument();
		await expect
			.poll(() => notificationsStore.notifications.map((notification) => notification.title))
			.toContain(`Couldn't find decision instance ${DECISION_INSTANCE_ID}`);
	});
});
