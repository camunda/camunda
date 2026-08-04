/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {afterEach, beforeEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {mockCurrentUserEndpoint} from '#/shared-test-modules/mock-handlers';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {ProcessesFilters} from './ProcessesFilters';

const TENANTS = [
	{tenantId: '<default>', name: 'Default', description: null},
	{tenantId: 'tenant-a', name: 'Tenant A', description: null},
];

describe('<ProcessesFilters />', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should set initial filters', async ({worker}) => {
		worker.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser({tenants: TENANTS}))}));

		const screen = await renderWithRouter(
			() => <ProcessesFilters initialFilterValues={{search: 'invoice', hasStartForm: 'yes'}} />,
			{path: '/tasklist/processes'},
		);

		await expect.element(screen.getByRole('searchbox', {name: 'Search processes'})).toHaveValue('invoice');
		await expect
			.element(screen.getByRole('combobox', {name: 'Filter processes'}))
			.toHaveTextContent('Requires form input to start');
	});

	it('should write the search filter to the URL and preserve other filters', async ({worker}) => {
		worker.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser({tenants: TENANTS}))}));
		const {router, ...screen} = await renderWithRouter(
			() => <ProcessesFilters initialFilterValues={{hasStartForm: 'no', tenantId: 'tenant-a'}} />,
			{
				path: '/tasklist/processes',
			},
		);

		await userEvent.fill(screen.getByRole('searchbox', {name: 'Search processes'}), 'invoice');

		await expect
			.poll(() => router.state.location.search)
			.toEqual({
				search: 'invoice',
				hasStartForm: 'no',
				tenantId: 'tenant-a',
			});
	});

	it('should update the start form filter', async ({worker}) => {
		worker.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser({tenants: TENANTS}))}));
		const {router, ...screen} = await renderWithRouter(() => <ProcessesFilters initialFilterValues={{}} />, {
			path: '/tasklist/processes',
		});

		await userEvent.click(screen.getByRole('combobox', {name: 'Filter processes'}));
		await userEvent.click(screen.getByRole('option', {name: 'Requires form input to start'}));

		await expect.poll(() => router.state.location.search).toEqual({hasStartForm: 'yes'});
	});

	it('should remove the start form filter when all processes is selected', async ({worker}) => {
		worker.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser({tenants: TENANTS}))}));
		const {router, ...screen} = await renderWithRouter(
			() => <ProcessesFilters initialFilterValues={{hasStartForm: 'yes'}} />,
			{
				path: '/tasklist/processes',
				initialEntry: '/tasklist/processes?hasStartForm=yes',
			},
		);

		await userEvent.click(screen.getByRole('combobox', {name: 'Filter processes'}));
		await userEvent.click(screen.getByRole('option', {name: 'All Processes'}));

		await expect.poll(() => router.state.location.search).toEqual({});
	});

	it('should show tenants and update the tenant filter when multi-tenancy is enabled', async ({worker}) => {
		sessionStorage.setItem(
			'clientConfig',
			JSON.stringify(createSystemConfiguration({deployment: {isMultiTenancyEnabled: true, maxRequestSize: 0}})),
		);
		worker.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser({tenants: TENANTS}))}));
		const {router, ...screen} = await renderWithRouter(() => <ProcessesFilters initialFilterValues={{}} />, {
			path: '/tasklist/processes',
		});

		const tenantFilter = screen.getByRole('combobox', {name: 'Tenant'});
		await expect.element(tenantFilter).toHaveTextContent('Default - <default>');

		await userEvent.click(tenantFilter);
		await userEvent.click(screen.getByRole('option', {name: 'Tenant A - tenant-a'}));

		await expect.poll(() => router.state.location.search).toEqual({tenantId: 'tenant-a'});
	});

	it('should hide the tenant filter when multi-tenancy is disabled', async ({worker}) => {
		worker.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser({tenants: TENANTS}))}));

		const screen = await renderWithRouter(() => <ProcessesFilters initialFilterValues={{}} />, {
			path: '/tasklist/processes',
		});

		await expect.element(screen.getByRole('combobox', {name: 'Tenant'})).not.toBeInTheDocument();
	});
});
