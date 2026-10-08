/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {HttpResponse} from 'msw';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockGetDecisionDefinitionXmlEndpoint} from '#/shared-test-modules/mock-handlers';
import {createDecisionDefinition} from '#/shared-test-modules/api-mocks/decision-definitions';
import {DMN_XML} from '#/shared-test-modules/api-mocks/decision-definition-xmls';
import {DecisionPanel} from './DecisionPanel';

function renderDecisionPanel(props: React.ComponentProps<typeof DecisionPanel>) {
	return renderWithRouter(() => <DecisionPanel {...props} />, {path: '/operate/decisions'});
}

const DEFINITION = createDecisionDefinition({
	decisionDefinitionKey: '2251799813685280',
	decisionDefinitionId: 'invoiceClassification',
});

describe('<DecisionPanel />', () => {
	it('should show an empty message when no decision is selected', async () => {
		const screen = await renderDecisionPanel({decisionDefinitionSelection: {kind: 'no-match'}});

		await expect.element(screen.getByText('There is no Decision selected')).toBeVisible();
	});

	it('should show an empty message when multiple versions are selected', async () => {
		const screen = await renderDecisionPanel({
			decisionDefinitionSelection: {
				kind: 'all-versions',
				definition: {name: 'Invoice Classification', decisionDefinitionId: 'invoice-classification'},
			},
		});

		await expect
			.element(screen.getByText('There is more than one Version selected for Decision "Invoice Classification"'))
			.toBeVisible();
	});

	it('should show an empty message when the selected version exists in multiple tenants', async () => {
		const screen = await renderDecisionPanel({
			decisionDefinitionSelection: {
				kind: 'multiple-tenants',
				definition: {name: 'Invoice Classification', decisionDefinitionId: 'invoice-classification'},
			},
		});

		await expect
			.element(screen.getByText('Decision "Invoice Classification" exists in more than one Tenant'))
			.toBeVisible();
	});

	it('should render the decision diagram for a single selected version', async ({worker}) => {
		worker.use(mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}));

		const screen = await renderDecisionPanel({
			decisionDefinitionSelection: {kind: 'single-version', definition: DEFINITION},
		});

		await expect.element(screen.getByTestId('decision-viewer')).toBeVisible();
		await expect.element(screen.getByText('Invoice Amount')).toBeVisible();
	});

	it('should render header actions next to the selected decision', async () => {
		const screen = await renderDecisionPanel({
			decisionDefinitionSelection: {
				kind: 'all-versions',
				definition: {name: 'Invoice', decisionDefinitionId: 'invoice'},
			},
			headerActions: <button type="button">Action</button>,
		});

		await expect.element(screen.getByRole('button', {name: 'Action'})).toBeVisible();
	});

	it('should show a loading indicator while the definition selection is loading', async () => {
		const screen = await renderDecisionPanel({
			decisionDefinitionSelection: {kind: 'no-match'},
			isDefinitionSelectionLoading: true,
		});

		await expect.element(screen.getByTestId('diagram-spinner')).toBeVisible();
	});

	it('should show an error when the definition selection failed', async () => {
		const screen = await renderDecisionPanel({
			decisionDefinitionSelection: {kind: 'no-match'},
			isDefinitionSelectionError: true,
		});

		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
	});

	it('should retry loading a decision definition after an error', async ({worker}) => {
		worker.use(
			mockGetDecisionDefinitionXmlEndpoint({
				successResponse: HttpResponse.json({}, {status: 500}),
			}),
		);

		const screen = await renderDecisionPanel({
			decisionDefinitionSelection: {kind: 'single-version', definition: DEFINITION},
		});

		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
		worker.use(mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}));

		await userEvent.click(screen.getByRole('button', {name: 'Try again'}));

		await expect.element(screen.getByTestId('decision-viewer')).toBeVisible();
	});
});
