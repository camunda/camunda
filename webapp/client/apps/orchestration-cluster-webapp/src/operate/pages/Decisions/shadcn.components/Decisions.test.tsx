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
	mockDeleteResourceEndpoint,
	mockGetDecisionDefinitionXmlEndpoint,
	mockQueryDecisionDefinitionsEndpoint,
	mockQueryDecisionInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createDecisionDefinition,
	createQueryDecisionDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/decision-definitions';
import {DMN_XML} from '#/shared-test-modules/api-mocks/decision-definition-xmls';
import {createQueryDecisionInstancesResponse} from '#/shared-test-modules/api-mocks/decision-instances';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {notificationsStore} from '#/shared/notifications/notifications.store';
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
	beforeEach(({worker}) => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
		worker.use(
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse({items: []})),
			}),
		);
	});

	afterEach(() => {
		sessionStorage.clear();
		notificationsStore.reset();
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
		await expect.element(screen.getByText('There is no decision selected')).toBeVisible();
		await expect.element(screen.getByText('Decision instances')).toBeVisible();
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
			mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}),
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

	it('should enable reset when a sort is applied in the URL', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?sort=evaluationDate%2Basc',
		});

		await expect.element(screen.getByRole('button', {name: 'Reset filters'})).toBeEnabled();
	});

	it('should render the decision filters with the version disabled until a decision is selected', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsWithSearch, {path: '/operate-preview/decisions'});

		await expect.element(screen.getByText('Instances states')).toBeVisible();
		await expect.element(screen.getByLabelText('Name')).toBeVisible();
		await expect.element(screen.getByLabelText('Version')).toBeDisabled();
	});

	it('should write the instance state checkboxes to the URL', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsWithSearch, {path: '/operate-preview/decisions'});

		await expect.element(screen.getByLabelText('Failed')).toBeVisible();
		await screen.getByLabelText('Failed').element().click();

		await expect.poll(() => screen.router.state.location.search).toMatchObject({failed: false});
	});

	it('should list the versions of the selected decision and enable the version select', async ({worker}) => {
		worker.use(
			mockInstances(),
			mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryDecisionDefinitionsResponse({
						items: [
							createDecisionDefinition({decisionDefinitionId: 'invoice', name: 'Invoice', version: 1}),
							createDecisionDefinition({decisionDefinitionId: 'invoice', name: 'Invoice', version: 2}),
						],
					}),
				),
			}),
		);
		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?decisionDefinitionId=invoice',
		});

		await expect.element(screen.getByLabelText('Version')).toBeEnabled();
		await screen.getByLabelText('Version').element().click();
		await screen.getByRole('option', {name: '2'}).element().click();

		await expect.poll(() => screen.router.state.location.search).toMatchObject({decisionDefinitionVersion: 2});
	});

	it('should remove a single optional filter from the URL when its remove button is clicked', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?businessId=eq_order-1&failed=false',
		});

		await expect.element(screen.getByRole('button', {name: 'Remove Business ID filter'})).toBeVisible();
		await screen.getByRole('button', {name: 'Remove Business ID filter'}).element().click();

		await expect.element(screen.getByLabelText('Business ID', {exact: true})).not.toBeInTheDocument();
		expect(screen.router.state.location.search).not.toHaveProperty('businessId');
		expect(screen.router.state.location.search).toMatchObject({failed: false});
	});

	it('should vertically center the remove button on the filter input', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?businessId=eq_order-1',
		});

		await expect.element(screen.getByRole('button', {name: 'Remove Business ID filter'})).toBeVisible();
		const input = screen.getByLabelText('Business ID', {exact: true}).element().getBoundingClientRect();
		const button = screen.getByRole('button', {name: 'Remove Business ID filter'}).element().getBoundingClientRect();

		expect(input.top + input.height / 2 - (button.top + button.height / 2)).toBe(0);
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
			mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}),
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
			.element(screen.getByText('There is more than one version selected for decision "Invoice Classification"'))
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
			.element(screen.getByText('Decision "Invoice Classification" exists in more than one tenant'))
			.toBeVisible();
	});

	it('should show the delete action only when a single decision version is selected', async ({worker}) => {
		worker.use(
			mockInstances(),
			mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}),
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

		await expect.element(screen.getByRole('button', {name: /Delete decision definition/})).toBeVisible();

		await screen.router.navigate({to: '.', search: {decisionDefinitionId: 'invoice'}});

		await expect.element(screen.getByRole('button', {name: /Delete decision definition/})).not.toBeInTheDocument();
	});

	it('should keep a newly selected version blocked until the previous deletion finishes', async ({worker}) => {
		worker.use(
			mockInstances(),
			mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}),
			mockDeleteResourceEndpoint({successResponse: HttpResponse.json({}), delay: 500}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryDecisionDefinitionsResponse({
						items: [
							createDecisionDefinition({
								name: 'Invoice Classification',
								decisionDefinitionId: 'invoice',
								decisionDefinitionKey: 'key-1',
								version: 1,
							}),
							createDecisionDefinition({
								name: 'Invoice Classification',
								decisionDefinitionId: 'invoice',
								decisionDefinitionKey: 'key-2',
								version: 2,
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

		await userEvent.click(screen.getByRole('button', {name: /Delete decision definition/}));
		await userEvent.click(screen.getByText(/Yes, I confirm I want to delete this DRD/));
		await userEvent.click(screen.getByRole('button', {name: 'Delete', exact: true}));
		await screen.router.navigate({to: '.', search: {decisionDefinitionId: 'invoice', decisionDefinitionVersion: 2}});

		await expect.element(screen.getByRole('button', {name: /Delete decision definition/})).toBeDisabled();
		await expect
			.poll(() => notificationsStore.notifications.some(({title}) => title === 'Operation created'))
			.toBe(true);
		await expect.element(screen.getByRole('button', {name: /Delete decision definition/})).toBeEnabled();
		expect(screen.router.state.location.search).toMatchObject({
			decisionDefinitionId: 'invoice',
			decisionDefinitionVersion: 2,
		});
	});
});
