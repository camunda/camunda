/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Suspense} from 'react';
import {afterEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {useSearch} from '@tanstack/react-router';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockQueryDecisionDefinitionsEndpoint} from '#/shared-test-modules/mock-handlers';
import {
	createDecisionDefinition,
	createQueryDecisionDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/decision-definitions';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {validateDecisionsSearch} from '../decisionsSearch';
import {DecisionFilters} from './DecisionFilters';

const PATH = '/operate-preview/decisions';

const Harness = () => {
	const search = validateDecisionsSearch(useSearch({strict: false}));
	return (
		<Suspense fallback={null}>
			<DecisionFilters
				decisionDefinitionId={search.decisionDefinitionId}
				decisionDefinitionVersion={search.decisionDefinitionVersion}
				tenantId={search.tenantId}
				evaluated={search.evaluated}
				failed={search.failed}
			/>
		</Suspense>
	);
};

function mockDefinitions(items: ReturnType<typeof createDecisionDefinition>[]) {
	return mockQueryDecisionDefinitionsEndpoint({
		successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse({items})),
	});
}

const INVOICE_V1 = createDecisionDefinition({decisionDefinitionId: 'invoice', name: 'Invoice', version: 1});
const INVOICE_V2 = createDecisionDefinition({decisionDefinitionId: 'invoice', name: 'Invoice', version: 2});
const INVOICE_V3 = createDecisionDefinition({decisionDefinitionId: 'invoice', name: 'Invoice', version: 3});

describe('<DecisionFilters /> (design system)', () => {
	it.beforeEach(({worker}) => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
		worker.use(mockDefinitions([INVOICE_V1, INVOICE_V2]));
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should show the decision name as the combobox label and the id when no name exists', async ({worker}) => {
		worker.use(
			mockDefinitions([
				createDecisionDefinition({decisionDefinitionId: 'unnamed', name: undefined, version: 1}),
				INVOICE_V1,
			]),
		);
		const screen = await renderWithRouter(Harness, {path: PATH, initialEntry: PATH});

		await userEvent.click(screen.getByLabelText('Name'));

		await expect.element(screen.getByRole('option', {name: 'Invoice'})).toBeVisible();
		await expect.element(screen.getByRole('option', {name: 'unnamed'})).toBeVisible();
	});

	it('should label a renamed decision with its newest name', async ({worker}) => {
		worker.use(
			mockDefinitions([
				createDecisionDefinition({decisionDefinitionId: 'invoice', name: 'Invoice renamed', version: 2}),
				createDecisionDefinition({decisionDefinitionId: 'invoice', name: 'Invoice original', version: 1}),
			]),
		);
		const screen = await renderWithRouter(Harness, {
			path: PATH,
			initialEntry: `${PATH}?decisionDefinitionId=invoice`,
		});

		await userEvent.click(screen.getByLabelText('Name'));

		await expect.element(screen.getByRole('option', {name: 'Invoice renamed'})).toBeVisible();
		await expect.element(screen.getByRole('option', {name: 'Invoice original'})).not.toBeInTheDocument();
	});

	it('should write the selected decision to the URL', async () => {
		const screen = await renderWithRouter(Harness, {path: PATH, initialEntry: PATH});

		await userEvent.click(screen.getByLabelText('Name'));
		await userEvent.click(screen.getByRole('option', {name: 'Invoice'}));

		await expect.poll(() => screen.router.state.location.search).toMatchObject({decisionDefinitionId: 'invoice'});
	});

	it('should clear the version when a different decision is selected', async ({worker}) => {
		worker.use(
			mockDefinitions([
				INVOICE_V1,
				createDecisionDefinition({decisionDefinitionId: 'order', name: 'Order', version: 1}),
			]),
		);
		const screen = await renderWithRouter(Harness, {
			path: PATH,
			initialEntry: `${PATH}?decisionDefinitionId=invoice&decisionDefinitionVersion=1`,
		});

		await userEvent.click(screen.getByLabelText('Name'));
		await userEvent.click(screen.getByRole('option', {name: 'Order'}));

		await expect.poll(() => screen.router.state.location.search).toMatchObject({decisionDefinitionId: 'order'});
		expect(screen.router.state.location.search).not.toHaveProperty('decisionDefinitionVersion');
	});

	it('should list versions newest first, including a selected version missing from the loaded definitions', async ({
		worker,
	}) => {
		worker.use(mockDefinitions([INVOICE_V1, INVOICE_V3]));
		const screen = await renderWithRouter(Harness, {
			path: PATH,
			initialEntry: `${PATH}?decisionDefinitionId=invoice&decisionDefinitionVersion=7`,
		});

		await expect.element(screen.getByLabelText('Version')).toBeEnabled();
		await userEvent.click(screen.getByLabelText('Version'));

		await expect
			.poll(() =>
				screen
					.getByRole('option')
					.elements()
					.map((option) => option.textContent),
			)
			.toEqual(['All versions', '7', '3', '1']);
	});

	it('should remove the version from the URL when all versions are selected', async () => {
		const screen = await renderWithRouter(Harness, {
			path: PATH,
			initialEntry: `${PATH}?decisionDefinitionId=invoice&decisionDefinitionVersion=2`,
		});

		await expect.element(screen.getByLabelText('Version')).toBeEnabled();
		await userEvent.click(screen.getByLabelText('Version'));
		await userEvent.click(screen.getByRole('option', {name: 'All versions'}));

		await expect.poll(() => screen.router.state.location.search).toMatchObject({decisionDefinitionId: 'invoice'});
		expect(screen.router.state.location.search).not.toHaveProperty('decisionDefinitionVersion');
	});

	it('should write both instance state checkboxes to the URL', async () => {
		const screen = await renderWithRouter(Harness, {path: PATH, initialEntry: `${PATH}?evaluated=true&failed=true`});

		await userEvent.click(screen.getByLabelText('Evaluated'));
		await expect.poll(() => screen.router.state.location.search).toMatchObject({evaluated: false});

		await userEvent.click(screen.getByLabelText('Failed'));
		await expect.poll(() => screen.router.state.location.search).toMatchObject({evaluated: false, failed: false});
	});
});
