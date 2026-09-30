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
import {z} from 'zod';
import {userEvent} from 'vitest/browser';
import {setUpFakeIntersectionObserver} from '#/vitest-modules/fake-intersection-observer';
import {
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockGetProcessDefinitionInstanceVersionStatisticsEndpoint,
	mockQueryProcessDefinitionsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createProcessDefinitionInstanceStatistics,
	createProcessDefinitionInstanceVersionStatistics,
} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {InstancesByProcess} from './InstancesByProcess';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';

const REQUEST_SCHEMA = z.object({
	sort: z.array(
		z.object({
			field: z.enum(['activeInstancesWithIncidentCount', 'activeInstancesWithoutIncidentCount']),
			order: z.literal('desc'),
		}),
	),
	page: z.object({
		limit: z.literal(50),
		from: z.number().optional(),
	}),
});
const FAILURE_RESPONSE = new HttpResponse(null, {status: 400});
const ERROR_RESPONSE = new HttpResponse(null, {status: 500});
const NO_DRAINING_RESPONSE = HttpResponse.json(createQueryProcessDefinitionsResponse());
const ALPHA_PROCESS_LINK_NAME = '1 Alpha Process – 6 instances in 1 version 5';
const BETA_PROCESS_LINK_NAME = '0 Beta Process – 3 instances in 1 version 3';

const PAGE_1_RESPONSE = HttpResponse.json(
	createPaginatedResponse({
		items: [
			createProcessDefinitionInstanceStatistics({
				processDefinitionId: 'p1',
				latestProcessDefinitionName: 'Alpha Process',
				activeInstancesWithoutIncidentCount: 5,
				activeInstancesWithIncidentCount: 1,
			}),
			createProcessDefinitionInstanceStatistics({
				processDefinitionId: 'p2',
				latestProcessDefinitionName: 'Beta Process',
				activeInstancesWithoutIncidentCount: 3,
			}),
		],
		page: {totalItems: 2, startCursor: null, endCursor: null, hasMoreTotalItems: false},
	}),
);

describe('<InstancesByProcess />', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should render the list of instances by process', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				schema: REQUEST_SCHEMA,
				successResponse: PAGE_1_RESPONSE,
				failureResponse: FAILURE_RESPONSE,
			}),
			mockQueryProcessDefinitionsEndpoint({successResponse: NO_DRAINING_RESPONSE}),
		);

		const screen = await renderWithRouter(() => <InstancesByProcess />, {path: '/operate-preview'});

		await expect.element(screen.getByRole('link', {name: ALPHA_PROCESS_LINK_NAME})).toBeVisible();
		await expect.element(screen.getByRole('link', {name: BETA_PROCESS_LINK_NAME})).toBeVisible();
	});

	it('should link each row to the processes page filtered by process', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				schema: REQUEST_SCHEMA,
				successResponse: PAGE_1_RESPONSE,
				failureResponse: FAILURE_RESPONSE,
			}),
			mockQueryProcessDefinitionsEndpoint({successResponse: NO_DRAINING_RESPONSE}),
		);

		const screen = await renderWithRouter(() => <InstancesByProcess />, {path: '/operate-preview'});

		await expect
			.element(screen.getByRole('link', {name: ALPHA_PROCESS_LINK_NAME}))
			.toHaveAttribute(
				'href',
				'/operate/processes?process=p1&active=true&incidents=true&completed=false&canceled=false&suspended=false',
			);
	});

	it('should show an error state when the request fails', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				successResponse: ERROR_RESPONSE,
			}),
			mockQueryProcessDefinitionsEndpoint({successResponse: NO_DRAINING_RESPONSE}),
		);

		const screen = await renderWithRouter(() => <InstancesByProcess />, {path: '/operate-preview'});

		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
	});

	it('should show a draining indicator for process definitions scheduled for deletion', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				schema: REQUEST_SCHEMA,
				successResponse: PAGE_1_RESPONSE,
				failureResponse: FAILURE_RESPONSE,
			}),
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessDefinitionsResponse({
						items: [createProcessDefinition({processDefinitionId: 'p2', state: 'DRAINING'})],
					}),
				),
			}),
		);

		const screen = await renderWithRouter(() => <InstancesByProcess />, {path: '/operate-preview'});

		await expect.element(screen.getByRole('link', {name: BETA_PROCESS_LINK_NAME})).toBeVisible();

		const betaRow = screen.getByRole('link', {name: BETA_PROCESS_LINK_NAME}).element() as HTMLElement;
		await expect.element(betaRow.querySelector('[data-testid="draining-indicator"]') as HTMLElement).toBeVisible();
		expect(betaRow.querySelector('[data-testid="draining-indicator"]')?.hasAttribute('tabindex')).toBe(false);

		const alphaRow = screen.getByRole('link', {name: ALPHA_PROCESS_LINK_NAME}).element() as HTMLElement;
		await expect
			.element(alphaRow.querySelector('[data-testid="draining-indicator"]') as HTMLElement | null)
			.not.toBeInTheDocument();
	});

	it('should show a draining indicator for a specific draining version when expanded', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				schema: REQUEST_SCHEMA,
				successResponse: HttpResponse.json(
					createPaginatedResponse({
						items: [
							createProcessDefinitionInstanceStatistics({
								processDefinitionId: 'p1',
								latestProcessDefinitionName: 'Alpha Process',
								hasMultipleVersions: true,
								activeInstancesWithoutIncidentCount: 4,
							}),
						],
						page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
				failureResponse: FAILURE_RESPONSE,
			}),
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessDefinitionsResponse({
						items: [
							createProcessDefinition({processDefinitionId: 'p1', processDefinitionKey: 'v2', state: 'DRAINING'}),
						],
					}),
				),
			}),
			mockGetProcessDefinitionInstanceVersionStatisticsEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({
						items: [
							createProcessDefinitionInstanceVersionStatistics({
								processDefinitionId: 'p1',
								processDefinitionKey: 'v2',
								processDefinitionVersion: 2,
								activeInstancesWithoutIncidentCount: 3,
							}),
							createProcessDefinitionInstanceVersionStatistics({
								processDefinitionId: 'p1',
								processDefinitionKey: 'v1',
								processDefinitionVersion: 1,
								activeInstancesWithoutIncidentCount: 1,
							}),
						],
						page: {totalItems: 2, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
		);

		const screen = await renderWithRouter(() => <InstancesByProcess />, {path: '/operate-preview'});

		await expect.element(screen.getByText(/Alpha Process/)).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'Expand row'}));

		await expect.element(screen.getByText(/version 2/)).toBeVisible();

		const version2Row = screen
			.getByText(/version 2/)
			.element()
			.closest('a') as HTMLElement;
		await expect.element(version2Row.querySelector('[data-testid="draining-indicator"]') as HTMLElement).toBeVisible();

		const version1Row = screen
			.getByText(/version 1/)
			.element()
			.closest('a') as HTMLElement;
		await expect
			.element(version1Row.querySelector('[data-testid="draining-indicator"]') as HTMLElement | null)
			.not.toBeInTheDocument();
	});
});

