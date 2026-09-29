/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {test, expect} from '#/pw-modules/test-extend';
import {
	mockCurrentUserEndpoint,
	mockGetDecisionDefinitionXmlEndpoint,
	mockGetDecisionInstanceEndpoint,
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockLicenseEndpoint,
	mockQueryDecisionInstancesEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {
	createDecisionInstance,
	createQueryDecisionInstancesResponse,
} from '#/shared-test-modules/api-mocks/decision-instances';
import {DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE} from '#/shared-test-modules/api-mocks/decision-definition-xmls';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createPaginatedResponse, createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';

const DECISION_INSTANCE_ID = '4294980768';
const DECISION_DEFINITION_KEY = '2251799813685253';
const DECISION_INSTANCE = createDecisionInstance({
	decisionEvaluationInstanceKey: DECISION_INSTANCE_ID,
	decisionDefinitionKey: DECISION_DEFINITION_KEY,
	decisionDefinitionId: 'invoiceClassification',
});

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockGetProcessDefinitionInstanceStatisticsEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
		mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
		mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(DECISION_INSTANCE)}),
		mockQueryDecisionInstancesEndpoint({
			successResponse: HttpResponse.json(createQueryDecisionInstancesResponse({items: [DECISION_INSTANCE]})),
		}),
		mockGetDecisionDefinitionXmlEndpoint({
			successResponse: HttpResponse.text(DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE),
		}),
	);
});

test('should maximize, persist, minimize, close and reopen the DRD on the decision route', async ({
	page,
	operateDecisionInstancePage,
}) => {
	await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);

	const panel = operateDecisionInstancePage.drdPanel;
	await expect(panel).toBeVisible();
	await expect(panel.getByText('Invoice Business Decisions')).toBeVisible();
	await expect(panel.getByTestId('state-overlay-EVALUATED')).toBeVisible();

	await operateDecisionInstancePage.maximizeDrd.click();
	await expect(operateDecisionInstancePage.drdPanel).not.toBeVisible();
	await expect(operateDecisionInstancePage.minimizeDrd).toBeFocused();
	await expect(page.getByTestId('instance-header')).not.toBeVisible();
	await expect(page.getByTestId('decision-panel')).not.toBeVisible();
	await expect(page.getByTestId('decision-instance-variables-panel')).not.toBeVisible();

	await page.reload();
	await expect(operateDecisionInstancePage.minimizeDrd).toBeVisible();
	await operateDecisionInstancePage.minimizeDrd.click();
	await expect(operateDecisionInstancePage.drdPanel).toBeVisible();
	await expect(operateDecisionInstancePage.maximizeDrd).toBeFocused();
	await operateDecisionInstancePage.closeDrd.click();
	await expect(operateDecisionInstancePage.drdPanel).not.toBeVisible();
	await expect(page.getByRole('button', {name: 'Open Decision Requirements Diagram'})).toBeFocused();
	await page.reload();
	await expect(operateDecisionInstancePage.drdPanel).not.toBeVisible();
	await page.getByRole('button', {name: 'Open Decision Requirements Diagram'}).click();
	await expect(operateDecisionInstancePage.drdPanel).toBeVisible();
	await expect(operateDecisionInstancePage.maximizeDrd).toBeFocused();
});

