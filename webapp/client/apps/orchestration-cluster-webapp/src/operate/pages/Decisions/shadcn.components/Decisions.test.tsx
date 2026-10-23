/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {useSearch} from '@tanstack/react-router';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {HttpResponse} from 'msw';
import {
	mockGetDecisionDefinitionXmlEndpoint,
	mockQueryDecisionDefinitionsEndpoint,
	mockQueryDecisionInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createDecisionDefinition,
	createQueryDecisionDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/decision-definitions';
import {createQueryDecisionInstancesResponse} from '#/shared-test-modules/api-mocks/decision-instances';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {Decisions} from './Decisions';

const DecisionsWithSearch = () => {
	const search = useSearch({strict: false});
	return <Decisions search={{evaluated: true, failed: true, ...search}} />;
};

function mockInstances() {
	return mockQueryDecisionInstancesEndpoint({
		successResponse: HttpResponse.json(createQueryDecisionInstancesResponse()),
	});
}

describe('<Decisions />', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should render the sr-only decisions title', async ({worker}) => {
		worker.use(
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionInstancesResponse()),
			}),
		);
		const screen = await renderWithRouter(DecisionsWithSearch, {path: '/operate-preview/decisions'});

		await expect.element(screen.getByRole('heading', {name: 'Decisions'})).toBeInTheDocument();
	});

	it('should render the filters panel with the optional filters menu, the decision panel and the instances table', async ({
		worker,
	}) => {
		worker.use(
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionInstancesResponse()),
			}),
		);
		const screen = await renderWithRouter(DecisionsWithSearch, {path: '/operate-preview/decisions'});

		await expect.element(screen.getByRole('button', {name: 'More Filters'})).toBeVisible();
		await expect.element(screen.getByText('There is no Decision selected')).toBeVisible();
		await expect.element(screen.getByText('Decision Instances')).toBeVisible();
	});

	it('should show optional filters that are active in the URL', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?businessId=eq_order-1',
		});

		await expect.element(screen.getByLabelText('Business ID', {exact: true})).toHaveValue('order-1');
	});

	it('should enable reset when optional filters are active in the URL', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?businessId=eq_order-1',
		});

		await expect.element(screen.getByRole('button', {name: 'Reset filters'})).toBeEnabled();
	});

	it('should enable reset when a decision definition is selected in the URL', async ({worker}) => {
		worker.use(
			mockInstances(),
			mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text('<definitions/>')}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryDecisionDefinitionsResponse({
						items: [createDecisionDefinition({decisionDefinitionId: 'invoice', version: 1})],
					}),
				),
			}),
		);
		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?decisionDefinitionId=invoice',
		});

		await expect.element(screen.getByRole('button', {name: 'Reset filters'})).toBeEnabled();
	});

	it('should write edited optional filters to the URL and keep unrelated search state', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?businessId=eq_order-1&tenantId=tenant-a&failed=false',
		});

		await userEvent.fill(screen.getByLabelText('Business ID', {exact: true}), 'order-2');

		await expect
			.poll(() => screen.router.state.location.search)
			.toEqual({
				businessId: 'eq_order-2',
				tenantId: 'tenant-a',
				failed: false,
			});

		await userEvent.clear(screen.getByLabelText('Business ID', {exact: true}));

		await expect.poll(() => screen.router.state.location.search).toEqual({tenantId: 'tenant-a', failed: false});
	});

	it('should clear a removed filter from the URL even when another filter field is invalid', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?businessId=eq_order-1&processInstanceKey=%22123%22',
		});

		await userEvent.fill(screen.getByLabelText('Process Instance Key', {exact: true}), 'abc');
		await expect
			.element(screen.getByLabelText('Process Instance Key', {exact: true}))
			.toHaveAttribute('aria-invalid', 'true');
		(screen.getByRole('button', {name: 'Remove Business ID Filter'}).element() as HTMLElement).click();

		await expect.element(screen.getByLabelText('Business ID', {exact: true})).not.toBeInTheDocument();
		await expect.poll(() => screen.router.state.location.search).not.toHaveProperty('businessId');
	});

	it('should keep unrelated edits when another filter is removed', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?businessId=eq_order-1&processInstanceKey=%22123%22',
		});

		await userEvent.fill(screen.getByLabelText('Process Instance Key', {exact: true}), 'abc');
		(screen.getByRole('button', {name: 'Remove Business ID Filter'}).element() as HTMLElement).click();

		await expect.element(screen.getByLabelText('Business ID', {exact: true})).not.toBeInTheDocument();
		await expect.element(screen.getByLabelText('Process Instance Key', {exact: true})).toHaveValue('abc');
	});
	it('should restore URL values when navigating back with a pending draft', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?businessId=eq_order-1',
		});
		await expect.element(screen.getByLabelText('Business ID', {exact: true})).toHaveValue('order-1');
		await screen.router.navigate({to: '.', search: (prev) => ({...prev, businessId: 'eq_order-2'})});
		await expect.element(screen.getByLabelText('Business ID', {exact: true})).toHaveValue('order-2');

		await userEvent.fill(screen.getByLabelText('Business ID', {exact: true}), 'order-3');
		screen.router.history.back();

		await expect.element(screen.getByLabelText('Business ID', {exact: true})).toHaveValue('order-1');
		await expect.poll(() => screen.router.state.location.search).toHaveProperty('businessId', 'eq_order-1');
	});

	it('should discard an invalid draft when resetting after removing an empty filter', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsWithSearch, {path: '/operate-preview/decisions'});

		(screen.getByRole('button', {name: 'More Filters'}).element() as HTMLElement).focus();
		await userEvent.keyboard('{Enter}');
		await userEvent.click(screen.getByRole('menuitem', {name: 'Process Instance Key'}));
		await userEvent.fill(screen.getByLabelText('Process Instance Key', {exact: true}), 'abc');
		(screen.getByRole('button', {name: 'More Filters'}).element() as HTMLElement).focus();
		await userEvent.keyboard('{Enter}');
		await userEvent.click(screen.getByRole('menuitem', {name: 'Business ID'}));
		(screen.getByRole('button', {name: 'Remove Business ID Filter'}).element() as HTMLElement).click();
		(screen.getByRole('button', {name: 'Reset filters'}).element() as HTMLElement).click();
		(screen.getByRole('button', {name: 'More Filters'}).element() as HTMLElement).focus();
		await userEvent.keyboard('{Enter}');
		await userEvent.click(screen.getByRole('menuitem', {name: 'Process Instance Key'}));

		await expect.element(screen.getByLabelText('Process Instance Key', {exact: true})).toHaveValue('');
	});

	it('should discard an invalid draft when resetting', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsWithSearch, {path: '/operate-preview/decisions'});

		(screen.getByRole('button', {name: 'More Filters'}).element() as HTMLElement).focus();
		await userEvent.keyboard('{Enter}');
		await userEvent.click(screen.getByRole('menuitem', {name: 'Process Instance Key'}));
		await userEvent.fill(screen.getByLabelText('Process Instance Key', {exact: true}), 'abc');
		(screen.getByRole('button', {name: 'Reset filters'}).element() as HTMLElement).click();
		(screen.getByRole('button', {name: 'More Filters'}).element() as HTMLElement).focus();
		await userEvent.keyboard('{Enter}');
		await userEvent.click(screen.getByRole('menuitem', {name: 'Process Instance Key'}));

		await expect.element(screen.getByLabelText('Process Instance Key', {exact: true})).toHaveValue('');
	});

	it('should enable reset when a sort is applied in the URL', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?sort=evaluationDate%2Basc',
		});

		await expect.element(screen.getByRole('button', {name: 'Reset filters'})).toBeEnabled();
	});

	it('should remove active optional filters when resetting', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?businessId=eq_order-1',
		});

		await userEvent.click(screen.getByRole('button', {name: 'Reset filters'}));

		await expect.element(screen.getByLabelText('Business ID', {exact: true})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Reset filters'})).toBeDisabled();
	});

	it('should show the selected decision in the decision panel', async ({worker}) => {
		worker.use(
			mockInstances(),
			mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text('<definitions/>')}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryDecisionDefinitionsResponse({
						items: [
							createDecisionDefinition({name: 'Invoice Classification', decisionDefinitionId: 'invoice', version: 1}),
						],
					}),
				),
			}),
		);

		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?decisionDefinitionId=invoice&decisionDefinitionVersion=1',
		});

		await expect.element(screen.getByRole('heading', {name: 'Invoice Classification'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: /Decision ID/})).toBeVisible();
	});

	it('should ask for a single version when all versions of a decision are selected', async ({worker}) => {
		worker.use(
			mockInstances(),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryDecisionDefinitionsResponse({
						items: [createDecisionDefinition({name: 'Invoice Classification', decisionDefinitionId: 'invoice'})],
					}),
				),
			}),
		);

		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?decisionDefinitionId=invoice',
		});

		await expect
			.element(screen.getByText('There is more than one Version selected for Decision "Invoice Classification"'))
			.toBeVisible();
	});

	it('should explain that the selected version exists in multiple tenants', async ({worker}) => {
		worker.use(
			mockInstances(),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryDecisionDefinitionsResponse({
						items: [
							createDecisionDefinition({
								name: 'Invoice Classification',
								decisionDefinitionId: 'invoice',
								version: 1,
								tenantId: 'tenant-a',
							}),
							createDecisionDefinition({
								name: 'Invoice Classification',
								decisionDefinitionId: 'invoice',
								version: 1,
								tenantId: 'tenant-b',
							}),
						],
					}),
				),
			}),
		);

		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?decisionDefinitionId=invoice&decisionDefinitionVersion=1',
		});

		await expect
			.element(screen.getByText('Decision "Invoice Classification" exists in more than one Tenant'))
			.toBeVisible();
	});
});