describe('<InstancesByProcess /> pagination', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	const {getObserver} = setUpFakeIntersectionObserver();

	const buildPage = (processDefinitionId: string, name: string) =>
		HttpResponse.json(
			createPaginatedResponse({
				items: [
					createProcessDefinitionInstanceStatistics({
						processDefinitionId,
						latestProcessDefinitionName: name,
						activeInstancesWithoutIncidentCount: 2,
					}),
				],
				page: {totalItems: 2, startCursor: null, endCursor: null, hasMoreTotalItems: false},
			}),
		);

	it('should load the next page when the bottom of the list becomes visible', async ({worker}) => {
		// given
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				once: true,
				successResponse: buildPage('p1', 'Alpha Process'),
			}),
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				schema: z.object({page: z.object({from: z.literal(1), limit: z.literal(50)})}),
				successResponse: buildPage('p2', 'Beta Process'),
				failureResponse: FAILURE_RESPONSE,
			}),
			mockQueryProcessDefinitionsEndpoint({successResponse: NO_DRAINING_RESPONSE}),
		);
		const screen = await renderWithRouter(() => <InstancesByProcess />, {path: '/operate-preview'});
		await expect.element(screen.getByRole('link', {name: /Alpha Process/})).toBeVisible();

		// when
		getObserver().intersect(screen.getByTestId('instances-by-process-list-bottom-sentinel').element());

		// then
		await expect.element(screen.getByRole('link', {name: /Beta Process/})).toBeVisible();
		await expect.element(screen.getByRole('link', {name: /Alpha Process/})).toBeVisible();
	});

	it('should not render pagination sentinels when everything fits on one page', async ({worker}) => {
		// given
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				schema: REQUEST_SCHEMA,
				successResponse: PAGE_1_RESPONSE,
				failureResponse: FAILURE_RESPONSE,
			}),
			mockQueryProcessDefinitionsEndpoint({successResponse: NO_DRAINING_RESPONSE}),
		);

		// when
		const screen = await renderWithRouter(() => <InstancesByProcess />, {path: '/operate-preview'});

		// then
		await expect.element(screen.getByRole('link', {name: ALPHA_PROCESS_LINK_NAME})).toBeVisible();
		expect(screen.getByTestId('instances-by-process-list-bottom-sentinel').elements()).toHaveLength(0);
		expect(screen.getByTestId('instances-by-process-list-top-sentinel').elements()).toHaveLength(0);
	});
});
