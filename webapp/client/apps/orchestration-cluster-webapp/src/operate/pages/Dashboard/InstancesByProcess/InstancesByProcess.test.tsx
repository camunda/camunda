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
} from '#/shared-test-modules/mock-handlers';
import {createProcessDefinitionInstanceStatistics} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
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
		);

		const screen = await renderWithRouter(() => <InstancesByProcess />, {path: '/operate'});

		await expect.element(screen.getByText('Alpha Process')).toBeVisible();
		await expect.element(screen.getByText('Beta Process')).toBeVisible();
		await expect.element(screen.getByText('Gamma Process')).toBeVisible();
	});

	it('should fetch the next page when scrolled to the bottom', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				schema: REQUEST_SCHEMA,
				successResponse: PAGE_1_RESPONSE,
				failureResponse: FAILURE_RESPONSE,
			}),
		);

		const screen = await renderWithRouter(
			() => (
				<div style={{height: '100px', display: 'flex', flexDirection: 'column'}}>
					<InstancesByProcess />
				</div>
			),
			{path: '/operate'},
		);

		await expect.element(screen.getByText('Alpha Process')).toBeVisible();

		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				schema: REQUEST_SCHEMA,
				successResponse: PAGE_2_RESPONSE,
				failureResponse: FAILURE_RESPONSE,
			}),
		);

		await userEvent.wheel(screen.getByTestId('instances-by-process-list'), {delta: {y: 10000}});

		await expect.element(screen.getByText('Page Two Process')).toBeVisible();
	});

	it('should show an error state when the request fails', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				successResponse: ERROR_RESPONSE,
			}),
		);

		const screen = await renderWithRouter(() => <InstancesByProcess />, {path: '/operate'});

		await expect.element(screen.getByText('Data could not be fetched')).toBeVisible();
	});
});
