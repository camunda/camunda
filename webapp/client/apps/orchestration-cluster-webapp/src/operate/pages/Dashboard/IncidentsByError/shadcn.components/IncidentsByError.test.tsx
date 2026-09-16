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
import {mockGetIncidentProcessInstanceStatisticsByErrorEndpoint} from '#/shared-test-modules/mock-handlers';
import {createIncidentProcessInstanceStatisticsByError} from '#/shared-test-modules/api-mocks/incident-statistics';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {IncidentsByError} from './IncidentsByError';

const REQUEST_SCHEMA = z.object({
	page: z.object({from: z.number(), limit: z.literal(50)}),
});
const FAILURE_RESPONSE = new HttpResponse(null, {status: 400});
const ERROR_RESPONSE = new HttpResponse(null, {status: 500});

const PAGE_1_RESPONSE = HttpResponse.json(
	createPaginatedResponse({
		items: [
			createIncidentProcessInstanceStatisticsByError({
				errorHashCode: 1,
				errorMessage: 'Alpha Connection Timeout',
				activeInstancesWithErrorCount: 5,
			}),
			createIncidentProcessInstanceStatisticsByError({
				errorHashCode: 2,
				errorMessage: 'Beta Null Pointer',
				activeInstancesWithErrorCount: 3,
			}),
		],
		page: {totalItems: 2, startCursor: null, endCursor: null, hasMoreTotalItems: false},
	}),
);

const EMPTY_RESPONSE = HttpResponse.json(createPaginatedResponse());

describe('<IncidentsByError />', () => {
	it('should render the list of incidents by error', async ({worker}) => {
		worker.use(
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				schema: REQUEST_SCHEMA,
				successResponse: PAGE_1_RESPONSE,
				failureResponse: FAILURE_RESPONSE,
			}),
		);

		const screen = await renderWithRouter(() => <IncidentsByError />, {path: '/operate-preview'});

		await expect.element(screen.getByText('Alpha Connection Timeout')).toBeVisible();
		await expect.element(screen.getByText('Beta Null Pointer')).toBeVisible();
	});

	it('should link each row to the processes page filtered by incidents', async ({worker}) => {
		worker.use(
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				schema: REQUEST_SCHEMA,
				successResponse: PAGE_1_RESPONSE,
				failureResponse: FAILURE_RESPONSE,
			}),
		);

		const screen = await renderWithRouter(() => <IncidentsByError />, {path: '/operate-preview'});

		await expect.element(screen.getByText('Alpha Connection Timeout')).toBeVisible();
		await expect
			.element(screen.getByText('Alpha Connection Timeout').element().closest('a')!)
			.toHaveAttribute(
				'href',
				'/operate/processes?errorMessage=Alpha+Connection+Timeout&incidents=true&active=false&completed=false&canceled=false&suspended=false',
			);
	});

	it('should show an error state when the request fails', async ({worker}) => {
		worker.use(
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				successResponse: ERROR_RESPONSE,
			}),
		);

		const screen = await renderWithRouter(() => <IncidentsByError />, {path: '/operate-preview'});

		await expect.element(screen.getByText('Data could not be fetched')).toBeVisible();
	});

	it('should show the healthy-processes empty state when there are no incidents', async ({worker}) => {
		worker.use(
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				schema: REQUEST_SCHEMA,
				successResponse: EMPTY_RESPONSE,
				failureResponse: FAILURE_RESPONSE,
			}),
		);

		const screen = await renderWithRouter(() => <IncidentsByError />, {path: '/operate-preview'});

		await expect.element(screen.getByText('Your processes are healthy')).toBeVisible();
	});
});
