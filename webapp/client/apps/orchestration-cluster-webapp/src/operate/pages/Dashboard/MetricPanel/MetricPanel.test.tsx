/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {describe, expect, vi} from 'vitest';
import {HttpResponse} from 'msw';
import {mockGetProcessDefinitionInstanceStatisticsEndpoint} from '#/shared-test-modules/mock-handlers';
import {createProcessDefinitionInstanceStatistics} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {MetricPanel} from './MetricPanel';

const ERROR_RESPONSE = new HttpResponse(null, {status: 500});

const STATS_WITH_INSTANCES = HttpResponse.json(
	createPaginatedResponse({
		items: [
			createProcessDefinitionInstanceStatistics({
				processDefinitionId: 'process-1',
				activeInstancesWithoutIncidentCount: 6,
				activeInstancesWithIncidentCount: 4,
			}),
		],
		page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
	}),
);

const STATS_EMPTY = HttpResponse.json(createPaginatedResponse());

describe('<MetricPanel />', () => {
	it('should render the running instances total and link it to running instances', async ({worker}) => {
		worker.use(mockGetProcessDefinitionInstanceStatisticsEndpoint({successResponse: STATS_WITH_INSTANCES}));

		const screen = await renderWithRouter(MetricPanel, {path: '/operate'});

		await expect.element(screen.getByText('10 Running Process Instances in total')).toBeVisible();
		await expect
			.element(screen.getByTestId('total-instances-link'))
			.toHaveAttribute(
				'href',
				'/operate/processes?active=true&incidents=true&completed=false&canceled=false&suspended=false',
			);
	});

	it('should link the total instances count to all instances when there are none running', async ({worker}) => {
		worker.use(mockGetProcessDefinitionInstanceStatisticsEndpoint({successResponse: STATS_EMPTY}));

		const screen = await renderWithRouter(MetricPanel, {path: '/operate'});

		await expect.element(screen.getByText('0 Running Process Instances in total')).toBeVisible();
		await expect
			.element(screen.getByTestId('total-instances-link'))
			.toHaveAttribute(
				'href',
				'/operate/processes?active=true&incidents=true&completed=true&canceled=true&suspended=false',
			);
	});

	it('should link the incident instances label to the processes page filtered by incidents', async ({worker}) => {
		worker.use(mockGetProcessDefinitionInstanceStatisticsEndpoint({successResponse: STATS_WITH_INSTANCES}));

		const screen = await renderWithRouter(MetricPanel, {path: '/operate'});

		await expect.element(screen.getByText('10 Running Process Instances in total')).toBeVisible();
		await expect
			.element(screen.getByTestId('incident-instances-link'))
			.toHaveAttribute(
				'href',
				'/operate/processes?active=false&incidents=true&completed=false&canceled=false&suspended=false',
			);
	});

	it('should link the active instances label to the processes page filtered by active instances', async ({worker}) => {
		worker.use(mockGetProcessDefinitionInstanceStatisticsEndpoint({successResponse: STATS_WITH_INSTANCES}));

		const screen = await renderWithRouter(MetricPanel, {path: '/operate'});

		await expect.element(screen.getByText('10 Running Process Instances in total')).toBeVisible();
		await expect
			.element(screen.getByTestId('active-instances-link'))
			.toHaveAttribute(
				'href',
				'/operate/processes?active=true&incidents=false&completed=false&canceled=false&suspended=false',
			);
	});

	it('should render a pending label and skeleton before the count resolves', async ({worker}) => {
		let statsRequests = 0;
		worker.events.on('request:start', ({request}) => {
			if (request.method === 'POST' && request.url.includes('/process-definitions/statistics/process-instances')) {
				statsRequests++;
			}
		});
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({successResponse: STATS_WITH_INSTANCES, delay: 'infinite'}),
		);

		const screen = await renderWithRouter(MetricPanel, {path: '/operate'});

		await expect.element(screen.getByText('Running Process Instances in total')).toBeVisible();
		await expect.element(screen.getByTestId('instances-bar-skeleton')).toBeVisible();
		await expect.poll(() => statsRequests).toBe(1);
		worker.events.removeAllListeners('request:start');
	});

	it('should show an explicit, scoped error instead of the running instances total', async ({worker}) => {
		worker.use(mockGetProcessDefinitionInstanceStatisticsEndpoint({successResponse: ERROR_RESPONSE}));

		const screen = await renderWithRouter(MetricPanel, {path: '/operate'});

		await expect.element(screen.getByText('Process statistics could not be fetched')).toBeVisible();
		await expect.element(screen.getByText('Refresh the page to try again')).toBeVisible();
		await expect.element(screen.getByTestId('total-instances-link')).not.toBeInTheDocument();
	});

	it('should recover once a later poll succeeds after a failed refetch', async ({worker}) => {
		vi.useFakeTimers({shouldAdvanceTime: true});
		worker.use(mockGetProcessDefinitionInstanceStatisticsEndpoint({successResponse: STATS_WITH_INSTANCES}));

		const screen = await renderWithRouter(MetricPanel, {path: '/operate'});
		await expect.element(screen.getByText('10 Running Process Instances in total')).toBeVisible();

		worker.use(mockGetProcessDefinitionInstanceStatisticsEndpoint({successResponse: ERROR_RESPONSE}));
		await vi.advanceTimersByTimeAsync(5000);
		await expect.element(screen.getByText('Process statistics could not be fetched')).toBeVisible();

		worker.use(mockGetProcessDefinitionInstanceStatisticsEndpoint({successResponse: STATS_WITH_INSTANCES}));
		await vi.advanceTimersByTimeAsync(5000);
		await expect.element(screen.getByText('10 Running Process Instances in total')).toBeVisible();
	});
});
