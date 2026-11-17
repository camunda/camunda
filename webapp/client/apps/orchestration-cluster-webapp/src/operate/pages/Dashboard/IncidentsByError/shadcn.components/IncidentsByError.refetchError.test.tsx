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
import {mockGetIncidentProcessInstanceStatisticsByErrorEndpoint} from '#/shared-test-modules/mock-handlers';
import {createIncidentProcessInstanceStatisticsByError} from '#/shared-test-modules/api-mocks/incident-statistics';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {IncidentsByError} from './IncidentsByError';

describe('<IncidentsByError /> refetch error', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
		vi.useRealTimers();
	});

	it('should keep cached rows visible when a poll request fails', async ({worker}) => {
		worker.use(
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				once: true,
				successResponse: HttpResponse.json(
					createPaginatedResponse({
						items: [
							createIncidentProcessInstanceStatisticsByError({
								errorHashCode: 1,
								errorMessage: 'Alpha Connection Timeout',
								activeInstancesWithErrorCount: 5,
							}),
						],
						page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				successResponse: new HttpResponse(null, {status: 500}),
			}),
		);

		const settledRequests: string[] = [];
		worker.events.on('response:mocked', ({request}) => {
			settledRequests.push(`${request.method} ${new URL(request.url).pathname}`);
		});

		vi.useFakeTimers({toFake: ['setInterval', 'clearInterval']});

		const screen = await renderWithRouter(() => <IncidentsByError />, {path: '/operate-preview'});

		await expect.element(screen.getByText('Alpha Connection Timeout')).toBeVisible();
		await vi.advanceTimersByTimeAsync(5500);
		await vi.waitFor(() => {
			expect(
				settledRequests.filter((request) => request === 'POST /v2/incidents/statistics/process-instances-by-error'),
			).toHaveLength(2);
		});
		await expect.element(screen.getByText('Alpha Connection Timeout')).toBeVisible();
		await expect.element(screen.getByText("Couldn't fetch data")).not.toBeInTheDocument();
	});
});