test('should clamp DRD panel resizing and restore its width after a reload', async ({
	page,
	operateDecisionInstancePage,
}) => {
	await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);
	await expect(operateDecisionInstancePage.drdPanel).toBeVisible();
	const viewport = page.viewportSize();
	if (viewport === null) {
		throw new Error('Missing browser viewport');
	}
	const maxWidth = Math.floor((viewport.width * 3) / 5);

	await operateDecisionInstancePage.resizeDrdBy(2000);
	await expect.poll(async () => (await operateDecisionInstancePage.drdPanel.boundingBox())?.width).toBe(maxWidth);

	await operateDecisionInstancePage.resizeDrdBy(-2000);
	await expect.poll(async () => (await operateDecisionInstancePage.drdPanel.boundingBox())?.width).toBe(540);

	const resizeHandle = page.getByRole('separator', {name: 'Resize DRD Panel'});
	await expect(resizeHandle).toHaveAttribute('aria-controls', 'operate-decision-drd-panel');
	await resizeHandle.focus();
	await resizeHandle.press('End');
	await expect(resizeHandle).toHaveCSS('outline-style', 'solid');
	await expect.poll(async () => (await operateDecisionInstancePage.drdPanel.boundingBox())?.width).toBe(maxWidth);
	await resizeHandle.press('Home');
	await expect.poll(async () => (await operateDecisionInstancePage.drdPanel.boundingBox())?.width).toBe(540);

	await operateDecisionInstancePage.resizeDrdBy(100);
	const width = (await operateDecisionInstancePage.drdPanel.boundingBox())?.width;
	expect(width).toBe(640);
	await page.reload();
	await expect.poll(async () => (await operateDecisionInstancePage.drdPanel.boundingBox())?.width).toBe(width);

	await page.setViewportSize({width: 800, height: 720});
	await expect.poll(async () => (await operateDecisionInstancePage.drdPanel.boundingBox())?.width).toBe(480);
	await page.reload();
	await expect(page.getByRole('separator', {name: 'Resize DRD Panel'})).toHaveAttribute('aria-valuemin', '480');
	await expect(page.getByRole('separator', {name: 'Resize DRD Panel'})).toHaveAttribute('aria-valuemax', '480');
	await expect(page.getByRole('separator', {name: 'Resize DRD Panel'})).toHaveAttribute('aria-valuenow', '480');
});

test('should release the drag cursor if the DRD panel unmounts during a resize', async ({
	page,
	operateDecisionInstancePage,
}) => {
	await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);
	await expect(operateDecisionInstancePage.drdPanel).toBeVisible();
	const bounds = await operateDecisionInstancePage.drdResizeHandle.boundingBox();
	if (bounds === null) {
		throw new Error('Missing DRD resize handle bounds');
	}
	await page.mouse.move(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
	await page.mouse.down();
	await expect(page.locator('body')).toHaveCSS('cursor', 'ew-resize');

	await operateDecisionInstancePage.maximizeDrd.press('Enter');
	await expect(operateDecisionInstancePage.drdPanel).not.toBeVisible();
	await expect(page.locator('body')).not.toHaveCSS('cursor', 'ew-resize');
	await page.mouse.up();
});

test('should navigate to the related evaluated decision selected in the DRD', async ({
	network,
	page,
	operateDecisionInstancePage,
}) => {
	const relatedInstance = createDecisionInstance({
		...DECISION_INSTANCE,
		decisionDefinitionId: 'calc-key-figures',
		decisionEvaluationInstanceKey: '4294980769',
		state: 'FAILED',
	});
	network.use(
		mockQueryDecisionInstancesEndpoint({
			successResponse: HttpResponse.json(
				createQueryDecisionInstancesResponse({items: [DECISION_INSTANCE, relatedInstance]}),
			),
		}),
	);

	await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);
	await expect(operateDecisionInstancePage.drdPanel.getByTestId('state-overlay-FAILED')).toBeVisible();
	const relatedDecision = operateDecisionInstancePage.drdPanel.getByRole('button', {
		name: 'Calculate Credit History Key Figures',
	});
	await relatedDecision.focus();
	await relatedDecision.press('Enter');

	await expect(page).toHaveURL(/\/operate\/decisions\/4294980769$/);
	await expect(operateDecisionInstancePage.maximizeDrd).toBeFocused();
});

test('should highlight the current definition when another evaluation of it is newer', async ({
	network,
	operateDecisionInstancePage,
}) => {
	network.use(
		mockQueryDecisionInstancesEndpoint({
			successResponse: HttpResponse.json(
				createQueryDecisionInstancesResponse({
					items: [
						DECISION_INSTANCE,
						createDecisionInstance({
							...DECISION_INSTANCE,
							decisionEvaluationInstanceKey: '4294980769',
						}),
					],
				}),
			),
		}),
	);

	await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);
	await expect(operateDecisionInstancePage.drdPanel.getByTestId('state-overlay-EVALUATED')).toBeVisible();
	await expect(operateDecisionInstancePage.drdPanel.locator('[data-element-id="invoiceClassification"]')).toHaveClass(
		/ope-selected/,
	);
});

