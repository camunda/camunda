/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {HttpResponse} from 'msw';
import {
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockQueryProcessDefinitionsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createProcessDefinitionInstanceStatistics} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {createQueryProcessDefinitionsResponse} from '#/shared-test-modules/api-mocks/process-definitions';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {InstancesByProcess} from './InstancesByProcess';

describe('<InstancesByProcess /> refetch error', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
		vi.useRealTimers();
	});

	it('should keep cached rows visible when a poll request fails', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				once: true,
				successResponse: HttpResponse.json(
					createPaginatedResponse({
						items: [
							createProcessDefinitionInstanceStatistics({
								processDefinitionId: 'p1',
								latestProcessDefinitionName: 'Alpha Process',
								activeInstancesWithoutIncidentCount: 5,
								activeInstancesWithIncidentCount: 1,
							}),
						],
						page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				successResponse: new HttpResponse(null, {status: 500}),
			}),
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
			}),
		);

		const settledRequests: string[] = [];
		worker.events.on('response:mocked', ({request}) => {
			settledRequests.push(`${request.method} ${new URL(request.url).pathname}`);
		});

		vi.useFakeTimers({toFake: ['setInterval', 'clearInterval']});

		const screen = await renderWithRouter(() => <InstancesByProcess />, {path: '/operate-preview'});

		await expect
			.element(screen.getByRole('link', {name: '1 Alpha Process – 6 instances in 1 version 5'}))
			.toBeVisible();
		await vi.advanceTimersByTimeAsync(5500);
		await vi.waitFor(() => {
			expect(
				settledRequests.filter((request) => request === 'POST /v2/process-definitions/statistics/process-instances'),
			).toHaveLength(2);
		});
		await expect
			.element(screen.getByRole('link', {name: '1 Alpha Process – 6 instances in 1 version 5'}))
			.toBeVisible();
		await expect.element(screen.getByText("Couldn't fetch data")).not.toBeInTheDocument();
	});
});
