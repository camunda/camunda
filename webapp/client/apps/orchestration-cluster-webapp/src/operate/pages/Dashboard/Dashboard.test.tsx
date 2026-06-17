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
import {
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockCurrentUserEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createProcessDefinitionInstanceStatistics,
	createProcessDefinitionInstanceStatisticsResponse,
} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {Dashboard} from './Dashboard';

const STATS_RESPONSE_WITH_INSTANCES = HttpResponse.json(
	createProcessDefinitionInstanceStatisticsResponse({
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

const STATS_RESPONSE_EMPTY = HttpResponse.json(createProcessDefinitionInstanceStatisticsResponse());

const CURRENT_USER_RESPONSE = HttpResponse.json({
	userId: 'test-user',
	displayName: 'Test User',
	c8Links: {},
});

describe('<Dashboard />', () => {
	it('should render metric panel with running instance counts', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				successResponse: STATS_RESPONSE_WITH_INSTANCES,
			}),
		);

		const screen = await renderWithRouter(Dashboard, {path: '/operate'});

		await expect.element(screen.getByTestId('metric-panel')).toBeVisible();
		await expect.element(screen.getByText('20 Running Process Instances in total')).toBeVisible();
	});

	it('should render tile titles when running instances exist', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				successResponse: STATS_RESPONSE_WITH_INSTANCES,
			}),
		);

		const screen = await renderWithRouter(Dashboard, {path: '/operate'});

		await expect.element(screen.getByText('Process Instances by Name')).toBeVisible();
		await expect.element(screen.getByText('Process Incidents by Error Message')).toBeVisible();
	});

	it('should render empty state when there are no running instances', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				successResponse: STATS_RESPONSE_EMPTY,
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
				successResponse: STATS_RESPONSE_EMPTY,
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
