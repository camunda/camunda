/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect} from 'vitest';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockCurrentUserEndpoint,
	mockGetDecisionDefinitionXmlEndpoint,
	mockQueryDecisionDefinitionsEndpoint,
	mockQueryDecisionInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {
	createDecisionDefinition,
	createQueryDecisionDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/decision-definitions';
import {DMN_XML} from '#/shared-test-modules/api-mocks/decision-definition-xmls';
import {createQueryDecisionInstancesResponse} from '#/shared-test-modules/api-mocks/decision-instances';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {DecisionsHarness} from './DecisionsHarness';

const DECISION_DEFINITIONS = HttpResponse.json(
	createQueryDecisionDefinitionsResponse({
		items: [createDecisionDefinition({name: 'Invoice Approval', decisionDefinitionId: 'invoice-approval', version: 1})],
	}),
);

const EMPTY_DECISION_INSTANCES = HttpResponse.json(createQueryDecisionInstancesResponse());

const TENANT_A_SCOPED_REQUEST_SCHEMA = z.strictObject({
	page: z.strictObject({limit: z.literal(1000)}),
	filter: z.strictObject({tenantId: z.literal('<tenant-A>')}),
});
const UNSCOPED_REQUEST_SCHEMA = z.strictObject({
	page: z.strictObject({limit: z.literal(1000)}),
	filter: z.never().optional(),
});
const FAILURE_RESPONSE = new HttpResponse(null, {status: 400});

function renderDecisionsPage(searchParams?: Record<string, string>) {
	const query = `?${new URLSearchParams({evaluated: 'true', failed: 'true', ...searchParams}).toString()}`;
	return renderWithRouter(DecisionsHarness, {
		path: '/operate/decisions',
		initialEntry: `/operate/decisions${query}`,
	});
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

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should hide the tenant filter when multi tenancy is not enabled', async ({worker}) => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
		worker.use(
			mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
			mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
		);

		const screen = await renderDecisionsPage();

		await expect.element(screen.getByText('Instances States')).toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: 'Select a tenant'})).not.toBeInTheDocument();
	});

	it('should load the tenant value from the URL', async ({worker}) => {
		worker.use(
			mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
			mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
			mockCurrentUserEndpoint({successResponse: CURRENT_USER}),
		);

		const screen = await renderDecisionsPage({tenantId: '<tenant-A>'});

		await expect.element(screen.getByRole('combobox', {name: 'Select a tenant'})).toMatchTextContent('Tenant A');
	});

	it('should set the tenant to the URL on change', async ({worker}) => {
		worker.use(
			mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
			mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
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
			mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
			mockCurrentUserEndpoint({successResponse: CURRENT_USER}),
			mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}),
		);

		const screen = await renderDecisionsPage({
			decisionDefinitionId: 'invoice-approval',
			decisionDefinitionVersion: '1',
			tenantId: '<default>',
		});
		const getSearch = () => screen.router.state.location.search as Record<string, unknown>;

		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toHaveValue('Invoice Approval');

		await screen.getByRole('combobox', {name: 'Select a tenant'}).click();
		await screen.getByRole('option', {name: 'Tenant A'}).click();

		await expect
			.poll(() => getSearch())
			.toMatchObject({
				tenantId: '<tenant-A>',
				evaluated: true,
				failed: true,
			});
	});

	it('should scope the decision-definitions request to the selected tenant', async ({worker}) => {
		worker.use(
			mockQueryDecisionDefinitionsEndpoint({
				schema: TENANT_A_SCOPED_REQUEST_SCHEMA,
				successResponse: DECISION_DEFINITIONS,
				failureResponse: FAILURE_RESPONSE,
			}),
			mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
			mockCurrentUserEndpoint({successResponse: CURRENT_USER}),
		);

		const screen = await renderDecisionsPage({tenantId: '<tenant-A>'});

		await screen.getByRole('combobox', {name: 'Name'}).click();
		await expect.element(screen.getByRole('option', {name: 'Invoice Approval'})).toBeVisible();
	});

	it('should not scope the decision-definitions request when "all tenants" is selected', async ({worker}) => {
		worker.use(
			mockQueryDecisionDefinitionsEndpoint({
				schema: UNSCOPED_REQUEST_SCHEMA,
				successResponse: DECISION_DEFINITIONS,
				failureResponse: FAILURE_RESPONSE,
			}),
			mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
			mockCurrentUserEndpoint({successResponse: CURRENT_USER}),
		);

		const screen = await renderDecisionsPage({tenantId: 'all'});

		await screen.getByRole('combobox', {name: 'Name'}).click();
		await expect.element(screen.getByRole('option', {name: 'Invoice Approval'})).toBeVisible();
	});
});
