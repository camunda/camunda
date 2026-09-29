/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse, http} from 'msw';
import {endpoints} from '@camunda/camunda-api-zod-schemas/8.11';
import {test, expect} from '#/pw-modules/test-extend';
import {
	mockCurrentUserEndpoint,
	mockGetDecisionDefinitionXmlEndpoint,
	mockGetDecisionInstanceEndpoint,
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockLicenseEndpoint,
	mockQueryDecisionInstancesEndpoint,
	mockQueryDecisionDefinitionsEndpoint,
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
import {createQueryDecisionDefinitionsResponse} from '#/shared-test-modules/api-mocks/decision-definitions';

const DECISION_INSTANCE_ID = '4294980768';
const RELATED_DECISION_INSTANCE_ID = '4294980769';
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

test('should clamp DRD panel resizing and restore its preferred width after viewport changes and a reload', async ({
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

	const initialHandleBounds = await resizeHandle.boundingBox();
	if (initialHandleBounds === null) {
		throw new Error('Missing DRD resize handle bounds');
	}
	await page.mouse.move(initialHandleBounds.x + initialHandleBounds.width / 2, initialHandleBounds.y + 5);
	await page.mouse.down();
	await page.setViewportSize({width: 1000, height: 720});
	await expect.poll(async () => (await operateDecisionInstancePage.drdPanel.boundingBox())?.width).toBe(600);
	await page.mouse.up();
	await page.setViewportSize(viewport);
	await expect.poll(async () => (await operateDecisionInstancePage.drdPanel.boundingBox())?.width).toBe(width);

	await page.setViewportSize({width: 1000, height: 720});
	await expect.poll(async () => (await operateDecisionInstancePage.drdPanel.boundingBox())?.width).toBe(600);
	const handleBounds = await resizeHandle.boundingBox();
	if (handleBounds === null) {
		throw new Error('Missing DRD resize handle bounds');
	}
	const handleX = handleBounds.x + handleBounds.width / 2;
	const handleY = handleBounds.y + handleBounds.height / 2;
	await page.mouse.move(handleX, handleY);
	await page.mouse.down();
	await page.mouse.move(handleX + 10, handleY);
	await expect.poll(async () => (await operateDecisionInstancePage.drdPanel.boundingBox())?.width).toBe(590);
	await page.mouse.move(handleX, handleY);
	await page.mouse.up();
	await expect.poll(async () => (await operateDecisionInstancePage.drdPanel.boundingBox())?.width).toBe(600);
	await page.mouse.move(handleX, handleY);
	await page.mouse.down();
	await page.mouse.move(handleX + 10, handleY);
	await page.mouse.move(handleX - 10, handleY);
	await page.mouse.up();
	await expect.poll(async () => (await operateDecisionInstancePage.drdPanel.boundingBox())?.width).toBe(600);
	await page.setViewportSize(viewport);
	await expect.poll(async () => (await operateDecisionInstancePage.drdPanel.boundingBox())?.width).toBe(width);

	await page.setViewportSize({width: 800, height: 720});
	await expect.poll(async () => (await operateDecisionInstancePage.drdPanel.boundingBox())?.width).toBe(480);
	await operateDecisionInstancePage.resizeDrdBy(0);
	await resizeHandle.press('End');
	await page.setViewportSize(viewport);
	await expect.poll(async () => (await operateDecisionInstancePage.drdPanel.boundingBox())?.width).toBe(width);

	await page.setViewportSize({width: 800, height: 720});
	await expect.poll(async () => (await operateDecisionInstancePage.drdPanel.boundingBox())?.width).toBe(480);
	await page.reload();
	await expect(page.getByRole('separator', {name: 'Resize DRD Panel'})).toHaveAttribute('aria-valuemin', '480');
	await expect(page.getByRole('separator', {name: 'Resize DRD Panel'})).toHaveAttribute('aria-valuemax', '480');
	await expect(page.getByRole('separator', {name: 'Resize DRD Panel'})).toHaveAttribute('aria-valuenow', '480');
	await page.setViewportSize(viewport);
	await expect.poll(async () => (await operateDecisionInstancePage.drdPanel.boundingBox())?.width).toBe(width);
});

test('should preserve a drag adjustment when the viewport changes before releasing the handle', async ({
	page,
	operateDecisionInstancePage,
}) => {
	const viewport = {width: 1300, height: 720};
	await page.setViewportSize(viewport);
	await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);
	await expect(operateDecisionInstancePage.drdPanel).toBeVisible();
	await operateDecisionInstancePage.resizeDrdBy(100);
	await expect.poll(async () => (await operateDecisionInstancePage.drdPanel.boundingBox())?.width).toBe(640);

	const handleBounds = await operateDecisionInstancePage.drdResizeHandle.boundingBox();
	if (handleBounds === null) {
		throw new Error('Missing DRD resize handle bounds');
	}
	const handleX = handleBounds.x + handleBounds.width / 2;
	const handleY = handleBounds.y + handleBounds.height / 2;
	await page.mouse.move(handleX, handleY);
	await page.mouse.down();
	await page.mouse.move(handleX + 20, handleY);
	await expect.poll(async () => (await operateDecisionInstancePage.drdPanel.boundingBox())?.width).toBe(620);
	await page.setViewportSize({width: 1000, height: 720});
	await expect.poll(async () => (await operateDecisionInstancePage.drdPanel.boundingBox())?.width).toBe(600);
	await page.mouse.move(handleX + 20, handleY + 10);
	await page.mouse.up();

	await page.setViewportSize(viewport);
	await expect.poll(async () => (await operateDecisionInstancePage.drdPanel.boundingBox())?.width).toBe(620);
	await page.reload();
	await expect.poll(async () => (await operateDecisionInstancePage.drdPanel.boundingBox())?.width).toBe(620);
});

test('should ignore non-primary mouse buttons on the DRD resize handle', async ({
	page,
	operateDecisionInstancePage,
}) => {
	await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);
	await expect(operateDecisionInstancePage.drdPanel).toBeVisible();
	const handleBounds = await operateDecisionInstancePage.drdResizeHandle.boundingBox();
	if (handleBounds === null) {
		throw new Error('Missing DRD resize handle bounds');
	}
	const handleX = handleBounds.x + handleBounds.width / 2;
	const handleY = handleBounds.y + handleBounds.height / 2;

	await page.mouse.move(handleX, handleY);
	await page.mouse.down({button: 'right'});
	await page.mouse.move(handleX - 100, handleY);
	await expect(page.locator('body')).not.toHaveCSS('cursor', 'ew-resize');
	await expect.poll(async () => (await operateDecisionInstancePage.drdPanel.boundingBox())?.width).toBe(540);
	await page.mouse.up({button: 'right'});

	await page.mouse.move(handleX, handleY);
	await page.mouse.down();
	await page.mouse.down({button: 'right'});
	await page.mouse.up({button: 'right'});
	await page.mouse.move(handleX - 100, handleY);
	await expect(page.locator('body')).toHaveCSS('cursor', 'ew-resize');
	await expect.poll(async () => (await operateDecisionInstancePage.drdPanel.boundingBox())?.width).toBe(640);
	await page.mouse.up();
	await expect(page.locator('body')).not.toHaveCSS('cursor', 'ew-resize');
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
		decisionDefinitionName: 'Calculate Credit History Key Figures',
		decisionEvaluationInstanceKey: RELATED_DECISION_INSTANCE_ID,
		state: 'FAILED',
	});
	network.use(
		http.get(endpoints.getDecisionInstance.getUrl({decisionEvaluationInstanceKey: RELATED_DECISION_INSTANCE_ID}), () =>
			HttpResponse.json(relatedInstance),
		),
		http.get(endpoints.getDecisionInstance.getUrl({decisionEvaluationInstanceKey: DECISION_INSTANCE_ID}), () =>
			HttpResponse.json(DECISION_INSTANCE),
		),
		mockQueryDecisionInstancesEndpoint({
			successResponse: HttpResponse.json(
				createQueryDecisionInstancesResponse({items: [DECISION_INSTANCE, relatedInstance]}),
			),
		}),
	);

	await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);
	await expect(page.getByTestId('instance-header')).toContainText(DECISION_INSTANCE_ID);
	await expect(page).toHaveTitle(`Operate: Decision Instance ${DECISION_INSTANCE_ID} of Invoice Classification`);
	await expect(operateDecisionInstancePage.drdPanel.getByTestId('state-overlay-FAILED')).toBeVisible();
	const relatedDecision = operateDecisionInstancePage.drdPanel.getByRole('button', {
		name: 'Calculate Credit History Key Figures',
	});
	await relatedDecision.focus();
	await relatedDecision.press('Enter');

	await expect(page).toHaveURL(new RegExp(`/operate/decisions/${RELATED_DECISION_INSTANCE_ID}$`));
	await expect(operateDecisionInstancePage.maximizeDrd).toBeFocused();
	await expect(page.getByTestId('instance-header')).toContainText(RELATED_DECISION_INSTANCE_ID);
	await expect(page.getByTestId('instance-header')).toContainText('Calculate Credit History Key Figures');
	await expect(page).toHaveTitle(
		`Operate: Decision Instance ${RELATED_DECISION_INSTANCE_ID} of Calculate Credit History Key Figures`,
	);
	await operateDecisionInstancePage.maximizeDrd.click();
	await expect(operateDecisionInstancePage.minimizeDrd).toBeVisible();
	await operateDecisionInstancePage.minimizeDrd.click();
	await expect(operateDecisionInstancePage.drdPanel).toBeVisible();
	await expect(operateDecisionInstancePage.decisionPanel).toBeVisible();
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
							decisionEvaluationInstanceKey: RELATED_DECISION_INSTANCE_ID,
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

