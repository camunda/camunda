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
import {
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
	mockCurrentUserEndpoint,
	mockQueryProcessDefinitionsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createProcessDefinitionInstanceStatistics} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {createIncidentProcessInstanceStatisticsByError} from '#/shared-test-modules/api-mocks/incident-statistics';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {createQueryProcessDefinitionsResponse} from '#/shared-test-modules/api-mocks/process-definitions';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {Dashboard} from './Dashboard';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';

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
		],
		page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
	}),
);

const STATS_RESPONSE_EMPTY = HttpResponse.json(createPaginatedResponse());

const INCIDENTS_RESPONSE_WITH_ERRORS = HttpResponse.json(
	createPaginatedResponse({
		items: [
			createIncidentProcessInstanceStatisticsByError({
				errorHashCode: 1,
				errorMessage: 'Payment gateway request timed out',
				activeInstancesWithErrorCount: 5,
			}),
		],
		page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
	}),
);

const INCIDENTS_RESPONSE_EMPTY = HttpResponse.json(createPaginatedResponse());

const NO_DRAINING_RESPONSE = HttpResponse.json(createQueryProcessDefinitionsResponse());

const CURRENT_USER_RESPONSE = HttpResponse.json({
	userId: 'test-user',
	displayName: 'Test User',
	c8Links: {},
});

describe('<Dashboard />', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should render the sr-only dashboard title', async ({worker}) => {
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
			mockQueryProcessDefinitionsEndpoint({successResponse: NO_DRAINING_RESPONSE}),
		);

		const screen = await renderWithRouter(Dashboard, {path: '/operate-preview'});

		await expect.element(screen.getByRole('heading', {name: 'Dashboard'})).toBeInTheDocument();
	});

	it('should render the metric panel tile when running instances exist', async ({worker}) => {
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
			mockQueryProcessDefinitionsEndpoint({successResponse: NO_DRAINING_RESPONSE}),
		);

		const screen = await renderWithRouter(Dashboard, {path: '/operate-preview'});

		await expect.element(screen.getByTestId('metric-panel')).toBeVisible();
	});

	it('should render both tile titles when running instances exist', async ({worker}) => {
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
			mockQueryProcessDefinitionsEndpoint({successResponse: NO_DRAINING_RESPONSE}),
		);

		const screen = await renderWithRouter(Dashboard, {path: '/operate-preview'});

		await expect.element(screen.getByText('Process instances by name')).toBeVisible();
		await expect.element(screen.getByText('Process incidents by error message')).toBeVisible();
	});

	it('should render real process rows and real incident rows', async ({worker}) => {
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
			mockQueryProcessDefinitionsEndpoint({successResponse: NO_DRAINING_RESPONSE}),
		);

		const screen = await renderWithRouter(Dashboard, {path: '/operate-preview'});

		await expect.element(screen.getByText('Process One', {exact: false})).toBeVisible();
		await expect.element(screen.getByText('Payment gateway request timed out')).toBeVisible();
	});

	it('should render the no-instances empty state when there are no running instances', async ({worker}) => {
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

		const screen = await renderWithRouter(Dashboard, {path: '/operate-preview'});

		await expect.element(screen.getByText('No running process instances')).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'Learn more about Operate'})).toBeVisible();
	});

	it('should not render the incidents tile when there are no running instances', async ({worker}) => {
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

		const screen = await renderWithRouter(Dashboard, {path: '/operate-preview'});

		await expect.element(screen.getByText('Process instances by name')).toBeVisible();
		await expect.element(screen.getByText('Process incidents by error message')).not.toBeInTheDocument();
	});

	it('should render the go-to-modeler button when the current user has a modeler link', async ({worker}) => {
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
				successResponse: HttpResponse.json({
					userId: 'test-user',
					displayName: 'Test User',
					c8Links: {modeler: 'https://modeler.example.com'},
				}),
			}),
		);

		const screen = await renderWithRouter(Dashboard, {path: '/operate-preview'});

		await expect.element(screen.getByRole('link', {name: 'Go to Modeler'})).toBeVisible();
	});
});
