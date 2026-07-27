/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect} from 'vitest';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockCurrentUserEndpoint, mockQueryProcessDefinitionsEndpoint} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {ProcessesHarness} from './ProcessesHarness';

const PROCESS_DEFINITIONS = HttpResponse.json(
	createQueryProcessDefinitionsResponse({
		items: [
			createProcessDefinition({name: 'Order Process', processDefinitionId: 'order-process', version: 2}),
			createProcessDefinition({name: 'Order Process', processDefinitionId: 'order-process', version: 1}),
		],
	}),
);

function renderProcessesPage(searchParams?: Record<string, string>) {
	const query = searchParams ? `?${new URLSearchParams(searchParams).toString()}` : '';
	return renderWithRouter(ProcessesHarness, {
		path: '/operate/processes',
		initialEntry: `/operate/processes${query}`,
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
			JSON.stringify(
				createSystemConfiguration({
					deployment: {isMultiTenancyEnabled: true, maxRequestSize: 0},
				}),
			),
		);
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should hide the tenant filter when multi tenancy is not enabled', async ({worker}) => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
		worker.use(mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}));

		const screen = await renderProcessesPage();

		await expect.element(screen.getByRole('button', {name: 'More Filters'})).toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: 'Select a tenant'})).not.toBeInTheDocument();
	});

	it('should load the tenant value from the URL', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockCurrentUserEndpoint({successResponse: CURRENT_USER}),
		);

		const screen = await renderProcessesPage({tenantId: '<tenant-A>'});

		await expect.element(screen.getByRole('combobox', {name: 'Select a tenant'})).toHaveTextContent('Tenant A');
	});

	it('should set the tenant to the URL on change', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockCurrentUserEndpoint({successResponse: CURRENT_USER}),
		);

		const screen = await renderProcessesPage();
		const getSearch = () => screen.router.state.location.search as Record<string, unknown>;

		await screen.getByRole('combobox', {name: 'Select a tenant'}).click();
		await screen.getByRole('option', {name: 'All tenants'}).click();

		await expect.poll(() => getSearch().tenantId).toBe('all');
	});

	it('should clear the process and version filters when the tenant changes', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockCurrentUserEndpoint({successResponse: CURRENT_USER}),
		);

		const screen = await renderProcessesPage({
			process: 'order-process',
			version: '2',
			tenantId: '<default>',
		});
		const getSearch = () => screen.router.state.location.search as Record<string, unknown>;

		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toHaveValue('Order Process');

		await screen.getByRole('combobox', {name: 'Select a tenant'}).click();
		await screen.getByRole('option', {name: 'Tenant A'}).click();

		await expect.poll(() => getSearch()).toEqual({tenantId: '<tenant-A>'});
	});
});
