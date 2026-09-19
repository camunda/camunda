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
import {mockGetProcessDefinitionInstanceStatisticsEndpoint} from '#/shared-test-modules/mock-handlers';
import {createProcessDefinitionInstanceStatistics} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {Dashboard} from './Dashboard';

const PROCESS_STATS_REQUEST_SCHEMA = z.object({
	sort: z.array(
		z.object({
			field: z.enum(['activeInstancesWithoutIncidentCount', 'activeInstancesWithIncidentCount']),
			order: z.literal('desc'),
		}),
	),
	page: z.object({from: z.number(), limit: z.number()}).optional(),
});
const FAILURE_RESPONSE = new HttpResponse(null, {status: 400});

const STATS_RESPONSE_WITH_INSTANCES = HttpResponse.json(
	createPaginatedResponse({
		items: [
			createProcessDefinitionInstanceStatistics({
				processDefinitionId: 'process-1',
				latestProcessDefinitionName: 'Process One',
				activeInstancesWithoutIncidentCount: 10,
				activeInstancesWithIncidentCount: 3,
			}),
		],
		page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
	}),
);

const STATS_RESPONSE_EMPTY = HttpResponse.json(createPaginatedResponse());

describe('<Dashboard />', () => {
	it('should render the sr-only dashboard title', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				schema: PROCESS_STATS_REQUEST_SCHEMA,
				successResponse: STATS_RESPONSE_WITH_INSTANCES,
				failureResponse: FAILURE_RESPONSE,
			}),
		);

		const screen = await renderWithRouter(Dashboard, {path: '/operate-preview'});

		await expect.element(screen.getByRole('heading', {name: 'Dashboard'})).toBeInTheDocument();
	});

	it('should render the metric panel card', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				schema: PROCESS_STATS_REQUEST_SCHEMA,
				successResponse: STATS_RESPONSE_WITH_INSTANCES,
				failureResponse: FAILURE_RESPONSE,
			}),
		);

		const screen = await renderWithRouter(Dashboard, {path: '/operate-preview'});

		await expect.element(screen.getByTestId('metric-panel')).toBeVisible();
	});

	it('should render both list titles when there are running instances', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				schema: PROCESS_STATS_REQUEST_SCHEMA,
				successResponse: STATS_RESPONSE_WITH_INSTANCES,
				failureResponse: FAILURE_RESPONSE,
			}),
		);

		const screen = await renderWithRouter(Dashboard, {path: '/operate-preview'});

		await expect.element(screen.getByText('Process Instances by Name')).toBeVisible();
		await expect.element(screen.getByText('Process Incidents by Error Message')).toBeVisible();
	});

	it('should only render the processes list title when there are no running instances', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				schema: PROCESS_STATS_REQUEST_SCHEMA,
				successResponse: STATS_RESPONSE_EMPTY,
				failureResponse: FAILURE_RESPONSE,
			}),
		);

		const screen = await renderWithRouter(Dashboard, {path: '/operate-preview'});

		await expect.element(screen.getByText('Process Instances by Name')).toBeVisible();
		await expect.element(screen.getByText('Process Incidents by Error Message')).not.toBeInTheDocument();
	});
});
