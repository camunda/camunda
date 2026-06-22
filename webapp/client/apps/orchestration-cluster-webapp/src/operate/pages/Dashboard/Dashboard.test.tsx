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
import {
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
	mockCurrentUserEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createProcessDefinitionInstanceStatistics} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {createIncidentProcessInstanceStatisticsByError} from '#/shared-test-modules/api-mocks/incident-statistics';
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
const INCIDENTS_REQUEST_SCHEMA = z.object({
	page: z.object({from: z.literal(0), limit: z.literal(50)}),
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
			createProcessDefinitionInstanceStatistics({
				processDefinitionId: 'process-2',
				latestProcessDefinitionName: 'Process Two',
				hasMultipleVersions: true,
				activeInstancesWithoutIncidentCount: 5,
				activeInstancesWithIncidentCount: 2,
			}),
		],
		page: {totalItems: 2, startCursor: null, endCursor: null, hasMoreTotalItems: false},
	}),
);

const STATS_RESPONSE_EMPTY = HttpResponse.json(createPaginatedResponse());

const INCIDENTS_RESPONSE_WITH_ERRORS = HttpResponse.json(
	createPaginatedResponse({
		items: [
			createIncidentProcessInstanceStatisticsByError({
				errorHashCode: 1,
				errorMessage: 'Connection timeout',
				activeInstancesWithErrorCount: 5,
			}),
			createIncidentProcessInstanceStatisticsByError({
				errorHashCode: 2,
				errorMessage: 'Null pointer exception',
				activeInstancesWithErrorCount: 3,
			}),
		],
		page: {totalItems: 2, startCursor: null, endCursor: null, hasMoreTotalItems: false},
	}),
);

const INCIDENTS_RESPONSE_EMPTY = HttpResponse.json(createPaginatedResponse());

const CURRENT_USER_RESPONSE = HttpResponse.json({
	userId: 'test-user',
	displayName: 'Test User',
	c8Links: {},
});

describe('<Dashboard />', () => {
	it('should render metric panel with running instance counts', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				schema: PROCESS_STATS_REQUEST_SCHEMA,
				successResponse: STATS_RESPONSE_WITH_INSTANCES,
				failureResponse: FAILURE_RESPONSE,
			}),
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				schema: INCIDENTS_REQUEST_SCHEMA,
				successResponse: INCIDENTS_RESPONSE_WITH_ERRORS,
				failureResponse: FAILURE_RESPONSE,
			}),
		);

		const screen = await renderWithRouter(Dashboard, {path: '/operate'});

		await expect.element(screen.getByTestId('metric-panel')).toBeVisible();
		await expect.element(screen.getByText('20 Running Process Instances in total')).toBeVisible();
	});

	it('should render tile titles when running instances exist', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				schema: PROCESS_STATS_REQUEST_SCHEMA,
				successResponse: STATS_RESPONSE_WITH_INSTANCES,
				failureResponse: FAILURE_RESPONSE,
			}),
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				schema: INCIDENTS_REQUEST_SCHEMA,
				successResponse: INCIDENTS_RESPONSE_WITH_ERRORS,
				failureResponse: FAILURE_RESPONSE,
			}),
		);

		const screen = await renderWithRouter(Dashboard, {path: '/operate'});

		await expect.element(screen.getByText('Process Instances by Name')).toBeVisible();
		await expect.element(screen.getByText('Process Incidents by Error Message')).toBeVisible();
	});

	it('should render instances by process list', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				schema: PROCESS_STATS_REQUEST_SCHEMA,
				successResponse: STATS_RESPONSE_WITH_INSTANCES,
				failureResponse: FAILURE_RESPONSE,
			}),
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				schema: INCIDENTS_REQUEST_SCHEMA,
				successResponse: INCIDENTS_RESPONSE_WITH_ERRORS,
				failureResponse: FAILURE_RESPONSE,
			}),
		);

		const screen = await renderWithRouter(Dashboard, {path: '/operate'});

		await expect.element(screen.getByTestId('instances-by-process-list')).toBeVisible();
		await expect.element(screen.getByText('Process One')).toBeVisible();
		await expect.element(screen.getByText('Process Two')).toBeVisible();
	});

	it('should render incidents by error list', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				schema: PROCESS_STATS_REQUEST_SCHEMA,
				successResponse: STATS_RESPONSE_WITH_INSTANCES,
				failureResponse: FAILURE_RESPONSE,
			}),
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				schema: INCIDENTS_REQUEST_SCHEMA,
				successResponse: INCIDENTS_RESPONSE_WITH_ERRORS,
				failureResponse: FAILURE_RESPONSE,
			}),
		);

		const screen = await renderWithRouter(Dashboard, {path: '/operate'});

		await expect.element(screen.getByTestId('incidents-by-error-list')).toBeVisible();
		await expect.element(screen.getByText('Connection timeout')).toBeVisible();
		await expect.element(screen.getByText('Null pointer exception')).toBeVisible();
	});

	it('should render healthy empty state when there are no incidents', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				schema: PROCESS_STATS_REQUEST_SCHEMA,
				successResponse: STATS_RESPONSE_WITH_INSTANCES,
				failureResponse: FAILURE_RESPONSE,
			}),
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				schema: INCIDENTS_REQUEST_SCHEMA,
				successResponse: INCIDENTS_RESPONSE_EMPTY,
				failureResponse: FAILURE_RESPONSE,
			}),
		);

		const screen = await renderWithRouter(Dashboard, {path: '/operate'});

		await expect.element(screen.getByText('Your processes are healthy')).toBeVisible();
	});

	it('should render empty state when there are no running instances', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				schema: PROCESS_STATS_REQUEST_SCHEMA,
				successResponse: STATS_RESPONSE_EMPTY,
				failureResponse: FAILURE_RESPONSE,
			}),
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				schema: INCIDENTS_REQUEST_SCHEMA,
				successResponse: INCIDENTS_RESPONSE_EMPTY,
				failureResponse: FAILURE_RESPONSE,
			}),
			mockCurrentUserEndpoint({
				successResponse: CURRENT_USER_RESPONSE,
			}),
		);

		const screen = await renderWithRouter(Dashboard, {path: '/operate'});

		await expect.element(screen.getByText('No running process instances')).toBeVisible();
	});

	it('should not render incidents tile when there are no running instances', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				schema: PROCESS_STATS_REQUEST_SCHEMA,
				successResponse: STATS_RESPONSE_EMPTY,
				failureResponse: FAILURE_RESPONSE,
			}),
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				schema: INCIDENTS_REQUEST_SCHEMA,
				successResponse: INCIDENTS_RESPONSE_EMPTY,
				failureResponse: FAILURE_RESPONSE,
			}),
			mockCurrentUserEndpoint({
				successResponse: CURRENT_USER_RESPONSE,
			}),
		);

		const screen = await renderWithRouter(Dashboard, {path: '/operate'});

		await expect.element(screen.getByText('Process Instances by Name')).toBeVisible();
		await expect.element(screen.getByText('Process Incidents by Error Message')).not.toBeInTheDocument();
	});
});