test('should assemble the header, evaluation, variables, result, and DRD on direct entry', async ({
	page,
	operateDecisionInstancePage,
}) => {
	await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);

	await expect(page.getByTestId('instance-header')).toContainText(DECISION_INSTANCE_ID);
	await expect(operateDecisionInstancePage.decisionTableLabel).toBeVisible();
	await expect(page.getByTestId('decision-instance-variables-panel')).toBeVisible();
	await expect(page.getByRole('tab', {name: 'Inputs and Outputs'})).toBeVisible();
	await page.getByRole('tab', {name: 'Result'}).click();
	await expect(page.getByTestId('decision-instance-variables-panel')).toContainText(DECISION_INSTANCE.result);
	await expect(operateDecisionInstancePage.drdPanel.getByTestId('state-overlay-EVALUATED')).toBeVisible();
	await expect(page).toHaveTitle(`Operate: Decision Instance ${DECISION_INSTANCE_ID} of Invoice Classification`);
});

for (const isMultiTenancyEnabled of [false, true]) {
	test(`should show the ${isMultiTenancyEnabled ? 'multi-tenant' : 'single-tenant'} route pending shell until the evaluation loads`, async ({
		network,
		page,
		operateDecisionInstancePage,
	}) => {
		let releaseResponse!: () => void;
		const heldResponse = new Promise<void>((resolve) => {
			releaseResponse = resolve;
		});
		network.use(
			mockSystemConfigurationEndpoint({
				successResponse: HttpResponse.json(
					createSystemConfiguration({
						components: {active: ['operate']},
						deployment: {...createSystemConfiguration().deployment, isMultiTenancyEnabled},
					}),
				),
			}),
			http.get(
				endpoints.getDecisionInstance.getUrl({decisionEvaluationInstanceKey: DECISION_INSTANCE_ID}),
				async () => {
					await heldResponse;
					return HttpResponse.json(DECISION_INSTANCE);
				},
			),
		);

		await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);

		const skeleton = page.getByTestId('instance-header-skeleton');
		try {
			await expect(skeleton).toBeVisible();
			await expect(page.locator('head link[rel="stylesheet"][href*="assets/index-"]')).toHaveCount(1);
			await expect(skeleton.getByRole('columnheader', {name: 'Tenant'})).toHaveCount(isMultiTenancyEnabled ? 1 : 0);
			await expect(skeleton.getByRole('columnheader', {name: 'Decision Instance Key'})).toBeVisible();
			await expect(operateDecisionInstancePage.loadingSpinner).toBeVisible();
			await expect(page.getByTestId('inputs-skeleton')).toBeVisible();
			await expect(page.getByTestId('outputs-skeleton')).toBeVisible();
			const viewport = page.viewportSize();
			if (viewport === null) {
				throw new Error('Missing browser viewport');
			}
			const panelBottom = await page.getByTestId('decision-instance-variables-panel').evaluate((panel) => {
				return panel.getBoundingClientRect().bottom;
			});
			expect(Math.abs(viewport.height - panelBottom)).toBeLessThan(1);
			await page.getByRole('tab', {name: 'Result'}).click();
			await expect(page.getByTestId('result-loading-spinner').getByRole('img', {name: 'loading'})).toBeVisible();
		} finally {
			releaseResponse();
		}
		await expect(page.getByTestId('instance-header')).toContainText(DECISION_INSTANCE_ID);
		await expect(skeleton).not.toBeVisible();
	});
}

