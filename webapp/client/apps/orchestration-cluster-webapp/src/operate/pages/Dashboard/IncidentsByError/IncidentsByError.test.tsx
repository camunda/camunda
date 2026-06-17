/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Suspense} from 'react';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {describe, expect} from 'vitest';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import {userEvent} from 'vitest/browser';
import {mockGetIncidentProcessInstanceStatisticsByErrorEndpointSequential} from '#/shared-test-modules/mock-handlers';
import {
	createIncidentProcessInstanceStatisticsByError,
	createIncidentProcessInstanceStatisticsByErrorResponse,
} from '#/shared-test-modules/api-mocks/incident-statistics';
import {IncidentsByError} from './IncidentsByError';

const REQUEST_SCHEMA = z.object({
	page: z.object({from: z.number(), limit: z.literal(50)}),
});
const FAILURE_RESPONSE = new HttpResponse(null, {status: 400});

const PAGE_1_RESPONSE = HttpResponse.json(
	createIncidentProcessInstanceStatisticsByErrorResponse({
		items: [
			createIncidentProcessInstanceStatisticsByError({errorHashCode: 1, errorMessage: 'Alpha Connection Timeout', activeInstancesWithErrorCount: 5}),
			createIncidentProcessInstanceStatisticsByError({errorHashCode: 2, errorMessage: 'Beta Null Pointer', activeInstancesWithErrorCount: 3}),
			createIncidentProcessInstanceStatisticsByError({errorHashCode: 3, errorMessage: 'Gamma Service Unavailable', activeInstancesWithErrorCount: 2}),
		],
		page: {totalItems: 60, startCursor: null, endCursor: null, hasMoreTotalItems: true},
	}),
);

const PAGE_2_RESPONSE = HttpResponse.json(
	createIncidentProcessInstanceStatisticsByErrorResponse({
		items: [
			createIncidentProcessInstanceStatisticsByError({errorHashCode: 51, errorMessage: 'Page Two Only Error', activeInstancesWithErrorCount: 1}),
		],
		page: {totalItems: 60, startCursor: null, endCursor: null, hasMoreTotalItems: false},
	}),
);

describe('<IncidentsByError />', () => {
	it('should render the list of incidents by error', async ({worker}) => {
		worker.use(mockGetIncidentProcessInstanceStatisticsByErrorEndpointSequential([PAGE_1_RESPONSE], {schema: REQUEST_SCHEMA, failureResponse: FAILURE_RESPONSE}));

		const screen = await renderWithRouter(
			() => (
				<Suspense>
					<IncidentsByError />
				</Suspense>
			),
			{path: '/operate'},
		);

		await expect.element(screen.getByText('Alpha Connection Timeout')).toBeVisible();
		await expect.element(screen.getByText('Beta Null Pointer')).toBeVisible();
		await expect.element(screen.getByText('Gamma Service Unavailable')).toBeVisible();
	});

	it('should fetch the next page when scrolled to the bottom', async ({worker}) => {
		worker.use(
			mockGetIncidentProcessInstanceStatisticsByErrorEndpointSequential(
				[PAGE_1_RESPONSE, PAGE_2_RESPONSE],
				{schema: REQUEST_SCHEMA, failureResponse: FAILURE_RESPONSE},
			),
		);

		const screen = await renderWithRouter(
			() => (
				<div style={{height: '100px', display: 'flex', flexDirection: 'column'}}>
					<Suspense>
						<IncidentsByError />
					</Suspense>
				</div>
			),
			{path: '/operate'},
		);

		await expect.element(screen.getByText('Alpha Connection Timeout')).toBeVisible();

		await userEvent.wheel(screen.getByTestId('incidents-by-error-list'), {delta: {y: 10000}});

		await expect.element(screen.getByText('Page Two Only Error')).toBeVisible();
	});
});
