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
	mockQueryDecisionDefinitionsEndpoint,
	mockQueryDecisionInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createDecisionDefinition,
	createQueryDecisionDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/decision-definitions';
import {createQueryDecisionInstancesResponse} from '#/shared-test-modules/api-mocks/decision-instances';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {DecisionsHarness} from './DecisionsHarness';

const DECISION_DEFINITIONS = HttpResponse.json(
	createQueryDecisionDefinitionsResponse({
		items: [
			createDecisionDefinition({name: 'Invoice Approval', decisionDefinitionId: 'invoice-approval', version: 2}),
			createDecisionDefinition({name: 'Invoice Approval', decisionDefinitionId: 'invoice-approval', version: 1}),
			createDecisionDefinition({name: 'Discount Rate', decisionDefinitionId: 'discount-rate', version: 1}),
		],
	}),
);

const EMPTY_DECISION_INSTANCES = HttpResponse.json(createQueryDecisionInstancesResponse());

function renderDecisionsPage(searchParams?: Record<string, string>) {
	const query = searchParams ? `?${new URLSearchParams(searchParams).toString()}` : '';
	return renderWithRouter(DecisionsHarness, {
		path: '/operate/decisions',
		initialEntry: `/operate/decisions${query}`,
	});
}

describe('<Decisions />', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should render the filter sections', async ({worker}) => {
		worker.use(
			mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
			mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
		);

		const screen = await renderDecisionsPage();

		await expect.element(screen.getByText('Instances States')).toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toBeVisible();
		await expect.element(screen.getByRole('checkbox', {name: 'Evaluated'})).toBeVisible();
		await expect.element(screen.getByRole('checkbox', {name: 'Failed'})).toBeVisible();
	});

	it('should disable the version dropdown until a decision is selected', async ({worker}) => {
		worker.use(
			mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
			mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
		);

		const screen = await renderDecisionsPage();

		await expect.element(screen.getByRole('combobox', {name: 'Version'})).toBeDisabled();
	});

	it('should enable the version dropdown once a decision is selected', async ({worker}) => {
		worker.use(
			mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
			mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
		);

		const screen = await renderDecisionsPage({decisionDefinitionId: 'invoice-approval'});

		await expect.element(screen.getByRole('combobox', {name: 'Version'})).not.toBeDisabled();
	});

	it('should navigate resetting version when a decision is selected', async ({worker}) => {
		worker.use(
			mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
			mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
		);

		const screen = await renderDecisionsPage({decisionDefinitionId: 'discount-rate', decisionDefinitionVersion: '1'});

		const nameCombobox = screen.getByRole('combobox', {name: 'Name'});
		await nameCombobox.click({force: true});
		await nameCombobox.fill('Invoice Approval');
		await userEvent.keyboard('{Enter}');

		const getSearch = () => screen.router.state.location.search as Record<string, unknown>;
		await expect.poll(getSearch).toMatchObject({decisionDefinitionId: 'invoice-approval'});
		expect(getSearch().decisionDefinitionVersion).toBeUndefined();
	});

	describe('instance state checkboxes', () => {
		it('defaults to both evaluated and failed checked', async ({worker}) => {
			worker.use(
				mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
				mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
			);

			const screen = await renderDecisionsPage();

			await expect.element(screen.getByRole('checkbox', {name: 'Evaluated'})).toBeChecked();
			await expect.element(screen.getByRole('checkbox', {name: 'Failed'})).toBeChecked();
		});

		it('updates the URL when a checkbox is unchecked', async ({worker}) => {
			worker.use(
				mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
				mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
			);

			const screen = await renderDecisionsPage();
			const getSearch = () => screen.router.state.location.search as Record<string, unknown>;

			// force: the checkbox's labelText is a Stack with an icon, which visually covers the
			// native input and fails real-browser click actionability (see Processes' own tests).
			await screen.getByRole('checkbox', {name: 'Failed'}).click({force: true});

			await expect.poll(() => getSearch().failed).toBe(false);
		});
	});

	describe('reset button', () => {
		it('is disabled at the default filter state', async ({worker}) => {
			worker.use(
				mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
				mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
			);

			const screen = await renderDecisionsPage();

			await expect.element(screen.getByRole('button', {name: 'Reset filters'})).toBeDisabled();
		});

		it('is enabled once a decision is selected', async ({worker}) => {
			worker.use(
				mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
				mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
			);

			const screen = await renderDecisionsPage({decisionDefinitionId: 'invoice-approval'});

			await expect.element(screen.getByRole('button', {name: 'Reset filters'})).not.toBeDisabled();
		});

		it('is enabled once a non-default instance state checkbox is set', async ({worker}) => {
			worker.use(
				mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
				mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
			);

			const screen = await renderDecisionsPage({failed: 'false'});

			await expect.element(screen.getByRole('button', {name: 'Reset filters'})).not.toBeDisabled();
		});
	});
});