test('should show a forbidden page for an inaccessible evaluation rather than an error route', async ({
	network,
	page,
	operateDecisionInstancePage,
}) => {
	network.use(
		mockGetDecisionInstanceEndpoint({
			successResponse: HttpResponse.json(createProblemDetails({status: 403}), {status: 403}),
		}),
	);

	await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);

	await expect(page.getByText('403 - You do not have permission to view this information')).toBeVisible({
		timeout: 15000,
	});
	await expect(operateDecisionInstancePage.pageErrorHeading).not.toBeVisible();
	await expect(page).toHaveURL(`/operate/decisions/${DECISION_INSTANCE_ID}`);
	await expect(page).not.toHaveTitle(`Operate: Decision Instance ${DECISION_INSTANCE_ID} of Invoice Classification`);
});

test('should notify and redirect when an evaluation is not found without retaining its previous title', async ({
	network,
	page,
	operateDecisionInstancePage,
}) => {
	network.use(
		mockQueryDecisionDefinitionsEndpoint({
			successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
		}),
		mockQueryDecisionInstancesEndpoint({
			successResponse: HttpResponse.json(
				createQueryDecisionInstancesResponse({
					items: [
						DECISION_INSTANCE,
						createDecisionInstance({
							...DECISION_INSTANCE,
							decisionDefinitionId: 'calc-key-figures',
							decisionEvaluationInstanceKey: RELATED_DECISION_INSTANCE_ID,
						}),
					],
				}),
			),
		}),
	);
	await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);
	await expect(page).toHaveTitle(`Operate: Decision Instance ${DECISION_INSTANCE_ID} of Invoice Classification`);
	await expect(
		operateDecisionInstancePage.drdPanel.getByRole('button', {name: 'Calculate Credit History Key Figures'}),
	).toBeVisible();
	network.use(
		http.get(endpoints.getDecisionInstance.getUrl({decisionEvaluationInstanceKey: RELATED_DECISION_INSTANCE_ID}), () =>
			HttpResponse.json(createProblemDetails({status: 404}), {status: 404}),
		),
	);
	await operateDecisionInstancePage.drdPanel
		.getByRole('button', {name: 'Calculate Credit History Key Figures'})
		.click();
	await expect(page).toHaveURL((url) => url.pathname === '/operate/decisions' && url.searchParams.has('evaluated'), {
		timeout: 15000,
	});
	await expect(page.getByText(`Couldn't find decision instance ${RELATED_DECISION_INSTANCE_ID}`)).toBeVisible();
	await expect(page).not.toHaveTitle(`Operate: Decision Instance ${DECISION_INSTANCE_ID} of Invoice Classification`);
});

test('should recover the real route from a network failure without losing the URL', async ({
	network,
	page,
	operateDecisionInstancePage,
}) => {
	network.use(mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.error()}));
	await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);
	await expect(operateDecisionInstancePage.pageErrorHeading).toBeVisible({timeout: 15000});
	await expect(page).toHaveURL(`/operate/decisions/${DECISION_INSTANCE_ID}`);
	await expect(page).not.toHaveTitle(`Operate: Decision Instance ${DECISION_INSTANCE_ID} of Invoice Classification`);

	network.use(mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(DECISION_INSTANCE)}));
	await operateDecisionInstancePage.pageErrorRetryButton.click();
	await expect(operateDecisionInstancePage.decisionTableLabel).toBeVisible();
	await expect(page).toHaveTitle(`Operate: Decision Instance ${DECISION_INSTANCE_ID} of Invoice Classification`);
	await expect(page).toHaveURL(`/operate/decisions/${DECISION_INSTANCE_ID}`);
});
