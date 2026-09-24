/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {afterEach, describe, expect} from 'vitest';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import {userEvent} from 'vitest/browser';
import {
	mockGetIncidentProcessInstanceStatisticsByDefinitionEndpoint,
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
	mockCurrentUserEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {
	createIncidentProcessInstanceStatisticsByDefinition,
	createIncidentProcessInstanceStatisticsByError,
} from '#/shared-test-modules/api-mocks/incident-statistics';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {IncidentsByError} from './IncidentsByError';

const REQUEST_SCHEMA = z.object({
	page: z.object({from: z.number(), limit: z.literal(50)}),
});
const FAILURE_RESPONSE = new HttpResponse(null, {status: 400});
const ERROR_RESPONSE = new HttpResponse(null, {status: 500});

const PAGE_1_RESPONSE = HttpResponse.json(
	createPaginatedResponse({
		items: [
			createIncidentProcessInstanceStatisticsByError({
				errorHashCode: 1,
				errorMessage: 'Alpha Connection Timeout',
				activeInstancesWithErrorCount: 5,
			}),
			createIncidentProcessInstanceStatisticsByError({
				errorHashCode: 2,
				errorMessage: 'Beta Null Pointer',
				activeInstancesWithErrorCount: 3,
			}),
			createIncidentProcessInstanceStatisticsByError({
				errorHashCode: 3,
				errorMessage: 'Gamma Service Unavailable',
				activeInstancesWithErrorCount: 2,
			}),
		],
		page: {totalItems: 60, startCursor: null, endCursor: null, hasMoreTotalItems: true},
	}),
);

const PAGE_2_RESPONSE = HttpResponse.json(
	createPaginatedResponse({
		items: [
			createIncidentProcessInstanceStatisticsByError({
				errorHashCode: 51,
				errorMessage: 'Page Two Only Error',
				activeInstancesWithErrorCount: 1,
			}),
		],
		page: {totalItems: 60, startCursor: null, endCursor: null, hasMoreTotalItems: false},
	}),
);

describe('<IncidentsByError />', () => {
	afterEach(() => {
		sessionStorage.clear();
	});

	it('should render the list of incidents by error', async ({worker}) => {
		worker.use(
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				schema: REQUEST_SCHEMA,
				successResponse: PAGE_1_RESPONSE,
				failureResponse: FAILURE_RESPONSE,
			}),
		);

		const screen = await renderWithRouter(() => <IncidentsByError />, {path: '/operate'});

		await expect.element(screen.getByText('Alpha Connection Timeout')).toBeVisible();
		await expect.element(screen.getByText('Beta Null Pointer')).toBeVisible();
		await expect.element(screen.getByText('Gamma Service Unavailable')).toBeVisible();
	});

	it('should fetch the next page when scrolled to the bottom', async ({worker}) => {
		worker.use(
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				schema: REQUEST_SCHEMA,
				successResponse: PAGE_1_RESPONSE,
				failureResponse: FAILURE_RESPONSE,
			}),
		);

		const screen = await renderWithRouter(
			() => (
				<div style={{height: '100px', display: 'flex', flexDirection: 'column'}}>
					<IncidentsByError />
				</div>
			),
			{path: '/operate'},
		);

		await expect.element(screen.getByText('Alpha Connection Timeout')).toBeVisible();

		worker.use(
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				schema: REQUEST_SCHEMA,
				successResponse: PAGE_2_RESPONSE,
				failureResponse: FAILURE_RESPONSE,
			}),
		);

		await userEvent.wheel(screen.getByTestId('incidents-by-error-list'), {delta: {y: 10000}});

		await expect.element(screen.getByText('Page Two Only Error')).toBeVisible();
	});

	it('should link each row to the processes page filtered by incidents', async ({worker}) => {
		worker.use(
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				schema: REQUEST_SCHEMA,
				successResponse: PAGE_1_RESPONSE,
				failureResponse: FAILURE_RESPONSE,
			}),
		);

		const screen = await renderWithRouter(() => <IncidentsByError />, {path: '/operate'});

		await expect.element(screen.getByText('Alpha Connection Timeout')).toBeVisible();
		await expect
			.element(screen.getByText('Alpha Connection Timeout').element().closest('a')!)
			.toHaveAttribute(
				'href',
				'/operate/processes?errorMessage=Alpha+Connection+Timeout&incidentErrorHashCode=1&incidents=true&active=false&completed=false&canceled=false&suspended=false',
			);
	});

	it('should distinguish similar incident messages by hash in drill-down links', async ({worker}) => {
		worker.use(
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({
						items: [
							createIncidentProcessInstanceStatisticsByError({
								errorMessage: 'Connection timeout',
								errorHashCode: -481,
							}),
							createIncidentProcessInstanceStatisticsByError({
								errorMessage: 'Connection timeout',
								errorHashCode: 0,
							}),
						],
						page: {totalItems: 2, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
		);

		const screen = await renderWithRouter(() => <IncidentsByError />, {path: '/operate'});

		await expect
			.element(screen.getByTestId('incident-byError').getByRole('link').nth(0))
			.toHaveAttribute('href', expect.stringContaining('errorMessage=Connection+timeout&incidentErrorHashCode=-481'));
		await expect
			.element(screen.getByTestId('incident-byError').getByRole('link').nth(1))
			.toHaveAttribute('href', expect.stringContaining('errorMessage=Connection+timeout&incidentErrorHashCode=0'));
	});

	it('should preserve incident hash, version and tenant for expanded process links', async ({worker}) => {
		sessionStorage.setItem(
			'clientConfig',
			JSON.stringify(
				createSystemConfiguration({
					deployment: {isMultiTenancyEnabled: true, isTenantsApiEnabled: true, maxRequestSize: 0},
				}),
			),
		);
		worker.use(
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({
						items: [
							createIncidentProcessInstanceStatisticsByError({errorMessage: 'Connection timeout', errorHashCode: -481}),
						],
						page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
			mockGetIncidentProcessInstanceStatisticsByDefinitionEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({
						items: [
							createIncidentProcessInstanceStatisticsByDefinition({
								processDefinitionId: 'orders',
								processDefinitionName: 'Orders',
								processDefinitionVersion: 2,
								tenantId: '<tenant-A>',
							}),
							createIncidentProcessInstanceStatisticsByDefinition({
								processDefinitionId: 'orders',
								processDefinitionName: 'Orders',
								processDefinitionVersion: 2,
								tenantId: '<tenant-B>',
							}),
						],
						page: {totalItems: 2, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(
					createCurrentUser({
						tenants: [
							{tenantId: '<tenant-A>', name: 'Tenant A', description: null},
							{tenantId: '<tenant-B>', name: 'Tenant B', description: null},
						],
					}),
				),
			}),
		);

		const screen = await renderWithRouter(() => <IncidentsByError />, {path: '/operate'});
		await expect.element(screen.getByRole('button', {name: 'Expand current row'})).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'Expand current row'}));

		for (const [name, tenant] of [
			['Tenant A', '<tenant-A>'],
			['Tenant B', '<tenant-B>'],
		] as const) {
			await expect
				.element(screen.getByRole('link', {name: new RegExp(name)}))
				.toHaveAttribute(
					'href',
					expect.stringContaining(
						`process=orders&version=2&errorMessage=Connection+timeout&incidentErrorHashCode=-481&tenantId=${encodeURIComponent(tenant)}`,
					),
				);
		}
	});

	it('should show an error state when the request fails', async ({worker}) => {
		worker.use(
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				successResponse: ERROR_RESPONSE,
			}),
		);

		const screen = await renderWithRouter(() => <IncidentsByError />, {path: '/operate'});

		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
	});
});
