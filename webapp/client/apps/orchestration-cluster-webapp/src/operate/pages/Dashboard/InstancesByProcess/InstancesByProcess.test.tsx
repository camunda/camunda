/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {describe, expect} from 'vitest';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import {userEvent} from 'vitest/browser';
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

const REQUEST_SCHEMA = z.object({
	sort: z.array(
		z.object({
			field: z.enum(['activeInstancesWithIncidentCount', 'activeInstancesWithoutIncidentCount']),
			order: z.literal('desc'),
		}),
	),
	page: z.object({from: z.number(), limit: z.literal(50)}),
});
const FAILURE_RESPONSE = new HttpResponse(null, {status: 400});
const ERROR_RESPONSE = new HttpResponse(null, {status: 500});
const NO_DRAINING_RESPONSE = HttpResponse.json(createQueryProcessDefinitionsResponse());
const ALPHA_PROCESS_LINK_NAME = '1 Alpha Process – 6 Instances in 1 Version 5';
const BETA_PROCESS_LINK_NAME = '0 Beta Process – 3 Instances in 1 Version 3';
const GAMMA_PROCESS_LINK_NAME = '1 Gamma Process – 3 Instances in 1 Version 2';

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
			createProcessDefinitionInstanceStatistics({
				processDefinitionId: 'p3',
				latestProcessDefinitionName: 'Gamma Process',
				activeInstancesWithoutIncidentCount: 2,
				activeInstancesWithIncidentCount: 1,
			}),
		],
		page: {totalItems: 60, startCursor: null, endCursor: null, hasMoreTotalItems: true},
	}),
);

const PAGE_2_RESPONSE = HttpResponse.json(
	createPaginatedResponse({
		items: [
			createProcessDefinitionInstanceStatistics({
				processDefinitionId: 'p51',
				latestProcessDefinitionName: 'Page Two Process',
				activeInstancesWithoutIncidentCount: 1,
			}),
		],
		page: {totalItems: 60, startCursor: null, endCursor: null, hasMoreTotalItems: false},
	}),
);

describe('<InstancesByProcess />', () => {
	it('should render the list of instances by process', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				schema: REQUEST_SCHEMA,
				successResponse: PAGE_1_RESPONSE,
				failureResponse: FAILURE_RESPONSE,
			}),
			mockQueryProcessDefinitionsEndpoint({successResponse: NO_DRAINING_RESPONSE}),
		);

		const screen = await renderWithRouter(() => <InstancesByProcess />, {path: '/operate'});

		await expect.element(screen.getByRole('link', {name: ALPHA_PROCESS_LINK_NAME})).toBeVisible();
		await expect.element(screen.getByRole('link', {name: BETA_PROCESS_LINK_NAME})).toBeVisible();
		await expect.element(screen.getByRole('link', {name: GAMMA_PROCESS_LINK_NAME})).toBeVisible();
	});

	it('should fetch the next page when scrolled to the bottom', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				schema: REQUEST_SCHEMA,
				successResponse: PAGE_1_RESPONSE,
				failureResponse: FAILURE_RESPONSE,
			}),
			mockQueryProcessDefinitionsEndpoint({successResponse: NO_DRAINING_RESPONSE}),
		);

		const screen = await renderWithRouter(
			() => (
				<div style={{height: '100px', display: 'flex', flexDirection: 'column'}}>
					<InstancesByProcess />
				</div>
			),
			{path: '/operate'},
		);

		await expect.element(screen.getByRole('link', {name: ALPHA_PROCESS_LINK_NAME})).toBeVisible();

		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				schema: REQUEST_SCHEMA,
				successResponse: PAGE_2_RESPONSE,
				failureResponse: FAILURE_RESPONSE,
			}),
		);

		await userEvent.wheel(screen.getByTestId('instances-by-process-list'), {delta: {y: 10000}});

		await expect
			.element(screen.getByRole('link', {name: '0 Page Two Process – 1 Instance in 1 Version 1'}))
			.toBeVisible();
	});

	it('should link each row to the processes page filtered by process', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				schema: REQUEST_SCHEMA,
				successResponse: HttpResponse.json(
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
								activeInstancesWithoutIncidentCount: 0,
								activeInstancesWithIncidentCount: 0,
							}),
						],
						page: {totalItems: 2, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
				failureResponse: FAILURE_RESPONSE,
			}),
			mockQueryProcessDefinitionsEndpoint({successResponse: NO_DRAINING_RESPONSE}),
		);

		const screen = await renderWithRouter(() => <InstancesByProcess />, {path: '/operate'});

		const alphaProcessLink = screen.getByRole('link', {name: ALPHA_PROCESS_LINK_NAME});
		const betaProcessLink = screen.getByRole('link', {name: '0 Beta Process – 0 Instances in 1 Version 0'});

		await expect.element(alphaProcessLink).toBeVisible();
		await expect
			.element(alphaProcessLink)
			.toHaveAttribute(
				'href',
				'/operate/processes?process=p1&active=true&incidents=true&completed=false&canceled=false&suspended=false',
			);
		await expect
			.element(betaProcessLink)
			.toHaveAttribute(
				'href',
				'/operate/processes?process=p2&active=true&incidents=true&completed=true&canceled=true&suspended=false',
			);
	});

	it('should show an error state when the request fails', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				successResponse: ERROR_RESPONSE,
			}),
			mockQueryProcessDefinitionsEndpoint({successResponse: NO_DRAINING_RESPONSE}),
		);

		const screen = await renderWithRouter(() => <InstancesByProcess />, {path: '/operate'});

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

		const screen = await renderWithRouter(() => <InstancesByProcess />, {path: '/operate'});

		const betaProcessLink = screen.getByTitle('Beta Process – 3 Instances in 1 Version');
		const alphaProcessLink = screen.getByTitle('Alpha Process – 6 Instances in 1 Version');

		await expect.element(betaProcessLink).toBeVisible();
		await expect.element(alphaProcessLink).toBeVisible();
		await expect.element(betaProcessLink.getByTestId('draining-indicator')).toBeVisible();
		await expect.element(alphaProcessLink.getByTestId('draining-indicator')).not.toBeInTheDocument();
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

		const screen = await renderWithRouter(() => <InstancesByProcess />, {path: '/operate'});

		await expect.element(screen.getByTitle('Alpha Process – 4 Instances in 2+ Versions')).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'Expand current row'}));

		const version2Link = screen.getByTitle('My Process – 3 Instances in Version 2');
		const version1Link = screen.getByTitle('My Process – 1 Instance in Version 1');

		await expect.element(version2Link).toBeVisible();
		await expect.element(version1Link).toBeVisible();
		await expect.element(version2Link.getByTestId('draining-indicator')).toBeVisible();
		await expect.element(version1Link.getByTestId('draining-indicator')).not.toBeInTheDocument();
	});
});