test('should show a recoverable DRD-only error when evaluation-family lookup fails', async ({
	network,
	operateDecisionInstancePage,
}) => {
	network.use(
		mockQueryDecisionInstancesEndpoint({
			successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
		}),
	);
	await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);

	await expect(operateDecisionInstancePage.drdPanel.getByText("Couldn't fetch data")).toBeVisible({timeout: 15000});
	await expect(operateDecisionInstancePage.decisionTableLabel).toBeVisible();
	network.use(
		mockQueryDecisionInstancesEndpoint({
			successResponse: HttpResponse.json(createQueryDecisionInstancesResponse({items: [DECISION_INSTANCE]})),
		}),
	);
	await operateDecisionInstancePage.drdPanel.getByRole('button', {name: 'Try again'}).click();
	await expect(operateDecisionInstancePage.drdPanel.getByTestId('state-overlay-EVALUATED')).toBeVisible();
});

test('should show a forbidden DRD without hiding the decision detail', async ({
	network,
	operateDecisionInstancePage,
}) => {
	network.use(
		mockQueryDecisionInstancesEndpoint({
			successResponse: HttpResponse.json(createProblemDetails({status: 403}), {status: 403}),
		}),
	);
	await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);

	await expect(
		operateDecisionInstancePage.drdPanel.getByText('Missing permissions to view the Definition'),
	).toBeVisible({timeout: 15000});
	await expect(operateDecisionInstancePage.decisionTableLabel).toBeVisible();
});

test('should recover from invalid DRD XML without replacing the decision detail', async ({
	network,
	operateDecisionInstancePage,
}) => {
	network.use(mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text('<invalid')}));
	await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);

	await expect(operateDecisionInstancePage.drdPanel.getByText("Couldn't fetch data")).toBeVisible();
	await expect(operateDecisionInstancePage.decisionPanel).toBeVisible();
	network.use(
		mockGetDecisionDefinitionXmlEndpoint({
			successResponse: HttpResponse.text(DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE),
			delay: 500,
		}),
	);
	await operateDecisionInstancePage.drdPanel.getByRole('button', {name: 'Try again'}).click();
	await expect(operateDecisionInstancePage.drdPanel.getByTestId('state-overlay-EVALUATED')).toBeVisible();
});

test('should render the loading state and then the evaluated DMN panel on direct entry', async ({
	network,
	operateDecisionInstancePage,
}) => {
	network.use(
		mockGetDecisionDefinitionXmlEndpoint({
			successResponse: HttpResponse.text(DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE),
			delay: 1200,
		}),
	);

	await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);

	await expect(operateDecisionInstancePage.loadingSpinner).toBeVisible();
	await expect(operateDecisionInstancePage.decisionTableLabel).toBeVisible();
});

test('should render panel-level forbidden state when xml access is denied', async ({
	network,
	operateDecisionInstancePage,
}) => {
	network.use(
		mockGetDecisionDefinitionXmlEndpoint({
			successResponse: HttpResponse.json(createProblemDetails({status: 403}), {status: 403}),
		}),
	);

	await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);

	await expect(operateDecisionInstancePage.xmlForbiddenMessage).toBeVisible();
	await expect(operateDecisionInstancePage.pageErrorHeading).not.toBeVisible();
});

test('should recover from a panel-level xml error when retrying', async ({network, operateDecisionInstancePage}) => {
	network.use(
		mockGetDecisionDefinitionXmlEndpoint({
			successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
		}),
	);

	await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);

	await expect(operateDecisionInstancePage.panelErrorMessage).toBeVisible();
	await expect(operateDecisionInstancePage.pageErrorHeading).not.toBeVisible();

	network.use(
		mockGetDecisionDefinitionXmlEndpoint({
			successResponse: HttpResponse.text(DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE),
		}),
	);
	await operateDecisionInstancePage.panelRetryButton.click();

	await expect(operateDecisionInstancePage.decisionTableLabel).toBeVisible();
});

test('should preserve page-level error and allow recovering by retrying the decision-instance query', async ({
	network,
	operateDecisionInstancePage,
}) => {
	network.use(
		mockGetDecisionInstanceEndpoint({
			successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
		}),
	);

	await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);

	await expect(operateDecisionInstancePage.pageErrorHeading).toBeVisible({timeout: 15000});

	network.use(mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(DECISION_INSTANCE)}));
	await operateDecisionInstancePage.pageErrorRetryButton.click();

	await expect(operateDecisionInstancePage.decisionTableLabel).toBeVisible();
});
