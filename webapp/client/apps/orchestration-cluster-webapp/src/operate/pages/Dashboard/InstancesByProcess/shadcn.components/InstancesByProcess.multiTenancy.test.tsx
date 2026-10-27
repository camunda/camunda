/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {afterEach, beforeEach, describe, expect} from 'vitest';
import {HttpResponse} from 'msw';
import {userEvent} from 'vitest/browser';
import {
	mockCurrentUserEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockGetProcessDefinitionInstanceVersionStatisticsEndpoint,
	mockQueryProcessDefinitionsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {
	createProcessDefinitionInstanceStatistics,
	createProcessDefinitionInstanceVersionStatistics,
} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {createQueryProcessDefinitionsResponse} from '#/shared-test-modules/api-mocks/process-definitions';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {InstancesByProcess} from './InstancesByProcess';

const NO_DRAINING_RESPONSE = HttpResponse.json(createQueryProcessDefinitionsResponse());

describe('<InstancesByProcess /> multi tenancy', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should keep tenant-aware links for duplicate process ids and default tenant', async ({worker}) => {
		sessionStorage.setItem(
			'clientConfig',
			JSON.stringify(
				createSystemConfiguration({
					deployment: {isMultiTenancyEnabled: true, isTenantsApiEnabled: true, maxRequestSize: 0},
				}),
			),
		);

		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({
						items: [
							createProcessDefinitionInstanceStatistics({
								processDefinitionId: 'orders',
								latestProcessDefinitionName: 'Orders',
								tenantId: '<tenant-A>',
								activeInstancesWithoutIncidentCount: 2,
							}),
							createProcessDefinitionInstanceStatistics({
								processDefinitionId: 'orders',
								latestProcessDefinitionName: 'Orders',
								tenantId: '<tenant-B>',
								activeInstancesWithoutIncidentCount: 3,
							}),
							createProcessDefinitionInstanceStatistics({
								processDefinitionId: 'orders',
								latestProcessDefinitionName: 'Orders',
								tenantId: '<default>',
								activeInstancesWithoutIncidentCount: 1,
							}),
						],
						page: {totalItems: 3, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
			mockQueryProcessDefinitionsEndpoint({successResponse: NO_DRAINING_RESPONSE}),
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(
					createCurrentUser({
						tenants: [
							{tenantId: '<tenant-A>', name: 'Tenant A', description: null},
							{tenantId: '<tenant-B>', name: 'Tenant B', description: null},
							{tenantId: '<default>', name: 'Default Tenant', description: null},
						],
					}),
				),
			}),
		);

		const screen = await renderWithRouter(() => <InstancesByProcess />, {path: '/operate-preview'});

		await expect
			.element(screen.getByRole('link', {name: /Tenant A/}))
			.toHaveAttribute('href', expect.stringContaining('process=orders&tenantId=%3Ctenant-A%3E'));
		await expect
			.element(screen.getByRole('link', {name: /Tenant B/}))
			.toHaveAttribute('href', expect.stringContaining('process=orders&tenantId=%3Ctenant-B%3E'));
		await expect
			.element(screen.getByRole('link', {name: /Default Tenant/}))
			.toHaveAttribute('href', expect.stringContaining('process=orders&tenantId=%3Cdefault%3E'));
	});

	it('should use tenant-aware labels and version drill-down links when multitenancy is enabled', async ({worker}) => {
		sessionStorage.setItem(
			'clientConfig',
			JSON.stringify(
				createSystemConfiguration({
					deployment: {isMultiTenancyEnabled: true, isTenantsApiEnabled: true, maxRequestSize: 0},
				}),
			),
		);

		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({
						items: [
							createProcessDefinitionInstanceStatistics({
								processDefinitionId: 'orders',
								latestProcessDefinitionName: 'Orders',
								tenantId: '<tenant-A>',
								hasMultipleVersions: true,
								activeInstancesWithoutIncidentCount: 2,
							}),
							createProcessDefinitionInstanceStatistics({
								processDefinitionId: 'invoices',
								latestProcessDefinitionName: 'Invoices',
								tenantId: '<tenant-A>',
								activeInstancesWithoutIncidentCount: 1,
							}),
						],
						page: {totalItems: 2, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
			mockGetProcessDefinitionInstanceVersionStatisticsEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({
						items: [
							createProcessDefinitionInstanceVersionStatistics({
								processDefinitionId: 'orders',
								processDefinitionName: 'Orders',
								processDefinitionVersion: 2,
								tenantId: '<tenant-A>',
								activeInstancesWithoutIncidentCount: 2,
							}),
						],
						page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
			mockQueryProcessDefinitionsEndpoint({successResponse: NO_DRAINING_RESPONSE}),
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(
					createCurrentUser({
						tenants: [{tenantId: '<tenant-A>', name: 'Tenant A', description: null}],
					}),
				),
			}),
		);

		const screen = await renderWithRouter(() => <InstancesByProcess />, {path: '/operate-preview'});

		await expect
			.element(screen.getByTitle('Invoices – 1 instance in 1 version – Tenant A'))
			.toHaveAttribute('href', expect.stringContaining('tenantId=%3Ctenant-A%3E'));
		await expect.element(screen.getByTitle('Orders – 2 instances in 2+ versions – Tenant A')).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'Expand row'}));
		await expect
			.element(screen.getByTitle('Orders – 2 instances in version 2 – Tenant A'))
			.toHaveAttribute('href', expect.stringContaining('version=2&tenantId=%3Ctenant-A%3E'));
	});

	it('should keep single-tenant labels and links unchanged', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({
						items: [
							createProcessDefinitionInstanceStatistics({
								processDefinitionId: 'orders',
								latestProcessDefinitionName: 'Orders',
								activeInstancesWithoutIncidentCount: 2,
							}),
						],
						page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
			mockQueryProcessDefinitionsEndpoint({successResponse: NO_DRAINING_RESPONSE}),
		);

		const screen = await renderWithRouter(() => <InstancesByProcess />, {path: '/operate-preview'});

		await expect.element(screen.getByRole('link', {name: '0 Orders – 2 instances in 1 version 2'})).toBeVisible();
		await expect
			.element(screen.getByRole('link', {name: '0 Orders – 2 instances in 1 version 2'}))
			.toHaveAttribute(
				'href',
				'/operate/processes?process=orders&active=true&incidents=true&completed=false&canceled=false&suspended=false',
			);
	});
});
