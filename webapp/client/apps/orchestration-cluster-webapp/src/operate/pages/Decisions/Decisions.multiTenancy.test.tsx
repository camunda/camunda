/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect} from 'vitest';
import {http, HttpResponse, type PathParams} from 'msw';
import {endpoints, type QueryDecisionDefinitionsRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockCurrentUserEndpoint, mockQueryDecisionDefinitionsEndpoint} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {
	createDecisionDefinition,
	createQueryDecisionDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/decision-definitions';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {DecisionsNavigationWithoutInstancesHarness, DecisionsWithoutInstancesHarness} from './DecisionsHarness';

const DECISION_DEFINITIONS = HttpResponse.json(
	createQueryDecisionDefinitionsResponse({
		items: [createDecisionDefinition({name: 'Invoice Approval', decisionDefinitionId: 'invoice-approval', version: 1})],
	}),
);

let unmountPage: (() => Promise<void>) | undefined;
let waitForRequests: (() => Promise<void>) | undefined;

async function renderDecisionsPage(
	searchParams?: Record<string, string>,
	Harness: React.ComponentType = DecisionsWithoutInstancesHarness,
) {
	const query = searchParams ? `?${new URLSearchParams(searchParams).toString()}` : '';
	const screen = await renderWithRouter(Harness, {
		path: '/operate/decisions',
		initialEntry: `/operate/decisions${query}`,
	});
	unmountPage = () => screen.unmount();
	waitForRequests = async () => {
		await expect.poll(() => screen.queryClient.isFetching()).toBe(0);
	};
	return screen;
}

const CURRENT_USER = HttpResponse.json(
	createCurrentUser({
		tenants: [
			{tenantId: '<default>', name: 'Default Tenant', description: null},
			{tenantId: '<tenant-A>', name: 'Tenant A', description: null},
		],
	}),
);

describe('Multi tenancy', () => {
	beforeEach(() => {
		sessionStorage.setItem(
			'clientConfig',
			JSON.stringify(createSystemConfiguration({deployment: {isMultiTenancyEnabled: true, maxRequestSize: 0}})),
		);
	});

	afterEach(async () => {
		await waitForRequests?.();
		await unmountPage?.();
		waitForRequests = undefined;
		unmountPage = undefined;
		sessionStorage.clear();
	});

	it('should hide the tenant filter when multi tenancy is not enabled', async ({worker}) => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
		worker.use(mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}));

		const screen = await renderDecisionsPage();

		await expect.element(screen.getByText('Instances States')).toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: 'Select a tenant'})).not.toBeInTheDocument();
	});

	it('should load the tenant value from the URL', async ({worker}) => {
		worker.use(
			mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
			mockCurrentUserEndpoint({successResponse: CURRENT_USER}),
		);

		const screen = await renderDecisionsPage({tenantId: '<tenant-A>'});

		await expect.element(screen.getByRole('combobox', {name: 'Select a tenant'})).toHaveTextContent('Tenant A');
	});

	it('should set the tenant to the URL on change', async ({worker}) => {
		worker.use(
			mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
			mockCurrentUserEndpoint({successResponse: CURRENT_USER}),
		);

		const screen = await renderDecisionsPage();
		const getSearch = () => screen.router.state.location.search as Record<string, unknown>;

		await screen.getByRole('combobox', {name: 'Select a tenant'}).click();
		await screen.getByRole('option', {name: 'All tenants'}).click();

		await expect.poll(() => getSearch().tenantId).toBe('all');
	});

	it('should clear the decision and version filters when the tenant changes', async ({worker}) => {
		worker.use(
			mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
			mockCurrentUserEndpoint({successResponse: CURRENT_USER}),
		);

		const screen = await renderDecisionsPage(
			{
				decisionDefinitionId: 'invoice-approval',
				decisionDefinitionVersion: '1',
				tenantId: '<default>',
			},
			DecisionsNavigationWithoutInstancesHarness,
		);
		const getSearch = () => screen.router.state.location.search as Record<string, unknown>;

		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toHaveValue('Invoice Approval');

		await screen.getByRole('combobox', {name: 'Select a tenant'}).click();
		await screen.getByRole('option', {name: 'Tenant A'}).click();

		await expect.poll(() => getSearch()).toEqual({tenantId: '<tenant-A>'});
	});

	it('should scope the decision-definitions request to the selected tenant', async ({worker}) => {
		let requestedFilter: unknown;
		worker.use(
			http.post<PathParams, QueryDecisionDefinitionsRequestBody>(
				endpoints.queryDecisionDefinitions.getUrl(),
				async ({request}) => {
					requestedFilter = (await request.json()).filter;
					return HttpResponse.json(createQueryDecisionDefinitionsResponse({items: []}));
				},
			),
			mockCurrentUserEndpoint({successResponse: CURRENT_USER}),
		);

		await renderDecisionsPage({tenantId: '<tenant-A>'});

		await expect.poll(() => requestedFilter).toEqual({tenantId: '<tenant-A>'});
	});

	it('should not scope the decision-definitions request when "all tenants" is selected', async ({worker}) => {
		let requestReceived = false;
		let requestedFilter: unknown;
		worker.use(
			http.post<PathParams, QueryDecisionDefinitionsRequestBody>(
				endpoints.queryDecisionDefinitions.getUrl(),
				async ({request}) => {
					requestedFilter = (await request.json()).filter;
					requestReceived = true;
					return HttpResponse.json(createQueryDecisionDefinitionsResponse({items: []}));
				},
			),
			mockCurrentUserEndpoint({successResponse: CURRENT_USER}),
		);

		await renderDecisionsPage({tenantId: 'all'});

		await expect.poll(() => requestReceived).toBe(true);
		expect(requestedFilter).toBeUndefined();
	});
});
